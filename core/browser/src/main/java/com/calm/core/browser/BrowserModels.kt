package com.calm.core.browser

import android.webkit.WebView
import android.webkit.WebSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.ConcurrentHashMap

/** WebView 池：每个标签一个实例，切换标签保留状态（BR-004）。 */
@Singleton
class WebViewPool @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
) {
    private val pool = ConcurrentHashMap<String, WebView>()

    fun get(tabId: String): WebView = pool.getOrPut(tabId) {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.mediaPlaybackRequiresUserGesture = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            settings.setSupportMultipleWindows(false)
        }
    }

    fun peek(tabId: String): WebView? = pool[tabId]

    fun destroy(tabId: String) {
        pool.remove(tabId)?.apply {
            loadUrl("about:blank")
            destroy()
        }
    }
}

data class SearchEngine(val name: String, val template: String)

/** 预置搜索引擎（对应设计方案 4.14）。 */
val presetEngines = listOf(
    SearchEngine("百度", "https://www.baidu.com/s?wd=%s"),
    SearchEngine("Google", "https://www.google.com/search?q=%s"),
    SearchEngine("必应", "https://www.bing.com/search?q=%s"),
    SearchEngine("搜狗", "https://www.sogou.com/web?query=%s"),
    SearchEngine("360", "https://www.so.com/s?q=%s"),
    SearchEngine("Duck", "https://duckduckgo.com/?q=%s"),
)

data class SniffedResource(
    val url: String,
    val mime: String?,
    val type: String, // image / audio / video / link
)

object UrlUtil {

    private val directUrl = Regex("^https?://[\\w\\-]+(\\.[\\w\\-]+)+([/#?].*)?$")

    /** 文本中的第一条 URL（SB-003）。 */
    private val anyUrl = Regex("https?://[\\w\\-]+(\\.[\\w\\-]+)+[/#?]?[^\\s]*")

    fun isUrl(text: String): Boolean = directUrl.matches(text.trim())

    fun firstUrlIn(text: String): String? = anyUrl.find(text)?.value

    fun searchUrl(engine: SearchEngine, query: String): String =
        engine.template.replace("%s", java.net.URLEncoder.encode(query, "UTF-8"))

    fun hostOf(url: String): String = try {
        java.net.URI(url).host ?: url
    } catch (_: Exception) {
        url
    }

    fun sniffType(url: String, mime: String?): String? {
        val path = url.substringBefore('?').lowercase()
        val m = mime?.lowercase().orEmpty()
        return when {
            m.startsWith("image/") || path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".png") ||
                path.endsWith(".webp") || path.endsWith(".gif") -> "image"
            m.startsWith("audio/") || path.endsWith(".mp3") || path.endsWith(".aac") || path.endsWith(".flac") ||
                path.endsWith(".wav") || path.endsWith(".m4a") || path.endsWith(".ogg") -> "audio"
            m.startsWith("video/") || path.endsWith(".mp4") || path.endsWith(".mkv") || path.endsWith(".webm") ||
                path.endsWith(".flv") || path.endsWith(".avi") || path.endsWith(".m3u8") || path.endsWith(".mov") -> "video"
            m == "application/octet-stream" || m.contains("attachment") -> "link"
            else -> null
        }
    }
}

const val NIGHT_CSS_JS =
    "(function(){var s=document.createElement('style');s.id='calm-night';" +
        "s.textContent='html{filter:invert(1) hue-rotate(180deg)!important}" +
        "img,video,canvas,iframe,svg,picture{filter:invert(1) hue-rotate(180deg)!important}';" +
        "document.head.appendChild(s);})()"
