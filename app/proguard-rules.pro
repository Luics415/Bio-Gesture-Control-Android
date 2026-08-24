# Preserve useful release crash traces while allowing R8 to shrink the app.
-keepattributes SourceFile,LineNumberTable,*Annotation*
-renamesourcefileattribute SourceFile

# MediaPipe publishes consumer rules, but its task graph also loads native/JNI-facing classes.
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**
