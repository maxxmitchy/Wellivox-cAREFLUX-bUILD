# Room Database keep rules
-keep class * extends androidx.room.RoomDatabase { *; }
-keepclassmembers class * {
    @androidx.room.Dao *;
    @androidx.room.Entity *;
}
-dontwarn androidx.room.paging.**

# Data Models (Keep model fields for Firestore & Room serialization)
-keep class com.example.data.** { *; }
-keepclassmembers class com.example.data.** { *; }

# Kotlinx Serialization
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-dontwarn kotlinx.serialization.**
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable *;
}
-keepclassmembers class * {
    @kotlinx.serialization.SerialName *;
}

# Firebase & Firestore
-dontwarn com.google.firebase.**
-keep class com.google.firebase.** { *; }

# Retrofit / OkHttp
-dontwarn okhttp3.**
-dontwarn retrofit2.**

