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

# ProGuard rules for Liftrix
-keep class com.example.liftrix.** { *; }
-dontwarn com.example.liftrix.**

# 🔥 CRITICAL FIX: Explicit Application class keep rules
-keep class com.example.liftrix.LiftrixApp { *; }
-keep class com.example.liftrix.LiftrixApp_* { *; }
-keep class dagger.hilt.android.** { *; }
-keep class **_HiltComponents { *; }
-keep class **_HiltComponents$* { *; }
-keep class **Hilt** { *; }

# 🔥 FIX: Hilt Application class generation
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep class * extends dagger.hilt.android.internal.managers.ApplicationComponentManager { *; }

# 🔥 FIX: Prevent Application class obfuscation
-keep class * extends android.app.Application { *; }
-keepclassmembers class * extends android.app.Application {
    <init>(...);
}

# Vico chart library
-keep class com.patrykandpatrick.vico.** { *; }
-dontwarn com.patrykandpatrick.vico.**
-keepclassmembers class com.patrykandpatrick.vico.** { *; }

# PDF library (iText 7) ProGuard rules
-keep class com.itextpdf.** { *; }
-keep class com.itextpdf.bouncycastle.** { *; }
-keep class com.itextpdf.bouncycastlefips.** { *; }
-keep class com.itextpdf.commons.** { *; }
-keep class com.itextpdf.layout.** { *; }
-keep class com.itextpdf.kernel.** { *; }
-keep class com.itextpdf.io.** { *; }
-keep class com.itextpdf.svg.** { *; }
-keep class com.itextpdf.html2pdf.** { *; }
-keep class com.itextpdf.pdfcleanup.** { *; }
-keep class com.itextpdf.forms.** { *; }
-keep class com.itextpdf.signatures.** { *; }
-keep class com.itextpdf.barcodes.** { *; }
-keep class com.itextpdf.pdfa.** { *; }
-keep class com.itextpdf.pdfua.** { *; }
-keep class com.itextpdf.pdftest.** { *; }
-dontwarn com.itextpdf.**
-dontwarn javax.xml.**
-dontwarn org.bouncycastle.**

# BouncyCastle related classes
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# Additional rules as needed for libraries