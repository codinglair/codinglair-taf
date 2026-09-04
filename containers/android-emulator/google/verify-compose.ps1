[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$composeFile = Join-Path $PSScriptRoot 'compose.qualify.yaml'
$configuration = docker compose --file $composeFile config --format json | ConvertFrom-Json
if ($LASTEXITCODE -ne 0) { throw 'Qualification Compose parsing failed.' }
$emulator = $configuration.services.'android-emulator'
$appium = $configuration.services.appium
if ($emulator.privileged -eq $true -or $appium.privileged -eq $true) { throw 'Privileged mode is prohibited.' }
if ($null -ne $emulator.ports -and @($emulator.ports).Count -ne 0) { throw 'ADB must not be published to the host.' }
if (@($emulator.devices).Count -ne 1 -or ($emulator.devices | ConvertTo-Json -Compress) -notmatch '/dev/kvm') { throw 'Only KVM may be mapped into the emulator.' }
if ($emulator.user -and $emulator.user -notmatch '^10001') { throw 'The image runtime user must remain UID 10001.' }
if (@($emulator.networks.PSObject.Properties.Name).Count -ne 1 -or @($emulator.networks.PSObject.Properties.Name)[0] -ne 'mobile') { throw 'Emulator must join only mobile.' }
if (@($appium.networks.PSObject.Properties.Name) -notcontains 'mobile' -or @($appium.networks.PSObject.Properties.Name) -notcontains 'appium-ingress') { throw 'Appium must bridge both intended networks.' }
if (@($appium.ports)[0].host_ip -ne '127.0.0.1') { throw 'Appium must be loopback-only.' }
Write-Output 'MOB-003 qualification Compose contract passed.'
