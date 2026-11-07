# InstaGRUM is a local simulator; no networking rules are required.

# kotlinx.serialization writes and reads every saved profile. R8 must not rename
# or strip these, or an optimized release build would fail to load existing data.
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisible*Annotations
-dontnote kotlinx.serialization.**

-keepclassmembers class com.instaos.app.model.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.instaos.app.model.**$$serializer { *; }
-keep @kotlinx.serialization.Serializable class com.instaos.app.model.** { *; }

-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# WorkManager instantiates the background worker reflectively by class name.
-keep class com.instaos.app.notifications.ActivityWorker { <init>(...); }

# The service and boot receiver are started by the system from the manifest.
-keep class com.instaos.app.notifications.GrowthService { *; }
-keep class com.instaos.app.notifications.BootReceiver { *; }
