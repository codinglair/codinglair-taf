$ErrorActionPreference = 'Stop'
$git = Get-Command git -ErrorAction Stop
$bash = Join-Path (Split-Path (Split-Path $git.Source -Parent) -Parent) 'bin/bash.exe'
if (-not (Test-Path $bash)) { throw 'Git for Windows Bash is required.' }
& $bash "$PSScriptRoot/bootstrap-mcp.sh"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
