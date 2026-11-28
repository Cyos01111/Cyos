# CyosPlay R8 / ProGuard rules
-keep class com.cyosplay.player.data.model.** { *; }

# Targeted Media3 reflection keep rules (replaces blanket -keep class androidx.media3.** so R8 shrinks unused classes)
-keep class androidx.media3.exoplayer.DefaultRenderersFactory { <init>(...); }
-keep class androidx.media3.extractor.mkv.MatroskaExtractor { <init>(...); }
-keep class androidx.media3.extractor.mp4.Mp4Extractor { <init>(...); }
-keep class io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory { *; }
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
