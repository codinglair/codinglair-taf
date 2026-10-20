[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Results,
    [Parameter(Mandatory = $true)][string]$Report,
    [Parameter(Mandatory = $true)][string]$Matrix,
    [string]$Output = "target/consumer-evidence/validator.json"
)

$ErrorActionPreference = "Stop"
$repository = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$module = Join-Path $repository "codinglair-taf-runtime/codinglair-taf-reporting-allure"
$maven = Join-Path $repository "mvnw.cmd"

foreach ($candidate in @($Results, $Report, $Matrix)) {
    if (-not (Test-Path -LiteralPath $candidate)) {
        throw "Required evidence input does not exist: $candidate"
    }
}

& $maven -B -ntp -pl codinglair-taf-runtime/codinglair-taf-reporting-allure -am `
    -DskipTests package dependency:build-classpath `
    "-Dmdep.outputFile=$module/target/evidence-validator-classpath.txt"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$dependencies = Get-Content -Raw -LiteralPath (Join-Path $module "target/evidence-validator-classpath.txt")
$classpath = "$module/target/classes$([IO.Path]::PathSeparator)$dependencies"
$outputPath = [IO.Path]::GetFullPath((Join-Path (Get-Location) $Output))

& java -cp $classpath com.codinglair.taf.runtime.core.reporting.impl.allure.AllureEvidenceValidatorCli `
    ([IO.Path]::GetFullPath($Results)) `
    ([IO.Path]::GetFullPath($Report)) `
    ([IO.Path]::GetFullPath($Matrix)) `
    $outputPath
exit $LASTEXITCODE
