@echo off
echo Testing build configuration and validation task...
echo.

echo Step 1: Running assembleDebug to ensure basic compilation works...
call gradlew.bat assembleDebug --configuration-cache --quiet
if %ERRORLEVEL% neq 0 (
    echo ERROR: Build failed!
    exit /b 1
)
echo ✅ Build successful

echo.
echo Step 2: Running validateRoomQueries task with configuration cache...
call gradlew.bat validateRoomQueries --configuration-cache
if %ERRORLEVEL% neq 0 (
    echo ERROR: Validation task failed!
    exit /b 1
)

echo.
echo ✅ All tests passed! Configuration cache compatibility confirmed.
pause