# GSON rules
# Preserve all annotations and generic signatures
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses

# Preserve GSON specific classes
-keep class com.google.gson.** { *; }

# Preserve your domain models used for JSON serialization (GSON)
# This prevents field names from being renamed, which would break JSON loading/saving
-keep class com.vodotxt.domain.** { *; }

# Google Drive and Auth rules
-keep class com.google.api.client.** { *; }
-keep class com.google.api.services.drive.** { *; }
-keep class com.google.android.gms.auth.** { *; }
-dontwarn com.google.api.client.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn javax.annotation.Nullable
-dontwarn org.checkerframework.**
-dontwarn javax.naming.**
-dontwarn org.ietf.jgss.**
-dontwarn org.apache.http.**

# WorkManager
-keep class androidx.work.** { *; }

# Compose rules are usually handled by the compiler plugin,
# but keeping standard Material3 components safe is a good practice.
-keep class androidx.compose.material3.** { *; }
