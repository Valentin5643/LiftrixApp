@echo off
echo Testing PublicProfileScreen compilation...
cd /d "C:\Users\Administrator\Liftrix"
call gradlew compileDebugKotlin 2>&1 | findstr /i "PublicProfileScreen"
echo Compilation check complete.