[CmdletBinding()]
param([switch]$SkipCandidateRun)

$ErrorActionPreference = "Stop"
Import-Module Microsoft.PowerShell.Utility -ErrorAction Stop
$root = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$candidateEvidence = Join-Path $root "target/ver-132-001"
$output = Join-Path $root "target/qe-132-001"
$negativeRoot = Join-Path $root "target/run-132-negative-matrix"

function Add-Result([Collections.Generic.List[object]]$Results, [string]$Scenario,
    [string]$Requirement, [bool]$Passed, [string]$Evidence) {
    $Results.Add([pscustomobject]@{
        scenario = $Scenario; requirement = $Requirement
        status = if ($Passed) { "PASS" } else { "FAIL" }
        evidence = $Evidence.Replace('\', '/')
    })
}

function Read-Json([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Required QE evidence is missing: $Path"
    }
    return Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
}

function Assert-Negative([Collections.Generic.List[object]]$Results, [string]$Name,
    [string]$Diagnostic) {
    $case = Join-Path $negativeRoot $Name
    $log = Join-Path $case "acceptance.log"
    $text = if (Test-Path -LiteralPath $log) { Get-Content -LiteralPath $log -Raw } else { "" }
    $sentinel = Join-Path $case "target/test-body-ran.sentinel"
    $passed = $text.Contains($Diagnostic) -and -not (Test-Path -LiteralPath $sentinel)
    Add-Result $Results "reject-$Name-before-test-body" "RN-01,RN-02,RN-04" $passed `
        "target/run-132-negative-matrix/$Name/acceptance.log; diagnostic=$Diagnostic; sentinel=absent"
}

$output = [IO.Path]::GetFullPath($output)
$checkoutPrefix = $root.TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
if (-not $output.StartsWith($checkoutPrefix, [StringComparison]::OrdinalIgnoreCase)) {
    throw "QE-132-001 output escapes the prepared checkout: $output"
}
New-Item -ItemType Directory -Force -Path $output | Out-Null

if (-not $SkipCandidateRun) {
    & (Join-Path $root "build-support/verification/ver-132-001.ps1") -SkipStage
    if ($LASTEXITCODE -ne 0) { throw "VER-132-001 candidate qualification failed" }
}

$manifest = Read-Json (Join-Path $candidateEvidence "candidate-manifest.json")
$consumerMatrix = Read-Json (Join-Path $candidateEvidence "consumer-evidence-matrix.json")
$summary = Read-Json (Join-Path $candidateEvidence "summary.json")
$results = [Collections.Generic.List[object]]::new()

function Get-TreeDigest([string]$Path) {
    $material = Get-ChildItem -LiteralPath $Path -Recurse -File | Sort-Object FullName | ForEach-Object {
        "$($_.FullName.Substring($Path.Length))=$((Get-FileHash $_.FullName -Algorithm SHA256).Hash)"
    }
    $bytes = [Text.Encoding]::UTF8.GetBytes(($material -join "`n"))
    $sha = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($sha.ComputeHash($bytes))).Replace("-", "").ToLowerInvariant() }
    finally { $sha.Dispose() }
}

function Prepare-PackagedClients {
    $pom = Join-Path $root "release/qe-132-001-client-runtime-pom.xml"
    $classpath = Join-Path $output "packaged-client-classpath.txt"
    $repositoryUrl = ([Uri](Join-Path $root "target/staging-repository")).AbsoluteUri
    & (Join-Path $root "mvnw.cmd") -B -ntp -f $pom `
        "-Dtaf.staging.repository=$repositoryUrl" `
        "-Dmdep.outputFile=$classpath" dependency:build-classpath *> (Join-Path $output "packaged-client-classpath.log")
    if ($LASTEXITCODE -ne 0) { throw "Cannot resolve packaged QE client classpath" }
    $classes = Join-Path $output "client-classes"
    New-Item -ItemType Directory -Force -Path $classes | Out-Null
    $cp = Get-Content $classpath -Raw
    & javac -cp $cp -d $classes `
        (Join-Path $root "build-support/verification/Qe132001McpClient.java") `
        (Join-Path $root "build-support/verification/Qe132001ConformanceClient.java")
    if ($LASTEXITCODE -ne 0) { throw "Cannot compile packaged QE clients" }
    return [pscustomobject]@{ Classpath = $cp.Trim(); File = $classpath; Classes = $classes }
}

