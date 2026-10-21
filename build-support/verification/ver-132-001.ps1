[CmdletBinding()]
param(
    [switch]$SkipStage,
    [string]$EvidenceResults = "target/consumer-smoke/aws-messaging/target/allure-results",
    [string]$EvidenceReport = "target/consumer-smoke/aws-messaging/target/allure-report/index.html",
    [string]$AllureExecutable = "",
    [string]$Boot3JavaHome = $env:VER132_BOOT3_JAVA_HOME,
    [string]$Boot4JavaHome = $env:JAVA_HOME
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$staging = Join-Path $root "target/staging-repository"
$consumers = Join-Path $root "target/consumer-smoke"
$output = Join-Path $root "target/ver-132-001"
$repository = Join-Path $root "target/consumer-repository"
$matrix = Join-Path $root "release/consumer-smoke/aws-messaging/evidence-expectations.json"
$maven = if ($IsWindows -or $env:OS -eq "Windows_NT") { Join-Path $root "mvnw.cmd" } else { Join-Path $root "mvnw" }
$taskTemp = Join-Path ([IO.Path]::GetTempPath()) "codinglair-ver-132-001-$PID"
New-Item -ItemType Directory -Force -Path $taskTemp | Out-Null
$env:TEMP = $taskTemp
$env:TMP = $taskTemp
$env:TMPDIR = $taskTemp
$existingMavenOptions = ($env:MAVEN_OPTS -replace '(?i)-Djava\.io\.tmpdir=("[^"]*"|\S+)', '').Trim()
$env:MAVEN_OPTS = "$existingMavenOptions -Djava.io.tmpdir=$taskTemp".Trim()

function Assert-WorkspacePath([string]$Path) {
    $full = [IO.Path]::GetFullPath($Path)
    $prefix = $root.TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
    if (-not $full.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "VER-132-001 path escapes the prepared checkout: $full"
    }
    return $full
}

function Invoke-Maven([string[]]$Arguments, [string]$Log) {
    $logDirectory = Split-Path -Parent $Log
    New-Item -ItemType Directory -Force -Path $logDirectory | Out-Null
    Push-Location $root
    try {
        & $maven @Arguments 2>&1 | Tee-Object -FilePath $Log
        if ($LASTEXITCODE -ne 0) { throw "Maven failed with exit code $LASTEXITCODE; see $Log" }
    } finally { Pop-Location }
}

function Get-ProtectedDigest([string]$SutRoot) {
    $files = Get-ChildItem -LiteralPath $SutRoot -Recurse -File |
        Where-Object { $_.FullName -notmatch '[\\/]target[\\/]' } | Sort-Object FullName
    $material = foreach ($file in $files) {
        "$($file.FullName.Substring($SutRoot.Length))=$((Get-FileHash $file.FullName -Algorithm SHA256).Hash)"
    }
    $bytes = [Text.Encoding]::UTF8.GetBytes(($material -join "`n"))
    $sha = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($sha.ComputeHash($bytes))).Replace("-", "").ToLowerInvariant() }
    finally { $sha.Dispose() }
}

