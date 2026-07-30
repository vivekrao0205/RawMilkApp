# R8 / ProGuard optimization rules for RawMilk Android Release builds

# Strip debug logging statements in production release builds to reduce bytecode size and improve execution speed
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Preserve Android Browser Helper TWA and Custom Tabs classes
-keep class com.google.androidbrowserhelper.** { *; }
-keep class androidx.browser.customtabs.** { *; }

# Preserve Firebase Auth, FCM, and Google Play Services entry points
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-keep class com.rawmilk.app.** { *; }
