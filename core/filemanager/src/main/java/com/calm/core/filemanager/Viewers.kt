package com.calm.core.filemanager

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File
import java.nio.charset.Charset

/** 图片查看器：缩放 / 平移（FM-002）。 */
@Composable
fun ImageViewerScreen(path: String) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.5f, 8f)
                    offset = offset + pan
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = File(path),
            contentDescription = path.substringAfterLast('/'),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
        Text(
            path.substringAfterLast('/'),
            color = Color(0xB3FFFFFF),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(12.dp),
        )
    }
}

/** 文本查看器：大文件截断 + 编码识别（FM-005）。 */
@Composable
fun TextViewerScreen(path: String) {
    var content by remember { mutableStateOf("加载中…") }
    var truncated by remember { mutableStateOf(false) }

    LaunchedEffect(path) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val file = File(path)
            val max = 2L * 1024 * 1024
            val bytes = file.inputStream().use { ins ->
                if (file.length() > max) {
                    truncated = true
                    val buf = ByteArray(max.toInt())
                    ins.read(buf)
                    buf
                } else {
                    ins.readBytes()
                }
            }
            content = decodeText(bytes)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            path.substringAfterLast('/'),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(12.dp),
        )
        if (truncated) {
            Text(
                "文件较大，仅显示前 2MB",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(content.split("\n")) { _, line ->
                Text(
                    line.ifBlank { " " },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                )
            }
        }
    }
}

private fun decodeText(bytes: ByteArray): String = try {
    String(bytes, Charsets.UTF_8)
} catch (_: Exception) {
    String(bytes, Charset.forName("GBK"))
}

/** PDF 查看器：PdfRenderer 顺序渲染（FM-006）。 */
@Composable
fun PdfViewerScreen(path: String) {
    val pages = remember { androidx.compose.runtime.mutableStateListOf<Bitmap>() }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(path) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        for (i in 0 until renderer.pageCount) {
                            val page = renderer.openPage(i)
                            val bmp = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            page.close()
                            pages.add(bmp)
                        }
                    }
                }
            } catch (e: SecurityException) {
                error = "PDF 已加密，暂不支持密码查看"
            } catch (e: Exception) {
                error = "无法打开 PDF：${e.message}"
            }
        }
    }

    if (error != null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(error!!, color = MaterialTheme.colorScheme.error)
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {
        itemsIndexed(pages) { index, bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "第 ${index + 1} 页",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp, horizontal = 8.dp),
            )
        }
        if (pages.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Text("正在渲染…")
                }
            }
        }
    }
}
