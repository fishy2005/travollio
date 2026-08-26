# Travollio R8/ProGuard rules (release hardening)
# Room, WorkManager, and Compose ship their own consumer rules; keep explicit
# rules only for things R8 cannot infer.

# Keep the crash-logger tag and agent classes debuggable in stack traces.
-keep class com.odago.travollio.agent.** { *; }
-keep class com.odago.travollio.routines.** { *; }

# kotlinx.serialization / reflection-free JSON: org.json needs no keep rules.

# pdfbox-android text extraction uses reflection/loaders internally; keep it intact.
-keep class com.tom_roush.** { *; }
-keep class org.apache.pdfbox.** { *; }
-dontwarn com.tom_roush.**
-dontwarn org.apache.pdfbox.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**

# If the app ever uses reflection on Room entities, uncomment:
# -keep class com.odago.travollio.data.** { *; }
