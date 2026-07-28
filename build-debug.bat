@echo off
REM ===========================================================================
REM  build-debug.bat
REM  Builds a debug APK of Brows.
REM
REM  Prerequisites: run setup.bat once first.
REM  Output: app\build\outputs\apk\debug\app-debug.apk
REM ===========================================================================
setlocal

cd /d "%~dp0"

if not exist "gradle\wrapper\gradle-wrapper.jar" (
    echo [INFO] Gradle wrapper JAR missing. Running download-wrapper.bat ...
    call download-wrapper.bat
    if %ERRORLEVEL% neq 0 exit /b 1
)

if not exist "local.properties" (
    echo [WARN] local.properties not found. Running setup.bat ...
    call setup.bat
    if %ERRORLEVEL% neq 0 exit /b 1
)

echo.
echo [INFO] Building debug APK ...
echo.
call gradlew.bat assembleDebug
if %ERRORLEVEL% neq 0 (
    echo.
    echo [ERROR] Build failed. See messages above.
    exit /b 1
)

echo.
echo ============================================================
echo   BUILD SUCCEEDED
echo ============================================================
echo.
echo APK location:
echo   %~dp0app\build\outputs\apk\debug\app-debug.apk
echo.
echo To install on a connected device (with USB debugging enabled):
echo   adb install -r app\build\outputs\apk\debug\app-debug.apk
echo.
exit /b 0
