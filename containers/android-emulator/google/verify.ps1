[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$dockerfile = Get-Content (Join-Path $PSScriptRoot 'Dockerfile') -Raw
$entrypoint = Get-Content (Join-Path $PSScriptRoot 'entrypoint.sh') -Raw
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$approval = Get-Content (Join-Path $repositoryRoot 'docs/decisions/MOB-003-reference-qualification-approval.md') -Raw
$handoff = Get-Content (Join-Path $repositoryRoot 'docs/assignments/MOB-003-handoff.md') -Raw

foreach ($required in @(
  'MOB-003-GOV-001',
  'No publication or distribution of the assembled emulator image',
  'consumers are responsible for providing and configuring their own mobile testing infrastructure'
)) {
  if (-not $approval.Contains($required)) { throw "Missing MOB-003 approval control: $required" }
}
foreach ($required in @(
  'MOB-003: COMPLETE — GITHUB QUALIFICATION PASSED',
  '90050184653',
  '9b5ca6906c25e21d69f99abdeb90fd5948ff0795'
)) {
  if (-not $handoff.Contains($required)) { throw "Missing accepted MOB-003 evidence reference: $required" }
}

foreach ($required in @(
  '0654f694b46794fae4b178f1e1a17cb60c5d2d34',
  '95771e0ae431897b2a4bd2d97fa095f29a8b0624a7b216baf529f9306161c266',
  '783a40134baf4f3012d4464fbe1571b1612a0dbd2e7a44d14bd8328923443833',
  'd230f13842f60f782a8645f9c813f8f845bf36089ea7289f28c48f17979313f1',
  'USER 10001:10001',
  'ANDROID_SDK_LICENSE_ACCEPTED=false'
)) {
  if (-not $dockerfile.Contains($required)) { throw "Missing immutable build control: $required" }
}
$buildScripts = (Get-Content (Join-Path $PSScriptRoot 'build.ps1') -Raw) +
  (Get-Content (Join-Path $PSScriptRoot 'build.sh') -Raw)
if (-not $buildScripts.Contains('187e1892a7752c9384c59aba9517dd8e40610b748c72773e87b63720514463c2')) {
  throw 'The SBOM generator must be pinned by its linux/amd64 digest.'
}
if ($dockerfile -notmatch 'ARG BASE_IMAGE=[^\r\n]+@sha256:[0-9a-f]{64}' -or
    $dockerfile -notmatch 'ARG BUILDER_IMAGE=[^\r\n]+@sha256:[0-9a-f]{64}') {
  throw 'The linux/amd64 builder and runtime base images must be digest pinned.'
}
if (-not $dockerfile.Contains('UBUNTU_SNAPSHOT=20260810T000000Z')) {
  throw 'OS packages must resolve through the immutable Ubuntu snapshot.'
}
if ($entrypoint -match 'privileged' -or $dockerfile -match 'APPIUM|nodejs|npm|VNC|noVNC') {
  throw 'The candidate contains a prohibited service or privilege setting.'
}
if (-not $entrypoint.Contains('-no-metrics')) { throw 'Metrics must be explicitly disabled.' }
foreach ($required in @(
  'system_images = f"{sdk}/system-images/android-34/google_apis"',
  '("SYSTEM_IMAGE_URL", "SYSTEM_IMAGE_SHA256", system_images)',
  'f"{system_images}/x86_64/NOTICE.txt"',
  'f"{sdk}/SYSTEM-IMAGE-NOTICE.txt"',
  'System image did not preserve the required x86_64 directory layout'
)) {
  if (-not $dockerfile.Contains($required)) { throw "Missing direct-extraction layout control: $required" }
}
if ($dockerfile.Contains('/tmp/system-image') -or $dockerfile.Contains('cp -a /tmp/system-image')) {
  throw 'The system image must not retain a duplicate expanded temporary tree.'
}

Write-Output 'MOB-003 approval, evidence, immutable-input, and runtime-boundary checks passed.'
