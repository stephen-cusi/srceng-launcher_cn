@echo off
setlocal enabledelayedexpansion
cd /d %~dp0

REM ============================================================
REM  Windows-native build + verify (no WSL2 required)
REM  Usage: build-win.bat [release|debug]   (default release)
REM  Requires: JDK 17 on JAVA_HOME; ANDROID_HOME points to a
REM    Windows Android SDK (platforms 10/23/34 + build-tools 35.0.0)
REM  Note: do NOT pass -Pandroid.aapt2FromMavenOverride and do NOT
REM    set LD_LIBRARY_PATH (those are Linux/WSL-only; AGP picks the
REM    Windows aapt2 automatically here).
REM ============================================================

set MODE=%1
if "%MODE%"=="" set MODE=release

if "%ANDROID_HOME%"=="" (
    echo [ERROR] ANDROID_HOME is not set. Point it at your Windows Android SDK.
    echo   e.g. setx ANDROID_HOME "C:\Users\12\AppData\Local\Android\Sdk"
    exit /b 1
)

REM apksigner needs java; prepend JAVA_HOME\bin to PATH just in case
if not "%JAVA_HOME%"=="" set "PATH=%JAVA_HOME%\bin;%PATH%"

set APKSIGNER=%ANDROID_HOME%\build-tools\35.0.0\apksigner.bat

if /i "%MODE%"=="debug" (
    set TASKS=assembleDebug lintDebug
    set APK=build\outputs\apk\debug\srceng-launcher-debug.apk
    set EXPECTED_DN=CN=Android Debug
    set EXPECTED_SHA=
) else (
    set TASKS=assembleRelease lintRelease
    set APK=build\outputs\apk\release\srceng-launcher-release.apk
    set EXPECTED_DN=CN=Source Engine Launcher
    set EXPECTED_SHA=66:F6:44:D3:23:38:46:60:DB:2D:ED:5F:46:F5:1B:8A:1A:2C:EC:5A:22:62:E2:6A:7F:AF:C3:BC:3A:71:EA:C5
)

echo === Build %MODE% (gradlew.bat %TASKS%) ===
call gradlew.bat %TASKS%
if errorlevel 1 (
    echo [ERROR] Gradle build failed
    exit /b 1
)

echo.
echo === Verify APK signature ===
if not exist "%APKSIGNER%" (
    echo [ERROR] apksigner not found: %APKSIGNER%
    echo   Please install build-tools;35.0.0
    exit /b 1
)

set VERIFY_TXT=%TEMP%\srceng-apkverify.txt
call "%APKSIGNER%" verify --verbose --print-certs "%APK%" > "%VERIFY_TXT%"
type "%VERIFY_TXT%"

findstr /C:"Signer #1 certificate DN: %EXPECTED_DN%" "%VERIFY_TXT%" >nul || (
    echo [ERROR] Signer mismatch, expected "%EXPECTED_DN%"
    exit /b 1
)

if defined EXPECTED_SHA (
    findstr /C:"Signer #1 certificate SHA-256 digest: %EXPECTED_SHA%" "%VERIFY_TXT%" >nul || (
        echo [ERROR] Release certificate SHA-256 fingerprint mismatch
        exit /b 1
    )
)

if /i "%MODE%"=="release" (
    copy /Y "%APK%" "%USERPROFILE%\srceng-launcher-release.apk" >nul
    echo.
    echo Copied to %USERPROFILE%\srceng-launcher-release.apk
)

echo.
echo === Build and verification passed (%MODE%) ===
endlocal
