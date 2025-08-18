# PowerShell script for deep cleaning Android Studio caches
Write-Host "Android Studio Deep Clean Script" -ForegroundColor Green
Write-Host "=================================" -ForegroundColor Green

# Kill all Java and Android Studio processes
Write-Host "`nStep 1: Killing all Java and Android Studio processes..." -ForegroundColor Yellow
Get-Process java, javaw, studio64, gradle -ErrorAction SilentlyContinue | Stop-Process -Force

# Clean project directories
Write-Host "`nStep 2: Cleaning project directories..." -ForegroundColor Yellow
Remove-Item -Path ".gradle", ".idea", ".kotlin", "build", "app\build" -Recurse -Force -ErrorAction SilentlyContinue

# Find and delete all build directories
Write-Host "`nStep 3: Removing all build directories..." -ForegroundColor Yellow
Get-ChildItem -Path . -Directory -Recurse -Filter "build" | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue

# Clean user Gradle cache
Write-Host "`nStep 4: Cleaning user Gradle cache..." -ForegroundColor Yellow
$gradlePath = "$env:USERPROFILE\.gradle"
Remove-Item -Path "$gradlePath\caches" -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -Path "$gradlePath\daemon" -Recurse -Force -ErrorAction SilentlyContinue

# Clean Android Studio directories
Write-Host "`nStep 5: Cleaning Android Studio system directories..." -ForegroundColor Yellow
$androidStudioPaths = Get-ChildItem -Path $env:USERPROFILE -Directory -Filter ".AndroidStudio*" -ErrorAction SilentlyContinue
foreach ($path in $androidStudioPaths) {
    Remove-Item -Path "$($path.FullName)\system\caches" -Recurse -Force -ErrorAction SilentlyContinue
    Remove-Item -Path "$($path.FullName)\system\compile-server" -Recurse -Force -ErrorAction SilentlyContinue
    Remove-Item -Path "$($path.FullName)\system\gradle" -Recurse -Force -ErrorAction SilentlyContinue
    Remove-Item -Path "$($path.FullName)\system\resource_folder_cache" -Recurse -Force -ErrorAction SilentlyContinue
}

# Clean local app data
Write-Host "`nStep 6: Cleaning Android Studio local app data..." -ForegroundColor Yellow
$localAndroidStudio = Get-ChildItem -Path "$env:LOCALAPPDATA\Google" -Directory -Filter "AndroidStudio*" -ErrorAction SilentlyContinue
foreach ($path in $localAndroidStudio) {
    Remove-Item -Path "$($path.FullName)\caches" -Recurse -Force -ErrorAction SilentlyContinue
}

# Delete all .iml files
Write-Host "`nStep 7: Removing all .iml files..." -ForegroundColor Yellow
Get-ChildItem -Path . -Filter "*.iml" -Recurse | Remove-Item -Force

Write-Host "`n=================================" -ForegroundColor Green
Write-Host "DEEP CLEAN COMPLETE!" -ForegroundColor Green
Write-Host "=================================" -ForegroundColor Green
Write-Host "`nIMPORTANT NEXT STEPS:" -ForegroundColor Red
Write-Host "1. Open Android Studio" -ForegroundColor White
Write-Host "2. When prompted, choose 'Do not import settings'" -ForegroundColor White
Write-Host "3. Let it create a new project import" -ForegroundColor White
Write-Host "4. Go to File -> Invalidate Caches... -> Invalidate and Restart" -ForegroundColor White
Write-Host "5. After restart, Build -> Clean Project" -ForegroundColor White
Write-Host "6. Build -> Rebuild Project" -ForegroundColor White
Write-Host "`nPress any key to continue..."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")