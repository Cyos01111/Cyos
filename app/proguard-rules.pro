# Add project specific ProGuard rules here.
-keep class com.cyosplay.player.data.model.** { *; }
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Strip all android.util.Log calls in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}

