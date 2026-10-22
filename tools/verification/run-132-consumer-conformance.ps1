param(
    [string]$BaseFixture = "target/run-132-cucumber-acceptance",
    [string]$MatrixRoot = "target/run-132-negative-matrix"
)

$ErrorActionPreference = "Stop"
$workspace = (Resolve-Path -LiteralPath ".").Path
$base = (Resolve-Path -LiteralPath $BaseFixture).Path
$matrix = [System.IO.Path]::GetFullPath((Join-Path $workspace $MatrixRoot))
$expectedBase = [System.IO.Path]::GetFullPath((Join-Path $workspace "target/run-132-cucumber-acceptance"))
$expectedMatrix = [System.IO.Path]::GetFullPath((Join-Path $workspace "target/run-132-negative-matrix"))
if ($base -ne $expectedBase -or $matrix -ne $expectedMatrix) {
    throw "RUN-132 fixture paths must remain under the expected workspace target directories"
}
if (Test-Path -LiteralPath $matrix) {
    Remove-Item -LiteralPath $matrix -Recurse -Force
}
New-Item -ItemType Directory -Path $matrix | Out-Null

function Replace-Exact([string]$Path, [string]$Old, [string]$New) {
    $content = Get-Content -LiteralPath $Path -Raw
    if (-not $content.Contains($Old)) {
        throw "Expected fixture text was not found in $Path"
    }
    [System.IO.File]::WriteAllText($Path, $content.Replace($Old, $New))
}

function New-Case([string]$Name) {
    $destination = Join-Path $matrix $Name
    Copy-Item -LiteralPath $base -Destination $destination -Recurse
    $ordinary = Join-Path $destination "src/test/java/com/example/generated/BlueprintPreflightTest.java"
    $cucumber = Join-Path $destination "src/test/java/com/example/generated/bdd/GeneratedSteps.java"
    $sentinel = 'try { java.nio.file.Files.writeString(java.nio.file.Path.of("target/test-body-ran.sentinel"), "ran"); } catch (java.io.IOException failure) { throw new AssertionError(failure); }'
    Replace-Exact $ordinary "    if (testSession() == null) {" "    $sentinel`r`n    if (testSession() == null) {"
    Replace-Exact $cucumber "    if (scenarioSession.session() == null) {" "    $sentinel`r`n    if (scenarioSession.session() == null) {"
    return $destination
}

function Invoke-ExpectedFailure(
    [string]$Name,
    [string]$ExpectedDiagnostic,
    [string[]]$Arguments
) {
    $directory = Join-Path $matrix $Name
    $log = Join-Path $directory "acceptance.log"
    Push-Location $directory
    try {
        & .\mvnw.cmd @Arguments *> $log
        $exitCode = $LASTEXITCODE
    } finally {
        Pop-Location
    }
    if ($exitCode -eq 0) {
        throw "$Name unexpectedly passed"
    }
    $output = Get-Content -LiteralPath $log -Raw
    if (-not $output.Contains($ExpectedDiagnostic)) {
        throw "$Name did not report expected diagnostic: $ExpectedDiagnostic"
    }
    if (Test-Path -LiteralPath (Join-Path $directory "target/test-body-ran.sentinel")) {
        throw "$Name executed a consumer test body before failing"
    }
    [pscustomobject]@{
        Case = $Name
        ExitCode = $exitCode
        Diagnostic = $ExpectedDiagnostic
        TestBodyExecutions = 0
    }
}

$provider = New-Case "profile-provider-substitution"
$pom = Join-Path $provider "pom.xml"
Replace-Exact $pom "<artifactId>surefire-testng</artifactId>" '<artifactId>${surefire.provider}</artifactId>'
Replace-Exact $pom "<taf.version>1.3.1</taf.version>" "<taf.version>1.3.1</taf.version>`r`n    <surefire.provider>surefire-testng</surefire.provider>"
Replace-Exact $pom "  <build>" @"
  <profiles>
    <profile>
      <id>provider-substitution</id>
      <properties><surefire.provider>surefire-junit-platform</surefire.provider></properties>
    </profile>
  </profiles>
  <build>
"@

$missingBase = New-Case "missing-base"
Replace-Exact (Join-Path $missingBase "src/test/java/com/example/generated/BlueprintPreflightTest.java") " extends com.codinglair.taf.runtime.testng.TafBaseTest" ""
Replace-Exact (Join-Path $missingBase "src/test/java/com/example/generated/BlueprintPreflightTest.java") "if (testSession() == null)" "if (this == null)"

$missingHooks = New-Case "missing-cucumber-hooks"
Replace-Exact (Join-Path $missingHooks "src/test/java/com/example/generated/bdd/GeneratedCucumberRunner.java") ', "com.codinglair.taf.runtime.cucumber"' ""

$copiedLifecycle = New-Case "copied-lifecycle"
$steps = Join-Path $copiedLifecycle "src/test/java/com/example/generated/bdd/GeneratedSteps.java"
Replace-Exact $steps "  @Then" "  @io.cucumber.java.Before`r`n  public void copiedLifecycle() {}`r`n`r`n  @Then"

$skipAttempt = New-Case "skip-tests-attempt"
Replace-Exact (Join-Path $skipAttempt "src/test/java/com/example/generated/BlueprintPreflightTest.java") " extends com.codinglair.taf.runtime.testng.TafBaseTest" ""
Replace-Exact (Join-Path $skipAttempt "src/test/java/com/example/generated/BlueprintPreflightTest.java") "if (testSession() == null)" "if (this == null)"

$results = @(
    Invoke-ExpectedFailure "profile-provider-substitution" "provider.testng" @("-B", "-ntp", "clean", "verify", "-Pprovider-substitution")
    Invoke-ExpectedFailure "missing-base" "integration.base-test" @("-B", "-ntp", "clean", "verify")
    Invoke-ExpectedFailure "missing-cucumber-hooks" "integration.cucumber-hooks" @("-B", "-ntp", "clean", "verify", "-Dsurefire.suiteXmlFiles=src/test/resources/testng-bdd.xml")
    Invoke-ExpectedFailure "copied-lifecycle" "lifecycle.consumer-owned" @("-B", "-ntp", "clean", "verify", "-Dsurefire.suiteXmlFiles=src/test/resources/testng-bdd.xml")
    Invoke-ExpectedFailure "skip-tests-attempt" "integration.base-test" @("-B", "-ntp", "clean", "verify", "-DskipTests")
)

$results | Format-Table -AutoSize
