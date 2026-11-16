package com.calm.core.browser

import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.calm.data.local.BookmarkEntity
import com.calm.data.local.HistoryEntity

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BrowserScreen(vm: BrowserViewModel = hiltViewModel(), onOpenSettings: () -> Unit = {}) {
    val currentId = vm.currentTabId ?: return
    val currentTab = vm.tabs.firstOrNull { it.id == currentId } ?: return
    val bookmarks by vm.bookmarks.collectAsState(initial = emptyList())
    val homeBookmarks by vm.homeBookmarks.collectAsState()
    val history by vm.history.collectAsState(initial = emptyList())
    val clipboard = LocalClipboardManager.current
    val batchProgress by vm.batchImages.progress.collectAsState()

    var menuOpen by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<String?>(null) }
    var clipboardUrl by remember { mutableStateOf<String?>(null) }

    // 返回键：图片选择页 > WebView 后退
    BackHandler(enabled = vm.imagePickerMode) { vm.closeImagePicker() }
    BackHandler(enabled = !vm.imagePickerMode && (vm.webViews.peek(currentId)?.canGoBack() == true)) {
        vm.goBack()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ---- 工具栏：后退 / 主页 / 网页名 / 标签页 / 二级菜单 ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vm.goBack() }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "后退")
            }
            IconButton(onClick = { vm.goHome() }) {
                Icon(Icons.Filled.Home, contentDescription = "主页")
            }
            // 网页名：点击进入编辑（网址/搜索 + 引擎栏 + 剪贴板检测）
            if (vm.editing) {
                OutlinedTextField(
                    value = vm.urlDraft,
                    onValueChange = { vm.urlDraft = it },
                    placeholder = { Text("搜索或输入网址", style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .onFocusChanged { state ->
                            if (!state.isFocused && vm.urlDraft.isBlank()) vm.editing = false
                        },
                )
            } else {
                Text(
                    text = currentTab.title.ifBlank { "搜索或输入网址" },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            vm.editing = true
                            vm.urlDraft = if (currentTab.url == HOME_URL) "" else currentTab.url
                        }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    color = if (currentTab.url == HOME_URL) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
            }
            // 标签页：一级菜单入口
            IconButton(onClick = { sheet = "tabs" }) {
                BadgedBox(badge = {
                    if (vm.tabs.size > 1) Badge { Text("${vm.tabs.size}") }
                }) {
                    Icon(Icons.Filled.Tab, contentDescription = "标签页")
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "菜单")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("添加书签") },
                        onClick = { vm.openBookmarkDialog(); menuOpen = false },
                        leadingIcon = { Icon(Icons.Filled.BookmarkBorder, contentDescription = null) },
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
                        text = { Text("阅读模式") },
                        onClick = { vm.openReaderMode(); menuOpen = false },
                    )
                    DropdownMenuItem(
                        text = { Text("广告拦截（已拦 ${vm.adBlocker.blockedCount}）") },
                        onClick = { vm.toggleAdBlock(); menuOpen = false },
                        trailingIcon = { Switch(checked = vm.adBlocker.enabled, onCheckedChange = { vm.toggleAdBlock(); menuOpen = false }) },
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

        // ---- 剪贴板检测（SB-001~006：编辑时） ----
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
        if (currentTab.url == HOME_URL) {
            BrowserHome(
                history = history.take(8),
                homeBookmarks = homeBookmarks,
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

    // ---- 书签添加/编辑弹窗（网页名 / 网址 / 目录 / 加入主页） ----
    vm.pendingBookmark?.let { draft ->
        var title by remember(draft.url) { mutableStateOf(draft.title) }
        var folder by remember(draft.url) { mutableStateOf(draft.folder) }
        var inHome by remember(draft.url) { mutableStateOf(draft.inHome) }
        AlertDialog(
            onDismissRequest = { vm.pendingBookmark = null },
            title = { Text("添加书签") },
            text = {
                Column {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("网页名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = draft.url,
                        onValueChange = {},
                        label = { Text("网址") },
                        singleLine = true,
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = folder,
                        onValueChange = { folder = it },
                        label = { Text("书签目录") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("添加到主页", modifier = Modifier.weight(1f))
                        Switch(checked = inHome, onCheckedChange = { inHome = it })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.saveBookmark(draft.copy(title = title, folder = folder, inHome = inHome)) }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { vm.pendingBookmark = null }) { Text("取消") } },
        )
    }

    // ---- 标签页 sheet ----
    if (sheet == "tabs") {
        ModalBottomSheet(onDismissRequest = { sheet = null }) {
            Text(
                "标签页 (${vm.tabs.size})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn(modifier = Modifier.height(420.dp)) {
                items(vm.tabs, key = { it.id }) { tab ->
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

    // ---- 阅读模式面板（BR-021：首行缩进 + 保留配图）----
    vm.readerResult?.let { result ->
        ModalBottomSheet(onDismissRequest = { vm.closeReaderMode() }) {
            if (!result.ok) {
                Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    Text("未能识别正文，可尝试手动选区（后续版本）", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Text(
                    result.title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
                LazyColumn(modifier = Modifier.fillMaxWidth().height(560.dp)) {
                    items(result.items) { item ->
                        when (item) {
                            is ReaderModeJs.Item.Text -> Text(
                                "　　" + item.value, // 首行缩进（两字符）
                                style = MaterialTheme.typography.bodyLarge,
                                lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.6f,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 6.dp),
                            )
                            is ReaderModeJs.Item.Image -> AsyncImage(
                                model = item.url,
                                contentDescription = "文章配图",
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 6.dp),
                            )
                        }
                    }
                    item { Spacer(Modifier.height(32.dp)) }
                }
            }
        }
    }

    // ---- 图片批量选择页（BR-010：长按图片打开；点选/滑动批量）----
    if (vm.imagePickerMode) {
        ImagePickerOverlay(vm, batchProgress)
    }

    // ---- 多图下载问询（BR-011：压缩包 or 文件夹）----
    if (vm.batchAskMode) {
        AlertDialog(
            onDismissRequest = { vm.batchAskMode = false },
            title = { Text("批量下载 ${vm.pickedCount} 张图片") },
            text = { Text("选择保存方式：") },
            confirmButton = {
                TextButton(onClick = { vm.startImageBatch(asZip = true) }) { Text("打包为 ZIP") }
            },
            dismissButton = {
                TextButton(onClick = { vm.startImageBatch(asZip = false) }) { Text("新建文件夹保存") }
            },
        )
    }

    // ---- 批量结果提示 ----
    vm.batchMessage?.let { msg ->
        LaunchedEffect(msg) {
            vm.consumeBatchMessage()
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

/** 图片批量选择：点选 + 在图片上滑动批量选择；顶部显示已选数量与下载按钮。 */
@Composable
private fun ImagePickerOverlay(vm: BrowserViewModel, batchProgress: Pair<Int, Int>?) {
    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { vm.closeImagePicker() }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                }
                Text("本页图片 (${vm.pickedImages.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.selectAllPicked() }) {
                    Icon(Icons.Filled.SelectAll, contentDescription = null)
                    Text("全选")
                }
                IconButton(onClick = { vm.onPickDownloadClicked() }, enabled = vm.pickedCount > 0) {
                    Icon(Icons.Filled.Download, contentDescription = "下载所选")
                }
            }
            Text(
                "已选 ${vm.pickedCount} 张 · 点击选择，滑动可批量选",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            batchProgress?.let { (done, total) ->
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text("下载中 $done/$total", style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(
                        progress = if (total == 0) 0f else done.toFloat() / total,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (vm.pickedImages.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("本页未嗅探到图片", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val cellW = maxWidth / 3
                    val cellH = cellW
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .pointerInput(vm.pickedImages) {
                                detectDragGestures(
                                    onDragStart = { offset -> pickCellAt(vm, offset.x, offset.y + scrollState.value, cellW.toPx(), cellH.toPx(), vm.pickedImages.size) },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        pickCellAt(vm, change.position.x, change.position.y + scrollState.value, cellW.toPx(), cellH.toPx(), vm.pickedImages.size)
                                    },
                                )
                            },
                    ) {
                        val rows = (vm.pickedImages.size + 2) / 3
                        repeat(rows) { row ->
                            Row {
                                repeat(3) { col ->
                                    val index = row * 3 + col
                                    if (index < vm.pickedImages.size) {
                                        val url = vm.pickedImages[index]
                                        val selected = url in vm.pickedSet
                                        Box(
                                            modifier = Modifier
                                                .size(cellW, cellH)
                                                .padding(1.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .combinedClickable(
                                                    onClick = { vm.togglePick(url) },
                                                    onLongClick = { vm.togglePick(url) },
                                                ),
                                        ) {
                                            AsyncImage(
                                                model = url,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                            if (selected) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(Color(0x33000000)),
                                                )
                                                Icon(
                                                    Icons.Filled.Check,
                                                    contentDescription = "已选",
                                                    tint = Color.White,
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(4.dp)
                                                        .background(Color(0x88000000), RoundedCornerShape(50)),
                                                )
                                            }
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.size(cellW, cellH))
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(60.dp))
                    }
                }
            }
        }
    }
}

private fun pickCellAt(vm: BrowserViewModel, x: Float, yScrollAdjusted: Float, cellW: Float, cellH: Float, total: Int) {
    if (cellW <= 0f || cellH <= 0f) return
    val col = (x / cellW).toInt().coerceIn(0, 2)
    val row = (yScrollAdjusted / cellH).toInt()
    val index = row * 3 + col
    if (index in 0 until total) {
        vm.pickRange(vm.pickedImages[index])
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ResourcePanel(vm: BrowserViewModel) {
    val groups = vm.resources.values.groupBy { it.type }
    var tabIndex by remember { mutableStateOf(0) }
    val categories = listOf("图片", "音频", "视频", "链接")
    val keys = listOf("image", "audio", "video", "link")
    val selected = groups[keys[tabIndex]].orEmpty()

    Column {
        TabHeader(
            categories = categories,
            keys = keys,
            groups = groups,
            tabIndex = tabIndex,
            onTab = { tabIndex = it },
            onPickImages = { vm.openImagePicker() },
        )
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
private fun TabHeader(
    categories: List<String>,
    keys: List<String>,
    groups: Map<String, List<SniffedResource>>,
    tabIndex: Int,
    onTab: (Int) -> Unit,
    onPickImages: () -> Unit,
) {
    androidx.compose.material3.TabRow(selectedTabIndex = tabIndex) {
        categories.forEachIndexed { i, label ->
            androidx.compose.material3.Tab(
                selected = tabIndex == i,
                onClick = { onTab(i) },
                text = { Text("$label(${groups[keys[i]]?.size ?: 0})") },
            )
        }
    }
    if (tabIndex == 0) {
        TextButton(
            onClick = onPickImages,
            modifier = Modifier.padding(horizontal = 12.dp),
        ) { Text("批量选择图片（长按页面图片也可打开）→") }
    }
}

@Composable
private fun BrowserHome(
    history: List<HistoryEntity>,
    homeBookmarks: List<BookmarkEntity>,
    onOpen: (String) -> Unit,
    onNewTab: (String?) -> Unit,
) {
    val quickLinks = listOf(
        "百度" to "https://www.baidu.com",
        "B站" to "https://www.bilibili.com",
        "知乎" to "https://www.zhihu.com",
        "微博" to "https://weibo.com",
        "GitHub" to "https://github.com",
        "必应" to "https://www.bing.com",
        "淘宝" to "https://www.taobao.com",
        "京东" to "https://www.jd.com",
        "抖音" to "https://www.douyin.com",
        "CSDN" to "https://www.csdn.net",
        "少数派" to "https://sspai.com",
        "36氪" to "https://www.36kr.com",
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("快速访问", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.height(((quickLinks.size + 3) / 4 * 96).dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(quickLinks) { (name, url) ->
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onOpen(url) }
                        .padding(vertical = 14.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        name.take(2),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(name, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (homeBookmarks.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("主页书签", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            homeBookmarks.forEach { b ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(b.url) }
                        .padding(vertical = 8.dp),
                ) {
                    Text(b.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        UrlUtil.hostOf(b.url),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("最近访问", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        history.forEach { h ->
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

/** 挂载 WebViewClient / ChromeClient / 嗅探桥 / DownloadListener（BR-008）。 */
private fun configureWebView(w: WebView, tabId: String, vm: BrowserViewModel) {
    if (w.tag == "configured") return
    w.tag = "configured"
    vm.loadAdBlockPref()
    w.addJavascriptInterface(
        object {
            @android.webkit.JavascriptInterface
            fun add(type: String, url: String) {
                if (type == "media") vm.addSniffed(url, null)
            }
        },
        "CalmSniffer",
    )
    // 长按图片 → 打开本页图片批量选择页（BR-010）
    w.setOnLongClickListener {
        val hr = w.hitTestResult
        if (hr.type == WebView.HitTestResult.IMAGE_TYPE || hr.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
            vm.openImagePicker()
            true
        } else {
            false
        }
    }
    w.webViewClient = object : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            // 广告拦截优先（BR-017）
            vm.adBlocker.intercept(view, request)?.let { return it }
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
            val url = request.url.toString()
            // GitHub 辅助访问（BR-028）：文件类资源走镜像
            if (vm.github.isAcceleratable(url)) {
                view.loadUrl(vm.github.proxied(url))
                return true
            }
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
