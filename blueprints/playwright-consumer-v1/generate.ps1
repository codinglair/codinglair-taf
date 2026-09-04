param(
    [Parameter(Mandatory = $true)][string]$Destination,
    [string]$GroupId = "com.example.taf",
    [string]$ArtifactId = "playwright-consumer",
    [string]$BasePackage = "com.example.taf.playwright",
    [string]$TafVersion,
    [switch]$IncludeBdd,
    [switch]$IncludeAllure
)

$ErrorActionPreference = "Stop"
$blueprintRoot = $PSScriptRoot
$repositoryRoot = (Resolve-Path (Join-Path $blueprintRoot "../..")).Path
if ([string]::IsNullOrWhiteSpace($TafVersion)) {
    [xml]$rootPom = [System.IO.File]::ReadAllText((Join-Path $repositoryRoot 'pom.xml'))
    $TafVersion = $rootPom.project.properties.revision
}
if ([string]::IsNullOrWhiteSpace($TafVersion) -or $TafVersion.Contains('${')) {
    throw 'TafVersion must be a literal Maven version.'
}
$destinationPath = [System.IO.Path]::GetFullPath($Destination)
if (Test-Path -LiteralPath $destinationPath) {
    throw "Destination already exists: $destinationPath"
}

Copy-Item -Recurse -LiteralPath (Join-Path $blueprintRoot "template") -Destination $destinationPath
Copy-Item -LiteralPath (Join-Path $repositoryRoot "mvnw"), (Join-Path $repositoryRoot "mvnw.cmd") -Destination $destinationPath
Copy-Item -Recurse -LiteralPath (Join-Path $repositoryRoot ".mvn") -Destination $destinationPath

$packagePath = $BasePackage.Replace('.', '/')
$sourceRoot = Join-Path $destinationPath "src"
$placeholder = Join-Path $sourceRoot "main/java/__PACKAGE_PATH__"
$targetPackage = Join-Path $sourceRoot "main/java/$packagePath"
New-Item -ItemType Directory -Force -Path (Split-Path $targetPackage) | Out-Null
Move-Item -LiteralPath $placeholder -Destination $targetPackage
$testPlaceholder = Join-Path $sourceRoot "test/java/__PACKAGE_PATH__"
$testPackage = Join-Path $sourceRoot "test/java/$packagePath"
New-Item -ItemType Directory -Force -Path (Split-Path $testPackage) | Out-Null
Move-Item -LiteralPath $testPlaceholder -Destination $testPackage

$pom = Join-Path $destinationPath "pom.xml"
$pomText = [System.IO.File]::ReadAllText($pom).Replace('__GROUP_ID__', $GroupId).Replace('__ARTIFACT_ID__', $ArtifactId).Replace('__TAF_VERSION__', $TafVersion)
$allure = if ($IncludeAllure) { @"
        <dependency>
            <groupId>com.codinglair.taf</groupId>
            <artifactId>codinglair-taf-reporting-allure</artifactId>
            <version>`${taf.version}</version>
        </dependency>
"@ } else { "" }
$pomText = $pomText.Replace('<!-- OPTIONAL_ALLURE -->', $allure)
if ($IncludeBdd) {
    $bdd = @"
        <dependency>
            <groupId>com.codinglair.taf</groupId>
            <artifactId>codinglair-taf-runner-cucumber</artifactId>
            <version>`${taf.version}</version>
            <scope>test</scope>
        </dependency>
"@
    $pomText = $pomText.Replace('<!-- OPTIONAL_BDD -->', $bdd)
    $descriptor = Join-Path $destinationPath "src/test/resources/taf-project.json"
    (Get-Content -Raw $descriptor).Replace('"runners":["testng"]', '"runners":["testng","cucumber-testng"]') | Set-Content $descriptor
    New-Item -ItemType Directory -Force -Path (Join-Path $sourceRoot "test/java/__PACKAGE_PATH__") | Out-Null
    Copy-Item -Recurse -LiteralPath (Join-Path $blueprintRoot "bdd-overlay/src/test/java/__PACKAGE_PATH__/bdd") -Destination (Join-Path $sourceRoot "test/java/__PACKAGE_PATH__")
    Copy-Item -Recurse -LiteralPath (Join-Path $blueprintRoot "bdd-overlay/src/test/resources/features") -Destination (Join-Path $sourceRoot "test/resources")
    Copy-Item -LiteralPath (Join-Path $blueprintRoot "bdd-overlay/src/test/resources/test-data/TC-BDD.csv") -Destination (Join-Path $sourceRoot "test/resources/test-data")
    $bddPlaceholder = Join-Path $sourceRoot "test/java/__PACKAGE_PATH__/bdd"
    Move-Item -LiteralPath $bddPlaceholder -Destination $testPackage
    Remove-Item -LiteralPath (Join-Path $sourceRoot "test/java/__PACKAGE_PATH__")
} else {
    $pomText = $pomText.Replace('<!-- OPTIONAL_BDD -->', '')
}
[System.IO.File]::WriteAllText($pom, $pomText)
Get-ChildItem -LiteralPath $destinationPath -Recurse -File | Where-Object { $_.Name -notin @('mvnw', 'mvnw.cmd') } | ForEach-Object {
    $text = [System.IO.File]::ReadAllText($_.FullName)
    $text = $text.Replace('__BASE_PACKAGE__', $BasePackage).Replace('__ARTIFACT_ID__', $ArtifactId)
    [System.IO.File]::WriteAllText($_.FullName, $text)
}
Write-Output $destinationPath
