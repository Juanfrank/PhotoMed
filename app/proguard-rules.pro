# MediaPipe Tasks usa protobuf-lite y JNI por reflexión: conservar sus clases en la build minificada.
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.mediapipe.**
-dontwarn com.google.protobuf.**
# MediaPipe crea en Graph (y otras clases) un registrador de Flogger, que busca su implementación por
# reflexión (com.google.common.flogger.backend.system.DefaultPlatform…). Si R8 renombra o quita
# Flogger, MediaPipe no se inicia en la build de publicación (NoClassDefFoundError de Graph).
-keep class com.google.common.flogger.** { *; }
-dontwarn com.google.common.flogger.**
