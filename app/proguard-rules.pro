# WorkManager instantiates workers by name.
-keep class * extends androidx.work.ListenableWorker { *; }

# Keep generated Room implementations used by the local data layer.
-keep class **_Impl { *; }

# Keep LiteRT-LM on-device LLM entry points referenced from the Flow local model layer.
-keep class com.google.ai.edge.litertlm.** { *; }
-dontwarn com.google.auto.value.AutoValue
-dontwarn com.google.auto.value.AutoValue$Builder
