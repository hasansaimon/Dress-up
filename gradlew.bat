@echo off
REM Minimal Windows shim to fetch Gradle 8.4 and run it. Requires PowerShell.
setlocal
set GRADLE_VERSION=8.4
set WRAPPER_DIR=%~dp0\.gradle-wrapper
set GRADLE_DIR=%WRAPPER_DIR%\gradle-%GRADLE_VERSION%
set GRADLE_BIN=%GRADLE_DIR%\bin\gradle.bat
if not exist "%GRADLE_BIN%" (
  echo Gradle %GRADLE_VERSION% not found. Downloading...
  powershell -Command "if(-not (Test-Path -Path '%WRAPPER_DIR%')){New-Item -ItemType Directory -Path '%WRAPPER_DIR%'}; Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%WRAPPER_DIR%\\gradle-%GRADLE_VERSION%-bin.zip'" 
  powershell -Command "Add-Type -AssemblyName System.IO.Compression.FileSystem; [System.IO.Compression.ZipFile]::ExtractToDirectory('%WRAPPER_DIR%\\gradle-%GRADLE_VERSION%-bin.zip','%WRAPPER_DIR%')"
)
"%GRADLE_BIN%" %*
