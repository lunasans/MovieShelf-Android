# Retrofit don't strip methods for serialization
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

# Keep Retrofit interface methods
-keep class at.neuhaus.movieshelf.data.api.** { *; }

# Keep Data Models from being obfuscated (required for GSON/Serialization)
-keep class at.neuhaus.movieshelf.data.model.** { *; }

# Gson specific rules
-keep class com.google.gson.** { *; }
-keep class sun.misc.Unsafe { *; }
# Generische Signaturen erhalten (sonst "TypeToken must be created with a type argument")
-keepattributes Signature
# TypeToken-Subklassen und mit @SerializedName annotierte Felder nicht entfernen/obfuskieren
-keep class * extends com.google.gson.reflect.TypeToken
-keep,allowobfuscation class com.google.gson.reflect.TypeToken
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# OkHttp specific
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**

# Tink / security-crypto (EncryptedSharedPreferences)
# WICHTIG: Tink wird zur Laufzeit u.a. per Reflection genutzt. Ohne diese Keep-Regeln
# entfernt/obfuskiert R8 im Release-Build Klassen -> Crash beim ersten Token-Schreiben (Login).
-keep class androidx.security.crypto.** { *; }
-keep class com.google.crypto.tink.** { *; }
-keepclassmembers class com.google.crypto.tink.** { *; }
-keep class * extends com.google.crypto.tink.shaded.protobuf.GeneratedMessageLite { *; }
-keepclassmembers class * extends com.google.crypto.tink.shaded.protobuf.GeneratedMessageLite {
    <fields>;
}
-dontwarn com.google.crypto.tink.**
-dontwarn com.google.api.client.http.**
-dontwarn org.joda.time.**
-dontwarn org.json.**

# Sensible Log-Aufrufe (debug/verbose/info) im Release entfernen,
# damit keine Tokens/E-Mails/Request-Bodies im Logcat landen.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
