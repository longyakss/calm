package com.calm.core.filemanager

import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FileManagerScreen(
    vm: FileManagerViewModel = hiltViewModel(),
    onOpenImage: (String) -> Unit = {},
    onOpenText: (String) -> Unit = {},
    onOpenPdf: (String) -> Unit = {},
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<File?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var newFolderDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val storageGranted = remember { Environment.isExternalStorageManager() }

    BackHandler(enabled = true) {
        if (vm.selectionMode) {
            vm.clearSelection()
        } else {
            vm.goUp()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ---- 顶部栏 ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { if (!vm.goUp()) vm.goHome() }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "上级")
            }
            IconButton(onClick = { vm.goHome() }) {
                Icon(Icons.Filled.Home, contentDescription = "根目录")
            }
            Text(
                vm.path.removePrefix("/storage/emulated/0").ifBlank { "根目录" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { newFolderDialog = true }) {
                Icon(Icons.Filled.CreateNewFolder, contentDescription = "新建文件夹")
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "菜单")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("按名称") }, onClick = { vm.setSort(0); menuOpen = false })
                    DropdownMenuItem(text = { Text("按大小") }, onClick = { vm.setSort(1); menuOpen = false })
                    DropdownMenuItem(text = { Text("按时间") }, onClick = { vm.setSort(2); menuOpen = false })
                    DropdownMenuItem(text = { Text("按类型") }, onClick = { vm.setSort(3); menuOpen = false })
                    DropdownMenuItem(
                        text = { Text(if (vm.showHidden) "隐藏隐藏文件" else "显示隐藏文件") },
                        onClick = { vm.toggleHidden(); menuOpen = false },
                    )
                }
            }
        }

        // ---- 类型筛选 ----
        LazyRow(
            modifier = Modifier.padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(listOf<FileKind?>(
                null, FileKind.IMAGE, FileKind.VIDEO, FileKind.AUDIO, FileKind.TEXT, FileKind.PDF, FileKind.ARCHIVE,
            )) { k ->
                FilterChip(
                    selected = vm.filterKind == k,
                    onClick = { vm.setFilter(k) },
                    label = {
                        Text(
                            when (k) {
                                null -> "全部"
                                FileKind.IMAGE -> "图片"
                                FileKind.VIDEO -> "视频"
                                FileKind.AUDIO -> "音频"
                                FileKind.TEXT -> "文档"
                                FileKind.PDF -> "PDF"
                                FileKind.ARCHIVE -> "压缩包"
                                else -> "其他"
                            },
                        )
                    },
                )
            }
        }

        // ---- 无权限提示 ----
        if (!storageGranted) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .clip(RoundedCornerShape(10.dp)),
            ) {
                TextButton(onClick = {
                    try {
                        context.startActivity(
                            Intent(
                                "android.settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION",
                                Uri.parse("package:com.calm.app"),
                            ),
                        )
                    } catch (e: Exception) {
                        context.startActivity(Intent("android.settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION"))
                    }
                }) { Text("需要「所有文件访问」权限才能管理存储，点此授权", color = MaterialTheme.colorScheme.onErrorContainer) }
            }
        }

        // ---- 文件列表 ----
        val shown = vm.entries.filter { searchQuery.isBlank() || it.name.contains(searchQuery, true) }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (vm.selectionMode) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TextButton(onClick = { vm.selectAll() }) { Icon(Icons.Filled.SelectAll, null); Spacer(Modifier.height(0.dp)); Text("全选") }
                        TextButton(onClick = { vm.clearSelection() }) { Text("取消") }
                    }
                }
            }
            items(shown, key = { it.absolutePath }) { f ->
                FileRow(
                    file = f,
                    checked = vm.selectionMode && f.absolutePath in vm.selected.value,
                    onClick = {
                        if (vm.selectionMode) {
                            vm.toggleSelect(f)
                        } else {
                            openFile(f, vm, onOpenImage, onOpenText, onOpenPdf, context)
                        }
                    },
                    onLongClick = { vm.toggleSelect(f) },
                )
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    // ---- 选择操作栏 ----
    if (vm.selectionMode) {
        Surface(
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .clip(RoundedCornerShape(14.dp)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                IconButton(onClick = { vm.copySelection(false) }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "复制")
                }
                IconButton(onClick = { vm.copySelection(true) }) {
                    Icon(Icons.Filled.DriveFileMove, contentDescription = "移动")
                }
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = "删除")
                }
                IconButton(onClick = {
                    shareFiles(context, vm.selected.value.map { File(it) })
                }) {
                    Icon(Icons.Filled.Share, contentDescription = "分享")
                }
                if (vm.selected.value.size == 1) {
                    IconButton(onClick = { renameTarget = File(vm.selected.value.first()) }) {
                        Icon(Icons.Filled.TextSnippet, contentDescription = "重命名")
                    }
                }
            }
        }
    }

    // ---- 粘贴浮动提示 ----
    if (vm.clipboardPaths.isNotEmpty()) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(10.dp)),
        ) {
            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (vm.clipboardIsMove) "剪切 ${vm.clipboardPaths.size} 项" else "复制 ${vm.clipboardPaths.size} 项",
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                TextButton(onClick = { vm.paste() }) { Text("粘贴到这里") }
                IconButton(onClick = { vm.clearClipboard() }) {
                    Icon(Icons.Filled.Close, contentDescription = "取消")
                }
            }
        }
    }

    // ---- 操作进度 ----
    vm.opProgress?.let { p ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("正在处理") },
            text = { Column { Text(p); Spacer(Modifier.height(8.dp)); LinearProgressIndicator(Modifier.fillMaxWidth()) } },
            confirmButton = {},
        )
    }

    // ---- 对话框 ----
    confirmDelete.let {
        if (it) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("删除") },
                text = { Text("确定删除选中的 ${vm.selected.value.size} 项吗？此操作不可恢复。") },
                confirmButton = {
                    TextButton(onClick = { vm.deleteSelection(); confirmDelete = false }) { Text("删除") }
                },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
            )
        }
    }

    renameTarget?.let { f ->
        var name by remember(f.absolutePath) { mutableStateOf(f.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = { vm.rename(f, name); renameTarget = null; vm.clearSelection() }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("取消") } },
        )
    }

    if (newFolderDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newFolderDialog = false },
            title = { Text("新建文件夹") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名称") }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = { if (name.isNotBlank()) vm.createDir(name); newFolderDialog = false }) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = { newFolderDialog = false }) { Text("取消") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    file: File,
    checked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = when (FileTypes.kindOf(file)) {
                FileKind.FOLDER -> Icons.Filled.Folder
                FileKind.IMAGE -> Icons.Filled.Image
                FileKind.VIDEO -> Icons.Filled.VideoFile
                FileKind.AUDIO -> Icons.Filled.MusicNote
                else -> Icons.Filled.TextSnippet
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(0.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(file.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (file.isDirectory) "${file.listFiles()?.size ?: 0} 项" else
                    FileTypes.fmtSize(file.length()) + " · " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(file.lastModified())),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (checked) Checkbox(checked = true, onCheckedChange = { onClick() })
    }
}

private fun openFile(
    f: File,
    vm: FileManagerViewModel,
    onOpenImage: (String) -> Unit,
    onOpenText: (String) -> Unit,
    onOpenPdf: (String) -> Unit,
    context: android.content.Context,
) {
    try {
        when (FileTypes.kindOf(f)) {
            FileKind.FOLDER -> vm.enter(f)
            FileKind.IMAGE -> onOpenImage(f.absolutePath)
            FileKind.TEXT -> onOpenText(f.absolutePath)
            FileKind.PDF -> onOpenPdf(f.absolutePath)
            else -> {
                // 视频 / 音频 → 走系统解析（Calm 播放器注册了对应 intent-filter）；其余交给系统
                val uri = FileProvider.getUriForFile(context, "com.calm.app.fileprovider", f)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, FileTypes.mimeOf(f))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { context.startActivity(intent) }
                    .onFailure {
                        android.widget.Toast.makeText(context, "无法打开：${f.name}", android.widget.Toast.LENGTH_SHORT).show()
                        vm.logError("打开失败：${f.absolutePath} ${it.message}")
                    }
            }
        }
    } catch (e: Exception) {
        // 兜底：任何打开异常都不允许崩溃（只提示 + 记日志）
        android.widget.Toast.makeText(context, "打开出错：${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        vm.logError("打开异常：${f.absolutePath} ${e.message}")
    }
}

private fun shareFiles(context: android.content.Context, files: List<File>) {
    val uris = files.map { FileProvider.getUriForFile(context, "com.calm.app.fileprovider", it) }
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uris[0]) }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply { putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris)) }
    }
    intent.type = "*/*"
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(Intent.createChooser(intent, "分享")) }
}
