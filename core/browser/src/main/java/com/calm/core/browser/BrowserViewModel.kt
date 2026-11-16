package com.calm.core.browser

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calm.data.local.BookmarkDao
import com.calm.data.local.BookmarkEntity
import com.calm.data.local.HistoryDao
import com.calm.data.local.HistoryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class BrowserTab(
    val id: String,
    val url: String,
    val title: String,
)

@HiltViewModel
class BrowserViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
    val webViews: WebViewPool,
    val downloader: com.calm.core.downloader.DownloadController,
    private val bookmarkDao: BookmarkDao,
    private val historyDao: HistoryDao,
) : ViewModel() {

    val tabs = mutableStateListOf<BrowserTab>()
    var currentTabId by mutableStateOf<String?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var progress by mutableStateOf(0)
        private set
    var nightMode by mutableStateOf(false)
    var engineIndex by mutableStateOf(0)
        private set
    var editing by mutableStateOf(false)
    var urlDraft by mutableStateOf("")

    /** 资源嗅探结果（当前标签）。 */
    val resources = mutableStateMapOf<String, SniffedResource>()
    var sniffEnabled by mutableStateOf(true)

    /** 待确认的下载（DownloadListener 触发）。 */
    var pendingDownload by mutableStateOf<PendingDownload?>(null)

    val bookmarks = bookmarkDao.observeAll()
    val history = historyDao.observeAll()

    data class PendingDownload(
        val url: String,
        val name: String,
        val mime: String?,
        val pageUrl: String?,
    )

    init {
        newTab(null)
    }

    fun newTab(url: String?) {
        if (tabs.size >= 10) return // BR-004 标签上限
        val tab = BrowserTab(UUID.randomUUID().toString(), url ?: "calm://home", url ?: "主页")
        tabs.add(tab)
        currentTabId = tab.id
        resources.clear()
        if (url != null) loadUrl(tab.id, url)
    }

    fun closeTab(id: String) {
        webViews.destroy(id)
        val idx = tabs.indexOfFirst { it.id == id }
        tabs.removeAll { it.id == id }
        if (tabs.isEmpty()) {
            newTab(null)
        } else if (currentTabId == id) {
            currentTabId = tabs[(idx - 1).coerceAtLeast(0)].id
        }
        resources.clear()
    }

    fun selectTab(id: String) {
        currentTabId = id
        editing = false
        resources.clear()
        val tab = tabs.firstOrNull { it.id == id }
        urlDraft = tab?.url?.takeIf { it != "calm://home" } ?: ""
    }

    fun loadUrl(tabId: String, raw: String) {
        val url = normalize(raw)
        val webView = webViews.get(tabId)
        tabs.replaceAll { if (it.id == tabId) it.copy(url = url, title = url) else it }
        urlDraft = url
        editing = false
        webView.loadUrl(url)
    }

    private fun normalize(raw: String): String {
        val t = raw.trim()
        if (t.isEmpty()) return "calm://home"
        return if (UrlUtil.isUrl(t)) t else UrlUtil.searchUrl(presetEngines[engineIndex], t)
    }

    fun searchWith(engineIndex: Int, query: String) {
        this.engineIndex = engineIndex
        currentTabId?.let { loadUrl(it, UrlUtil.searchUrl(presetEngines[engineIndex], query)) }
    }

    fun goBack() {
        currentTabId?.let { id -> webViews.peek(id)?.takeIf { it.canGoBack() }?.goBack() }
    }

    fun goForward() {
        currentTabId?.let { id -> webViews.peek(id)?.takeIf { it.canGoForward() }?.goForward() }
    }

    fun reload() {
        currentTabId?.let { id ->
            val w = webViews.peek(id)
            if (w == null) {
                val tab = tabs.firstOrNull { it.id == id }
                if (tab != null && tab.url != "calm://home") loadUrl(id, tab.url)
            } else {
                w.reload()
            }
        }
    }

    fun onProgress(tabId: String, p: Int) {
        if (tabId == currentTabId) {
            progress = p
            loading = p in 1..99
        }
    }

    fun onTitle(tabId: String, title: String) {
        if (title.isBlank() || title.startsWith("about:")) return
        tabs.replaceAll { if (it.id == tabId) it.copy(title = title) else it }
    }

    fun onUrlChanged(tabId: String, url: String) {
        tabs.replaceAll { if (it.id == tabId) it.copy(url = url) else it }
        if (tabId == currentTabId && !editing) urlDraft = url
    }

    fun recordHistory(tabId: String, url: String, title: String) {
        if (url.startsWith("calm://") || url.startsWith("about:")) return
        viewModelScope.launch {
            historyDao.insert(HistoryEntity(title = title.ifBlank { url }, url = url, visitedAt = System.currentTimeMillis()))
        }
    }

    // ---- 书签 ----

    fun toggleBookmark() {
        val tab = tabs.firstOrNull { it.id == currentTabId } ?: return
        if (tab.url.startsWith("calm://")) return
        viewModelScope.launch {
            if (bookmarkDao.exists(tab.url)) {
                bookmarkDao.deleteByUrl(tab.url)
            } else {
                bookmarkDao.insert(
                    BookmarkEntity(title = tab.title, url = tab.url, folder = "默认", createdAt = System.currentTimeMillis()),
                )
            }
        }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch { bookmarkDao.delete(id) }
    }

    fun isBookmarked(): Boolean {
        val tab = tabs.firstOrNull { it.id == currentTabId } ?: return false
        // 简化：非响应式判断由 UI 层 collect 处理，此处仅用于占位
        return false
    }

    // ---- 嗅探 ----

    fun addSniffed(url: String, mime: String?) {
        if (!sniffEnabled) return
        if (url.startsWith("data:") || url.startsWith("blob:")) return
        val type = UrlUtil.sniffType(url, mime) ?: return
        if (resources.size > 400) return
        resources.putIfAbsent(url, SniffedResource(url, mime, type))
    }

    fun onDownloadRequested(url: String, disposition: String?, mime: String?, pageUrl: String?) {
        val name = com.calm.core.downloader.NameUtil.fileNameFromUrl(url)
            ?: disposition?.let { Regex("filename\\*?=(?:UTF-8''|\"?)([^\";]+)").find(it)?.groupValues?.get(1) }
            ?: "download_${System.currentTimeMillis() % 100000}"
        pendingDownload = PendingDownload(url, name, mime, pageUrl)
    }

    fun confirmDownload(name: String, threads: Int) {
        val pd = pendingDownload ?: return
        pendingDownload = null
        viewModelScope.launch {
            downloader.enqueue(pd.url, name, threads, pd.mime, pd.pageUrl)
            com.calm.core.downloader.DownloadService.start(appContext)
        }
    }

    fun sniffDownload(res: SniffedResource) {
        val name = com.calm.core.downloader.NameUtil.fileNameFromUrl(res.url)
            ?: "media_${System.currentTimeMillis() % 100000}.${res.type}"
        pendingDownload = PendingDownload(res.url, name, res.mime, null)
    }

    fun clearHistory() {
        viewModelScope.launch { historyDao.clear() }
    }

    fun applyNightMode(enabled: Boolean) {
        nightMode = enabled
        tabs.forEach { tab ->
            val w = webViews.peek(tab.id)
            if (w != null) {
                if (enabled) {
                    w.evaluateJavascript(NIGHT_CSS_JS, null)
                } else {
                    w.evaluateJavascript(
                        "(function(){var s=document.getElementById('calm-night');if(s&&s.parentNode){s.parentNode.removeChild(s)}})()",
                        null,
                    )
                    w.reload()
                }
            }
        }
    }
}

private fun androidx.compose.runtime.snapshots.SnapshotStateList<BrowserTab>.replaceAll(op: (BrowserTab) -> BrowserTab) {
    val mapped = map(op)
    clear()
    addAll(mapped)
}
