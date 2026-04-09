# WorkManager instantiates workers by name.
-keep class * extends androidx.work.ListenableWorker { *; }

# Keep generated Room implementations used by the local data layer.
-keep class **_Impl { *; }

# Keep MediaPipe on-device LLM entry points referenced from the Flow local model layer.
-keep class com.google.mediapipe.tasks.genai.llminference.** { *; }
-dontwarn com.google.auto.value.AutoValue
-dontwarn com.google.auto.value.AutoValue$Builder
-dontwarn com.google.mediapipe.framework.image.BitmapExtractor
-dontwarn com.google.mediapipe.framework.image.ByteBufferExtractor
-dontwarn com.google.mediapipe.framework.image.MPImage
-dontwarn com.google.mediapipe.framework.image.MPImageProperties
-dontwarn com.google.mediapipe.framework.image.MediaImageExtractor
