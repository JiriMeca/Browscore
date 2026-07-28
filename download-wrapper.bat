@echo off
REM ===========================================================================
REM  download-wrapper.bat
REM  Downloads the official Gradle wrapper JAR into gradle\wrapper\.
REM  This is needed because the wrapper JAR is a binary file that cannot be
REM  shipped as plain text. We fetch it directly from Gradle's GitHub release.
REM ===========================================================================
setlocal

set WRAPPER_DIR=%~dp0gradle\wrapper
set WRAPPER_JAR=%WRAPPER_DIR%\gradle-wrapper.jar
set WRAPPER_URL=https://raw.githubusercontent.com/gradle/gradle/v8.9.0/gradle/wrapper/gradle-wrapper.jar

if exist "%WRAPPER_JAR%" (
    echo [OK] gradle-wrapper.jar already exists. Nothing to do.
    exit /b 0
)

if not exist "%WRAPPER_DIR%" mkdir "%WRAPPER_DIR%"

echo [INFO] Downloading gradle-wrapper.jar ...
echo [INFO] Source: %WRAPPER_URL%

REM Windows 10 1803+ ships with curl.exe
where curl >nul 2>&1
if %ERRORLEVEL% equ 0 (
    curl -L -o "%WRAPPER_JAR%" "%WRAPPER_URL%"
    if exist "%WRAPPER_JAR%" (
        echo [OK] Downloaded gradle-wrapper.jar successfully.
        exit /b 0
    )
)

REM Fallback: PowerShell
echo [INFO] curl not found or failed. Trying PowerShell ...
powershell -NoProfile -Command "try { Invoke-WebRequest -Uri '%WRAPPER_URL%' -OutFile '%WRAPPER_JAR%' -UseBasicParsing } catch { Write-Host $_.Exception.Message; exit 1 }"
if exist "%WRAPPER_JAR%" (
    echo [OK] Downloaded gradle-wrapper.jar successfully via PowerShell.
    exit /b 0
)

echo [ERROR] Could not download gradle-wrapper.jar.
echo [ERROR] Please download it manually from:
echo [ERROR]   %WRAPPER_URL%
echo [ERROR] and place it at: %WRAPPER_JAR%
exit /b 1
