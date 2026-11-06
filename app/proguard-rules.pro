# Keep kotlinx.serialization generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class com.avi.peek.**$$serializer { *; }
-keepclassmembers class com.avi.peek.** {
    *** Companion;
}
-keepclasseswithmembers class com.avi.peek.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Room
-keep class * extends androidx.room.RoomDatabase
