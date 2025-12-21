# ProGuard rules for Pentagram AirPlay Receiver

# Keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# JNI native methods - must preserve for native library calls
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep all classes in our crypto package that interface with native code
-keep class com.pentagram.airplay.crypto.** { *; }
-keep class com.pentagram.airplay.service.AirPlayCryptoNative { *; }
-keep class com.pentagram.airplay.service.FairPlay { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# BouncyCastle
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# Conscrypt (Google's OpenSSL provider)
-keep class org.conscrypt.** { *; }
-dontwarn org.conscrypt.**

# Lazysodium (libsodium bindings)
-keep class com.goterl.lazysodium.** { *; }
-keep class com.sun.jna.** { *; }
-dontwarn com.goterl.lazysodium.**
-dontwarn com.sun.jna.**

# JNA (Java Native Access)
-keep class net.java.dev.jna.** { *; }
-dontwarn net.java.dev.jna.**

# dd-plist (Apple plist parsing)
-keep class com.dd.plist.** { *; }
-dontwarn com.dd.plist.**

# AndroidX
-keep class androidx.** { *; }
-dontwarn androidx.**

# Keep R8 from removing used classes
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep service declarations
-keep public class * extends android.app.Service

# Keep activities
-keep public class * extends android.app.Activity
