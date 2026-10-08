#requires -Version 7.0
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/local-verify.ps1"
$script:assertions = 0
function Assert([bool] $Condition, [string] $Message) {
    if (-not $Condition) { throw $Message }
    $script:assertions++
}
function Assert-Throws([scriptblock] $Action, [string] $Message) {
    $thrown = $false
    try { & $Action } catch { $thrown = $true }
    Assert $thrown $Message
}

$success = @('[INFO] Reactor Summary for any changing reactor:', '[INFO] One .... SUCCESS [ 1 s]', '[INFO] Two .... SUCCESS [ 2 s]', '[INFO] BUILD SUCCESS')
Assert (Test-MavenOutput $success) 'Valid reactor rejected.'
Assert (-not (Test-MavenOutput ($success -replace 'Two .... SUCCESS', 'Two .... SKIPPED'))) 'Skipped module accepted.'
Assert (-not (Test-MavenOutput ($success -replace 'Two .... SUCCESS', 'Two .... FAILURE'))) 'Failed module accepted.'
Assert (-not (Test-MavenOutput @('[INFO] BUILD SUCCESS'))) 'Missing reactor accepted.'
$ignoredError = '[ERROR] Error fetching link: E:\JavaTestProjects\codinglair-taf\codinglair-taf-starter-messaging-aws\target\reports\apidocs. Ignored it.'
Assert (Test-MavenOutput ($success + $ignoredError)) 'Ignored Javadoc diagnostic incorrectly failed a successful reactor.'
Assert (-not (Test-MavenOutput ($success | Where-Object { $_ -notmatch 'BUILD SUCCESS' }))) 'Missing build marker accepted.'
$consumer = @('[INFO] Build Summary:', '[INFO] Passed: 23, Failed: 0, Errors: 0, Skipped: 0', '[INFO] BUILD SUCCESS')
Assert (Test-MavenOutput $consumer -Consumer) 'Nonrecursive consumer summary rejected.'
foreach ($bad in @('Passed: 0', 'Failed: 1', 'Errors: 1', 'Skipped: 1')) {
    $field = $bad.Split(':')[0]
    Assert (-not (Test-MavenOutput ($consumer -replace "$field`: \d+", $bad) -Consumer)) "Invalid consumer count accepted: $bad"
}

$pwsh = (Get-Command pwsh).Source
$footer = @('[INFO] ------------------------------------------------------------------------', '[INFO] BUILD SUCCESS', '[INFO] ------------------------------------------------------------------------', '[INFO] Total time: 1 s', '[INFO] Finished at: 2026-10-02T12:00:00-05:00', '[INFO] ------------------------------------------------------------------------')
$outer = @('[INFO] Reactor Summary for outer:', '[INFO]', '[INFO] Outer .... SUCCESS [ 1 s]') + $footer
$nested = @('[INFO] downloading something', '[INFO] Reactor Summary for nested:', '[INFO] Nested .... SUCCESS [ 1 s]') + $footer + @('[INFO] --- plugin execution ---', '[INFO] Tests run: 99, Failures: 0, Errors: 0, Skipped: 0')
$display = @(Select-MavenDisplayLines ($nested + $outer))
Assert (($display -join "`n") -eq ($outer -join "`n")) 'Green reactor output differs from final summary/footer.'
$consumerDisplay = @(Select-MavenDisplayLines ($nested + $consumer + $footer) -Consumer)
Assert (-not (($consumerDisplay -join "`n") -match 'nested|Nested|plugin|Tests run:')) 'Consumer leaked nested build logs.'
Assert (($consumerDisplay -join "`n") -match 'Passed: 23') 'Consumer count omitted.'
$androidResults = @('[INFO] Results:', '[INFO]', '[INFO] Tests run: 176, Failures: 0, Errors: 0, Skipped: 0', '[INFO]', '[INFO] --- jar:3.4.2:jar (default-jar) @ taf-mobile-appium ---')
$androidDisplay = @(Select-MavenDisplayLines ($nested + $androidResults + $outer) -Detail Android)
Assert (($androidDisplay -join "`n") -eq (($androidResults + $outer) -join "`n")) 'Android output differs from requested results and final reactor.'
$bddDisplay = @(Select-MavenDisplayLines ($nested + $outer) -Detail Bdd)
Assert (($bddDisplay -join "`n") -match 'Tests run: 99') 'BDD test count omitted.'
$errorDisplay = @(Select-MavenDisplayLines ($nested + '[ERROR] failure details' + $outer))
Assert (($errorDisplay -join "`n") -match '\[ERROR\] failure details') 'Errors omitted from compact output.'
$releaseDisplay = @(Select-MavenDisplayLines (@('[INFO] Skipping artifact deployment') + $outer) -Detail Release)
Assert ($releaseDisplay[0] -eq '[INFO] Skipping artifact deployment') 'Release detail omitted.'
$dependency = @('[INFO] Found Resolved Dependency/DependencyManagement mismatches:', '[INFO] Ignoring Direct Dependencies.', '[INFO] None')
$dependencyDisplay = @(Select-MavenDisplayLines ($dependency + $outer) -Detail Dependency)
Assert (($dependencyDisplay -join "`n") -match 'mismatches:') 'Dependency detail omitted.'
Invoke-CommandCheck $pwsh @('-NoProfile', '-Command', "'[INFO] Reactor Summary for test:'; '[INFO] Module .... SUCCESS [ 1 s]'; '[INFO] BUILD SUCCESS'; exit 0") Maven
$diagnosticRun = @(Invoke-CommandCheck $pwsh @('-NoProfile', '-Command', "'[ERROR] Error fetching link: fake apidocs. Ignored it.'; '[INFO] Reactor Summary for test:'; '[INFO] Module .... SUCCESS [ 1 s]'; '[INFO] BUILD SUCCESS'; exit 0") Maven 6>&1)
Assert (($diagnosticRun -join "`n") -match '\[ERROR\] Error fetching link: fake apidocs. Ignored it.') 'Ignored error diagnostic was not displayed.'
Assert-Throws { Invoke-CommandCheck $pwsh @('-NoProfile', '-Command', "'[INFO] Reactor Summary for test:'; '[INFO] Module .... SUCCESS [ 1 s]'; '[INFO] BUILD SUCCESS'; exit 7") Maven } 'Nonzero Maven exit accepted despite success markers.'
Invoke-CommandCheck $pwsh @('-NoProfile', '-Command', "[Console]::Error.WriteLine('no leaks found'); exit 0") Leaks
Assert-Throws { Invoke-CommandCheck $pwsh @('-NoProfile', '-Command', "'no leaks found'; exit 7") Leaks } 'Nonzero native exit accepted.'
Assert-Throws { Invoke-CommandCheck $pwsh @('-NoProfile', '-Command', "'scan incomplete'; exit 0") Leaks } 'Missing leak marker accepted.'
Assert-Throws { Invoke-CommandCheck 'taf-nonexistent-command' @() Native } 'Missing executable accepted.'