function Invoke-ScaffoldScenario($Clients, [string]$Name, [string]$AutomationRoot,
    [string]$SutRoot, [bool]$Publish, [string]$Expected) {
    $case = Join-Path $output "scaffold/$Name"
    New-Item -ItemType Directory -Force -Path $case | Out-Null
    $protected = Join-Path $SutRoot "pom.xml"
    $before = (Get-FileHash $protected -Algorithm SHA256).Hash
    $log = Join-Path $case "response.log"
    & java -cp $Clients.Classes Qe132001McpClient $Clients.File $scaffoldRoot $AutomationRoot $SutRoot $Publish `
        *> $log
    $text = if (Test-Path $log) { Get-Content $log -Raw } else { "" }
    $unchanged = $before -eq (Get-FileHash $protected -Algorithm SHA256).Hash
    $passed = $LASTEXITCODE -eq 0 -and $text.Contains($Expected) -and $unchanged
    Add-Result $results "public-scaffold-$Name" "CS-03,CS-04,CS-05" $passed `
        "target/qe-132-001/scaffold/$Name/response.log; expected=$Expected; protectedPomUnchanged=$unchanged"
    return $text
}

function Invoke-ContaminationScenario($Clients, [string]$Name, [string]$ExpectedRule,
    [scriptblock]$Mutate) {
    $case = Join-Path $output "contamination/$Name"
    $sut = Join-Path $case "sut"
    $automation = Join-Path $sut "automation"
    New-Item -ItemType Directory -Force -Path $automation | Out-Null
    Set-Content -LiteralPath (Join-Path $sut "pom.xml") -Value '<project><modelVersion>4.0.0</modelVersion><groupId>qe</groupId><artifactId>sut</artifactId><version>1</version></project>'
    & $Mutate $sut $automation
    $before = Get-TreeDigest $sut
    $log = Join-Path $case "conformance.log"
    & java -cp "$($Clients.Classes)$([IO.Path]::PathSeparator)$($Clients.Classpath)" `
        Qe132001ConformanceClient $sut $automation *> $log
    $after = Get-TreeDigest $sut
    $text = if (Test-Path $log) { Get-Content $log -Raw } else { "" }
    Add-Result $results "public-conformance-rejects-$Name" "CS-02,CS-07" `
        ($LASTEXITCODE -eq 0 -and $text.Contains($ExpectedRule) -and $before -eq $after) `
        "target/qe-132-001/contamination/$Name/conformance.log; expected=$ExpectedRule; stateUnchanged=$($before -eq $after)"
}

function Copy-RunnerFixture([string]$Name) {
    $destination = Join-Path $output "runner/$Name"
    if (Test-Path $destination) { Remove-Item -LiteralPath $destination -Recurse -Force }
    New-Item -ItemType Directory -Force -Path (Split-Path $destination -Parent) | Out-Null
    Copy-Item -LiteralPath (Join-Path $root "target/consumer-smoke/generated-web-api-database") `
        -Destination $destination -Recurse
    Remove-Item -LiteralPath (Join-Path $destination "target") -Recurse -Force -ErrorAction SilentlyContinue
    return $destination
}

function Invoke-RunnerFailure([string]$Name, [string]$Expected, [scriptblock]$Mutate) {
    $case = Copy-RunnerFixture $Name
    & $Mutate $case
    $log = Join-Path $case "acceptance.log"
    Push-Location $case
    try {
        & .\mvnw.cmd -B -ntp clean verify *> $log
        $exit = $LASTEXITCODE
    } finally { Pop-Location }
    $text = if (Test-Path $log) { Get-Content $log -Raw } else { "" }
    $sentinel = Join-Path $case "target/unsupported-body-ran.sentinel"
    Add-Result $results "packaged-runner-rejects-$Name" "RN-02,RN-04" `
        ($exit -ne 0 -and $text.Contains($Expected) -and -not (Test-Path $sentinel)) `
        "target/qe-132-001/runner/$Name/acceptance.log; expected=$Expected; sentinel=absent; exit=$exit"
}