function Verify-SutBoundary([string]$Name, [string]$JavaHome) {
    $sut = Assert-WorkspacePath (Join-Path $root "release/sut-boundary/$Name")
    $before = Get-ProtectedDigest $sut
    $previousJavaHome = $env:JAVA_HOME
    if (-not [string]::IsNullOrWhiteSpace($JavaHome)) { $env:JAVA_HOME = $JavaHome }
    Push-Location $sut
    try {
        & $maven -B -ntp "-Dmaven.repo.local=$repository" clean package dependency:tree `
            "-DoutputFile=target/dependency-tree.txt" 2>&1 |
            Tee-Object -FilePath (Join-Path $output "$Name-build.log") | Out-Host
        if ($LASTEXITCODE -ne 0) { throw "$Name independent SUT build failed" }
    } finally {
        Pop-Location
        $env:JAVA_HOME = $previousJavaHome
    }
    $after = Get-ProtectedDigest $sut
    if ($before -ne $after) { throw "$Name protected SUT files changed during qualification" }
    $tree = Get-Content -Raw -LiteralPath (Join-Path $sut "target/dependency-tree.txt")
    if ($tree.Contains("com.codinglair.taf")) { throw "$Name production graph is contaminated by TAF" }
    $jar = Get-ChildItem (Join-Path $sut "target") -File -Filter "*.jar" |
        Where-Object { $_.Name -notlike "*.original" } | Select-Object -First 1
    $entries = & jar tf $jar.FullName
    if ($entries -match 'codinglair/taf|src/test|automation') {
        throw "$Name packaged SUT contains TAF or automation output"
    }
    Copy-Item -LiteralPath (Join-Path $sut "target/dependency-tree.txt") `
        -Destination (Join-Path $output "$Name-dependency-tree.txt")
    return $before
}

function Invoke-StandaloneSutQualification([string]$Name, [int]$Port, [string]$JavaHome) {
    $sut = Assert-WorkspacePath (Join-Path $root "release/sut-boundary/$Name")
    $jar = Get-ChildItem (Join-Path $sut "target") -File -Filter "*.jar" |
        Where-Object { $_.Name -notlike "*.original" } | Select-Object -First 1
    if (-not $jar) { throw "$Name packaged SUT is missing" }
    $stdout = Join-Path $output "$Name-sut.stdout.log"
    $stderr = Join-Path $output "$Name-sut.stderr.log"
    $java = if ([string]::IsNullOrWhiteSpace($JavaHome)) { "java" } else {
        $javaRelativePath = if ($IsWindows -or $env:OS -eq "Windows_NT") {
            "bin/java.exe"
        } else {
            "bin/java"
        }
        Join-Path $JavaHome $javaRelativePath
    }
    $processArguments = @{
        FilePath = $java
        ArgumentList = @("-jar", $jar.FullName, "--server.port=$Port")
        PassThru = $true
        RedirectStandardOutput = $stdout
        RedirectStandardError = $stderr
    }
    if ($IsWindows -or $env:OS -eq "Windows_NT") { $processArguments.WindowStyle = "Hidden" }
    $process = Start-Process @processArguments
    try {
        $ready = $false
        for ($attempt = 1; $attempt -le 60; $attempt++) {
            if ($process.HasExited) { throw "$Name SUT exited before becoming ready; see $stderr" }
            try {
                $response = Invoke-WebRequest -Uri "http://127.0.0.1:$Port/qualification" `
                    -TimeoutSec 1 -UseBasicParsing
                if ($response.StatusCode -eq 200) { $ready = $true; break }
            } catch {
                Start-Sleep -Milliseconds 250
            }
        }
        if (-not $ready) { throw "$Name SUT did not become ready on port $Port" }
        $automationPom = Join-Path $consumers "generated-web-api-database/pom.xml"
        Invoke-Maven @("-B", "-ntp", "-f", $automationPom,
            "-Dmaven.repo.local=$repository",
            "-Dsurefire.suiteXmlFiles=src/test/resources/testng-sut.xml",
            "-Dsut.base-url=http://127.0.0.1:$Port",
            "-Dsut.expected-response=${Name}-ok", "test") `
            (Join-Path $output "$Name-external-automation.log")
    } finally {
        if (-not $process.HasExited) {
            Stop-Process -Id $process.Id -Force
            $process.WaitForExit()
        }
        if (Get-Process -Id $process.Id -ErrorAction SilentlyContinue) {
            throw "$Name SUT process $($process.Id) was not cleaned up"
        }
    }
}

$output = Assert-WorkspacePath $output
$staging = Assert-WorkspacePath $staging
$consumers = Assert-WorkspacePath $consumers
$repository = Assert-WorkspacePath $repository
$staleWorkspaceTemp = Join-Path $output "tmp"
if (Test-Path -LiteralPath $staleWorkspaceTemp) {
    Remove-Item -LiteralPath $staleWorkspaceTemp -Recurse -Force
}

if (-not $SkipStage) {
    $stageLog = Join-Path $taskTemp "release-staging.log"
    Invoke-Maven @("-B", "-ntp", "clean", "deploy", "-Prelease-staging") $stageLog
}
if (-not (Test-Path -LiteralPath $staging)) { throw "Candidate staging repository is missing: $staging" }
New-Item -ItemType Directory -Force -Path $output | Out-Null
if (-not $SkipStage) {
    Move-Item -LiteralPath $stageLog -Destination (Join-Path $output "release-staging.log") -Force
}

$boot3Before = Verify-SutBoundary "boot3" $Boot3JavaHome
$boot4Before = Verify-SutBoundary "boot4" $Boot4JavaHome

$generatedAcceptance = Assert-WorkspacePath (Join-Path $root "target/run-132-cucumber-acceptance")
$generatedMatrix = Assert-WorkspacePath (Join-Path $root "target/run-132-negative-matrix")
foreach ($generatedTarget in @($generatedAcceptance, $generatedMatrix)) {
    if (Test-Path -LiteralPath $generatedTarget) {
        Remove-Item -LiteralPath $generatedTarget -Recurse -Force
    }
}
Invoke-Maven @("-B", "-ntp", "-pl", "taf-mcp-server/taf-mcp-tools",
    "-Dtest=CapabilityContributionCatalogTest", "-Dscf.cucumber.fixture.root=$generatedAcceptance",
    "test") `
    (Join-Path $output "generator.log")
if ($boot3Before -ne (Verify-SutBoundary "boot3" $Boot3JavaHome)) {
    throw "boot3 protected SUT files changed after official generation"
}
if ($boot4Before -ne (Verify-SutBoundary "boot4" $Boot4JavaHome)) {
    throw "boot4 protected SUT files changed after official generation"
}
& (Join-Path $root "tools/verification/run-132-consumer-conformance.ps1")
if (-not $?) { throw "RUN-132 expected-failure matrix did not complete successfully" }

Invoke-Maven @("-B", "-ntp", "-N", "-Pconsumer-smoke", "verify") (Join-Path $output "consumer-smoke.log")

Invoke-StandaloneSutQualification "boot3" 18083 $Boot3JavaHome
Invoke-StandaloneSutQualification "boot4" 18084 $Boot4JavaHome

$pomFiles = Get-ChildItem -LiteralPath $staging -Recurse -File -Filter "*.pom"
if ($pomFiles.Count -eq 0) { throw "No staged candidate POMs were found" }
$manifest = foreach ($file in Get-ChildItem -LiteralPath $staging -Recurse -File | Sort-Object FullName) {
    [pscustomobject]@{
        path = $file.FullName.Substring($staging.Length + 1).Replace('\', '/')
        sha256 = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        bytes = $file.Length
    }
}
$manifest | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath (Join-Path $output "candidate-manifest.json") -Encoding utf8

$results = Get-ChildItem -LiteralPath $consumers -Recurse -File -Filter "TEST-*.xml" |
    Where-Object { $_.DirectoryName -notmatch '[\\/]junitreports$' }
if ($results.Count -eq 0) { throw "Consumer qualification produced no Surefire result files" }
$testCount = 0
$dispositions = @()
foreach ($result in $results) {
    [xml]$xml = Get-Content -LiteralPath $result.FullName -Raw
    $testCount += [int]$xml.testsuite.tests
    $relative = $result.FullName.Substring($consumers.Length + 1).Replace('\', '/')
    $dispositions += [pscustomobject]@{
        fixture = $relative.Split('/')[0]
        report = $relative
        tests = [int]$xml.testsuite.tests
        failures = [int]$xml.testsuite.failures
        errors = [int]$xml.testsuite.errors
        skipped = [int]$xml.testsuite.skipped
        disposition = if (([int]$xml.testsuite.failures + [int]$xml.testsuite.errors) -eq 0) { "passed" } else { "failed" }
    }
}
if ($testCount -eq 0) { throw "Consumer qualification executed zero tests" }
$dispositions | ConvertTo-Json -Depth 4 | Set-Content `
    -LiteralPath (Join-Path $output "consumer-evidence-matrix.json") -Encoding utf8

$consumerLog = Get-Content -LiteralPath (Join-Path $output "consumer-smoke.log") -Raw
if ($consumerLog -match "com.codinglair.taf.*:(?!@taf.version@)[0-9]+\.[0-9]+\.[0-9]+" -and
    -not $consumerLog.Contains((Get-Content (Join-Path $root "pom.xml") -Raw | Select-String -Pattern '<revision>([^<]+)' | ForEach-Object { $_.Matches[0].Groups[1].Value }))) {
    throw "Consumer log contains a TAF version other than the prepared candidate"
}

$resolvedResults = Assert-WorkspacePath (Join-Path $root $EvidenceResults)
$resolvedReport = Assert-WorkspacePath (Join-Path $root $EvidenceReport)
if ((Test-Path -LiteralPath $resolvedResults) -and -not (Test-Path -LiteralPath $resolvedReport)) {
    if ([string]::IsNullOrWhiteSpace($AllureExecutable)) {
        $command = Get-Command allure -ErrorAction SilentlyContinue
        if ($command) { $AllureExecutable = $command.Source }
    }
    if ([string]::IsNullOrWhiteSpace($AllureExecutable)) {
        throw "Allure CLI is required to generate the standalone packaged-consumer report"
    }
    $reportDirectory = Split-Path -Parent $resolvedReport
    & $AllureExecutable generate $resolvedResults --single-file --clean -o $reportDirectory
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
if ((Test-Path -LiteralPath $resolvedResults) -and (Test-Path -LiteralPath $resolvedReport)) {
    & (Join-Path $root "build-support/verification/verify-consumer-evidence.ps1") `
        -Results $resolvedResults -Report $resolvedReport -Matrix $matrix `
        -Output (Join-Path $output "evidence-validator.json")
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} else {
    throw "Packaged evidence consumer did not produce Allure results and standalone HTML"
}

[pscustomobject]@{
    candidateArtifacts = $manifest.Count
    consumerTests = $testCount
    isolatedRepository = $repository.Substring($root.Length + 1).Replace('\', '/')
    status = "PASS"
} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $output "summary.json") -Encoding utf8
