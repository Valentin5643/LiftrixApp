@echo off
echo Cleaning Android Studio and Gradle caches...
echo.

echo Step 1: Stopping Gradle daemon...
call gradlew --stop

echo Step 2: Cleaning project build directories...
rmdir /s /q app\build 2>nul
rmdir /s /q build 2>nul
rmdir /s /q .gradle 2>nul
rmdir /s /q .kotlin 2>nul

echo Step 3: Cleaning Android Studio caches...
rmdir /s /q .idea\modules 2>nul
rmdir /s /q .idea\libraries 2>nul
rmdir /s /q .idea\caches 2>nul

echo Step 4: Cleaning user Gradle cache...
rmdir /s /q %USERPROFILE%\.gradle\caches\transforms-3 2>nul
rmdir /s /q %USERPROFILE%\.gradle\caches\jars-9 2>nul

echo Step 5: Removing .hprof files...
del /q *.hprof 2>nul

echo.
echo ===================================
echo IMPORTANT: Now follow these steps in Android Studio:
echo.
echo 1. Close Android Studio completely
echo 2. Open Android Studio
echo 3. Go to File -> Invalidate Caches...
echo 4. Check all options:
echo    - Clear file system cache
echo    - Clear VCS Log caches
echo    - Clear downloaded shared indexes
echo 5. Click "Invalidate and Restart"
echo 6. After restart, let it re-index
echo 7. Then do: Build -> Clean Project
echo 8. Finally: Build -> Rebuild Project
echo ===================================
echo.
pause