<div align="center">
  
# 💪 Liftrix

### Android Fitness Tracking App with AI Coaching & Social Engagement

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-26-brightgreen?style=for-the-badge)](https://developer.android.com/studio/releases/platforms#8.0)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-35-brightgreen?style=for-the-badge)](https://developer.android.com/studio/releases/platforms)
[![License](https://img.shields.io/badge/License-MIT-blue?style=for-the-badge)](LICENSE)

[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?style=flat-square&logo=jetpack-compose)](https://developer.android.com/jetpack/compose)
[![Firebase](https://img.shields.io/badge/Firebase-12%20Services-FFCA28?style=flat-square&logo=firebase)](https://firebase.google.com)
[![Clean Architecture](https://img.shields.io/badge/Architecture-MVI%20+%20MVVM-orange?style=flat-square)](https://developer.android.com/topic/architecture)
[![Offline First](https://img.shields.io/badge/Design-Offline%20First-success?style=flat-square)](https://developer.android.com/topic/architecture/data-layer/offline-first)

<img src="assets/banner.png" alt="Liftrix Banner" width="800"/>

*Transform your fitness journey with intelligent workout tracking, AI-powered coaching, and social motivation*

[**📱 Demo**](#demo) • [**🚀 Quick Start**](#quick-start) • [**📖 Documentation**](#documentation) • [**🤝 Contributing**](#contributing)

</div> 

---

## ✨ Overview

**Liftrix** is an Android fitness app that combines workout tracking, progress analytics, social engagement and AI-powered coaching. It uses a modular, testable architecture with offline-first persistence and Firebase-backed synchronization.

### 🎯 Key Highlights

- **🏗️ Clean Architecture**: layered UI, ViewModel, use case, repository and data boundaries
- **📱 Modern UI**: 100% Jetpack Compose with Material 3 design system
- **🔄 Offline-First**: Room database as source of truth with Firebase sync
- **🤖 AI Integration**: Gemini 2.5 Flash Lite for intelligent coaching
- **👥 Social Features**: Privacy-first feed system with engagement tracking
- **📊 Advanced Analytics**: progress widgets and charts optimized for fluid rendering
- **🔐 Security**: User-scoped data isolation and privacy controls


---

## 🚀 Quick Start

### Prerequisites

- **Android Studio** Jellyfish (2023.3.1) or later
- **JDK 17+** (OpenJDK recommended)
- **Android SDK** API 34
- **16GB RAM** minimum (32GB recommended)

### Installation

```bash
# Clone the repository
git clone https://github.com/valentin5643/LiftrixApp.git
cd liftrix

# Verify Gradle wrapper
./gradlew --version

# Download dependencies
./gradlew dependencies

# Build debug APK
./gradlew assembleDebug
```

### Firebase Setup

1. Create a new Firebase project at [console.firebase.google.com](https://console.firebase.google.com)
2. Enable required services:
   - Authentication (Email/Google/Anonymous)
   - Firestore Database
   - Storage
   - Analytics, Performance, Crashlytics
   - Remote Config
   - Firebase AI (Gemini)
3. Download `google-services.json` and place in `app/` directory
4. Deploy security rules:

```bash
firebase init
firebase deploy --only firestore:rules storage:rules
```

### Run the App

```bash
# Install on connected device/emulator
./gradlew installDebug

# Run with live logs
adb logcat -s Liftrix:V
```

---

## 🏗️ Architecture

<div align="center">
<img src="assets/architecture-diagram.png" alt="Architecture Diagram" width="700"/>
</div>

### Clean Architecture Layers

```
UI Layer (Jetpack Compose)
    ↓ StateFlow<UiState<T>>
ViewModel Layer (MVI Pattern)
    ↓ LiftrixResult<T>
Use Case Layer
    ↓ Domain Models
Repository Layer
    ↓ Flow<Entity>
DAO Layer
    ↓ SQL Queries
Room Database
    ↓ Background Sync
Firebase Services (8 Integrated)
```

### Key Architectural Patterns

- **🎯 MVVM with MVI**: Unidirectional data flow with event handling
- **🔒 User Scoping**: Mandatory userId filtering for data security
- **🔄 Offline-First**: Room as single source of truth
- **⚡ Type-Safe Navigation**: Serializable routes with compile-time safety
- **💉 Dependency Injection**: Hilt modules for clean separation

---

## 🛠️ Tech Stack

<table>
<tr>
<td align="center" width="96">
<img src="https://raw.githubusercontent.com/github/explore/main/topics/kotlin/kotlin.png" width="48" height="48" alt="Kotlin" />
<br>Kotlin
</td>
<td align="center" width="96">
<img src="https://3.bp.blogspot.com/-VVp3WvJvl84/X0Vu6EjYqDI/AAAAAAAAPjU/ZOMKiUlgfg8ok8DY8Hc-ocOvGdB0z86AgCLcBGAsYHQ/s1600/jetpack%2Bcompose%2Bicon_RGB.png" width="48" height="48" alt="Compose" />
<br>Compose
</td>
<td align="center" width="96">
<img src="https://static.vecteezy.com/system/resources/previews/072/678/006/non_2x/firebase-logo-icon-free-png.png" width="48" height="48" alt="Firebase" />
<br>Firebase
</td>
<td align="center" width="96">
<img src="https://raw.githubusercontent.com/github/explore/main/topics/android/android.png" width="48" height="48" alt="Android" />
<br>Android
</td>
<td align="center" width="96">
<img src="https://avatars.githubusercontent.com/u/51121562?s=200&v=4" width="48" height="48" alt="Hilt" />
<br>Hilt
</td>
<td align="center" width="96">
<img src="https://velog.velcdn.com/images/heymoko/post/3fbb6e8c-5922-490a-815a-477bc7c18577/image.png" width="48" height="48" alt="Room" />
<br>Room
</td>
</tr>
</table>

### Core Dependencies

| Category | Technologies |
|----------|-------------|
| **UI Framework** | Jetpack Compose, Material 3, Navigation Compose |
| **Architecture** | MVVM, MVI, Clean Architecture, Repository Pattern |
| **Database** | Room, Firestore-backed synchronization |
| **Networking** | Firebase Services, Retrofit, OkHttp |
| **DI Framework** | Hilt, Dagger |
| **Async** | Kotlin Coroutines, Flow, StateFlow |
| **Testing** | JUnit, MockK, Turbine, Compose Testing |
| **Background** | WorkManager, Firebase Cloud Messaging |

---

## 📊 Performance Metrics

<table>
<tr>
<th>Metric</th>
<th>Target</th>
<th>Status</th>
</tr>
<tr>
<td>UI Rendering</td>
<td>60fps</td>
<td>Optimized in tested screens</td>
</tr>
<tr>
<td>Database Queries</td>
<td><100ms</td>
<td>Requires device/methodology when reported</td>
</tr>
<tr>
<td>Sync Operations</td>
<td><5s</td>
<td>Scenario-dependent</td>
</tr>
<tr>
<td>Component Interactions</td>
<td>150ms</td>
<td>Target</td>
</tr>
<tr>
<td>Memory Usage</td>
<td>Adaptive</td>
<td>Memory-aware</td>
</tr>
<tr>
<td>Accessibility</td>
<td>WCAG 2.1 AA</td>
<td>Requires final accessibility pass</td>
</tr>
</table>

---

## 📱 Screenshots

<div align="center">
<table>
<tr>
<td><img src="assets/Home-screen.jpg" width="200" alt="Home Screen"/></td>
<td><img src="assets/Workout.jpg" width="200" alt="Workout Tracking"/></td>
<td><img src="assets/Progress.jpg" width="200" alt="Progress Dashboard"/></td>
<td><img src="assets/Coach.jpg" width="200" alt="Coach"/></td>
</tr>
<tr>
<td align="center">Home</td>
<td align="center">Workout</td>
<td align="center">Analytics</td>
<td align="center">Coach</td>
</tr>
</table>
</div>

---

## 📖 Documentation

### Core Concepts

- **[Architecture Overview](docs/readme/architecture.md)** - Clean Architecture implementation details
- **[Feature Documentation](docs/readme/features.md)** - Comprehensive feature descriptions
- **[Setup Guide](docs/readme/setup.md)** - Detailed development environment setup
- **[Dependencies](docs/readme/dependencies.md)** - Library integration patterns
- **[API Reference](docs/api/README.md)** - Complete API documentation

### Development Guides

- **[CLAUDE.md](CLAUDE.md)** - AI assistant instructions and patterns
- **[Contributing Guidelines](CONTRIBUTING.md)** - How to contribute
- **[Code of Conduct](CODE_OF_CONDUCT.md)** - Community guidelines

---

## 🧪 Testing

```bash
# Run unit tests
./gradlew testDebugUnitTest

# Run instrumentation tests (requires emulator)
./gradlew connectedDebugAndroidTest

# Generate coverage report
./gradlew jacocoTestReport

# Run lint checks
./gradlew lint
```

### Test Coverage

- **Unit Tests**: ViewModels, Use Cases, Repositories
- **Integration Tests**: Database operations, Sync flows
- **UI Tests**: Compose screens, Navigation flows
- **End-to-End**: Complete user workflows

---

## 🚀 Deployment

### Build Variants

```bash
# Debug build (with logging)
./gradlew assembleDebug

# Release build (optimized + obfuscated)
./gradlew assembleRelease

# Bundle for Play Store
./gradlew bundleRelease
```

### ProGuard Configuration

The app includes comprehensive ProGuard rules for:
- Firebase services preservation
- Room entity protection
- Hilt generated code retention
- Compose optimization

---

## 🗺️ Roadmap

### ✅ Completed
- [x] Core workout tracking system
- [x] Social feed with privacy controls
- [x] AI coaching integration
- [x] Progress analytics dashboard
- [x] Offline-first architecture
- [x] QR code gym buddy pairing

### 🚧 In Progress
- [ ] Wearable device integration
- [ ] Video form analysis
- [ ] Nutrition tracking
- [ ] Competition features

### 📋 Planned
- [ ] iOS companion app
- [ ] Web dashboard
- [ ] Advanced AI personalization
- [ ] Multi-language expansion
- [ ] Export to fitness platforms


---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## 💬 Community & Support

<div align="center">

[![Email](https://img.shields.io/badge/Email-Contact-EA4335?style=for-the-badge&logo=gmail&logoColor=white)](mailto:valijianu98@gmail.com)

**Found a bug?** [Report Issue](https://github.com/yourusername/liftrix/issues/new?template=bug_report.md)  
**Have a feature request?** [Request Feature](https://github.com/yourusername/liftrix/issues/new?template=feature_request.md)  
**Need help?** [Documentation](https://docs.liftrix.app) • [FAQ](https://liftrix.app/faq)

</div>

---
<div align="center">

## InfoEducație 2026

Materialele pentru jurizare sunt disponibile în folderul:

[`00_InfoEducatie_2026/`](./00_InfoEducatie_2026/)

Conține documentația tehnică, declarația de resurse externe, prezentarea, APK-ul și dovezile tehnice relevante.

</div>

---

<div align="center">

**Built with ❤️ by the Liftrix Team**

[⬆ Back to Top](#-liftrix)

</div>
