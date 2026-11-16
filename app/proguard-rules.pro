# WebView JS 接口（CalmSniffer）混淆保护
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
# WebView 桥内部类
-keep class com.calm.core.browser.BrowserScreenKt$configureWebView$1 { *; }
