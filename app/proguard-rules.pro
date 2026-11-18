# MediaPipe Tasks usa protobuf-lite y JNI por reflexión: conservar sus clases en la build minificada.
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.mediapipe.**
-dontwarn com.google.protobuf.**
