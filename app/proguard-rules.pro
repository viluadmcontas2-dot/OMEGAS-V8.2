-keep class com.hoho.android.usbserial.** { *; }
# Todas as pontes JS (Hub, V7, AutoCal, Power): sem isto o R8 de um APK release renomeia/remove os métodos e as telas somem.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

-dontwarn org.jetbrains.annotations.**

