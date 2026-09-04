$ErrorActionPreference = 'Stop'
$git = Get-Command git -ErrorAction Stop
$gitRoot = Split-Path (Split-Path $git.Source -Parent) -Parent
$bash = Join-Path $gitRoot 'bin/bash.exe'
if (-not (Test-Path $bash)) { throw 'Git for Windows Bash is required.' }
& $bash "$PSScriptRoot/smoke.sh"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
