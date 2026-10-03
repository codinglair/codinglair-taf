#requires -Version 7.0
<#
.SYNOPSIS
Runs all checks requested for local TAF verification. Exit 0 means every check passed.
.DESCRIPTION
Run with pwsh -File build-support/local/local-verify.ps1. Requires Java 25,
Docker/Compose, the qualified Android image and KVM, installed Playwright browsers,
Allure on PATH, and SAUCE_DEMO_PASSWORD supplied through the environment.
Maven uses the existing wrapper and settings. Release staging writes to the local
repository configured by the release-staging profile. No checks are skipped.
Output includes final summaries, requested test results, Docker output and errors.
No output files or secret values are written.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest

function Test-MavenOutput {
    param([string[]] $Lines, [switch] $Consumer)
    $text = $Lines -join "`n"
    # Tests/plugins may intentionally emit error diagnostics. Display those
    # separately; they do not override the requested Maven success criteria.
    if ($text -notmatch '(?m)^\[INFO\]\s+BUILD SUCCESS\s*$') { return $false }
    $rows = @($Lines | Where-Object { $_ -match '^\[INFO\].*\.{2,}\s+(SUCCESS|FAILURE|SKIPPED)\b' })
    if ($Consumer) {
        return ($text -match 'Passed:\s*[1-9]\d*,\s*Failed:\s*0,\s*Errors:\s*0,\s*Skipped:\s*0' -and
            @($rows | Where-Object { $_ -notmatch '\.{2,}\s+SUCCESS\b' }).Count -eq 0)
    }
    return ($text -match 'Reactor Summary' -and $rows.Count -gt 0 -and
        @($rows | Where-Object { $_ -notmatch '\.{2,}\s+SUCCESS\b' }).Count -eq 0)
}

function Select-MavenDisplayLines {
    param([string[]] $Lines, [switch] $Consumer, [string] $Detail)
    # Consumer builds can contain many nested Maven reactor summaries. Only
    # the final outer summary belongs in this command's display block.
    $marker = if ($Consumer) { '^\[INFO\]\s+Build Summary:' } else { '^\[INFO\]\s+Reactor Summary' }
    $start = -1
    for ($i = 0; $i -lt $Lines.Count; $i++) {
        if ($Lines[$i] -match $marker) { $start = $i }
    }
    $selected = [System.Collections.Generic.HashSet[int]]::new()
    if ($start -ge 0) {
        if ($start -gt 0 -and $Lines[$start - 1] -match '^\[INFO\]\s+-+$') { $null = $selected.Add($start - 1) }
        for ($i = $start; $i -lt $Lines.Count; $i++) {
            # Whitelist the summary/footer so later plugin logs cannot leak in.
            if ($Lines[$i] -match '^\[INFO\]\s*(?:$|-+$|Reactor Summary|Build Summary:|.*\.{2,}\s+(?:SUCCESS|FAILURE|SKIPPED)\b|Passed:|BUILD (?:SUCCESS|FAILURE)|Total time:|Finished at:)') {
                $null = $selected.Add($i)
            }
        }
    }
    if ($Detail -eq 'Android') {
        $resultStart = -1
        for ($i = 0; $i -lt $start; $i++) {
            if ($Lines[$i] -match '^\[INFO\]\s+Results:') { $resultStart = $i }
        }
        if ($resultStart -ge 0) {
            for ($i = $resultStart; $i -lt $start; $i++) {
                if ($Lines[$i] -match '^\[INFO\]\s*$|^\[INFO\]\s+(?:Results:|Tests run:|--- jar:)') { $null = $selected.Add($i) }
            }
        }
    }
    elseif ($Detail -eq 'Bdd') {
        for ($i = $start - 1; $i -ge 0; $i--) {
            if ($Lines[$i] -match '^\[INFO\]\s+Tests run:') { $null = $selected.Add($i); break }
        }
    }
    elseif ($Detail -eq 'Dependency') {
        for ($i = $start - 1; $i -ge 0; $i--) {
            if ($Lines[$i] -match '^\[INFO\]\s+Found Resolved Dependency/DependencyManagement mismatches:') {
                for ($j = $i; $j -lt $start; $j++) {
                    if ($Lines[$j] -match '^\[INFO\]\s+-+$') { break }
                    $null = $selected.Add($j)
                }
                break
            }
        }
    }
    for ($i = 0; $i -lt $Lines.Count; $i++) {
        if ($selected.Contains($i) -or $Lines[$i] -match '^\[ERROR\]|\bERROR\b|\bException\b' -or
            ($Detail -eq 'Release' -and $Lines[$i] -match 'Skipping artifact deployment')) { $Lines[$i] }
    }
}

