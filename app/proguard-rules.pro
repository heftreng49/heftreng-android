-keep class com.heftreng.app.data.model.** { *; }
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn io.github.jan.supabase.**

# ── Meta Audience Network (AdMob Mediation) ───────────────────────────────────
-keep class com.facebook.** { *; }
-keep interface com.facebook.** { *; }
-keepattributes Signature
-dontwarn com.facebook.infer.annotation.**
-dontwarn com.facebook.ads.**
-keep class com.facebook.infer.annotation.** { *; }
