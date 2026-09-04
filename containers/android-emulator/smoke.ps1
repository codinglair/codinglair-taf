[CmdletBinding()]
param(
  [ValidateRange(60, 900)]
  [int]$ReadyTimeoutSeconds = 360
)

$ErrorActionPreference = 'Stop'
$composeFile = Join-Path $PSScriptRoot 'compose.yaml'
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$provisioningAttempted = $false

try {
  & (Join-Path $PSScriptRoot 'verify-stack.ps1')
  $provisioningAttempted = $true
  docker compose --file $composeFile up --detach --wait --wait-timeout $ReadyTimeoutSeconds
  if ($LASTEXITCODE -ne 0) { throw 'Android/Appium services did not become healthy.' }

  $status = Invoke-RestMethod -Uri 'http://127.0.0.1:4724/status' -TimeoutSec 10
  if (-not $status.value.ready) { throw 'Appium status did not report ready.' }
  docker compose --file $composeFile exec --no-TTY appium sh -lc "adb devices | grep -q '^android-emulator:5555[[:space:]]*device$'"
  if ($LASTEXITCODE -ne 0) { throw 'Appium cannot reach the emulator over container-network ADB.' }

  $env:TAF_ANDROID_APPIUM_URL = 'http://127.0.0.1:4724'
  $env:TAF_ANDROID_DEVICE_NAME = 'taf-api34'
  $env:TAF_ANDROID_DEVICE_ID = 'android-emulator:5555'
  $env:TAF_ANDROID_APP_PACKAGE = 'com.android.settings'
  $env:TAF_ANDROID_APP_ACTIVITY = '.Settings'
  & (Join-Path $repositoryRoot 'mvnw.cmd') -pl codinglair-taf-runtime/taf-mobile-appium -am verify -Pandroid-emulator
  if ($LASTEXITCODE -ne 0) { throw 'MOB-001 Appium session smoke failed.' }

  docker compose --file $composeFile exec --no-TTY appium adb -s android-emulator:5555 shell am force-stop com.android.settings
  if ($LASTEXITCODE -ne 0) { throw 'Failed to restore the smoke application state.' }
  $processId = docker compose --file $composeFile exec --no-TTY appium adb -s android-emulator:5555 shell pidof com.android.settings
  if (-not [string]::IsNullOrWhiteSpace(($processId -join ''))) {
    throw 'Smoke application remained active after cleanup.'
  }
} finally {
  if ($provisioningAttempted) {
    docker compose --file $composeFile down --remove-orphans
    $remaining = docker compose --file $composeFile ps --quiet
    if (-not [string]::IsNullOrWhiteSpace(($remaining -join ''))) {
      throw 'Compose cleanup left containers behind.'
    }
  }
}

Write-Output 'MOB-002 emulator, Appium session, state restoration, and cleanup smoke passed.'
