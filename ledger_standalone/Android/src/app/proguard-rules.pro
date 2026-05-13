# LiteRT / LiteRT-LM — keep all JNI-bound classes and native methods
-keep class com.google.mediapipe.** { *; }
-keep class com.google.android.gms.tflite.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# Room — keep generated DAOs and database implementations
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Dao class *
-keepclassmembers @androidx.room.Dao class * { *; }

# Hilt — keep component and module classes
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class *
-keep @dagger.hilt.android.lifecycle.HiltViewModel class *

# Gson — keep model classes used for JSON parsing
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.ledger.app.data.** { *; }
-keep class com.ledger.app.db.** { *; }

# Moshi — keep generated adapters
-keep class com.squareup.moshi.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
    @com.squareup.moshi.JsonClass <fields>;
}

# AppAuth
-keep class net.openid.appauth.** { *; }

# WorkManager
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker
-keepclassmembers class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Kotlin coroutines
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# Keep R class
-keepclassmembers class **.R$* {
    public static <fields>;
}
