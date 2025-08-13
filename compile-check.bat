@echo off
cd "C:\Users\Administrator\Liftrix"
.\gradlew compileDebugKotlin > compile.log 2>&1
echo Build completed. Check compile.log for results.