# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface



-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose

-dontshrink
-dontoptimize
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature
-keepattributes Exceptions


# Keep your main application class
#-keep class com.remote.firetvremote.MyApp       { * ; }
-keep class com.connectsdk.**       { * ; }
-keep class com.out.androidtvremote.androidLib.** {*;}




# Keep your main application class
-keep public class com.out.androidtvremote.App
-keep class com.connectsdk.**       { * ; }


-repackageclasses 'com.out.androidtvremote'

# Keep any classes that are referenced in your AndroidManifest.xml
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.app.Application
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# Keep the native methods of your NDK libraries
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep all fields and methods with "R" in the name to prevent resource shrinking
-keepclassmembers class **.R$* {
    public static <fields>;
}



# Preserve annotations
-keepclassmembers class * {
    @com.example.annotation.* <methods>;
}

# Keep all enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
-renamesourcefileattribute SourceFile




-assumenoexternalsideeffects class android.util.Log{
 public static *** d(...);
 public static *** v(...);
 public static *** i(...);
 public static *** w(...);
}


-keep,allowobfuscation class * {
        public <fields>;
        public <methods>;
}



-dontwarn org.ietf.**
-dontwarn org.apache.**


-dontwarn com.amazon.**
-dontwarn java.beans.**
-dontwarn java.beans.**
-dontwarn javax.**
-dontwarn org.ietf.**
-dontwarn org.slf4j.**
-dontwarn org.w3c.**
-dontwarn sun.security.**
-dontwarn com.connectsdk.**
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
-dontwarn android.media.LoudnessCodecController
-dontwarn android.media.LoudnessCodecController$OnLoudnessCodecUpdateListener
-dontwarn okhttp3.internal.annotations.EverythingIsNonNull
-dontwarn com.facebook.infer.annotation.**
-dontwarn com.sun.nio.file.SensitivityWatchEventModifier

# ── AdMob / Google Mobile Ads ─────────────────────────────────────────────────
# Keep all ad model/config classes used with Gson serialisation
-keep class com.out.androidtvremote.ads.model.** { *; }
-keep class com.out.androidtvremote.ads.repository.** { *; }

# Required by Google Mobile Ads SDK
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.ads.** { *; }

# ── Gson ─────────────────────────────────────────────────────────────────────
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
