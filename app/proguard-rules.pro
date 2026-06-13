# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# ---------------------------------------------------------------------------
# Stack trace readability
# ---------------------------------------------------------------------------
# Preserve source file names and line numbers so crash reports are readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---------------------------------------------------------------------------
# Kotlin
# ---------------------------------------------------------------------------
# Keep Kotlin metadata so reflection-based libraries can inspect types.
-keepattributes *Annotation*, RuntimeVisibleAnnotations
-keep class kotlin.Metadata { *; }

# ---------------------------------------------------------------------------
# Accompanist DrawablePainter
# ---------------------------------------------------------------------------
# Accompanist accesses Drawable subclass members via reflection at runtime.
-keep class com.google.accompanist.drawablepainter.** { *; }

# ---------------------------------------------------------------------------
# Android / Jetpack internals
# ---------------------------------------------------------------------------
# Navigation component uses class names as route keys.
-keepnames class androidx.navigation.** { *; }