function Invoke-CommandCheck {
    param([string] $Executable, [string[]] $Arguments, [ValidateSet('Maven', 'Consumer', 'Leaks', 'Native')] [string] $Kind = 'Native', [string] $Detail)
    Write-Host ("## Command: {0} {1}" -f $Executable, ($Arguments -join ' '))
    $null = Get-Command $Executable -ErrorAction Stop
    $lines = [System.Collections.Generic.List[string]]::new()
    # Native stderr is evidence, not a PowerShell terminating error. Exit codes
    # and explicit criteria below determine success, including under caller preferences.
    $ErrorActionPreference = 'Continue'
    $PSNativeCommandUseErrorActionPreference = $false
    & $Executable @Arguments 2>&1 | ForEach-Object {
        $line = ([string] $_) -replace '\x1b\[[0-9;]*[A-Za-z]', ''
        $lines.Add($line)
    }
    $code = $LASTEXITCODE
    Write-Host '## Output'
    if ($Kind -in @('Maven', 'Consumer')) {
        Select-MavenDisplayLines -Lines $lines.ToArray() -Consumer:($Kind -eq 'Consumer') -Detail $Detail | ForEach-Object { Write-Host $_ }
    }
    else { $lines | ForEach-Object { Write-Host $_ } }
    $valid = switch ($Kind) {
        Maven { Test-MavenOutput -Lines $lines.ToArray() }
        Consumer { Test-MavenOutput -Lines $lines.ToArray() -Consumer }
        Leaks { ($lines -join "`n") -match '\bno leaks found\b' }
        Native { $true }
    }
    if ($code -ne 0 -or -not $valid) {
        throw "Command failed: exit code $code; output criteria satisfied: $valid."
    }
}

function Get-ComposeResources {
    param([string] $Project, [switch] $Networks)
    $arguments = if ($Networks) { @('network', 'ls', '--quiet', '--filter', "label=com.docker.compose.project=$Project") }
        else { @('ps', '--all', '--quiet', '--filter', "label=com.docker.compose.project=$Project") }
    $output = @(& docker @arguments 2>&1)
    if ($LASTEXITCODE -ne 0) { throw 'Unable to query Docker project resources.' }
    return @($output | Where-Object { -not [string]::IsNullOrWhiteSpace([string] $_) })
}

function Invoke-AndroidCheck {
    $project = 'taf-local-verify-' + [guid]::NewGuid().ToString('N').Substring(0, 12)
    $compose = @('compose', '--project-name', $project, '--file', 'containers/android-emulator/google/compose.qualify.yaml')
    try {
        Invoke-CommandCheck docker ($compose + @('up', '--detach', '--wait', '--wait-timeout', '360'))
        $ids = @(Get-ComposeResources $project)
        if ($ids.Count -ne 2) { throw 'Expected both Android and Appium containers.' }
        foreach ($id in $ids) {
            $health = @(& docker inspect --format '{{.State.Health.Status}}' $id 2>&1)
            if ($LASTEXITCODE -ne 0 -or ($health -join '').Trim() -ne 'healthy') { throw 'Android/Appium container is not healthy.' }
            Write-Host "Container $id Healthy"
        }
        $env:TAF_ANDROID_APPIUM_URL = 'http://127.0.0.1:4724'
        $env:TAF_ANDROID_DEVICE_NAME = 'taf-api34'
        $env:TAF_ANDROID_DEVICE_ID = 'android-emulator:5555'
        $env:TAF_ANDROID_APP_PACKAGE = 'com.android.settings'
        $env:TAF_ANDROID_APP_ACTIVITY = '.Settings'
        Invoke-CommandCheck .\mvnw.cmd @('-pl', 'codinglair-taf-runtime/taf-mobile-appium', '-am', 'verify', '-Pandroid-emulator') Maven Android
    }
    finally {
        Invoke-CommandCheck docker ($compose + @('down', '--remove-orphans'))
        if (@(Get-ComposeResources $project).Count -ne 0 -or
            @(Get-ComposeResources $project -Networks).Count -ne 0) { throw 'Android containers or networks remain after cleanup.' }
        Write-Host 'Android containers and networks removed.'
    }
}

