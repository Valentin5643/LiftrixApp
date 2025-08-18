@echo off
cd C:\Users\Administrator\Liftrix
echo Testing compilation of WorkoutRepositoryImpl.kt fixes...
gradlew compileDebugKotlin --console=plain 2>&1 | findstr /i "WorkoutRepositoryImpl\|error\|failed"
echo Compilation test completed.