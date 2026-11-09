# kotlinx.serialization keeps its generated serializers through reflection-free lookups.
-keepclassmembers class org.openturnly.mobile.** { *** Companion; }
-keepclasseswithmembers class org.openturnly.mobile.** { kotlinx.serialization.KSerializer serializer(...); }
