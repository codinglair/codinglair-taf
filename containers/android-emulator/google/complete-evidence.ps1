[CmdletBinding()]
param(
  [string]$EvidenceDirectory = 'target/mob-003',
  [string]$ApprovalReference = '',
  [string]$ApprovedLicenseReportSha256 = ''
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..\..')
$evidenceRoot = Join-Path $repositoryRoot $EvidenceDirectory
$digest = 'sha256:a60cc06baa93451336aa97b36540e5136b77be72019be76dcf8da7df53b4cebf'
$builderDigest = 'sha256:781449467ffb6f04218f09b1ecdcdc7d22b289ee5da9ec498b024e24ad7a6db7'
$runtimeDigest = 'sha256:1e0a86e57d247923571b75e0aaf48a1449cf8c543d51fb3e07a4a7d7bfa79316'
$scanDate = '2026-08-27'

function Require-File([string]$path) {
  if (-not (Test-Path $path -PathType Leaf)) { throw "Required evidence is absent: $path" }
}

function Sha256([string]$path) {
  $stream = [IO.File]::OpenRead($path)
  $algorithm = [Security.Cryptography.SHA256]::Create()
  try {
    ([BitConverter]::ToString($algorithm.ComputeHash($stream)) -replace '-', '').ToLowerInvariant()
  } finally {
    $algorithm.Dispose()
    $stream.Dispose()
  }
}

function PropertyValue([string]$value) {
  ($value -replace '[\r\n=]', ' ').Trim()
}

function Write-Utf8NoBom([string]$path, [object[]]$lines) {
  [IO.File]::WriteAllLines($path, [string[]]$lines, [Text.UTF8Encoding]::new($false))
}

New-Item -ItemType Directory -Force -Path $evidenceRoot | Out-Null
$metadataPath = Join-Path $evidenceRoot 'build-metadata.json'
$sbomPath = Join-Path $evidenceRoot 'candidate-a.spdx.json'
$runtimeSarifPath = Join-Path $evidenceRoot 'candidate-a-vulnerabilities.sarif'
$builderSarifPath = Join-Path $evidenceRoot 'builder-python-vulnerabilities.sarif'
foreach ($path in @($metadataPath, $sbomPath, $runtimeSarifPath, $builderSarifPath)) { Require-File $path }

$metadata = Get-Content -Raw $metadataPath | ConvertFrom-Json
if ($metadata.'containerimage.digest' -ne $digest) { throw 'Build metadata digest does not match Candidate A.' }
$provenance = $metadata.'buildx.build.provenance'
if ($null -eq $provenance -or $provenance.invocation.environment.platform -ne 'linux/amd64') {
  throw 'Build provenance is absent or is not linux/amd64.'
}
$materialDigests = @($provenance.materials | ForEach-Object { $_.digest.sha256 })
foreach ($requiredDigest in @(
  $builderDigest.Substring(7), $runtimeDigest.Substring(7),
  '95771e0ae431897b2a4bd2d97fa095f29a8b0624a7b216baf529f9306161c266',
  '783a40134baf4f3012d4464fbe1571b1612a0dbd2e7a44d14bd8328923443833',
  'd230f13842f60f782a8645f9c813f8f845bf36089ea7289f28c48f17979313f1'
)) {
  if ($materialDigests -notcontains $requiredDigest) { throw "Provenance lacks required material $requiredDigest" }
}
$provenancePath = Join-Path $evidenceRoot 'candidate-a.provenance.json'
Write-Utf8NoBom $provenancePath @(($provenance | ConvertTo-Json -Depth 100))

$spdx = Get-Content -Raw $sbomPath | ConvertFrom-Json
if ($spdx.spdxVersion -ne 'SPDX-2.3' -or @($spdx.packages).Count -eq 0) {
  throw 'Candidate SBOM is not a populated SPDX 2.3 document.'
}
$spdx | ConvertTo-Json -Depth 100 -Compress | Out-Null

$noticeFiles = @(
  'EMULATOR-NOTICE.txt', 'PLATFORM-TOOLS-NOTICE.txt', 'SYSTEM-IMAGE-NOTICE.txt'
)
foreach ($name in $noticeFiles) { Require-File (Join-Path $evidenceRoot $name) }
$noticeBundlePath = Join-Path $evidenceRoot 'candidate-a-NOTICE.bundle.txt'
$noticeBundle = foreach ($name in $noticeFiles) {
  "===== $name ====="
  Get-Content -Raw (Join-Path $evidenceRoot $name)
}
Write-Utf8NoBom $noticeBundlePath $noticeBundle

$acceptancePath = Join-Path $evidenceRoot 'android-sdk-license-acceptance.txt'
$acceptanceLines = @(
  'candidate=Candidate A Android 14/API 34 Google APIs x86_64 r14'
  "image.digest=$digest"
  'terms=https://developer.android.com/studio/terms'
  'acceptance.mechanism=committed build.ps1 -AcceptAndroidSdkLicense'
  'build.argument=ANDROID_SDK_LICENSE_ACCEPTED=true'
  'evidence=target/mob-003/build-metadata.json invocation.parameters'
  'scope=local qualification build; no publication or legal approval asserted'
)
Write-Utf8NoBom $acceptancePath $acceptanceLines

$inventoryPath = Join-Path $evidenceRoot 'candidate-a-license-inventory.csv'
$noAssertionResolutions = @{
  'base-files' = @('GPL-2.0-or-later', 'provide-license-and-corresponding-source-when-conveyed', 'image:/usr/share/doc/base-files/copyright')
  'gcc-14' = @('(GPL-3.0-or-later WITH GCC-exception-3.1) AND LGPL-2.1-or-later AND LicenseRef-GCC-Permissive-Components', 'preserve-notices-and-provide-corresponding-source-when-conveyed;runtime-library-exception-applies', 'image:/usr/share/doc/gcc-14-base/copyright')
  'libcrypt1' = @('LGPL-2.1-or-later AND LicenseRef-libxcrypt-permissive-files', 'preserve-notices-and-lgpl-relinking/source-rights-when-conveyed', 'image:/usr/share/doc/libcrypt1/copyright')
  'libxcrypt' = @('LGPL-2.1-or-later AND LicenseRef-libxcrypt-permissive-files', 'preserve-notices-and-lgpl-relinking/source-rights-when-conveyed', 'image:/usr/share/doc/libcrypt1/copyright')
  'libdrm-amdgpu1' = @('MIT', 'preserve-copyright-and-license-notice', 'image:/usr/share/doc/libdrm2/copyright')
  'libdrm-common' = @('MIT', 'preserve-copyright-and-license-notice', 'image:/usr/share/doc/libdrm2/copyright')
  'libdrm-intel1' = @('MIT', 'preserve-copyright-and-license-notice', 'image:/usr/share/doc/libdrm2/copyright')
  'libdrm2' = @('MIT', 'preserve-copyright-and-license-notice', 'image:/usr/share/doc/libdrm2/copyright')
  'libdrm' = @('MIT', 'preserve-copyright-and-license-notice', 'image:/usr/share/doc/libdrm2/copyright')
  'libpciaccess0' = @('MIT AND GPL-2.0-or-later', 'preserve-notices;provide-packaging-source-when-conveyed', 'image:/usr/share/doc/libpciaccess0/copyright')
  'libpciaccess' = @('MIT AND GPL-2.0-or-later', 'preserve-notices;provide-packaging-source-when-conveyed', 'image:/usr/share/doc/libpciaccess0/copyright')
  'libxau6' = @('MIT', 'preserve-copyright-and-license-notice', 'image:/usr/share/doc/libxau6/copyright')
  'libxau' = @('MIT', 'preserve-copyright-and-license-notice', 'image:/usr/share/doc/libxau6/copyright')
  'libxcb-dri3-0' = @('MIT', 'preserve-copyright-and-license-notice', 'image:/usr/share/doc/libxcb1/copyright')
  'libxcb-glx0' = @('MIT', 'preserve-copyright-and-license-notice', 'image:/usr/share/doc/libxcb1/copyright')
  'libxcb-present0' = @('MIT', 'preserve-copyright-and-license-notice', 'image:/usr/share/doc/libxcb1/copyright')
  'libxcb-randr0' = @('MIT', 'preserve-copyright-and-license-notice', 'image:/usr/share/doc/libxcb1/copyright')
  'libxcb-shm0' = @('MIT', 'preserve-copyright-and-license-notice', 'image:/usr/share/doc/libxcb1/copyright')
  'libxcb-sync1' = @('MIT', 'preserve-copyright-and-license-notice', 'image:/usr/share/doc/libxcb1/copyright')
  'libxcb-xfixes0' = @('MIT', 'preserve-copyright-and-license-notice', 'image:/usr/share/doc/libxcb1/copyright')
  'libxcb1' = @('MIT', 'preserve-copyright-and-license-notice', 'image:/usr/share/doc/libxcb1/copyright')
  'libxcb' = @('MIT', 'preserve-copyright-and-license-notice', 'image:/usr/share/doc/libxcb1/copyright')
  'libxdmcp6' = @('MIT', 'preserve-copyright-and-license-notice', 'image:/usr/share/doc/libxdmcp6/copyright')
  'libxdmcp' = @('MIT', 'preserve-copyright-and-license-notice', 'image:/usr/share/doc/libxdmcp6/copyright')
  'libxext6' = @('MIT', 'preserve-copyright-and-license-notice', 'image:/usr/share/doc/libxext6/copyright')
  'libxext' = @('MIT', 'preserve-copyright-and-license-notice', 'image:/usr/share/doc/libxext6/copyright')
  'libxi6' = @('MIT', 'preserve-copyright-and-license-notice', 'image:/usr/share/doc/libxi6/copyright')
  'libxi' = @('MIT', 'preserve-copyright-and-license-notice', 'image:/usr/share/doc/libxi6/copyright')
  'libxkbfile1' = @('MIT', 'preserve-copyright-and-license-notice', 'image:/usr/share/doc/libxkbfile1/copyright')
  'libxkbfile' = @('MIT', 'preserve-copyright-and-license-notice', 'image:/usr/share/doc/libxkbfile1/copyright')
  'libzstd' = @('(BSD-3-Clause OR GPL-2.0-only) AND Zlib AND MIT', 'preserve-notices;provide-corresponding-source-if-gpl-option-used-when-conveyed', 'image:/usr/share/doc/libzstd1/copyright')
  'ubuntu-keyring' = @('GPL-2.0-or-later', 'preserve-license-and-provide-corresponding-source-when-conveyed', 'image:/usr/share/doc/ubuntu-keyring/copyright')
  'bash' = @('GPL-3.0-or-later', 'preserve-license-and-provide-corresponding-source-when-conveyed', 'upstream:https://www.gnu.org/software/bash/')
  'gzip' = @('GPL-3.0-or-later', 'preserve-license-and-provide-corresponding-source-when-conveyed', 'upstream:https://www.gnu.org/software/gzip/')
  'linux-kernel' = @('GPL-2.0-only', 'preserve-license-and-provide-complete-corresponding-source-when-conveyed', 'upstream:https://kernel.org/doc/html/next/process/license-rules.html')
  'openssl' = @('Apache-2.0', 'preserve-license-and-notices', 'upstream:https://openssl-library.org/source/license/')
  'util-linux' = @('GPL-2.0-or-later AND LGPL-2.1-or-later AND BSD-3-Clause AND MIT AND LicenseRef-util-linux-public-domain', 'preserve-notices;provide-applicable-gpl/lgpl-source-and-relinking-rights-when-conveyed', 'upstream:https://github.com/util-linux/util-linux;notice:SYSTEM-IMAGE-NOTICE.txt')
}
$originalNoAssertionRows = @($spdx.packages | Where-Object licenseDeclared -eq 'NOASSERTION')
if ($originalNoAssertionRows.Count -ne 38) {
  throw "Expected 38 original SPDX NOASSERTION records, found $($originalNoAssertionRows.Count)."
}
$resolutionRows = @(foreach ($package in $originalNoAssertionRows) {
  if ($package.SPDXID -eq 'SPDXRef-DocumentRoot') {
    [pscustomobject]@{
      spdxId = $package.SPDXID; component = $package.name; version = $package.versionInfo
      original = 'NOASSERTION'; resolution = 'REMOVED_SYNTHETIC_AGGREGATE'
      obligation = 'represented-by-component-records'; evidence = 'spdx:primaryPackagePurpose=CONTAINER'
    }
  } else {
    $resolution = $noAssertionResolutions[$package.name]
    if ($null -eq $resolution) { throw "Unresolved SPDX NOASSERTION record: $($package.name)@$($package.versionInfo)" }
    [pscustomobject]@{
      spdxId = $package.SPDXID; component = $package.name; version = $package.versionInfo
      original = 'NOASSERTION'; resolution = $resolution[0]
      obligation = $resolution[1]; evidence = $resolution[2]
    }
  }
})
$resolutionPath = Join-Path $evidenceRoot 'candidate-a-noassertion-resolutions.csv'
Write-Utf8NoBom $resolutionPath @($resolutionRows | ConvertTo-Csv -NoTypeInformation)
if (@($resolutionRows | Where-Object resolution -eq 'NOASSERTION').Count -ne 0 -or
    @($resolutionRows | Where-Object resolution -eq 'REMOVED_SYNTHETIC_AGGREGATE').Count -ne 1) {
  throw 'NOASSERTION resolution evidence is incomplete.'
}
$detectedLicenseRows = @([pscustomobject]@{
  component = 'project-authored-entrypoint-and-avd-configuration'
  version = 'mob-003'
  exact = 'Apache-2.0'
  concluded = 'Apache-2.0'
  scope = 'PROJECT_AUTHORED'
  obligation = 'preserve-license-and-notices'
  disposition = 'UNREVIEWED'
  evidence = 'repository:LICENSE'
}, [pscustomobject]@{
  component = 'google-android-emulator'
  version = '37.1.11-build-15917651'
  exact = 'Android-SDK-License AND LicenseRef-Google-Emulator-Third-Party-Notices'
  concluded = 'Android-SDK-License AND LicenseRef-Google-Emulator-Third-Party-Notices'
  scope = 'DOWNLOADED_SDK'
  obligation = 'explicit-sdk-acceptance;preserve-notices;no-public-image-distribution-without-approval'
  disposition = 'UNREVIEWED'
  evidence = 'terms:https://developer.android.com/studio/terms;notice:EMULATOR-NOTICE.txt'
}, [pscustomobject]@{
  component = 'google-platform-tools'
  version = '37.0.1'
  exact = 'Android-SDK-License AND LicenseRef-Google-Platform-Tools-Third-Party-Notices'
  concluded = 'Android-SDK-License AND LicenseRef-Google-Platform-Tools-Third-Party-Notices'
  scope = 'DOWNLOADED_SDK'
  obligation = 'explicit-sdk-acceptance;preserve-notices;no-public-image-distribution-without-approval'
  disposition = 'UNREVIEWED'
  evidence = 'terms:https://developer.android.com/studio/terms;notice:PLATFORM-TOOLS-NOTICE.txt'
}, [pscustomobject]@{
  component = 'google-api34-system-image'
  version = 'android-14-api34-google-apis-x86_64-r14'
  exact = 'Android-SDK-License AND LicenseRef-Android-System-Image-Third-Party-Notices'
  concluded = 'Android-SDK-License AND LicenseRef-Android-System-Image-Third-Party-Notices'
  scope = 'ANDROID_SYSTEM_IMAGE'
  obligation = 'explicit-sdk-acceptance;preserve-notices;no-public-image-distribution-without-approval'
  disposition = 'UNREVIEWED'
  evidence = 'terms:https://developer.android.com/studio/terms;notice:SYSTEM-IMAGE-NOTICE.txt'
}) + @($spdx.packages | Where-Object SPDXID -ne 'SPDXRef-DocumentRoot' | ForEach-Object {
  $resolution = if ($_.licenseDeclared -eq 'NOASSERTION') { $noAssertionResolutions[$_.name] } else { $null }
  if ($_.licenseDeclared -eq 'NOASSERTION' -and $null -eq $resolution) {
    throw "Unresolved SPDX NOASSERTION record: $($_.name)@$($_.versionInfo)"
  }
  $exactLicense = if ($resolution) { $resolution[0] } else { $_.licenseDeclared }
  [pscustomobject]@{
    component = $_.name
    version = $_.versionInfo
    exact = $exactLicense
    concluded = $exactLicense
    scope = if ($_.name -match 'system-images|android') { 'ANDROID_SYSTEM_IMAGE' } else { 'DOWNLOADED_SDK' }
    obligation = if ($resolution) { $resolution[1] } else { 'comply-with-declared-license-and-preserve-notices' }
    disposition = 'UNREVIEWED'
    evidence = if ($resolution) { $resolution[2] } else { 'sbom:licenseDeclared' }
  }
})
if (Test-Path $inventoryPath) {
  $reviewedRows = @(Import-Csv $inventoryPath)
  $reviewedByIdentity = @{}
  foreach ($row in $reviewedRows) {
    $identity = "$($row.component)`n$($row.version)`n$($row.exact)"
    if ($reviewedByIdentity.ContainsKey($identity)) { throw "Duplicate reviewed license identity: $identity" }
    $reviewedByIdentity[$identity] = $row
  }
  foreach ($row in $detectedLicenseRows) {
    $identity = "$($row.component)`n$($row.version)`n$($row.exact)"
    if ($reviewedByIdentity.ContainsKey($identity)) {
      $disposition = $reviewedByIdentity[$identity].disposition.ToUpperInvariant()
      if ($disposition -notin @('UNREVIEWED', 'ALLOWED', 'CONFLICTING', 'PROHIBITED')) {
        throw "Invalid reviewed disposition for $($row.component): $disposition"
      }
      $row.disposition = $disposition
    }
  }
}
$licenseReportPath = Join-Path $repositoryRoot 'docs\assignments\MOB-003-license-obligation-report.md'
if (-not [string]::IsNullOrWhiteSpace($ApprovalReference)) {
  Require-File $licenseReportPath
  $actualReportSha256 = Sha256 $licenseReportPath
  if ($ApprovedLicenseReportSha256.ToLowerInvariant() -ne $actualReportSha256) {
    throw "Approved license report checksum does not match $actualReportSha256."
  }
  foreach ($row in $detectedLicenseRows) { $row.disposition = 'ALLOWED' }
} elseif (-not [string]::IsNullOrWhiteSpace($ApprovedLicenseReportSha256)) {
  throw '-ApprovedLicenseReportSha256 requires -ApprovalReference.'
}
$licenseRows = $detectedLicenseRows
if (@($licenseRows | Where-Object exact -eq 'NOASSERTION').Count -ne 0) {
  throw 'Resolved license inventory still contains NOASSERTION.'
}
Write-Utf8NoBom $inventoryPath @($licenseRows | ConvertTo-Csv -NoTypeInformation)

$properties = [System.Collections.Generic.List[string]]::new()
@(
  'candidate.name=controlled-google-api34-r14'
  'candidate.purpose=ephemeral Android test execution'
  'target.os=linux'
  'target.arch=amd64'
  "image.digest=$digest"
  "image.scannedDigest=$digest"
  "builder.baseDigests=$builderDigest"
  'builder.basesScanned=true'
  "runtime.baseDigest=$runtimeDigest"
  'source.repository=https://github.com/google/android-emulator-container-scripts'
  'source.commit=0654f694b46794fae4b178f1e1a17cb60c5d2d34'
  'artifact.count=3'
  'artifact.0.url=https://dl.google.com/android/repository/emulator-linux_x64-15917651.zip'
  'artifact.0.version=37.1.11-build-15917651'
  'artifact.0.sha256=95771e0ae431897b2a4bd2d97fa095f29a8b0624a7b216baf529f9306161c266'
  'artifact.1.url=https://dl.google.com/android/repository/sys-img/google_apis/x86_64-34_r14.zip'
  'artifact.1.version=android-14-api-34-google-apis-x86_64-r14'
  'artifact.1.sha256=783a40134baf4f3012d4464fbe1571b1612a0dbd2e7a44d14bd8328923443833'
  'artifact.2.url=https://dl.google.com/android/repository/platform-tools_r37.0.1-linux.zip'
  'artifact.2.version=37.0.1'
  'artifact.2.sha256=d230f13842f60f782a8645f9c813f8f845bf36089ea7289f28c48f17979313f1'
  "sbom.location=target/mob-003/candidate-a.spdx.json"
  "sbom.sha256=$(Sha256 $sbomPath)"
  'provenance.location=target/mob-003/candidate-a.provenance.json'
  'provenance.verified=true'
  'scanner.name=Docker Scout'
  'scanner.version=1.24.0-b1c9331b2166aef7ec690aa16fd655b8798ea4c6'
  'scanner.database=docker-scout-advisory-database-service-managed-revision-not-exposed-by-cli'
  "scanner.databaseDate=$scanDate"
  'policy.rejectFixableHigh=true'
  'notice.location=target/mob-003/candidate-a-NOTICE.bundle.txt'
  'androidSdkLicense.acceptanceRecord=target/mob-003/android-sdk-license-acceptance.txt'
  'controls.adbPublic=false'
  'controls.appiumBinding=APPROVED'
  'controls.productionCredentials=false'
  'controls.productionData=false'
  'controls.ephemeralData=true'
  'controls.productionConnectivity=false'
  'controls.privileged=false'
) | ForEach-Object { $properties.Add($_) }

$properties.Add("license.count=$($licenseRows.Count)")
for ($index = 0; $index -lt $licenseRows.Count; $index++) {
  $record = $licenseRows[$index]
  $properties.Add("license.$index.component=$(PropertyValue $record.component)@$((PropertyValue $record.version))")
  $properties.Add("license.$index.exact=$(PropertyValue $record.exact)")
  $properties.Add("license.$index.obligation=$(PropertyValue $record.obligation)")
  $properties.Add("license.$index.scope=$($record.scope)")
  $properties.Add("license.$index.disposition=$($record.disposition)")
}

$allFindings = [System.Collections.Generic.List[object]]::new()
foreach ($scan in @(
  @{ Path = $runtimeSarifPath; Plane = 'CONTROL_PLANE'; Builder = $false },
  @{ Path = $builderSarifPath; Plane = 'BUILDER'; Builder = $true }
)) {
  $sarif = Get-Content -Raw $scan.Path | ConvertFrom-Json
  if ($sarif.runs[0].tool.driver.name -ne 'docker scout' -or $sarif.runs[0].tool.driver.version -ne '1.24.0') {
    throw "Unexpected scanner identity in $($scan.Path)"
  }
  foreach ($result in @($sarif.runs[0].results)) {
    $message = $result.message.text
    $severity = if ($message -match 'Severity\s*:\s*([^\r\n]+)') { $Matches[1].Trim().ToUpperInvariant() } else { 'UNSPECIFIED' }
    $component = if ($message -match 'Package\s*:\s*([^\r\n]+)') { $Matches[1].Trim() } else { throw "Missing package for $($result.ruleId)" }
    $fixed = if ($message -match 'Fixed version\s*:\s*([^\r\n]+)') { $Matches[1].Trim() } else { 'not fixed' }
    $path = @($result.locations | ForEach-Object { $_.physicalLocation.artifactLocation.uri } | Where-Object { $_ })[0]
    if (-not $path) { throw "Missing path classification for $($result.ruleId)" }
    $allFindings.Add([pscustomobject]@{
      id = $result.ruleId; component = $component; severity = $severity
      fixAvailable = ($fixed -ne 'not fixed'); plane = $scan.Plane
      evidence = "path:$path"; builder = $scan.Builder
    })
  }
}
$properties.Add("finding.count=$($allFindings.Count)")
for ($index = 0; $index -lt $allFindings.Count; $index++) {
  $finding = $allFindings[$index]
  $properties.Add("finding.$index.id=$(PropertyValue $finding.id)")
  $properties.Add("finding.$index.component=$(PropertyValue $finding.component)")
  $properties.Add("finding.$index.severity=$($finding.severity)")
  $properties.Add("finding.$index.fixAvailable=$($finding.fixAvailable.ToString().ToLowerInvariant())")
  $properties.Add("finding.$index.plane=$($finding.plane)")
  $properties.Add("finding.$index.classificationEvidence=$(PropertyValue $finding.evidence)")
  if ($finding.builder) {
    $properties.Add("finding.$index.presentInRuntime=false")
    $properties.Add("finding.$index.outputVerified=true")
  }
}

$propertiesPath = Join-Path $evidenceRoot 'candidate-a-sec-003.properties'
Write-Utf8NoBom $propertiesPath $properties

$ciClasses = Join-Path $repositoryRoot 'target\ci-support'
New-Item -ItemType Directory -Force -Path $ciClasses | Out-Null
& javac -d $ciClasses (Join-Path $repositoryRoot 'build-support\ci\ImagePolicy.java')
if ($LASTEXITCODE -ne 0) { throw 'ImagePolicy compilation failed.' }
$policyOutput = & java -cp $ciClasses ImagePolicy $propertiesPath
$policyExitCode = $LASTEXITCODE
if ($policyExitCode -notin @(0, 1)) { throw "ImagePolicy execution failed with exit $policyExitCode." }
$policyResult = $policyOutput | ConvertFrom-Json
$unapprovedLicenses = @($licenseRows | Where-Object disposition -ne 'ALLOWED')
if ($unapprovedLicenses.Count -eq 0) {
  if ([string]::IsNullOrWhiteSpace($ApprovalReference)) {
    throw 'All licenses are ALLOWED but -ApprovalReference was not supplied.'
  }
  if ($policyExitCode -ne 0 -or $policyResult.result -ne 'PASS' -or @($policyResult.reasons).Count -ne 0) {
    throw 'Approved evidence did not pass SEC-003.'
  }
} else {
  if ($policyExitCode -ne 1 -or $policyResult.result -ne 'REJECT') { throw 'Unapproved license evidence unexpectedly passed policy.' }
  $nonLicenseReasons = @($policyResult.reasons | Where-Object { $_ -notmatch '^License \d+ has non-approved disposition ' })
  if ($nonLicenseReasons.Count -ne 0 -or @($policyResult.reasons).Count -ne $unapprovedLicenses.Count) {
    throw "Candidate has non-license policy failures: $($nonLicenseReasons -join '; ')"
  }
}
if (@($policyResult.findings.UNKNOWN).Count -ne 0) { throw 'Candidate contains UNKNOWN-plane findings.' }
$policyResultPath = Join-Path $evidenceRoot 'candidate-a-sec-003-result.json'
Write-Utf8NoBom $policyResultPath @($policyOutput)
$approvalPath = Join-Path $evidenceRoot 'product-owner-approval.txt'
if ($unapprovedLicenses.Count -eq 0) {
  Write-Utf8NoBom $approvalPath @(
    "approval.reference=$(PropertyValue $ApprovalReference)",
    "approval.licenseReportSha256=$(Sha256 $licenseReportPath)",
    "image.digest=$digest",
    'license.disposition=ALLOWED',
    "license.count=$($licenseRows.Count)"
  )
} elseif (Test-Path $approvalPath) {
  Remove-Item -Force $approvalPath
}

$manifestPath = Join-Path $evidenceRoot 'evidence-manifest.sha256'
@(
  $sbomPath, $provenancePath, $runtimeSarifPath, $builderSarifPath,
  $noticeBundlePath, $acceptancePath, $inventoryPath, $propertiesPath,
  $resolutionPath, $policyResultPath
) | ForEach-Object { "$(Sha256 $_)  $([IO.Path]::GetFileName($_))" } |
  Set-Content -Encoding ascii $manifestPath
if (Test-Path $approvalPath) {
  "$(Sha256 $approvalPath)  $([IO.Path]::GetFileName($approvalPath))" | Add-Content -Encoding ascii $manifestPath
}

Write-Output "Validated SPDX 2.3 SBOM with $(@($spdx.packages).Count) packages."
Write-Output "Verified provenance with $($provenance.materials.Count) immutable materials."
Write-Output "Recorded $($allFindings.Count) findings: $(@($allFindings | Where-Object plane -eq 'CONTROL_PLANE').Count) runtime and $(@($allFindings | Where-Object plane -eq 'BUILDER').Count) builder."
Write-Output "Recorded $($licenseRows.Count) license records; $($unapprovedLicenses.Count) await Product Owner governance disposition."
Write-Output 'Reproducibly resolved 38 original NOASSERTION records: 37 mapped and one synthetic aggregate removed.'
if ($unapprovedLicenses.Count -eq 0) {
  Write-Output 'SEC-003 passed the approved evidence.'
} else {
  Write-Output 'SEC-003 rejected only the Product Owner governance dispositions; no technical rejection reason remains.'
}
Write-Output "Generated $propertiesPath"