# Failed startup still tears down and verifies both resource types.
$script:calls = [System.Collections.Generic.List[string]]::new()
function Invoke-CommandCheck {
    param($Executable, $Arguments, $Kind)
    $script:calls.Add(($Arguments -join ' '))
    if ($Arguments -contains 'up') { throw 'Simulated partial startup failure' }
}
function Get-ComposeResources {
    param($Project, [switch] $Networks)
    $script:calls.Add("query networks=$Networks")
    return @()
}
Assert-Throws { Invoke-AndroidCheck } 'Android startup failure lost.'
Assert (@($script:calls | Where-Object { $_ -match 'down --remove-orphans' }).Count -eq 1) 'Teardown not executed.'
Assert (@($script:calls | Where-Object { $_ -match 'query networks=' }).Count -eq 2) 'Cleanup not verified.'

$script:healthy = $true
$script:removed = $false
$script:leftovers = $false
$script:cleanupFails = $false
function docker {
    $global:LASTEXITCODE = 0
    if ($script:healthy) { 'healthy' } else { 'unhealthy' }
}
function Invoke-CommandCheck {
    param($Executable, $Arguments, $Kind)
    $script:calls.Add(($Arguments -join ' '))
    if ($Arguments -contains 'down') {
        $script:removed = $true
        if ($script:cleanupFails) { throw 'Simulated teardown failure' }
    }
}
function Get-ComposeResources {
    param($Project, [switch] $Networks)
    if ($script:removed) {
        if ($script:leftovers) { 'remaining-resource' }
    }
    elseif (-not $Networks) { 'android-id'; 'appium-id' }
}
$androidVariables = @('TAF_ANDROID_APPIUM_URL', 'TAF_ANDROID_DEVICE_NAME', 'TAF_ANDROID_DEVICE_ID', 'TAF_ANDROID_APP_PACKAGE', 'TAF_ANDROID_APP_ACTIVITY')
$androidSaved = @{}
foreach ($name in $androidVariables) { $androidSaved[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
try {
    Invoke-AndroidCheck
    Assert $script:removed 'Successful Android run did not clean up.'
    Assert (@($script:calls | Where-Object { $_ -match '-Pandroid-emulator' }).Count -eq 1) 'Healthy Android did not run Maven.'
    $script:removed = $false
    $script:healthy = $false
    Assert-Throws { Invoke-AndroidCheck } 'Unhealthy Android accepted.'
    Assert $script:removed 'Unhealthy Android did not clean up.'
    Assert (@($script:calls | Where-Object { $_ -match '-Pandroid-emulator' }).Count -eq 1) 'Unhealthy Android ran Maven.'
    $script:removed = $false
    $script:healthy = $true
    $script:leftovers = $true
    Assert-Throws { Invoke-AndroidCheck } 'Leftover resources accepted.'
    $script:removed = $false
    $script:leftovers = $false
    $script:cleanupFails = $true
    Assert-Throws { Invoke-AndroidCheck } 'Teardown failure accepted.'
}
finally {
    foreach ($name in $androidVariables) { [Environment]::SetEnvironmentVariable($name, $androidSaved[$name], 'Process') }
}

# Every check runs after failure; environment and caller directory are restored.
$script:commands = 0
function Invoke-CommandCheck {
    param($Executable, $Arguments, $Kind)
    $script:commands++
    $env:TEST_ENVIRONMENT = 'changed-by-test'
    if ($script:commands -eq 1) { throw 'Simulated first check failure' }
}
function Invoke-AndroidCheck { Invoke-CommandCheck docker @() Native }
function Invoke-PlaywrightCheck { Invoke-CommandCheck .\mvnw.cmd @() Maven }
$original = $env:TEST_ENVIRONMENT
$location = (Get-Location).Path
try {
    $env:TEST_ENVIRONMENT = 'caller-value'
    Assert ((Invoke-LocalVerification) -eq 1) 'Overall failure exit lost.'
    Assert ($script:commands -eq 10) 'Not all ten checks ran.'
    Assert ($env:TEST_ENVIRONMENT -eq 'caller-value') 'Caller environment changed.'
    Assert ((Get-Location).Path -eq $location) 'Caller directory changed.'
    $script:commands = 1
    Assert ((Invoke-LocalVerification) -eq 0) 'Successful aggregate failed.'
}
finally { $env:TEST_ENVIRONMENT = $original }
Write-Host "PASS: $script:assertions assertions"
