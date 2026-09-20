# Keep WebView JS interfaces if you add any later.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
