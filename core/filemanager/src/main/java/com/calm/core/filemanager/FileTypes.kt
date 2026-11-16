package com.calm.core.filemanager

import java.io.File

enum class FileKind { FOLDER, IMAGE, VIDEO, AUDIO, TEXT, PDF, ARCHIVE, APK, OTHER }

object FileTypes {

    val textExtensions = setOf("txt", "md", "json", "xml", "html", "log", "csv", "kt", "java", "js", "css", "py", "sh")

    fun kindOf(file: File): FileKind {
        if (file.isDirectory) return FileKind.FOLDER
        val ext = file.extension.lowercase()
        return when (ext) {
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic" -> FileKind.IMAGE
            "mp4", "mkv", "webm", "avi", "flv", "ts", "mov", "m4v" -> FileKind.VIDEO
            "mp3", "aac", "flac", "wav", "ogg", "opus", "m4a" -> FileKind.AUDIO
            "pdf" -> FileKind.PDF
            "zip", "rar", "7z", "tar", "gz" -> FileKind.ARCHIVE
            "apk" -> FileKind.APK
            in textExtensions -> FileKind.TEXT
            else -> FileKind.OTHER
        }
    }

    fun mimeOf(file: File): String = when (kindOf(file)) {
        FileKind.IMAGE -> "image/*"
        FileKind.VIDEO -> "video/*"
        FileKind.AUDIO -> "audio/*"
        FileKind.PDF -> "application/pdf"
        FileKind.ARCHIVE -> "application/zip"
        FileKind.APK -> "application/vnd.android.package-archive"
        FileKind.TEXT -> "text/plain"
        else -> "application/octet-stream"
    }

    fun fmtSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / 1048576.0)
        else -> "%.2f GB".format(bytes / 1073741824.0)
    }
}
