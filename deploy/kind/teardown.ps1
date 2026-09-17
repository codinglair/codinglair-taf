$ErrorActionPreference = 'Stop'
$git = Get-Command git -ErrorAction Stop
$gitRoot = Split-Path (Split-Path $git.Source -Parent) -Parent
$bash = Join-Path $gitRoot 'bin/bash.exe'
if (-not (Test-Path $bash)) { throw 'Git for Windows Bash is required.' }

# Windows PowerShell 5.1 represents a native process's stderr as an ErrorRecord. Kind writes its
# normal "Deleting cluster ..." progress message to stderr, so the script-wide Stop preference
# would otherwise abort this wrapper even when teardown.sh exits successfully. Merge the native
# streams at this boundary, preserve the real process exit code, and then restore strict handling
# for PowerShell errors.
$previousErrorActionPreference = $ErrorActionPreference
try {
    $ErrorActionPreference = 'Continue'
    & $bash "$PSScriptRoot/teardown.sh" 2>&1 | ForEach-Object { Write-Output $_.ToString() }
    $teardownExitCode = $LASTEXITCODE
}
finally {
    $ErrorActionPreference = $previousErrorActionPreference
}

if ($teardownExitCode -ne 0) { exit $teardownExitCode }
