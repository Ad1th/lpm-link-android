# Add project specific ProGuard rules here.

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class cx.lpm.link.**$$serializer { *; }
-keepclassmembers class cx.lpm.link.** { *** Companion; }
-keepclasseswithmembers class cx.lpm.link.** { kotlinx.serialization.KSerializer serializer(...); }
