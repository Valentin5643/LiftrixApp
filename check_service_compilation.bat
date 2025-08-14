@echo off
echo Checking compilation of service implementations...
echo.

echo Compiling PRDetectionServiceImpl and QRCodeServiceImpl...
gradlew compileDebugKotlin 2>&1 | findstr /C:"PRDetectionServiceImpl" /C:"QRCodeServiceImpl" /C:"error" /C:"failed"

if %ERRORLEVEL% equ 0 (
    echo.
    echo Service implementations compiled successfully!
) else (
    echo.
    echo Compilation issues found - check output above
)