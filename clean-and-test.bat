@echo off
echo Cleaning build cache and testing specific file compilation...
.\gradlew clean
.\gradlew :app:compileTestKotlin --no-daemon --info | findstr /i "error\|failed\|NotificationRouterTest" > test-specific-errors.log 2>&1
echo Check test-specific-errors.log for results