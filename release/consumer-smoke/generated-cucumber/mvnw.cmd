@echo off
setlocal
set "BASE_DIR=%~dp0"
for /f "tokens=1,* delims==" %%A in ('findstr /b distributionUrl "%BASE_DIR%.mvn\wrapper\maven-wrapper.properties"') do set "URL=%%B"
for %%A in ("%URL%") do set "ARCHIVE=%%~nxA"
set "MAVEN_HOME=%BASE_DIR%.mvn\wrapper\dists\%ARCHIVE:.zip=%"
if not exist "%MAVEN_HOME%\bin\mvn.cmd" powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $u=$env:URL; $a=Join-Path $env:TEMP $env:ARCHIVE; Invoke-WebRequest $u -OutFile $a; $t=$env:MAVEN_HOME+'.tmp'; New-Item -ItemType Directory -Force $t|Out-Null; Expand-Archive $a $t -Force; Move-Item (Get-ChildItem $t -Directory | Select-Object -First 1).FullName $env:MAVEN_HOME; Remove-Item $t -Recurse -Force"
call "%MAVEN_HOME%\bin\mvn.cmd" %*
exit /b %ERRORLEVEL%

