# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Keep WorkManager workers from being stripped by R8
-keep class com.example.liftrix.sync.** { *; }

# Keep all Hilt workers and generated factories
-keep class * extends androidx.work.ListenableWorker
-keep @androidx.hilt.work.HiltWorker class *
-keepclassmembers class * {
    @dagger.assisted.AssistedInject <init>(...);
}

# Keep Hilt Worker factories and assisted injection
-keep class * extends androidx.hilt.work.HiltWorkerFactory { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }

# Keep WorkManager and Hilt annotations
-keep class androidx.hilt.work.HiltWorker
-keep class dagger.assisted.AssistedInject
-keep class dagger.assisted.Assisted

# Keep generated Hilt assisted factories
-keep class **_AssistedFactory { *; }
-keep class **_Factory { *; }

# ProGuard rules for Liftrix - BALANCED FOR R8 PERFORMANCE & HILT COMPATIBILITY

# Essential app classes
-keep class com.example.liftrix.LiftrixApp { *; }
-keep class com.example.liftrix.MainActivity { *; }

# Keep ViewModels - optimized to allow obfuscation while preserving structure
-keep,allowobfuscation class * extends androidx.lifecycle.ViewModel
-keep class com.example.liftrix.ui.**.*ViewModel$* { *; }

# Keep all Hilt modules and injected classes
-keep class com.example.liftrix.di.** { *; }
-keep @dagger.Module class * { *; }
-keep @dagger.hilt.InstallIn class * { *; }
-keep @javax.inject.Inject class * { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }

# Keep data models - optimized for serialization only
-keep @kotlinx.serialization.Serializable class com.example.liftrix.domain.model.** { *; }
-keep class com.example.liftrix.data.local.entity.** { *; }
-keep class com.example.liftrix.data.local.dao.** { *; }

# Keep Firebase-related classes and prevent obfuscation
-keep class com.example.liftrix.service.LiftrixFirebaseMessagingService { *; }
-keep class com.example.liftrix.data.remote.FirebaseDataSource* { *; }
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Firebase App Check and Play Integrity
-keep class com.google.firebase.appcheck.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# Hilt dependency injection - comprehensive rules
-keep class com.example.liftrix.LiftrixApp_* { *; }
-keep class dagger.hilt.** { *; }
-keep class **_HiltComponents { *; }
-keep class **_HiltComponents$* { *; }
-keep class **_Factory { *; }
-keep class **_Impl { *; }
-keep class **_Impl$* { *; }

# 🔥 FIX: Hilt Application class generation
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep class * extends dagger.hilt.android.internal.managers.ApplicationComponentManager { *; }

# 🔥 FIX: Prevent Application class obfuscation
-keep class * extends android.app.Application { *; }
-keepclassmembers class * extends android.app.Application {
    <init>(...);
}

# Keep repositories and use cases - optimized to allow obfuscation
# Hilt can handle obfuscated class names as long as structure is preserved
-keep,allowobfuscation class com.example.liftrix.data.repository.** { *; }
-keep,allowobfuscation interface com.example.liftrix.domain.repository.** { *; }
-keep,allowobfuscation class com.example.liftrix.domain.usecase.** { *; }
-keep,allowobfuscation class com.example.liftrix.domain.service.** { *; }
-keep,allowobfuscation class com.example.liftrix.data.service.** { *; }

# Vico chart library
-keep class com.patrykandpatrick.vico.** { *; }
-dontwarn com.patrykandpatrick.vico.**
-keepclassmembers class com.patrykandpatrick.vico.** { *; }

# PDF library (iText 7) - OPTIMIZED RULES
# Only keep the specific iText classes we actually use
-keep class com.itextpdf.kernel.pdf.** { *; }
-keep class com.itextpdf.layout.element.** { *; }
-keep class com.itextpdf.layout.Document { *; }
-keep class com.itextpdf.io.font.constants.StandardFonts { *; }

# Suppress warnings for unused iText features
-dontwarn com.itextpdf.**
-dontwarn javax.xml.**
-dontwarn org.bouncycastle.**

# If BouncyCastle is actually needed (for PDF signing), uncomment:
# -keep class org.bouncycastle.jce.provider.BouncyCastleProvider { *; }
# -keep class org.bouncycastle.** { <init>(...); }

# Native library handling
-keepclasseswithmembernames class * {
    native <methods>;
}

# Prevent stripping of native method registrations
-keepclasseswithmembers class * {
    @com.facebook.jni.annotations.DoNotStrip *;
}

# System library exclusions - Don't obfuscate Android system classes
-keep class android.** { *; }
-keep class com.android.** { *; }
-dontwarn android.**
-dontwarn com.android.**

# Prevent issues with native library loading
-keep class java.lang.System {
    public static void loadLibrary(java.lang.String);
    public static void load(java.lang.String);
}

# ZXing QR code library native components
-keep class com.google.zxing.** { *; }
-keep class com.journeyapps.barcodescanner.** { *; }

# CameraX native components
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# Room Database and SQLCipher encryption
-keep class androidx.room.** { *; }
-keep class androidx.sqlite.** { *; }
-keep class net.zetetic.database.sqlcipher.** { *; }
-dontwarn net.zetetic.database.**

# Keep Room entities and DAOs from obfuscation
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Database class * { *; }

# Encrypted SharedPreferences (AndroidX Security Crypto)
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

# Keep database encryption utilities
-keep class com.example.liftrix.core.security.** { *; }

# Keep release builds shrinkable, but avoid name obfuscation. This app still has
# several Gson/reflection paths where obfuscated field names break persisted JSON.
-dontobfuscate

# Kotlin serialization for navigation
-keep class kotlinx.serialization.** { *; }
-dontwarn kotlinx.serialization.**
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod
-dontnote kotlinx.serialization.SerializationKt

# Keep Gson fields that are addressed by serialized JSON names. These fields may
# be obfuscated, but R8 must not remove them because Gson reads them reflectively.
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep Kotlin metadata for serialization
-keep class kotlin.Metadata { *; }

# Jetpack Compose optimizations
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Additional rules as needed for libraries
