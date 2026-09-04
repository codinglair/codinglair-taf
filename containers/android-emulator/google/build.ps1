[CmdletBinding()]
param(
  [switch]$AcceptAndroidSdkLicense,
  [switch]$Clean,
  [string]$Image = 'codinglair-taf/android-emulator:mob-003-api34'
)

$ErrorActionPreference = 'Stop'
if (-not $AcceptAndroidSdkLicense) {
  throw 'Read https://developer.android.com/studio/terms and pass -AcceptAndroidSdkLicense explicitly.'
}

$context = $PSScriptRoot
$evidenceDirectory = Join-Path (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')) 'target\mob-003'
New-Item -ItemType Directory -Force -Path $evidenceDirectory | Out-Null
$arguments = @(
  'buildx', 'build', '--platform', 'linux/amd64', '--load', '--provenance=mode=max',
  '--attest', 'type=sbom,generator=docker.io/docker/buildkit-syft-scanner@sha256:187e1892a7752c9384c59aba9517dd8e40610b748c72773e87b63720514463c2',
  '--metadata-file', (Join-Path $evidenceDirectory 'build-metadata.json'),
  '--build-arg', 'ANDROID_SDK_LICENSE_ACCEPTED=true',
  '--build-arg', 'SOURCE_DATE_EPOCH=1786291200', '--tag', $Image
)
if ($Clean) { $arguments += '--no-cache' }
$arguments += $context
& docker @arguments
if ($LASTEXITCODE -ne 0) { throw 'Controlled Android emulator image build failed.' }

$inspection = docker image inspect $Image | ConvertFrom-Json
if ($LASTEXITCODE -ne 0 -or @($inspection).Count -ne 1) { throw 'Built image inspection failed.' }
if ($inspection[0].Architecture -ne 'amd64' -or $inspection[0].Os -ne 'linux') {
  throw 'Built image is not linux/amd64.'
}
Write-Output "Built $Image with image ID $($inspection[0].Id)."
