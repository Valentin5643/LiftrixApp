@echo off
echo Fixing duplicate class issues for Android Studio...
echo.

echo Step 1: Stopping all Gradle processes...
call gradlew --stop
taskkill /F /IM java.exe 2>nul
taskkill /F /IM kotlin-compiler-daemon.exe 2>nul

echo Step 2: Deleting ALL build and cache directories...
rmdir /s /q .gradle 2>nul
rmdir /s /q .idea 2>nul
rmdir /s /q .kotlin 2>nul
rmdir /s /q app\build 2>nul
rmdir /s /q build 2>nul
rmdir /s /q buildSrc\build 2>nul
rmdir /s /q buildSrc\.gradle 2>nul

echo Step 3: Deleting user-level Gradle caches...
rmdir /s /q %USERPROFILE%\.gradle\caches\8.11.1 2>nul
rmdir /s /q %USERPROFILE%\.gradle\caches\transforms-3 2>nul
rmdir /s /q %USERPROFILE%\.gradle\caches\jars-9 2>nul
rmdir /s /q %USERPROFILE%\.gradle\caches\modules-2 2>nul

echo Step 4: Deleting Android Studio system caches...
rmdir /s /q %USERPROFILE%\.AndroidStudio*\system\caches 2>nul
rmdir /s /q %LOCALAPPDATA%\Google\AndroidStudio*\caches 2>nul

echo Step 5: Removing all .iml files...
del /s /q *.iml 2>nul

echo.
echo ===================================
echo CRITICAL STEPS FOR ANDROID STUDIO:
echo ===================================
echo.
echo 1. CLOSE Android Studio COMPLETELY
echo.
echo 2. Open Task Manager and END any remaining:
echo    - java.exe
echo    - studio64.exe
echo    - gradle processes
echo.
echo 3. Delete these folders manually if they still exist:
echo    - C:\Users\Administrator\.gradle\caches\8.11.1
echo    - C:\Users\Administrator\.gradle\daemon\8.11.1
echo.
echo 4. Re-open Android Studio
echo.
echo 5. IMMEDIATELY go to:
echo    File -> Invalidate Caches...
echo    CHECK ALL OPTIONS and click "Invalidate and Restart"
echo.
echo 6. After restart, wait for indexing to complete
echo.
echo 7. Open Terminal in Android Studio and run:
echo    .\gradlew clean
echo    .\gradlew --stop
echo    .\gradlew assembleDebug
echo.
echo ===================================
echo.
pause