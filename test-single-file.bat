@echo off
echo Testing compilation of specific test file...
.\gradlew compileTestKotlin --include-task compileTestKotlin 2> test-compilation-specific.log
if %ERRORLEVEL% NEQ 0 (
    echo Compilation failed. Check test-compilation-specific.log for errors.
    type test-compilation-specific.log
) else (
    echo Compilation successful!
)