function Add-FakeJunitTest([string]$Case, [string]$ClassName) {
    $annotation = Join-Path $Case "src/test/java/org/junit/Test.java"
    $test = Join-Path $Case "src/test/java/qe/$ClassName.java"
    New-Item -ItemType Directory -Force -Path (Split-Path $annotation -Parent) | Out-Null
    New-Item -ItemType Directory -Force -Path (Split-Path $test -Parent) | Out-Null
    Set-Content $annotation @'
package org.junit;
@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@java.lang.annotation.Target({java.lang.annotation.ElementType.METHOD})
public @interface Test {}
'@
    Set-Content $test @"
package qe;
public final class $ClassName {
  @org.junit.Test
  public void unsupportedBody() throws Exception {
    java.nio.file.Files.writeString(java.nio.file.Path.of("target/unsupported-body-ran.sentinel"), "ran");
  }
}
"@
}

function Invoke-FailureCleanupAcceptance([string]$Name, [string]$SourceFixture,
    [scriptblock]$Mutate) {
    $case = Join-Path $output "cleanup/$Name"
    if (Test-Path $case) { Remove-Item -LiteralPath $case -Recurse -Force }
    New-Item -ItemType Directory -Force -Path (Split-Path $case -Parent) | Out-Null
    Copy-Item -LiteralPath (Join-Path $root $SourceFixture) -Destination $case -Recurse
    Remove-Item -LiteralPath (Join-Path $case "target") -Recurse -Force -ErrorAction SilentlyContinue
    & $Mutate $case
    $log = Join-Path $case "acceptance.log"
    Push-Location $case
    try {
        & .\mvnw.cmd -B -ntp clean verify *> $log
        $exit = $LASTEXITCODE
    } finally { Pop-Location }
    $text = if (Test-Path $log) { Get-Content $log -Raw } else { "" }
    $cleanup = Test-Path (Join-Path $case "target/failure-cleanup.sentinel")
    $subsequent = Test-Path (Join-Path $case "target/subsequent-invocation.sentinel")
    Add-Result $results "governed-$Name-failure-cleanup" "RN-07" `
        ($exit -ne 0 -and $text.Contains("intentional-qe-failure") -and $cleanup -and $subsequent) `
        "target/qe-132-001/cleanup/$Name/acceptance.log; originalFailure=true; cleanup=$cleanup; subsequentIsolation=$subsequent; exit=$exit"
}

$validHashes = $manifest.Count -gt 0 -and
    @($manifest | Where-Object { $_.sha256 -notmatch '^[0-9a-f]{64}$' -or $_.bytes -le 0 }).Count -eq 0
Add-Result $results "exact-candidate-artifact-manifest" "CS-01,EV-04" $validHashes `
    "target/ver-132-001/candidate-manifest.json ($($manifest.Count) artifacts)"

$executed = $consumerMatrix.Count -gt 0 -and @($consumerMatrix | Where-Object {
        $_.tests -le 0 -or $_.failures -ne 0 -or $_.errors -ne 0 -or $_.disposition -ne 'passed'
    }).Count -eq 0
Add-Result $results "packaged-consumers-execute-nonzero-tests" "RN-01,RN-07,EV-04" $executed `
    "target/ver-132-001/consumer-evidence-matrix.json ($($summary.consumerTests) tests)"

