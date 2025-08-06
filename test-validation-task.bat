@echo off
echo Testing Room validation task with configuration cache...
echo.

echo Running validateRoomQueries task with configuration cache enabled...
call gradlew.bat validateRoomQueries --configuration-cache

echo.
echo Task completed. Check output above for validation results.
pause