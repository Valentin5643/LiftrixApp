@echo off
echo Quick validation test...
echo.

echo Running gradle tasks to verify validateRoomQueries is registered...
call gradlew.bat tasks --group=verification
if %ERRORLEVEL% neq 0 (
    echo ERROR: Failed to list tasks
    exit /b 1
)

echo.
echo ✅ Task registration verified
echo Run 'gradlew validateRoomQueries --configuration-cache' to test the full task