# WorkManager instantiates workers by name.
-keep class * extends androidx.work.ListenableWorker { *; }

# Keep generated Room implementations used by the local data layer.
-keep class **_Impl { *; }
