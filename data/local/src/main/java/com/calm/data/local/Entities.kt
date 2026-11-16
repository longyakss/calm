package com.calm.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "download_task")
data class DownloadTaskEntity(
    @PrimaryKey val id: String,
    val url: String,
    val fileName: String,
    val savePath: String,
    val mimeType: String,
    val category: String,
    val pageUrl: String?,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val threadCount: Int,
    /** 每个分段已下载字节数，逗号分隔（断点续传用） */
    val chunkState: String,
    val speedLimit: Long,
    /** pending / running / paused / completed / failed */
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "bookmark")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val folder: String,
    val createdAt: Long,
)

@Entity(tableName = "web_history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val visitedAt: Long,
)

@Entity(tableName = "book")
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String,
    val filePath: String,
    /** txt / epub */
    val format: String,
    /** 章节标题列表，\n 分隔 */
    val chapterTitles: String,
    /** 章节起始偏移（txt 按字符；epub 为文件序号），逗号分隔 */
    val chapterOffsets: String,
    val totalChars: Long,
    val progressChapter: Int,
    /** txt：章节内字符偏移；epub：章节内滚动百分比 0-1000 */
    val progressOffset: Int,
    val addedAt: Long,
    val lastReadAt: Long,
)
