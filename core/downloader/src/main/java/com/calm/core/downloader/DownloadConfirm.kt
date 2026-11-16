package com.calm.core.downloader

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

/**
 * 下载确认弹窗（对应设计方案 5.6：文件名 / 线程数 / 复制链接 / 选择下载器 / 空白关闭）。
 */
@Composable
fun DownloadConfirmDialog(
    url: String,
    suggestedName: String,
    mimeType: String?,
    pageUrl: String?,
    onDismiss: () -> Unit,
    onStart: (name: String, threads: Int) -> Unit,
) {
    var name by remember { mutableStateOf(suggestedName) }
    var threads by remember { mutableIntStateOf(8) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("即将开始下载") },
        text = {
            Column {
                Text(
                    url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = NameUtil.sanitize(it) },
                    label = { Text("文件名") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text("线程数：$threads")
                Slider(
                    value = threads.toFloat(),
                    onValueChange = { threads = it.toInt() },
                    valueRange = 1f..16f,
                    steps = 14,
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(url))
                        Toast.makeText(context, "已复制链接", Toast.LENGTH_SHORT).show()
                    }) { Text("复制链接") }
                    TextButton(onClick = {
                        startSystemDownload(context, url, name, mimeType)
                        onDismiss()
                    }) { Text("用系统下载器") }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onStart(name, threads) }) { Text("开始下载") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 用系统 DownloadManager 下载（DL-006 弹窗内选择下载器）。 */
fun startSystemDownload(context: Context, url: String, fileName: String, mimeType: String?) {
    try {
        val req = DownloadManager.Request(Uri.parse(url))
            .setTitle(fileName)
            .setDescription("Calm 系统下载器")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        if (!mimeType.isNullOrBlank()) req.setMimeType(mimeType)
        context.getSystemService(DownloadManager::class.java).enqueue(req)
        Toast.makeText(context, "已交给系统下载器", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "系统下载器启动失败：${e.message}", Toast.LENGTH_LONG).show()
    }
}
