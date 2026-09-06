[CmdletBinding()]
param(
  [ValidateRange(1, 10)]
  [int]$Runs = 2,
  [switch]$ControlledFailure,
  [string]$Image = 'codinglair-taf/android-emulator:mob-003-api34'
)

$ErrorActionPreference = 'Stop'
$composeFile = Join-Path $PSScriptRoot 'compose.qualify.yaml'
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$evidenceRoot = Join-Path $repositoryRoot 'target\mob-003\qualification'
$mavenWrapper = if ([System.Environment]::OSVersion.Platform -eq [System.PlatformID]::Win32NT) {
  Join-Path $repositoryRoot 'mvnw.cmd'
} else {
  Join-Path $repositoryRoot 'mvnw'
}
$smokeReports = Join-Path $repositoryRoot 'codinglair-taf-runtime\taf-mobile-appium\target\surefire-reports'
$env:TAF_MOB003_IMAGE = $Image
$kvmGid = (& stat -c '%g' /dev/kvm | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $kvmGid -notmatch '^\d+$') {
  throw 'Unable to determine the numeric KVM device group ID.'
}
$env:TAF_KVM_GID = $kvmGid
New-Item -ItemType Directory -Force -Path $evidenceRoot | Out-Null

function Invoke-Docker([string[]]$Arguments) {
  & docker @Arguments
  if ($LASTEXITCODE -ne 0) { throw "Docker command failed: docker $($Arguments -join ' ')" }
}

for ($run = 1; $run -le $Runs; $run++) {
  $runName = if ($ControlledFailure) { 'controlled-failure' } else { "run-$run" }
  $runDirectory = Join-Path $evidenceRoot $runName
  New-Item -ItemType Directory -Force -Path $runDirectory | Out-Null
  foreach ($evidenceFile in @('result.txt', 'failure.txt', 'compose-ps.txt', 'compose-logs.txt', 'cleanup.txt')) {
    Remove-Item -Force -ErrorAction SilentlyContinue (Join-Path $runDirectory $evidenceFile)
  }
  try {
    Invoke-Docker @('compose', '--file', $composeFile, 'down', '--remove-orphans')
    Invoke-Docker @('compose', '--file', $composeFile, 'up', '--detach', '--wait', '--wait-timeout', '420')
    $uid = docker compose --file $composeFile exec --no-TTY android-emulator id -u
    if ($LASTEXITCODE -ne 0 -or ($uid -join '').Trim() -ne '10001') { throw 'Emulator runtime UID is not 10001.' }
    Invoke-Docker @('compose', '--file', $composeFile, 'exec', '--no-TTY', 'android-emulator', 'test', '-r', '/dev/kvm')
    Invoke-Docker @('compose', '--file', $composeFile, 'exec', '--no-TTY', 'android-emulator', 'test', '-w', '/dev/kvm')
    Invoke-Docker @('compose', '--file', $composeFile, 'exec', '--no-TTY', 'android-emulator', 'adb', 'connect', '127.0.0.1:5557')
    $deviceState = docker compose --file $composeFile exec --no-TTY android-emulator adb -s 127.0.0.1:5557 get-state
    $boot = docker compose --file $composeFile exec --no-TTY android-emulator adb -s 127.0.0.1:5557 shell getprop sys.boot_completed
    $release = docker compose --file $composeFile exec --no-TTY android-emulator adb -s 127.0.0.1:5557 shell getprop ro.build.version.release
    $sdk = docker compose --file $composeFile exec --no-TTY android-emulator adb -s 127.0.0.1:5557 shell getprop ro.build.version.sdk
    if (($deviceState -join '').Trim() -ne 'device' -or ($boot -join '').Trim() -ne '1' -or
        ($release -join '').Trim() -ne '14' -or ($sdk -join '').Trim() -ne '34') {
      throw 'Android device state or boot identity did not match Candidate A.'
    }
    Invoke-Docker @('compose', '--file', $composeFile, 'exec', '--no-TTY', 'appium', 'getent', 'hosts', 'android-emulator')
    $appiumDevices = docker compose --file $composeFile exec --no-TTY appium adb devices
    if (($appiumDevices -join "`n") -notmatch 'android-emulator:5555\s+device') { throw 'Appium cannot reach Candidate A through internal DNS.' }

    if ($ControlledFailure) { throw 'CONTROLLED_FAILURE: cleanup verification.' }

    $env:TAF_ANDROID_APPIUM_URL = 'http://127.0.0.1:4724'
    $env:TAF_ANDROID_DEVICE_NAME = 'taf-api34'
    $env:TAF_ANDROID_DEVICE_ID = 'android-emulator:5555'
    $env:TAF_ANDROID_APP_PACKAGE = 'com.android.settings'
    $env:TAF_ANDROID_APP_ACTIVITY = '.Settings'
    Remove-Item -Recurse -Force -ErrorAction SilentlyContinue $smokeReports
    & $mavenWrapper -pl codinglair-taf-runtime/taf-mobile-appium -am verify -Pandroid-emulator
    if ($LASTEXITCODE -ne 0) { throw 'Real Android/Appium smoke failed.' }
    if (-not (Test-Path -PathType Container $smokeReports)) { throw 'Real Android/Appium smoke produced no Surefire reports.' }

    New-Item -ItemType Directory -Force -Path (Join-Path $repositoryRoot 'target\ci-support') | Out-Null
    & javac -d (Join-Path $repositoryRoot 'target\ci-support') (Join-Path $repositoryRoot 'build-support\ci\SmokeReportCheck.java')
    if ($LASTEXITCODE -ne 0) { throw 'Smoke report checker compilation failed.' }
    & java -cp (Join-Path $repositoryRoot 'target\ci-support') SmokeReportCheck $smokeReports
    if ($LASTEXITCODE -ne 0) { throw 'Smoke report did not prove one zero-skip execution.' }
    "PASS $((Get-Date).ToUniversalTime().ToString('o'))" | Set-Content (Join-Path $runDirectory 'result.txt')
  } catch {
    docker compose --file $composeFile ps --all 2>&1 | Set-Content (Join-Path $runDirectory 'compose-ps.txt')
    docker compose --file $composeFile logs --no-color --timestamps --tail 2000 2>&1 | Set-Content (Join-Path $runDirectory 'compose-logs.txt')
    $_ | Out-String | Set-Content (Join-Path $runDirectory 'failure.txt')
    if (-not $ControlledFailure) { throw }
  } finally {
    docker compose --file $composeFile down --remove-orphans
    $remainingContainers = docker ps --all --quiet --filter label=com.docker.compose.project=codinglair-taf-mob-003
    $remainingNetworks = docker network ls --quiet --filter label=com.docker.compose.project=codinglair-taf-mob-003
    "remaining_containers=$($remainingContainers -join ',')`nremaining_networks=$($remainingNetworks -join ',')" |
      Set-Content (Join-Path $runDirectory 'cleanup.txt')
    if ($remainingContainers -or $remainingNetworks) { throw 'Qualification cleanup left project resources.' }
  }
}

if ($ControlledFailure) {
  Write-Output 'MOB-003 controlled failure cleanup passed.'
} else {
  Write-Output "MOB-003 local qualification passed $Runs clean run(s)."
}
