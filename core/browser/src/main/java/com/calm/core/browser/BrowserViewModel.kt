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
import com.calm.data.local.LogStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class BrowserTab(
    val id: String,
    val url: String,
    val title: String,
)

/** 书签添加/编辑弹窗数据（网页名/网址/目录/加入主页）。 */
data class BookmarkDraft(
    val url: String,
    val title: String,
    val folder: String = "默认",
    val inHome: Boolean = false,
)

@HiltViewModel
class BrowserViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
    val webViews: WebViewPool,
    val downloader: com.calm.core.downloader.DownloadController,
    val batchImages: BatchImageDownloader,
    val adBlocker: AdBlocker,
    val github: GitHubHelper,
    private val logStore: LogStore,
    private val bookmarkDao: BookmarkDao,
    private val historyDao: HistoryDao,
) : ViewModel() {

    // 标签页：不设上限（用户明确要求）
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

    /** 书签添加/编辑弹窗。 */
    var pendingBookmark by mutableStateOf<BookmarkDraft?>(null)

    /** 图片批量选择页（BR-010）。 */
    var imagePickerMode by mutableStateOf(false)
        private set
    val pickedImages = mutableStateListOf<String>()
    val pickedSet = mutableSetOf<String>()
    var pickedCount by mutableStateOf(0)
        private set
    var batchAskMode by mutableStateOf(false)
        private set
    var batchMessage by mutableStateOf<String?>(null)
        private set

    /** 阅读模式提取结果（null = 未开启）。 */
    var readerResult by mutableStateOf<ReaderModeJs.Result?>(null)
        private set

    val bookmarks = bookmarkDao.observeAll()
    val homeBookmarks = bookmarkDao.observeHome()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
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
        val tab = BrowserTab(UUID.randomUUID().toString(), url ?: HOME_URL, if (url == null) "主页" else url)
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
        urlDraft = tab?.url?.takeIf { it != HOME_URL } ?: ""
    }

    fun loadUrl(tabId: String, raw: String) {
        val url = normalize(raw)
        val webView = webViews.get(tabId)
        // UA 适配：B 站等需桌面模式（bilibili 未适配修复）
        webView.settings.userAgentString = if (needsDesktopUa(url)) DESKTOP_UA else null
        tabs.replaceAll { if (it.id == tabId) it.copy(url = url, title = if (url == HOME_URL) "主页" else url) else it }
        urlDraft = if (url == HOME_URL) "" else url
        editing = false
        logStore.log("浏览器", "打开 ${UrlUtil.hostOf(url)}")
        webView.loadUrl(url)
    }

    /** 回到起始页（修复「点击首页无法返回起始页」）。 */
    fun goHome() {
        currentTabId?.let { loadUrl(it, HOME_URL) }
    }

    private fun normalize(raw: String): String {
        val t = raw.trim()
        if (t.isEmpty() || t == HOME_URL) return HOME_URL
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
                if (tab != null && tab.url != HOME_URL) loadUrl(id, tab.url)
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
        if (tabId == currentTabId && !editing) urlDraft = if (url == HOME_URL) "" else url
    }

    fun recordHistory(tabId: String, url: String, title: String) {
        if (url.startsWith("calm://") || url.startsWith("about:")) return
        viewModelScope.launch {
            historyDao.insert(HistoryEntity(title = title.ifBlank { url }, url = url, visitedAt = System.currentTimeMillis()))
        }
    }

    // ---- 书签 ----

    /** 打开添加/编辑书签弹窗（预填当前页信息）。 */
    fun openBookmarkDialog() {
        val tab = tabs.firstOrNull { it.id == currentTabId } ?: return
        if (tab.url.startsWith("calm://")) return
        pendingBookmark = BookmarkDraft(url = tab.url, title = tab.title)
    }

    fun saveBookmark(draft: BookmarkDraft) {
        pendingBookmark = null
        viewModelScope.launch {
            if (bookmarkDao.exists(draft.url)) {
                bookmarkDao.updateByUrl(draft.url, draft.title.ifBlank { draft.url }, draft.folder.ifBlank { "默认" }, draft.inHome)
            } else {
                bookmarkDao.insert(
                    BookmarkEntity(
                        title = draft.title.ifBlank { draft.url },
                        url = draft.url,
                        folder = draft.folder.ifBlank { "默认" },
                        inHome = draft.inHome,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
            }
            logStore.log("书签", "${if (draft.inHome) "主页书签" else "书签"}：${draft.title}")
        }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch { bookmarkDao.delete(id) }
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
            logStore.log("下载", "开始：$name")
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

    // ---- 图片批量选择（BR-010/011）----

    fun openImagePicker() {
        val images = resources.values.filter { it.type == "image" }.map { it.url }
        pickedImages.clear()
        pickedImages.addAll(images.take(240))
        pickedSet.clear()
        pickedCount = 0
        batchMessage = null
        imagePickerMode = true
    }

    fun closeImagePicker() {
        imagePickerMode = false
        batchAskMode = false
    }

    fun togglePick(url: String) {
        if (pickedSet.contains(url)) pickedSet.remove(url) else pickedSet.add(url)
        pickedCount = pickedSet.size
    }

    fun pickRange(url: String) {
        if (pickedSet.contains(url)) return
        pickedSet.add(url)
        pickedCount = pickedSet.size
    }

    fun selectAllPicked() {
        pickedSet.clear()
        pickedSet.addAll(pickedImages)
        pickedCount = pickedSet.size
    }

    /** 下载按钮：单图直下；多图弹出压缩包/文件夹问询。 */
    fun onPickDownloadClicked() {
        if (pickedCount == 0) return
        if (pickedCount == 1) {
            val url = pickedSet.first()
            val name = com.calm.core.downloader.NameUtil.fileNameFromUrl(url)
                ?: "img_${System.currentTimeMillis() % 100000}.jpg"
            pendingDownload = PendingDownload(url, name, null, null)
            closeImagePicker()
        } else {
            batchAskMode = true
        }
    }

    fun startImageBatch(asZip: Boolean) {
        batchAskMode = false
        val urls = pickedImages.filter { it in pickedSet }
        val pageUrl = tabs.firstOrNull { it.id == currentTabId }?.url ?: ""
        closeImagePicker()
        viewModelScope.launch {
            val path = if (asZip) {
                batchImages.downloadAsZip(urls, pageUrl)
            } else {
                batchImages.downloadToFolder(urls, pageUrl)
            }
            batchMessage = if (path != null) "已保存：$path" else "批量下载失败"
        }
    }

    fun consumeBatchMessage() {
        batchMessage = null
    }

    // ---- 广告拦截 / GitHub 辅助 / 夜间 ----

    fun toggleAdBlock() {
        val v = !adBlocker.enabled
        adBlocker.setEnabled(v)
        appContext.getSharedPreferences("browser_settings", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean("adBlock", v).apply()
        logStore.log("广告拦截", if (v) "开启" else "关闭")
    }

    fun loadAdBlockPref() {
        val v = appContext.getSharedPreferences("browser_settings", android.content.Context.MODE_PRIVATE)
            .getBoolean("adBlock", false)
        adBlocker.setEnabled(v)
    }

    /** 阅读模式：当前页提取正文（BR-021）。 */
    fun openReaderMode() {
        val w = webViews.peek(currentTabId ?: return) ?: return
        w.evaluateJavascript(ReaderModeJs.EXTRACT) { raw ->
            readerResult = ReaderModeJs.Result.fromJson(raw ?: "null")
        }
    }

    fun closeReaderMode() {
        readerResult = null
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
