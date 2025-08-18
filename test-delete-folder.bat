@echo off
echo Testing DeleteFolderUseCaseTest...
echo.

echo 1. Compiling test classes...
call gradlew.bat compileTestKotlin
if %ERRORLEVEL% neq 0 (
    echo ERROR: Test compilation failed
    exit /b 1
)

echo.
echo 2. Running DeleteFolderUseCaseTest...
call gradlew.bat test --tests "com.example.liftrix.domain.usecase.folder.DeleteFolderUseCaseTest" --info
if %ERRORLEVEL% neq 0 (
    echo ERROR: Tests failed
    exit /b 1
)

echo.
echo ✅ DeleteFolderUseCaseTest passed successfully!