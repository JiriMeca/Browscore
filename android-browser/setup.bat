@echo off
REM ===========================================================================
REM  setup.bat
REM  One-time setup for building Brows on Windows.
REM
REM  This script:
REM    1. Checks that Java (JDK 17+) is installed.
REM    2. Checks that the Android SDK is installed (or tells you how to get it).
REM    3. Creates local.properties pointing at the Android SDK.
REM    4. Downloads the Gradle wrapper JAR if missing.
REM
REM  Run this ONCE before running build-debug.bat or build-release.bat.
REM ===========================================================================
setlocal enableextensions

echo.
echo ============================================================
echo   Brows - Windows setup
echo ============================================================
echo.

REM ---- 1. Java ----------------------------------------------------------------
echo [1/4] Checking Java...
where java >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo.
    echo [ERROR] Java is not installed or not on PATH.
    echo.
    echo Install JDK 17 (Temurin is recommended):
    echo   https://adoptium.net/temurin/releases/?version=17
    echo.
    echo After installing, set JAVA_HOME to the JDK folder, e.g.:
    echo   setx JAVA_HOME "C:\Program Files\Eclipse Adoptium\jdk-17.x.x-hotspot"
    echo then open a NEW terminal and re-run this script.
    echo.
    exit /b 1
)
java -version
echo.

REM ---- 2. Android SDK ---------------------------------------------------------
echo [2/4] Checking Android SDK...
set "SDK_DIR="

REM Try ANDROID_HOME / ANDROID_SDK_ROOT first
if defined ANDROID_HOME (
    set "SDK_DIR=%ANDROID_HOME%"
) else if defined ANDROID_SDK_ROOT (
    set "SDK_DIR=%ANDROID_SDK_ROOT%"
) else if exist "%LOCALAPPDATA%\Android\Sdk" (
    set "SDK_DIR=%LOCALAPPDATA%\Android\Sdk"
) else if exist "C:\Android\Sdk" (
    set "SDK_DIR=C:\Android\Sdk"
)

if not defined SDK_DIR (
    echo.
    echo [ERROR] Could not find the Android SDK.
    echo.
    echo OPTION A - Full Android Studio (easiest, ~8 GB):
    echo   Download from https://developer.android.com/studio
    echo   Install it, then run the SDK Manager from inside Android Studio.
    echo.
    echo OPTION B - Command-line tools only (~1 GB, fits in your 3 GB budget):
    echo   1. Download "Command line tools only" from:
    echo      https://developer.android.com/studio#command-line-tools-only
    echo   2. Extract to C:\Android\Sdk\cmdline-tools\latest
    echo   3. Run: C:\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat
    echo          --sdk_root=C:\Android\Sdk "platform-tools" "platforms;android-34" "build-tools;34.0.0"
    echo   4. Re-run this script.
    echo.
    exit /b 1
)

echo [OK] Android SDK found at: %SDK_DIR%
echo.

REM ---- 3. local.properties ----------------------------------------------------
echo [3/4] Writing local.properties...
set "LOCAL_PROPS=%~dp0local.properties"
REM Escape backslashes for Java properties file
set "ESCAPED_SDK=%SDK_DIR:\=\\%"
echo sdk.dir=%ESCAPED_SDK%> "%LOCAL_PROPS%"
echo [OK] Wrote %LOCAL_PROPS%
echo.

REM ---- 4. Gradle wrapper JAR --------------------------------------------------
echo [4/4] Ensuring Gradle wrapper JAR is present...
call "%~dp0download-wrapper.bat"
if %ERRORLEVEL% neq 0 exit /b 1

echo.
echo ============================================================
echo   Setup complete!
echo ============================================================
echo.
echo Next steps:
echo   - Build a debug APK:      build-debug.bat
echo   - Build a release APK:    build-release.bat
echo.
echo The APK will appear in:  app\build\outputs\apk\debug\
echo                          app\build\outputs\apk\release\
echo.
exit /b 0
