# Journalator R8 configuration.
#
# The app has no reflection of its own, no serialisation library and no dynamic class
# loading, so almost nothing needs keeping. What is here is either required by a library
# or is a deliberate hardening choice.

# --- Logging -----------------------------------------------------------------
# Strip every log call from the release build. Nothing in this app should be writing to
# logcat in production, and a stray log line is a plaintext leak into a buffer other apps
# on older devices can read.
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
    public static *** wtf(...);
}

# --- Kotlin / coroutines -----------------------------------------------------
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# --- Room --------------------------------------------------------------------
# Room generates implementations that are looked up by name at runtime.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# --- Compose -----------------------------------------------------------------
# Compose ships its own rules; these only silence warnings about optional integrations
# that are not on the classpath.
-dontwarn androidx.compose.**

# --- AndroidX Biometric ------------------------------------------------------
-keep class androidx.biometric.** { *; }

# --- Desugared java.time -----------------------------------------------------
-dontwarn java.lang.invoke.**
-dontwarn build.IgnoreJava8API

# --- Crypto ------------------------------------------------------------------
# Provider lookups are by algorithm string, not by class, so nothing needs keeping — but
# make sure R8 does not decide the JCA service files are unused.
-keep class javax.crypto.** { *; }
-keep class javax.crypto.spec.** { *; }

# --- Debug-only tooling must not appear in release ---------------------------
# LeakCanary is a debugImplementation dependency, so it is absent here by construction.
# This rule turns "absent" into "verifiably absent" if that ever changes by accident.
-checkdiscard class leakcanary.** { *; }
