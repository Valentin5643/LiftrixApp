# Liftrix App - Project Overview

## Project Purpose
Fitness tracking application with social features, workouts, achievements, and gym buddy system.

## Tech Stack
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose + Material 3
- **Database**: Room (with SQLCipher encryption, v1)
- **Backend**: Firebase (Auth, Firestore, Storage, Functions, Cloud Messaging)
- **Serialization**: 
  - Kotlinx Serialization (json)
  - Gson
- **Dependency Injection**: Hilt
- **Background Tasks**: WorkManager
- **Architecture**: Clean Architecture (Domain/Data/UI layers)
- **Build System**: Gradle with KSP compiler

## Key Libraries
- Firebase Auth, Firestore, Storage, AI (Gemini), Functions, Cloud Messaging
- Room with Paging3 for feed
- Navigation Compose
- WorkManager with Hilt integration
- ZXing for QR code generation/scanning
- Coil for image loading
- CameraX for camera access

## Code Organization
- **app/src/main**: Main source code
- **app/src/test**: Unit tests
- **app/src/androidTest**: Integration tests
- Modules: Domain/Data/UI layers

## Build Targets
- Min SDK: 26
- Target SDK: 35
- Compile SDK: 35
- JVM Target: 17

## Important Dependencies
- Firebase: analytics, auth, firestore, storage, ai, crashlytics, perf, functions, messaging
- Kotlin: 2.0.21
- AndroidX: Latest versions with specific constraints
