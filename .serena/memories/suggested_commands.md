# Development Commands

## Build & Compilation
```bash
./gradlew build                    # Full build with tests
./gradlew assembleDebug           # Build debug APK
./gradlew compileDebugKotlin      # Compile Kotlin only
./gradlew --stop                  # Stop Gradle daemon (fixes sync issues)
```

## Testing
```bash
./gradlew test                    # Run unit tests
./gradlew connectedAndroidTest    # Run instrumentation tests
```

## Development Tasks
```bash
./gradlew clean                   # Clean build artifacts
./gradlew sync                    # Gradle sync
```

## Important Notes
- Always run `./gradlew compileDebugKotlin` before committing
- Use `./gradlew --stop` if Gradle is stuck
- Firebase emulator is available for testing
