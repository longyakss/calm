package com.calm.core.browser

import android.content.Context
import com.calm.data.local.LogStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 图片批量下载（BR-011）：多图 → 新建文件夹保存，或问询后打包为 ZIP。
 * 顺序下载（图片体积小，无需多线程），完成后写日志并通知。
 */
@javax.inject.Singleton
class BatchImageDownloader @javax.inject.Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val controller: com.calm.core.downloader.DownloadController,
    private val logStore: LogStore,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val _progress = MutableStateFlow<Pair<Int, Int>?>(null)
    val progress: StateFlow<Pair<Int, Int>?> = _progress

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private fun nameFor(url: String, index: Int): String {
        val ext = NameUtil.extensionOf(url.substringBefore('?').substringAfterLast('/')) ?: "jpg"
        return "%03d.%s".format(index + 1, ext)
    }

    private fun hostTag(pageUrl: String): String =
        UrlUtil.hostOf(pageUrl).replace(Regex("[^\\w.]"), "_").ifBlank { "images" }

    private fun downloadOne(url: String, target: File): Boolean = runCatching {
        client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) return false
            resp.body!!.byteStream().use { ins ->
                target.outputStream().use { ins.copyTo(it) }
            }
        }
        true
    }.getOrDefault(false)

    /** 多图 → 新建文件夹逐一下载（DL/BR-011 不压缩方案）。 */
    suspend fun downloadToFolder(urls: List<String>, pageUrl: String): String? = withContext(Dispatchers.IO) {
        val dir = File(controller.saveDir(com.calm.core.downloader.Category.PICTURE), "${hostTag(pageUrl)}_${System.currentTimeMillis() % 100000}")
        runBatch(urls, dir) { i, url -> downloadOne(url, File(dir, nameFor(url, i))) }
        dir.absolutePath
    }

    /** 多图 → 下载到缓存后打包 ZIP。 */
    suspend fun downloadAsZip(urls: List<String>, pageUrl: String): String? = withContext(Dispatchers.IO) {
        val tmp = File(context.cacheDir, "img_${System.currentTimeMillis()}").apply { mkdirs() }
        val ok = runBatch(urls, tmp) { i, url -> downloadOne(url, File(tmp, nameFor(url, i))) }
        if (!ok) return@withContext null
        val zipFile = File(controller.saveDir(com.calm.core.downloader.Category.PICTURE), "${hostTag(pageUrl)}_${System.currentTimeMillis() % 100000}.zip")
        runCatching {
            ZipOutputStream(zipFile.outputStream().buffered()).use { zip ->
                tmp.listFiles()?.sortedBy { it.name }?.forEach { f ->
                    zip.putNextEntry(ZipEntry(f.name))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }.getOrElse { return@withContext null }
        tmp.deleteRecursively()
        zipFile.absolutePath
    }

    /** 返回是否全部成功；过程中更新 progress / message / 日志。 */
    private suspend fun runBatch(urls: List<String>, dir: File, block: suspend (Int, String) -> Boolean): Boolean {
        dir.mkdirs()
        var okCount = 0
        urls.forEachIndexed { i, url ->
            _progress.value = i to urls.size
            val ok = block(i, url)
            if (ok) okCount++
        }
        _progress.value = null
        val msg = "批量图片：成功 $okCount/${urls.size}"
        _message.value = msg
        logStore.log("嗅探", msg)
        return okCount == urls.size || okCount > 0
    }
}
