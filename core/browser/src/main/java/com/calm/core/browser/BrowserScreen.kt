package com.calm.core.browser

import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.calm.data.local.BookmarkEntity
import com.calm.data.local.HistoryEntity

@Composable
fun BrowserScreen(vm: BrowserViewModel = hiltViewModel(), onOpenSettings: () -> Unit = {}) {
    val currentId = vm.currentTabId ?: return
    val currentTab = vm.tabs.firstOrNull { it.id == currentId } ?: return
    val bookmarks by vm.bookmarks.collectAsState(initial = emptyList())
    val history by vm.history.collectAsState(initial = emptyList())
    val clipboard = LocalClipboardManager.current

    var menuOpen by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<String?>(null) }
    var clipboardUrl by remember { mutableStateOf<String?>(null) }

    // 返回键：WebView 后退优先（BR-004）
    BackHandler(enabled = (vm.webViews.peek(currentId)?.canGoBack() == true)) {
        vm.goBack()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ---- 工具栏 ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vm.goBack() }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "后退")
            }
            IconButton(onClick = { vm.goForward() }) {
                Icon(Icons.Filled.ArrowForward, contentDescription = "前进")
            }
            OutlinedTextField(
                value = vm.urlDraft,
                onValueChange = { vm.urlDraft = it },
                placeholder = { Text("搜索或输入网址", style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .onFocusChanged { vm.editing = it.isFocused },
                trailingIcon = {
                    if (currentTab.url.startsWith("http")) {
                        val marked = bookmarks.any { it.url == currentTab.url }
                        IconButton(onClick = { vm.toggleBookmark() }) {
                            Icon(
                                if (marked) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = "书签",
                            )
                        }
                    }
                },
            )
            IconButton(onClick = { vm.reload() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "刷新")
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "菜单")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("标签页 (${vm.tabs.size})") },
                        onClick = { menuOpen = false; sheet = "tabs" },
                    )
                    DropdownMenuItem(
                        text = { Text("书签") },
                        onClick = { menuOpen = false; sheet = "bookmarks" },
                    )
                    DropdownMenuItem(
                        text = { Text("历史") },
                        onClick = { menuOpen = false; sheet = "history" },
                    )
                    DropdownMenuItem(
                        text = { Text("资源嗅探 (${vm.resources.size})") },
                        onClick = { menuOpen = false; sheet = "resources" },
                        trailingIcon = {
                            Switch(checked = vm.sniffEnabled, onCheckedChange = { vm.sniffEnabled = it })
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("夜间模式") },
                        onClick = { vm.applyNightMode(!vm.nightMode); menuOpen = false },
                        trailingIcon = { Switch(checked = vm.nightMode, onCheckedChange = { vm.applyNightMode(it) }) },
                    )
                    DropdownMenuItem(
                        text = { Text("新建标签页") },
                        onClick = { vm.newTab(null); menuOpen = false },
                        leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    )
                    DropdownMenuItem(
                        text = { Text("设置") },
                        onClick = { menuOpen = false; onOpenSettings() },
                    )
                }
            }
        }

        // ---- 加载进度 ----
        if (vm.loading) {
            LinearProgressIndicator(
                progress = vm.progress / 100f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
            )
        }

        // ---- 剪贴板检测（SB-001~006：点击搜索栏时） ----
        if (vm.editing && clipboardUrl == null) {
            val clip = clipboard.getText()?.toString()
            val url = clip?.let { if (UrlUtil.isUrl(it.trim())) it.trim() else UrlUtil.firstUrlIn(it) }
            if (url != null && url != currentTab.url) clipboardUrl = url
        }
        clipboardUrl?.let { url ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "检测到链接：$url",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { vm.loadUrl(currentId, url); clipboardUrl = null }) { Text("打开") }
                TextButton(onClick = { clipboardUrl = null }) { Text("忽略") }
            }
        }

        // ---- 引擎栏（SB-007/008） ----
        if (vm.editing) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(presetEngines.size) { idx ->
                    FilterChip(
                        selected = vm.engineIndex == idx,
                        onClick = {
                            val q = vm.urlDraft.trim()
                            if (q.isNotEmpty()) vm.searchWith(idx, q)
                        },
                        label = { Text(presetEngines[idx].name) },
                    )
                }
            }
        }

        // ---- 内容区 ----
        if (currentTab.url == "calm://home") {
            BrowserHome(
                history = history.take(8),
                onOpen = { vm.loadUrl(currentId, it) },
                onNewTab = { vm.newTab(it) },
            )
        } else {
            key(currentTab.id) {
                AndroidView(
                    factory = { ctx ->
                        val w = vm.webViews.get(currentTab.id)
                        configureWebView(w, currentTab.id, vm)
                        w
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    // ---- 标签页 sheet ----
    if (sheet == "tabs") {
        ModalBottomSheet(onDismissRequest = { sheet = null }) {
            Text(
                "标签页 (${vm.tabs.size}/10)",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            vm.tabs.forEach { tab ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vm.selectTab(tab.id); sheet = null }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        tab.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                        color = if (tab.id == vm.currentTabId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                    IconButton(onClick = { vm.closeTab(tab.id) }) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭")
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                TextButton(onClick = { vm.newTab(null); sheet = null }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.size(4.dp))
                    Text("新建标签页")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // ---- 书签 sheet ----
    if (sheet == "bookmarks") {
        ModalBottomSheet(onDismissRequest = { sheet = null }) {
            Text(
                "书签 (${bookmarks.size})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (bookmarks.isEmpty()) {
                Text("暂无书签", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LazyColumn(modifier = Modifier.height(360.dp)) {
                items(bookmarks, key = { it.id }) { b ->
                    BookmarkRow(b, onClick = {
                        val id = vm.currentTabId
                        if (id != null) vm.loadUrl(id, b.url)
                        sheet = null
                    }) {
                        vm.deleteBookmark(b.id)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // ---- 历史 sheet ----
    if (sheet == "history") {
        ModalBottomSheet(onDismissRequest = { sheet = null }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("历史记录", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.clearHistory() }) { Text("清空") }
            }
            if (history.isEmpty()) {
                Text("暂无记录", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LazyColumn(modifier = Modifier.height(360.dp)) {
                items(history, key = { it.id }) { h ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val id = vm.currentTabId
                                if (id != null) vm.loadUrl(id, h.url)
                                sheet = null
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(h.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            UrlUtil.hostOf(h.url),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // ---- 资源嗅探 sheet ----
    if (sheet == "resources") {
        ModalBottomSheet(onDismissRequest = { sheet = null }) {
            ResourcePanel(vm)
            Spacer(Modifier.height(24.dp))
        }
    }

    // ---- 下载确认弹窗（对应设计方案 5.6） ----
    vm.pendingDownload?.let { pd ->
        com.calm.core.downloader.DownloadConfirmDialog(
            url = pd.url,
            suggestedName = pd.name,
            mimeType = pd.mime,
            pageUrl = pd.pageUrl,
            onDismiss = { vm.pendingDownload = null },
            onStart = { name, threads -> vm.confirmDownload(name, threads) },
        )
    }
}

@Composable
private fun BookmarkRow(b: BookmarkEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(b.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                UrlUtil.hostOf(b.url),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "删除") }
    }
}

@Composable
private fun ResourcePanel(vm: BrowserViewModel) {
    val groups = vm.resources.values.groupBy { it.type }
    var tabIndex by remember { mutableStateOf(0) }
    val categories = listOf("图片", "音频", "视频", "链接")
    val keys = listOf("image", "audio", "video", "link")
    val selected = groups[keys[tabIndex]].orEmpty()

    Column {
        TabRow(selectedTabIndex = tabIndex) {
            categories.forEachIndexed { i, label ->
                Tab(
                    selected = tabIndex == i,
                    onClick = { tabIndex = i },
                    text = { Text("$label(${groups[keys[i]]?.size ?: 0})") },
                )
            }
        }
        if (selected.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                Text("暂无嗅探结果", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier = Modifier.height(320.dp)) {
                items(selected, key = { it.url }) { r ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            r.url,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { vm.sniffDownload(r) }) {
                            Icon(Icons.Filled.Download, contentDescription = "下载")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowserHome(history: List<HistoryEntity>, onOpen: (String) -> Unit, onNewTab: (String?) -> Unit) {
    val quickLinks = listOf(
        "百度" to "https://www.baidu.com",
        "B站" to "https://www.bilibili.com",
        "知乎" to "https://www.zhihu.com",
        "微博" to "https://weibo.com",
        "GitHub" to "https://github.com",
        "必应" to "https://www.bing.com",
    )
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("快速访问", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.height(160.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(quickLinks) { (name, url) ->
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onOpen(url) }
                        .padding(vertical = 18.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Filled.Home, contentDescription = name)
                    Spacer(Modifier.height(6.dp))
                    Text(name, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("最近访问", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        LazyColumn {
            items(history, key = { it.id }) { h ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(h.url) }
                        .padding(vertical = 8.dp),
                ) {
                    Text(h.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        UrlUtil.hostOf(h.url),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 挂载 WebViewClient / ChromeClient / 嗅探桥 / DownloadListener（BR-008）。 */
private fun configureWebView(w: WebView, tabId: String, vm: BrowserViewModel) {
    if (w.tag == "configured") return
    w.tag = "configured"
    w.addJavascriptInterface(
        object {
            @android.webkit.JavascriptInterface
            fun add(type: String, url: String) {
                if (type == "media") vm.addSniffed(url, null)
            }
        },
        "CalmSniffer",
    )
    w.webViewClient = object : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            val url = request.url.toString()
            val mime = request.requestHeaders["Accept"]
            vm.addSniffed(url, mime)
            return null
        }

        override fun onPageFinished(view: WebView, url: String) {
            vm.onUrlChanged(tabId, url)
            vm.recordHistory(tabId, url, view.title ?: "")
            if (vm.nightMode) view.evaluateJavascript(NIGHT_CSS_JS, null)
            view.evaluateJavascript(
                "(function(){try{['video','audio'].forEach(function(t){document.querySelectorAll(t).forEach(function(m){" +
                    "var s=m.currentSrc||m.src||((m.querySelector('source')||{}).src||'');if(s)CalmSniffer.add('media',s)})})}catch(e){}})()",
                null,
            )
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val scheme = request.url.scheme ?: return false
            if (scheme != "http" && scheme != "https") {
                // 跳转 App 的 scheme 交给系统（BR-033）
                try {
                    val ctx = view.context
                    ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, request.url))
                } catch (_: Exception) {}
                return true
            }
            return false
        }
    }
    w.webChromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            vm.onProgress(tabId, newProgress)
        }

        override fun onReceivedTitle(view: WebView, title: String) {
            vm.onTitle(tabId, title)
        }
    }
    w.setDownloadListener { url, _, disposition, mime, _ ->
        vm.onDownloadRequested(url, disposition, mime, w.url)
    }
}
