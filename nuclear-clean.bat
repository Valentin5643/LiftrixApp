@echo off
echo ============================================
echo NUCLEAR CLEAN - This will reset EVERYTHING
echo ============================================
echo.
echo This will delete ALL build files, caches, and IDE configs.
echo Press Ctrl+C to cancel, or
pause

echo.
echo Step 1: Killing ALL Java and Gradle processes...
taskkill /F /IM java.exe 2>nul
taskkill /F /IM javaw.exe 2>nul
taskkill /F /IM studio64.exe 2>nul
taskkill /F /IM gradle.exe 2>nul
taskkill /F /IM gradlew.exe 2>nul
call gradlew --stop 2>nul

echo Step 2: Deleting project-level build and cache directories...
rmdir /s /q .gradle 2>nul
rmdir /s /q .idea 2>nul
rmdir /s /q .kotlin 2>nul
rmdir /s /q build 2>nul
rmdir /s /q app\build 2>nul
rmdir /s /q buildSrc\build 2>nul
rmdir /s /q buildSrc\.gradle 2>nul

echo Step 3: Deleting ALL module build directories...
for /d /r . %%d in (build) do @if exist "%%d" rmdir /s /q "%%d" 2>nul

echo Step 4: Deleting global Gradle cache for this project...
rmdir /s /q %USERPROFILE%\.gradle\caches 2>nul
rmdir /s /q %USERPROFILE%\.gradle\daemon 2>nul
rmdir /s /q %USERPROFILE%\.gradle\wrapper 2>nul

echo Step 5: Deleting Android Studio caches...
for /d %%i in (%USERPROFILE%\.AndroidStudio*) do (
    rmdir /s /q "%%i\system\caches" 2>nul
    rmdir /s /q "%%i\system\compile-server" 2>nul
    rmdir /s /q "%%i\system\gradle" 2>nul
)

echo Step 6: Deleting all .iml and .idea files...
del /s /q *.iml 2>nul
del /s /q .idea 2>nul

echo Step 7: Creating minimal settings.gradle.kts...
echo pluginManagement { > settings.gradle.kts
echo     repositories { >> settings.gradle.kts
echo         google() >> settings.gradle.kts
echo         mavenCentral() >> settings.gradle.kts
echo         gradlePluginPortal() >> settings.gradle.kts
echo     } >> settings.gradle.kts
echo } >> settings.gradle.kts
echo dependencyResolutionManagement { >> settings.gradle.kts
echo     repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS) >> settings.gradle.kts
echo     repositories { >> settings.gradle.kts
echo         google() >> settings.gradle.kts
echo         mavenCentral() >> settings.gradle.kts
echo     } >> settings.gradle.kts
echo } >> settings.gradle.kts
echo rootProject.name = "Liftrix" >> settings.gradle.kts
echo include(":app") >> settings.gradle.kts

echo.
echo ============================================
echo NUCLEAR CLEAN COMPLETE
echo ============================================
echo.
echo NOW DO EXACTLY THIS:
echo.
echo 1. CLOSE Android Studio completely
echo 2. Open Windows Explorer
echo 3. Navigate to: %USERPROFILE%\.gradle
echo 4. DELETE the entire "caches" folder manually
echo 5. Open Android Studio
echo 6. DO NOT let it import anything automatically
echo 7. Click "File" -^> "Sync Project with Gradle Files"
echo 8. Wait for sync to complete
echo 9. Then run: Build -^> Clean Project
echo 10. Finally run: Build -^> Rebuild Project
echo.
echo ============================================
echo.
pause