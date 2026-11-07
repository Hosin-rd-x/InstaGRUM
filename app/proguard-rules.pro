# InstaOS is a web-session client; it talks to instagram.com/i.instagram.com
# over HTTPS. No special networking rules are required.

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
