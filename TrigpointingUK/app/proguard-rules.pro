-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-dontpreverify
-verbose
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*

# Do not obfuscate; keep names readable for stack traces
-dontobfuscate

# Keep source file and line numbers for better stack traces
-keepattributes SourceFile,LineNumberTable

-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference
-keep public class com.android.vending.licensing.ILicensingService

-keepclasseswithmembernames class * {
    native <methods>;
}

-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}

-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

-keepclassmembers class * extends android.app.Activity {
   public void *(android.view.View);
}

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}

# --- Gson + reflection friendly rules ---
# Keep generic signatures and annotations used by Gson
-keepattributes Signature
-keepattributes *Annotation*

# Keep Gson adapters and factories if present
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep TypeToken and subclasses (used for generics)
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# Keep fields annotated with @SerializedName even if obfuscated
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep API models used via reflection by Gson
-keep class uk.trigpointing.android.api.AuthResponse { *; }
-keep class uk.trigpointing.android.api.User { *; }
-keep class uk.trigpointing.android.api.ErrorResponse { *; }

# Keep all inner classes in TrigApiClient that are used as data models
-keep class uk.trigpointing.android.api.TrigApiClient$** { *; }

# Keep Trig export data models used by Gson in DownloadTrigsActivity
-keep class uk.trigpointing.android.DownloadTrigsActivity$TrigExportResponse { *; }
-keep class uk.trigpointing.android.DownloadTrigsActivity$TrigExportItem { *; }

# Keep Coil classes that might be referenced indirectly by GSON
-keep class coil.size.** { *; }
-dontwarn coil.size.**

# Defensive: ensure AndroidX BundleCompat is not altered in a way that breaks reflection
-keep class androidx.core.os.BundleCompat { *; }

# --- Jackson + reflection friendly rules ---
# Keep Jackson annotations and generic signatures
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# Keep Jackson core classes
-keep class com.fasterxml.jackson.** { *; }
-keep class com.fasterxml.jackson.databind.** { *; }
-keep class com.fasterxml.jackson.dataformat.** { *; }

# Keep Jackson ObjectMapper and related classes
-keep class com.fasterxml.jackson.databind.ObjectMapper { *; }
-keep class com.fasterxml.jackson.dataformat.yaml.YAMLFactory { *; }
-keep class com.fasterxml.jackson.dataformat.yaml.YAMLGenerator { *; }
-keep class com.fasterxml.jackson.dataformat.yaml.YAMLParser { *; }

# Keep Jackson deserializers and serializers
-keep class * implements com.fasterxml.jackson.databind.JsonDeserializer
-keep class * implements com.fasterxml.jackson.databind.JsonSerializer
-keep class * extends com.fasterxml.jackson.databind.deser.std.StdDeserializer
-keep class * extends com.fasterxml.jackson.databind.ser.std.StdSerializer

# Keep Jackson type information
-keep class com.fasterxml.jackson.databind.type.** { *; }
-keep class com.fasterxml.jackson.databind.jsontype.** { *; }

# Keep fields annotated with Jackson annotations
-keepclassmembers class * {
    @com.fasterxml.jackson.annotation.* <fields>;
    @com.fasterxml.jackson.annotation.* <methods>;
}

# Keep MapDownload classes used by Jackson
-keep class uk.trigpointing.android.mapping.MapDownload { *; }
-keep class uk.trigpointing.android.mapping.MapDownload$MapDownloadsList { *; }

# Keep Kotlin coroutines and related classes that might be used by Jackson
-keep class kotlin.coroutines.** { *; }
-keep class kotlinx.coroutines.** { *; }

# Suppress warnings for missing Java classes that Jackson depends on
-dontwarn java.beans.ConstructorProperties
-dontwarn java.beans.Transient
