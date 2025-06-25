# Liftrix

## Getting Started
1. Clone this repository
2. Open in Android Studio (latest stable version)
3. Create `.env` file from `.env.sample` with your API keys
4. Build and run on device/emulator

## Project Structure
- `app/src/main/java`: Core application code
- `app/src/main/res`: Resources and assets
- `docs/`: Design documents and architecture decisions
- `buildSrc/`: Shared dependency management

## Key Features
- Jetpack Compose UI with Material 3
- Hilt for dependency injection
- Offline-first WorkManager synchronization
- Modular architecture (data/domain/ui layers)

## Design System
- **Primary Color**: Teal (#20C9B7)
- **Secondary Color**: Indigo (#2A3B7D)
- **Accent Color**: Coral (#FF6B6B)
- **Typography**: 
  - Headlines: Poppins Bold
  - Body: Inter Medium
  - Data: Roboto Mono Light

## Testing
Run all tests: `./gradlew test`
