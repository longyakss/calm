package com.calm.core.downloader

import android.content.Context
import android.os.Environment
import com.calm.data.local.DownloadDao
import com.calm.data.local.DownloadTaskEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            if (cont.isActive) cont.resume(response)
            else response.close()
        }

        override fun onFailure(call: Call, e: IOException) {
            if (cont.isActive) cont.resumeWithException(e)
        }
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}

/** 下载控制器：多线程分段 + 断点续传 + 自动分类（对应设计方案 8.4）。 */
@javax.inject.Singleton
class DownloadController @javax.inject.Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val dao: DownloadDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val jobs = ConcurrentHashMap<String, Job>()

    private val _progress = MutableStateFlow<Map<String, ProgressState>>(emptyMap())
    val progress: StateFlow<Map<String, ProgressState>> = _progress

    private val _events = MutableStateFlow<String?>(null)
    val events: StateFlow<String?> = _events

    suspend fun enqueue(url: String, fileName: String, threadCount: Int, mimeType: String?, pageUrl: String?): String {
        val id = DownloadIds.newId()
        val safeName = NameUtil.sanitize(fileName)
        val category = NameUtil.categoryOf(safeName, mimeType)
        val dir = saveDir(category)
        dir.mkdirs()
        val task = DownloadTaskEntity(
            id = id,
            url = url,
            fileName = dedupeName(dir, safeName),
            savePath = dir.absolutePath,
            mimeType = mimeType.orEmpty(),
            category = category.dir,
            pageUrl = pageUrl,
            totalBytes = -1,
            downloadedBytes = 0,
            threadCount = threadCount.coerceIn(1, 16),
            chunkState = (1..threadCount.coerceIn(1, 16)).joinToString(",") { "0" },
            speedLimit = 0,
            status = "pending",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        dao.insert(task)
        start(id)
        return id
    }

    fun start(id: String) {
        if (jobs[id]?.isActive == true) return
        jobs[id] = scope.launch { runTask(id) }
    }

    fun pause(id: String) {
        scope.launch { jobs[id]?.cancelAndJoin() }
    }

    fun cancelAndDelete(id: String, deleteFile: Boolean) {
        scope.launch {
            jobs[id]?.cancelAndJoin()
            val task = dao.get(id) ?: return@launch
            if (deleteFile) {
                File(task.savePath, task.fileName).delete()
                File(task.savePath, task.fileName + ".part").delete()
            }
            dao.delete(id)
        }
    }

    suspend fun saveDir(category: Category): File {
        val base = if (Environment.isExternalStorageManager()) {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), category.dir)
        } else {
            File(context.getExternalFilesDir(null), "Download/${category.dir}")
        }
        return base
    }

    private fun dedupeName(dir: File, name: String): String {
        var candidate = name
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (File(dir, candidate).exists() || File(dir, "$candidate.part").exists()) {
            candidate = "$stem($i)$ext"
            i++
        }
        return candidate
    }

    private suspend fun runTask(id: String) {
        try {
            val task = dao.get(id) ?: return
            val part = File(task.savePath, task.fileName + ".part")
            val chunks = task.chunkState.split(',').map { it.toLong() }.toLongArray()

            dao.updateStatus(id, "running")

            var request = Request.Builder().url(task.url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
                .build()
            val head = withContext(Dispatchers.IO) { client.newCall(request).await() }
            val total = head.header("Content-Length")?.toLongOrNull() ?: -1L
            val acceptsRange = head.header("Accept-Ranges")?.equals("bytes", ignoreCase = true) == true && total > 0
            head.close()
            if (total >= 0) dao.updateTotal(id, total)

            var downloaded = 0L
            if (!acceptsRange) {
                downloaded = downloadSingle(id, task, part)
            } else {
                downloaded = downloadChunked(id, task, part, chunks, total)
            }

            // 完成校验：长度一致
            if (total > 0 && part.length() != total) {
                dao.updateStatus(id, "failed")
                _events.value = "文件校验失败：${task.fileName}"
                return
            }
            val final = File(task.savePath, task.fileName)
            if (final.exists()) final.delete()
            if (!part.renameTo(final)) {
                part.copyTo(final, overwrite = true)
                part.delete()
            }
            dao.updateStatus(id, "completed")
            dao.updateProgress(id, part.length(), chunks.joinToString(","))
            _events.value = "下载完成：${task.fileName}"
        } catch (e: CancellationException) {
            try {
                val t = dao.get(id)
                if (t != null && t.status != "completed") dao.updateStatus(id, "paused")
            } catch (_: Exception) {}
            throw e
        } catch (e: Exception) {
            try {
                dao.updateStatus(id, "failed")
                _events.value = "下载失败：${e.message ?: "网络错误"}"
            } catch (_: Exception) {}
        } finally {
            _progress.value = _progress.value - id
        }
    }

    private suspend fun downloadSingle(id: String, task: DownloadTaskEntity, part: File): Long {
        val req = Request.Builder().url(task.url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
            .build()
        var downloaded = 0L
        client.newCall(req).await().body.byteStream().use { input ->
            part.outputStream().use { out ->
                val buf = ByteArray(DEFAULT_BUF)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    downloaded += n
                    tick(id, downloaded, task.totalBytes)
                }
            }
        }
        return downloaded
    }

    private suspend fun downloadChunked(
        id: String,
        task: DownloadTaskEntity,
        part: File,
        chunks: LongArray,
        total: Long,
    ): Long {
        val n = task.threadCount.coerceIn(1, 16).coerceAtMost(chunks.size)
        val base = total / n
        kotlinx.coroutines.coroutineScope {
            (0 until n).map { i ->
                launch(Dispatchers.IO) {
                    val segStart = i * base
                    val segEnd = if (i == n - 1) total else (i + 1) * base
                    var pos = segStart + chunks[i]
                    if (pos >= segEnd) return@launch
                    val req = Request.Builder().url(task.url)
                        .header("Range", "bytes=$pos-${segEnd - 1}")
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
                        .build()
                    client.newCall(req).await().body.byteStream().use { input ->
                        RandomAccessFile(part, "rwd").use { raf ->
                            raf.seek(pos)
                            val buf = ByteArray(DEFAULT_BUF)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val read = input.read(buf)
                                if (read < 0) break
                                raf.write(buf, 0, read)
                                pos += read
                                chunks[i] = pos - segStart
                                tick(id, chunks.sum(), total, persistChunks = chunks)
                            }
                        }
                    }
                }
            }
        }.join()
        return chunks.sum()
    }

    // 进度节流：300ms UI 更新 / 1s 数据库落盘（对应设计方案 8.4）
    private var lastTickAt = 0L
    private var lastPersistAt = 0L
    private var lastBytes = 0L
    private var lastSpeedBytes = 0L
    private var speed = 0L
    private var persistChunks: LongArray? = null

    private suspend fun tick(id: String, downloaded: Long, total: Long, persistChunks: LongArray? = null) {
        if (persistChunks != null) this.persistChunks = persistChunks
        val now = System.currentTimeMillis()
        if (now - lastTickAt >= 300) {
            val dt = now - lastTickAt
            if (dt in 1..5000) {
                speed = (downloaded - lastSpeedBytes) * 1000 / dt
                lastSpeedBytes = downloaded
            }
            lastTickAt = now
            _progress.value = _progress.value + (id to ProgressState(downloaded, total, speed))
        }
        if (now - lastPersistAt >= 1000) {
            lastPersistAt = now
            lastBytes = downloaded
            val cs = this.persistChunks
            dao.updateProgress(id, downloaded, cs?.joinToString(",") ?: "")
        }
    }

    companion object {
        private const val DEFAULT_BUF = 64 * 1024
        const val MAX_THREADS = 16
    }
}