function Invoke-PlaywrightCheck {
    param([string] $Suite, [string] $Directory)
    if ([string]::IsNullOrWhiteSpace($env:SAUCE_DEMO_PASSWORD)) { throw 'SAUCE_DEMO_PASSWORD must be provided through the environment.' }
    $env:SAUCE_DEMO_BASE_URL = 'https://www.saucedemo.com'
    $env:SAUCE_DEMO_PASSWORD_REF = 'secret://env/SAUCE_DEMO_PASSWORD'
    $env:TEST_ENVIRONMENT = 'local'
    $env:TAF_ALLURE_EXECUTABLE = (Get-Command allure -ErrorAction Stop).Source
    $detail = if ($Suite -eq 'bdd-suite') { 'Bdd' } else { '' }
    Invoke-CommandCheck .\mvnw.cmd @('-pl', 'demos/playwright-sauce-demo', '-am', "-P$Suite", "-Dallure.results.directory=target/$Directory", 'test') Maven $detail
}

function Invoke-LocalVerification {
    $checks = [ordered]@{
        'GitLeaks' = { Invoke-CommandCheck docker @('run', '--rm', '--network', 'none', '--mount', "type=bind,source=$((Get-Location).Path),target=/repo,readonly", '--workdir', '/repo', 'ghcr.io/gitleaks/gitleaks:v8.30.1@sha256:c00b6bd0aeb3071cbcb79009cb16a60dd9e0a7c60e2be9ab65d25e6bc8abbb7f', 'git', '--no-banner', '--redact=100', '--config', '/repo/.gitleaks.toml', '--log-opts=--all', '/repo') Leaks }
        'Full reactor' = { Invoke-CommandCheck .\mvnw.cmd @('clean', 'install', '-am') Maven }
        'Full reactor with containers' = { Invoke-CommandCheck .\mvnw.cmd @('clean', 'install', '-am', '-Pcontainers') Maven }
        'Conformance test' = { Invoke-CommandCheck .\mvnw.cmd @('-pl', 'codinglair-taf-runtime/taf-consumer-conformance', '-am', '-Pdependency-analysis,architecture', 'verify') Maven }
        'Release staging' = { Invoke-CommandCheck .\mvnw.cmd @('clean', 'deploy', '-Prelease-staging') Maven Release }
        'Consumer smoke' = { Invoke-CommandCheck .\mvnw.cmd @('-N', '-Pconsumer-smoke', 'verify') Consumer }
        'Android Smoke test' = { Invoke-AndroidCheck }
        'Playwright Smoke Test Functional Suite' = { Invoke-PlaywrightCheck 'functional-suite' 'allure-results-functional' }
        'Playwright Smoke Test BDD Suite' = { Invoke-PlaywrightCheck 'bdd-suite' 'allure-results-bdd' }
        'Appium offline architecture and dependency analysis' = { Invoke-CommandCheck .\mvnw.cmd @('-o', '-pl', 'codinglair-taf-runtime/taf-mobile-appium', '-am', 'verify', '-Parchitecture,dependency-analysis') Maven Dependency }
    }
    $results = [ordered]@{}
    $variables = @('TAF_ANDROID_APPIUM_URL', 'TAF_ANDROID_DEVICE_NAME', 'TAF_ANDROID_DEVICE_ID', 'TAF_ANDROID_APP_PACKAGE', 'TAF_ANDROID_APP_ACTIVITY', 'SAUCE_DEMO_BASE_URL', 'SAUCE_DEMO_PASSWORD_REF', 'TEST_ENVIRONMENT', 'TAF_ALLURE_EXECUTABLE')
    Push-Location (Join-Path $PSScriptRoot '../..')
    try {
        foreach ($check in $checks.GetEnumerator()) {
            Write-Host "`n# $($check.Key)"
            $saved = @{}
            foreach ($name in $variables) { $saved[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
            try {
                & $check.Value
                $results[$check.Key] = 'PASS'
            }
            catch {
                Write-Host "ERROR: $($_.Exception.Message)" -ForegroundColor Red
                $results[$check.Key] = 'FAIL'
            }
            finally {
                foreach ($name in $variables) { [Environment]::SetEnvironmentVariable($name, $saved[$name], 'Process') }
            }
            Write-Host "## Success criteria: $($results[$check.Key])"
        }
    }
    finally { Pop-Location }
    Write-Host "`n# Verification summary"
    foreach ($result in $results.GetEnumerator()) { Write-Host "$($result.Value)  $($result.Key)" }
    return [int] (@($results.Values | Where-Object { $_ -eq 'FAIL' }).Count -gt 0)
}

# Dot sourcing exposes functions for the dependency-free tests without running checks.
if ($MyInvocation.InvocationName -ne '.') { exit (Invoke-LocalVerification) }
