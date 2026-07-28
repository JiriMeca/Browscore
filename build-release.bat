@echo off
REM ===========================================================================
REM  build-release.bat
REM  Builds a release APK of Brows.
REM
REM  A release APK must be signed. This script generates a temporary debug
REM  keystore the first time, OR you can supply your own keystore.
REM
REM  Prerequisites: run setup.bat once first.
REM  Output: app\build\outputs\apk\release\app-release-unsigned.apk
REM          (or app-release.apk if you provide a keystore)
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
echo [INFO] Building release APK ...
echo.
call gradlew.bat assembleRelease
if %ERRORLEVEL% neq 0 (
    echo.
    echo [ERROR] Build failed. See messages above.
    exit /b 1
)

echo.
echo ============================================================
echo   RELEASE BUILD SUCCEEDED
echo ============================================================
echo.
echo Output APK:
echo   %~dp0app\build\outputs\apk\release\app-release-unsigned.apk
echo.
echo -------------------------------------------------------------------
echo IMPORTANT: this APK is UNSIGNED and Android will refuse to install
echo it as-is. You must sign it before installing on a real device.
echo.
echo EASIEST WAY (debug signing for personal use):
echo   Use Android Studio's "Build > Generate Signed Bundle / APK"
echo   and pick the release variant, OR run apksigner manually:
echo.
echo   1. Create a keystore once:
echo      keytool -genkey -v -keystore permanent.keystore -alias permanent ^
echo        -keyalg RSA -keysize 2048 -validity 10000
echo.
echo   2. Sign the APK:
echo      "%LOCALAPPDATA%\Android\Sdk\build-tools\34.0.0\apksigner.bat" sign ^
echo        --ks permanent.keystore ^
echo        --out app-release.apk ^
echo        app\build\outputs\apk\release\app-release-unsigned.apk
echo -------------------------------------------------------------------
echo.
exit /b 0
