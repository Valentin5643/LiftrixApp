@echo off
echo Running compilation test...
cd /d "C:\Users\Administrator\Liftrix"
call gradlew.bat compileDebugKotlin --no-daemon --stacktrace --console=plain > compilation_output.log 2>&1
echo Compilation test completed. Check compilation_output.log for results.