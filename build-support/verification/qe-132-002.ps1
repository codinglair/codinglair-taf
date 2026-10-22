[CmdletBinding()]
param(
    [switch]$SkipStage,
    [string]$AllureExecutable = ""
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$output = Join-Path $root "target/qe-132-002"
$staging = Join-Path $root "target/staging-repository"
$maven = Join-Path $root "mvnw.cmd"
$revision = ([xml](Get-Content -Raw (Join-Path $root "pom.xml"))).project.properties.revision
New-Item -ItemType Directory -Force -Path $output | Out-Null

function Invoke-Candidate([string]$Project, [string[]]$Goals, [string]$Log) {
    & $maven -B -ntp -f $Project "-Dtaf.version=$revision" "-Dtaf.staging.repository=$($staging.Replace('\','/'))" @Goals 2>&1 |
        Tee-Object -FilePath $Log | Out-Host
    return $LASTEXITCODE
}

function Assert-ExpectedResults([string]$Reports, [int]$Tests, [int]$Failures) {
    $files = Get-ChildItem -LiteralPath $Reports -Filter "TEST-*.xml" -File
    $actualTests = 0
    $actualFailures = 0
    foreach ($file in $files) {
        [xml]$result = Get-Content -Raw -LiteralPath $file.FullName
        $actualTests += [int]$result.testsuite.tests
        $actualFailures += [int]$result.testsuite.failures + [int]$result.testsuite.errors
    }
    if ($actualTests -ne $Tests -or $actualFailures -ne $Failures) {
        throw "Unexpected result counts in ${Reports}: tests=$actualTests failures=$actualFailures"
    }
}

function New-StandaloneReport([string]$ProjectRoot, [string]$Name) {
    if ([string]::IsNullOrWhiteSpace($AllureExecutable)) {
        $command = Get-Command allure -ErrorAction SilentlyContinue
        if ($command) { $script:AllureExecutable = $command.Source }
    }
    if ([string]::IsNullOrWhiteSpace($AllureExecutable)) { throw "Allure CLI is required" }
    $results = Join-Path $ProjectRoot "target/allure-results"
    $reportDirectory = Join-Path $output "$Name-report"
    & $AllureExecutable generate $results --single-file --clean -o $reportDirectory | Out-Host
    if ($LASTEXITCODE -ne 0) { throw "Allure report generation failed for $Name" }
    return Join-Path $reportDirectory "index.html"
}

if (-not $SkipStage) {
    & $maven -B -ntp clean deploy -Prelease-staging
    if ($LASTEXITCODE -ne 0) { throw "Candidate staging failed" }
}
if (-not (Test-Path -LiteralPath $staging)) { throw "Candidate staging repository is missing" }

$noAllure = Join-Path $root "release/qe-132-002/no-allure/pom.xml"
if ((Invoke-Candidate $noAllure @("clean", "test") (Join-Path $output "no-allure.log")) -ne 0) {
    throw "No-Allure qualification failed"
}

$testngRoot = Join-Path $root "release/qe-132-002/testng"
$testngExit = Invoke-Candidate (Join-Path $testngRoot "pom.xml") @("clean", "test") (Join-Path $output "testng.log")
if ($testngExit -eq 0) { throw "TestNG expected-failure suite unexpectedly passed" }
Assert-ExpectedResults (Join-Path $testngRoot "target/surefire-reports") 4 1
$testngReport = New-StandaloneReport $testngRoot "testng"
& (Join-Path $root "build-support/verification/verify-consumer-evidence.ps1") -Results (Join-Path $testngRoot "target/allure-results") -Report $testngReport -Matrix (Join-Path $testngRoot "evidence-expectations.json") -Output (Join-Path $output "testng-validator.json")
if ($LASTEXITCODE -ne 0) { throw "TestNG evidence validation failed" }

$cucumberRoot = Join-Path $root "release/qe-132-002/cucumber"
$cucumberExit = Invoke-Candidate (Join-Path $cucumberRoot "pom.xml") @("clean", "test") (Join-Path $output "cucumber.log")
if ($cucumberExit -eq 0) { throw "Cucumber expected-failure suite unexpectedly passed" }
Assert-ExpectedResults (Join-Path $cucumberRoot "target/surefire-reports") 2 1
$cucumberReport = New-StandaloneReport $cucumberRoot "cucumber"
& (Join-Path $root "build-support/verification/verify-consumer-evidence.ps1") -Results (Join-Path $cucumberRoot "target/allure-results") -Report $cucumberReport -Matrix (Join-Path $cucumberRoot "evidence-expectations.json") -Output (Join-Path $output "cucumber-validator.json")
if ($LASTEXITCODE -ne 0) { throw "Cucumber evidence validation failed" }

$artifacts = @($testngReport, $cucumberReport, (Join-Path $output "testng-validator.json"), (Join-Path $output "cucumber-validator.json")) | ForEach-Object {
    [pscustomobject]@{ path = $_.Substring($root.Length + 1).Replace('\','/'); sha256 = (Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash.ToLowerInvariant() }
}
[pscustomobject]@{ assignment="QE-132-002"; candidate=$revision; testng=@{tests=4;failures=1}; cucumber=@{tests=2;failures=1}; noAllure=@{tests=1;failures=0}; artifacts=$artifacts; status="PASS" } |
    ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $output "summary.json") -Encoding utf8
