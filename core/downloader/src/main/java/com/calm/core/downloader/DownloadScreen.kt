package com.calm.core.downloader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calm.data.local.DownloadDao
import com.calm.data.local.DownloadTaskEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val dao: DownloadDao,
    val controller: DownloadController,
) : ViewModel() {

    val tasks = dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun pause(id: String) = controller.pause(id)

    fun resume(id: String) = controller.start(id)

    fun delete(id: String, deleteFile: Boolean) {
        viewModelScope.launch { controller.cancelAndDelete(id, deleteFile) }
    }
}

@Composable
fun DownloadScreen(vm: DownloadViewModel = hiltViewModel()) {
    val tasks by vm.tasks.collectAsState()
    val progress by vm.controller.progress.collectAsState()
    var filter by remember { mutableStateOf("全部") }
    var deleteTarget by remember { mutableStateOf<DownloadTaskEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("全部", "进行中", "已完成", "失败").forEach { f ->
                FilterChip(
                    selected = filter == f,
                    onClick = { filter = f },
                    label = { Text(f) },
                )
            }
        }

        val shown = tasks.filter { t ->
            when (filter) {
                "进行中" -> t.status == "running" || t.status == "pending"
                "已完成" -> t.status == "completed"
                "失败" -> t.status == "failed"
                else -> true
            }
        }

        if (shown.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("暂无下载任务", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(6.dp))
                Text(
                    "浏览器下载的文件会出现在这里",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(shown, key = { it.id }) { t ->
                    val st = progress[t.id]
                    val downloaded = st?.downloaded ?: t.downloadedBytes
                    val total = st?.total ?: t.totalBytes
                    val speed = st?.speed ?: 0
                    Column(modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(t.fileName, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                        Spacer(Modifier.height(4.dp))
                        if (t.status == "running") {
                            if (total > 0) {
                                LinearProgressIndicator(
                                    progress = (downloaded.toFloat() / total).coerceIn(0f, 1f),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                            Spacer(Modifier.height(4.dp))
                            val pct = if (total > 0) " ${(downloaded * 100 / total)}%" else ""
                            Text(
                                "${Fmt.bytes(downloaded)}${if (total > 0) " / " + Fmt.bytes(total) else ""}$pct · ${Fmt.speed(speed)} · ${t.threadCount} 线程",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                when (t.status) {
                                    "completed" -> "已完成 · ${Fmt.bytes(if (total > 0) total else t.downloadedBytes)}"
                                    "failed" -> "失败 · 点击重试"
                                    "paused" -> "已暂停 · ${Fmt.bytes(downloaded)}${if (total > 0) " / " + Fmt.bytes(total) else ""}"
                                    else -> "等待中"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            when (t.status) {
                                "running", "pending" -> IconButton(onClick = { vm.pause(t.id) }) {
                                    Icon(Icons.Filled.Pause, contentDescription = "暂停")
                                }
                                "paused", "failed" -> IconButton(onClick = { vm.resume(t.id) }) {
                                    Icon(
                                        if (t.status == "failed") Icons.Filled.Refresh else Icons.Filled.PlayArrow,
                                        contentDescription = "继续",
                                    )
                                }
                            }
                            IconButton(onClick = { deleteTarget = t }) {
                                Icon(Icons.Filled.Delete, contentDescription = "删除")
                            }
                        }
                    }
                }
            }
        }
    }

    deleteTarget?.let { t ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除任务") },
            text = { Text("是否同时删除已下载的文件「${t.fileName}」？") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(t.id, true)
                    deleteTarget = null
                }) { Text("删除任务和文件") }
            },
            dismissButton = {
                TextButton(onClick = {
                    vm.delete(t.id, false)
                    deleteTarget = null
                }) { Text("仅删除任务") }
            },
        )
    }
}
