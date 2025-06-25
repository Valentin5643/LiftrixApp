# Liftrix Architecture Documentation

## Overview
Liftrix follows Clean Architecture principles with MVI (Model-View-Intent) pattern and offline-first approach. The app is built using Jetpack Compose for UI, Firebase for backend services, and Room for local data persistence.

## Architecture Layers

### 1. Presentation Layer (UI)
- **Jetpack Compose** for declarative UI
- **ViewModels** for UI state management
- **MVI Pattern** for unidirectional data flow
- **Navigation Component** for screen navigation

### 2. Domain Layer
- **Use Cases** contain business logic
- **Repository Interfaces** define data contracts
- **Domain Models** represent business entities
- **Validators** for input validation

### 3. Data Layer
- **Repository Implementations** handle data sources
- **Firebase SDK** for remote data
- **Room Database** for local data
- **DataStore** for preferences
- **WorkManager** for background sync

## Key Components

### Firebase Integration
- **Authentication**: User management with email/password and Google Sign-In
- **Firestore**: Real-time database for workouts, progress, and user data
- **Storage**: File uploads for profile pictures and exports
- **Analytics**: User behavior tracking
- **Crashlytics**: Error monitoring
- **Cloud Functions**: Server-side logic

### Local Storage
- **Room Database**: Offline-first data persistence
- **DataStore**: User preferences and settings
- **Cache Strategy**: Smart caching for better performance

### Background Processing
- **WorkManager**: Periodic sync with Firebase
- **Coroutines**: Asynchronous operations
- **Flow**: Reactive data streams

## Data Flow

```
UI Layer (Compose) → ViewModel → Use Case → Repository → Data Source (Firebase/Room)
```

### MVI Pattern
1. **Model**: Represents UI state
2. **View**: Composable functions that render UI
3. **Intent**: User actions that trigger state changes

## Security
- Firebase Security Rules for data access control
- Input validation in domain layer
- Encrypted local storage for sensitive data

## Testing Strategy
- **Unit Tests**: Domain layer and ViewModels
- **Integration Tests**: Repository implementations
- **UI Tests**: Compose screens and navigation
- **Firebase Emulator**: Local testing environment

## Performance Considerations
- Lazy loading for large datasets
- Image caching and compression
- Background sync optimization
- Memory management for media files

## Offline Support
- Room database as single source of truth
- Conflict resolution for sync
- Queue-based operations for offline actions
- User feedback for sync status 