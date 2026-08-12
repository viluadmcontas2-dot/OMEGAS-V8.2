-keep class com.hoho.android.usbserial.** { *; }
-keepclassmembers class com.omegas.prohub.web.HubJavascriptBridge {
    @android.webkit.JavascriptInterface <methods>;
}

-dontwarn org.jetbrains.annotations.**

