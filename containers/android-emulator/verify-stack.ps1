[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$composeFile = Join-Path $PSScriptRoot 'compose.yaml'
$configuration = docker compose --file $composeFile config --format json | ConvertFrom-Json
if ($LASTEXITCODE -ne 0) { throw 'Docker Compose configuration validation failed.' }

$emulator = $configuration.services.'android-emulator'
$appium = $configuration.services.appium

foreach ($service in @($emulator, $appium)) {
  if ($service.image -notmatch '@sha256:[0-9a-f]{64}$') {
    throw "Image is not pinned by digest: $($service.image)"
  }
  if ($service.privileged -eq $true) { throw 'Privileged containers are prohibited.' }
  if (($service.volumes | ConvertTo-Json -Compress) -match 'docker\.sock') {
    throw 'Docker socket mounts are prohibited.'
  }
}

if ($emulator.security_opt -contains 'no-new-privileges:true') {
  throw 'The approved emulator image requires its explicit no-new-privileges exception during initialization.'
}
if ($appium.security_opt -notcontains 'no-new-privileges:true') {
  throw 'Appium must enable no-new-privileges.'
}
if ($null -ne $emulator.ports -and @($emulator.ports).Count -gt 0) {
  throw 'The emulator must not publish host ports.'
}
if (@($emulator.networks.PSObject.Properties.Name).Count -ne 1 -or
    @($emulator.networks.PSObject.Properties.Name)[0] -ne 'mobile') {
  throw 'The emulator must remain exclusively on the internal mobile network.'
}
if (@($appium.networks.PSObject.Properties.Name) -notcontains 'mobile' -or
    @($appium.networks.PSObject.Properties.Name) -notcontains 'appium-ingress') {
  throw 'Appium must join the internal ADB network and the host-ingress bridge.'
}
if (@($emulator.devices).Count -ne 1 -or ($emulator.devices | ConvertTo-Json -Compress) -notmatch '/dev/kvm') {
  throw 'The emulator may receive only the explicit KVM device boundary.'
}
if ($emulator.shm_size -ne 2147483648) {
  throw 'The emulator must receive an effective 2 GiB /dev/shm allocation.'
}
if ($null -ne $emulator.environment.SHM_SIZE) {
  throw 'SHM_SIZE environment variables do not configure Docker shared memory; use Compose shm_size.'
}

$published = @($appium.ports)[0]
if ($published.host_ip -ne '127.0.0.1' -or $published.target -ne 4723 -or $published.published -ne '4724') {
  throw 'Appium must publish container port 4723 as loopback-only host port 4724.'
}

Write-Output 'MOB-002 Compose security and reproducibility checks passed.'
