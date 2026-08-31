# Keep native methods
-keepclassmembers class * {
    native <methods>;
}

# Keep classes that are used as a parameter type of methods that are also marked as keep
# to preserve changing those methods' signature.
-keep class helium314.keyboard.latin.dictionary.Dictionary
-keep class helium314.keyboard.latin.NgramContext
-keep class helium314.keyboard.latin.makedict.ProbabilityInfo

# after upgrading to gradle 8, stack traces contain "unknown source"
-keepattributes SourceFile,LineNumberTable
-dontobfuscate

# ---------------------------------------------------------------- SuperVoiceBoard
# sherpa-onnx: JNI entry points are looked up reflectively from native code.
-keep class com.k2fsa.sherpa.onnx.** { *; }

# MediaPipe tasks-genai uses JNI plus protobuf-lite reflection, and references
# AutoValue/protobuf annotations that are compile-time only.
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**
-dontwarn com.google.auto.value.**
-dontwarn com.google.protobuf.**

# Apache Commons Compress: only the bzip2/tar codepaths are used.
-dontwarn org.apache.commons.compress.**

# The voice layer is reached from LatinIME and from the manifest's services.
-keep class com.vboard.app.llm.LlmRefinerService { *; }
-keep class com.vboard.app.models.ModelDownloadService { *; }
-keep class com.vboard.app.models.ModelDownloadWorker { *; }
