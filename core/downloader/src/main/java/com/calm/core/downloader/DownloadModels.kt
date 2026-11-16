package com.calm.core.downloader

import java.util.UUID

/** 下载分类（对应设计方案 16.4：Download 下按类型分目录）。 */
enum class Category(val dir: String, val extensions: Set<String>) {
    PICTURE("Pictures", setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "svg")),
    VIDEO("Videos", setOf("mp4", "mkv", "webm", "avi", "flv", "ts", "mov", "m4v")),
    AUDIO("Music", setOf("mp3", "aac", "flac", "wav", "ogg", "opus", "m4a", "wma")),
    DOCUMENT("Documents", setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "epub", "mobi", "azw3", "umd")),
    ARCHIVE("Archives", setOf("zip", "rar", "7z", "tar", "gz", "apk", "jar"));

    companion object {
        fun fromExtension(ext: String?): Category? =
            ext?.lowercase()?.let { e -> entries.firstOrNull { e in it.extensions } }
    }
}

data class ProgressState(
    val downloaded: Long = 0,
    val total: Long = -1,
    val speed: Long = 0,
)

object DownloadIds {
    fun newId(): String = UUID.randomUUID().toString().replace("-", "").substring(0, 16)
}

/** 从 URL / Content-Type 推断文件名与分类。 */
object NameUtil {

    private val unsafe = Regex("[\\\\/:*?\"<>|]")

    fun sanitize(name: String): String = unsafe.replace(name, "_").take(120).ifBlank { "file" }

    fun fileNameFromUrl(url: String): String? = try {
        val path = url.substringBefore("?").substringAfterLast('/')
        val decoded = java.net.URLDecoder.decode(path, "UTF-8")
        if (decoded.contains('.') && decoded.isNotBlank()) sanitize(decoded) else null
    } catch (_: Exception) {
        null
    }

    fun extensionOf(fileName: String): String? =
        fileName.substringAfterLast('.', "").lowercase().takeIf { it.isNotBlank() && fileName.contains('.') }

    fun categoryOf(fileName: String, mimeType: String?): Category {
        extensionOf(fileName)?.let { ext -> Category.fromExtension(ext)?.let { return it } }
        val mime = mimeType?.lowercase().orEmpty()
        return when {
            mime.startsWith("image/") -> Category.PICTURE
            mime.startsWith("video/") -> Category.VIDEO
            mime.startsWith("audio/") -> Category.AUDIO
            mime.contains("pdf") || mime.contains("document") || mime.contains("text") -> Category.DOCUMENT
            mime.contains("zip") || mime.contains("compressed") || mime.contains("octet-stream") -> Category.ARCHIVE
            else -> Category.ARCHIVE
        }
    }
}
