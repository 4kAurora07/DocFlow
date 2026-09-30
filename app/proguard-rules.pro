# DoseFlow - R8 / ProGuard Configuration

# Kotlin Serialization
-keepattributes *Annotation*,InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room SQLite
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Keep Data Entities and DAOs
-keep class com.doseflow.app.data.local.entity.** { *; }
-keep interface com.doseflow.app.data.local.dao.** { *; }

# Keep domain models
-keep class com.doseflow.app.domain.model.** { *; }

# Strip debug and verbose logging from release APK
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
    public static int i(...);
    public static boolean isLoggable(java.lang.String, int);
}