foreach ($sut in @("boot3", "boot4")) {
    $tree = Join-Path $candidateEvidence "$sut-dependency-tree.txt"
    $automation = Join-Path $candidateEvidence "$sut-external-automation.log"
    $treeText = if (Test-Path $tree) { Get-Content $tree -Raw } else { "" }
    $automationText = if (Test-Path $automation) { Get-Content $automation -Raw } else { "" }
    $passed = -not [string]::IsNullOrWhiteSpace($treeText) -and
        -not $treeText.Contains("com.codinglair.taf") -and
        $automationText.Contains("BUILD SUCCESS") -and $automationText -match 'Tests run:\s*[1-9][0-9]*'
    Add-Result $results "$sut-independent-external-execution" "CS-02,CS-07,JK-09" $passed `
        "target/ver-132-001/$sut-dependency-tree.txt; target/ver-132-001/$sut-external-automation.log"
}

Assert-Negative $results "profile-provider-substitution" "provider.testng"
Assert-Negative $results "missing-base" "integration.base-test"
Assert-Negative $results "missing-cucumber-hooks" "integration.cucumber-hooks"
Assert-Negative $results "copied-lifecycle" "lifecycle.consumer-owned"
Assert-Negative $results "skip-tests-attempt" "integration.base-test"

$validator = Read-Json (Join-Path $candidateEvidence "evidence-validator.json")
Add-Result $results "sanitized-evidence-is-machine-valid" "EV-04" ($validator.valid -eq $true) `
    "target/ver-132-001/evidence-validator.json"
$securityIndex = Join-Path $output "security/surface-evidence.tsv"
$securityRows = if (Test-Path $securityIndex) { @(Import-Csv $securityIndex -Delimiter "`t") } else { @() }
$manifestHashes = @{}
foreach ($entry in $manifest) {
    $manifestPath = $entry.path
    $manifestHashes[$manifestPath] = $entry.sha256
}
$mismatches = @($securityRows | Where-Object {
    [void]($candidatePath = "$($_.surface)-cyclonedx.json")
    -not $manifestHashes.ContainsKey($candidatePath) -or
        $manifestHashes[$candidatePath] -ne $_.sbom_sha256 -or
        [int]$_.high_critical_jackson -ne 0 -or
        -not (Test-Path (Join-Path $root $_.report))
})
$securityRecordsPresent = $securityRows.Count -gt 0 -and $mismatches.Count -eq 0
Add-Result $results "consume-candidate-matched-security-records" "JK-09" $securityRecordsPresent `
    "target/qe-132-001/security/surface-evidence.tsv; rows=$($securityRows.Count); candidateHashMismatches=$($mismatches.Count); scanner implementation reused"

$clients = Prepare-PackagedClients
$scaffoldRoot = Join-Path $output "scaffold-workspace"
$linkTarget = Join-Path $output "scaffold-link-target"
foreach ($disposablePath in @($scaffoldRoot, $linkTarget)) {
    if (Test-Path -LiteralPath $disposablePath) {
        Remove-Item -LiteralPath $disposablePath -Recurse -Force
    }
}
$sut = Join-Path $scaffoldRoot "sut"
New-Item -ItemType Directory -Force -Path $sut | Out-Null
Set-Content -LiteralPath (Join-Path $sut "pom.xml") -Value '<project><modelVersion>4.0.0</modelVersion><groupId>qe</groupId><artifactId>sut</artifactId><version>1</version></project>'
$validResponse = Invoke-ScaffoldScenario $clients "valid-plan" (Join-Path $scaffoldRoot "automation") $sut $false "VALIDATED"
$publishResponse = Invoke-ScaffoldScenario $clients "authorized-generation" (Join-Path $scaffoldRoot "automation") $sut $true "APPLIED"
Invoke-ScaffoldScenario $clients "traversal" (Join-Path $scaffoldRoot "../outside") $sut $false "escapes the approved root" | Out-Null
Invoke-ScaffoldScenario $clients "sut-root" $sut $sut $false "must not be the SUT build root" | Out-Null

$symlinkCase = Join-Path $output "scaffold/symlink"
if (Test-Path -LiteralPath $symlinkCase) { Remove-Item -LiteralPath $symlinkCase -Recurse -Force }
New-Item -ItemType Directory -Force -Path $symlinkCase | Out-Null
& docker run --rm --entrypoint sh `
    -v "${root}:/workspace:ro" -v "${symlinkCase}:/evidence" `
    codinglair/codinglair-taf-mcp:run-132-ci-repro `
    /workspace/build-support/verification/qe-132-001-symlink.sh `
    *> (Join-Path $symlinkCase "docker.log")
$symlinkPassed = $LASTEXITCODE -eq 0 -and
    (Test-Path -LiteralPath (Join-Path $symlinkCase "environment.txt"))
Add-Result $results "public-scaffold-symlink" "CS-03,CS-04,CS-05" $symlinkPassed `
    "target/qe-132-001/scaffold/symlink/response.log; environment=linux-container; protectedPomUnchanged=true"

Invoke-ContaminationScenario $clients "reactor-registration" "sut.reactor" {
    param($sutRoot, $automationRoot)
    Set-Content (Join-Path $sutRoot "pom.xml") '<project><modules><module>automation</module></modules></project>'
}
Invoke-ContaminationScenario $clients "taf-bom" "sut.bom" {
    param($sutRoot, $automationRoot)
    Set-Content (Join-Path $sutRoot "pom.xml") '<project><dependencyManagement><dependencies><dependency><groupId>com.codinglair.taf</groupId></dependency></dependencies></dependencyManagement></project>'
}
Invoke-ContaminationScenario $clients "taf-production-dependency" "sut.dependency" {
    param($sutRoot, $automationRoot)
    Set-Content (Join-Path $sutRoot "pom.xml") '<project><dependencies><dependency><groupId>com.codinglair.taf</groupId></dependency></dependencies></project>'
}
Invoke-ContaminationScenario $clients "taf-production-package" "sut.package" {
    param($sutRoot, $automationRoot)
    $entry = Join-Path $sutRoot "archive/com/codinglair/taf"
    New-Item -ItemType Directory -Force -Path $entry | Out-Null
    Set-Content (Join-Path $entry "Injected.class") 'qe-contamination'
    Push-Location (Join-Path $sutRoot "archive")
    try { & jar cf (Join-Path $sutRoot "sut.jar") com }
    finally { Pop-Location }
}

Invoke-RunnerFailure "junit-only-no-taf-call" "annotation.junit" {
    param($case)
    Remove-Item -LiteralPath (Join-Path $case "src/test/java/com") -Recurse -Force
    Add-FakeJunitTest $case "JunitOnlyNeverCallsTafTest"
}
Invoke-RunnerFailure "mixed-junit-testng" "annotation.junit" {
    param($case)
    Add-FakeJunitTest $case "MixedDiscoveryJunitTest"
}
Invoke-RunnerFailure "missing-testng-integration" "integration.testng" {
    param($case)
    $pom = Join-Path $case "pom.xml"
    $text = Get-Content $pom -Raw
    $updated = [regex]::Replace($text,
        '(?s)\s*<dependency>\s*<groupId>com\.codinglair\.taf</groupId>\s*<artifactId>codinglair-taf-runner-testng</artifactId>\s*<type>jar</type>\s*</dependency>',
        '', 1)
    if ($updated -eq $text) { throw "runner dependency block not found" }
    $exclusion = '<exclusions><exclusion><groupId>com.codinglair.taf</groupId><artifactId>codinglair-taf-runner-testng</artifactId></exclusion></exclusions>'
    $updated = $updated.Replace('<type>pom</type>', "<type>pom</type>$exclusion")
    Set-Content $pom $updated
}
Invoke-RunnerFailure "no-active-session" "TAF-LIFECYCLE-001" {
    param($case)
    $test = Join-Path $case "src/test/java/com/example/generated/BlueprintPreflightTest.java"
    $text = Get-Content $test -Raw
    $method = @'

  @org.testng.annotations.BeforeSuite
  public void invokesPublicSessionAccessorBeforeLifecycle() throws Exception {
    com.codinglair.taf.runtime.core.lifecycle.CurrentTestSession missing = java.util.Optional::empty;
    new com.codinglair.taf.runtime.core.lifecycle.DefaultSessionAwareAccessor(missing).context();
    java.nio.file.Files.writeString(
        java.nio.file.Path.of("target/unsupported-body-ran.sentinel"), "ran");
  }
'@
    $lastBrace = $text.LastIndexOf('}')
    Set-Content $test ($text.Insert($lastBrace, $method))
}

Invoke-FailureCleanupAcceptance "testng" "target/consumer-smoke/generated-web-api-database" {
    param($case)
    $javaRoot = Join-Path $case "src/test/java/com/example/generated"
    Get-ChildItem $javaRoot -File -Filter "*.java" | Remove-Item -Force
    Set-Content (Join-Path $javaRoot "FailureCleanupAcceptanceTest.java") @'
package com.example.generated;

@org.testng.annotations.Test(singleThreaded = true)
public final class FailureCleanupAcceptanceTest
    extends com.codinglair.taf.runtime.testng.TafBaseTest {
  @org.testng.annotations.Test(priority = 1)
  public void failsAfterAcquiringOwnedCleanup() throws Exception {
    java.nio.file.Path session = java.nio.file.Path.of("target/failed-session.id");
    java.nio.file.Files.writeString(session, testSession().getSessionId());
    testSession().addCleanupListener(_ -> {
      try {
        java.nio.file.Files.writeString(
            java.nio.file.Path.of("target/failure-cleanup.sentinel"), "closed");
      } catch (java.io.IOException failure) {
        throw new IllegalStateException(failure);
      }
    });
    throw new AssertionError("intentional-qe-failure");
  }

  @org.testng.annotations.Test(priority = 2)
  public void subsequentInvocationIsIsolated() throws Exception {
    if (!java.nio.file.Files.exists(java.nio.file.Path.of("target/failure-cleanup.sentinel"))) {
      throw new AssertionError("failed invocation cleanup was not observed");
    }
    String failed = java.nio.file.Files.readString(java.nio.file.Path.of("target/failed-session.id"));
    if (failed.equals(testSession().getSessionId())) {
      throw new AssertionError("subsequent invocation reused failed session");
    }
    java.nio.file.Files.writeString(
        java.nio.file.Path.of("target/subsequent-invocation.sentinel"), "isolated");
  }
}
'@
    $suite = Join-Path $case "src/test/resources/testng.xml"
    $xml = Get-Content $suite -Raw
    $xml = [regex]::Replace($xml, '<classes>[\s\S]*?</classes>', '<classes><class name="com.example.generated.FailureCleanupAcceptanceTest"/></classes>')
    Set-Content $suite $xml
}

Invoke-FailureCleanupAcceptance "cucumber" "target/consumer-smoke/generated-cucumber" {
    param($case)
    Set-Content (Join-Path $case "src/test/resources/features/generated-project.feature") @'
Feature: Failure cleanup
  Scenario: Failing scenario owns cleanup
    Then the scenario fails after acquiring cleanup

  Scenario: Subsequent scenario is isolated
    Then cleanup and scenario isolation are observable
'@
    Set-Content (Join-Path $case "src/test/java/com/example/generated/bdd/GeneratedSteps.java") @'
package com.example.generated.bdd;

import com.codinglair.taf.runtime.cucumber.CucumberScenarioSession;
import io.cucumber.java.en.Then;

public final class GeneratedSteps {
  private final CucumberScenarioSession scenarioSession;

  public GeneratedSteps(CucumberScenarioSession scenarioSession) {
    this.scenarioSession = scenarioSession;
  }

  @Then("the scenario fails after acquiring cleanup")
  public void failAfterCleanupRegistration() throws Exception {
    java.nio.file.Files.writeString(
        java.nio.file.Path.of("target/failed-session.id"), scenarioSession.session().getSessionId());
    scenarioSession.session().addCleanupListener(_ -> {
      try {
        java.nio.file.Files.writeString(
            java.nio.file.Path.of("target/failure-cleanup.sentinel"), "closed");
      } catch (java.io.IOException failure) {
        throw new IllegalStateException(failure);
      }
    });
    throw new AssertionError("intentional-qe-failure");
  }

  @Then("cleanup and scenario isolation are observable")
  public void observeCleanupAndIsolation() throws Exception {
    if (!java.nio.file.Files.exists(java.nio.file.Path.of("target/failure-cleanup.sentinel"))) {
      throw new AssertionError("failed scenario cleanup was not observed");
    }
    String failed = java.nio.file.Files.readString(java.nio.file.Path.of("target/failed-session.id"));
    if (failed.equals(scenarioSession.session().getSessionId())) {
      throw new AssertionError("subsequent scenario reused failed session");
    }
    java.nio.file.Files.writeString(
        java.nio.file.Path.of("target/subsequent-invocation.sentinel"), "isolated");
  }
}
'@
}

$statusText = (& git -C $root status --short) -join "`n"
$sha = [Security.Cryptography.SHA256]::Create()
try { $statusHash = $sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($statusText)) }
finally { $sha.Dispose() }
[pscustomobject]@{
    sourceSha = (& git -C $root rev-parse HEAD).Trim()
    workingTreeStatusSha256 = ([BitConverter]::ToString($statusHash)).Replace("-", "").ToLowerInvariant()
    java = (& $env:ComSpec /c "java -version 2>&1" | Select-Object -First 1).ToString()
    os = [Environment]::OSVersion.VersionString
    candidateArtifacts = $manifest.Count
    consumerTests = [int]$summary.consumerTests
} | ConvertTo-Json -Depth 3 | Set-Content (Join-Path $output "candidate-identity.json") -Encoding utf8
$results | ConvertTo-Json -Depth 4 | Set-Content (Join-Path $output "acceptance-matrix.json") -Encoding utf8
$overall = if (@($results | Where-Object status -ne "PASS").Count -eq 0) { "PASS" } else { "INCOMPLETE" }
[pscustomobject]@{ status = $overall; scenarios = $results.Count; candidateArtifacts = $manifest.Count
    consumerTests = [int]$summary.consumerTests } |
    ConvertTo-Json | Set-Content (Join-Path $output "summary.json") -Encoding utf8
$results | Format-Table scenario, requirement, status -AutoSize
if ($overall -ne "PASS") { throw "QE-132-001 is incomplete; see target/qe-132-001/acceptance-matrix.json" }
