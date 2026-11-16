package com.calm.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: DownloadTaskEntity)

    @Update
    suspend fun update(task: DownloadTaskEntity)

    @Query("SELECT * FROM download_task WHERE id = :id")
    suspend fun get(id: String): DownloadTaskEntity?

    @Query("SELECT * FROM download_task ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadTaskEntity>>

    @Query("UPDATE download_task SET status = :status, updatedAt = :now WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE download_task SET downloadedBytes = :bytes, chunkState = :chunks, updatedAt = :now WHERE id = :id")
    suspend fun updateProgress(id: String, bytes: Long, chunks: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE download_task SET totalBytes = :total WHERE id = :id")
    suspend fun updateTotal(id: String, total: Long)

    @Query("DELETE FROM download_task WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface BookmarkDao {
    @Insert
    suspend fun insert(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmark WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM bookmark ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<BookmarkEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM bookmark WHERE url = :url)")
    suspend fun exists(url: String): Boolean

    @Query("DELETE FROM bookmark WHERE url = :url")
    suspend fun deleteByUrl(url: String)
}

@Dao
interface HistoryDao {
    @Insert
    suspend fun insert(history: HistoryEntity)

    @Query("SELECT * FROM web_history ORDER BY visitedAt DESC LIMIT 500")
    fun observeAll(): Flow<List<HistoryEntity>>

    @Query("DELETE FROM web_history")
    suspend fun clear()
}

@Dao
interface BookDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(book: BookEntity): Long

    @Query("SELECT * FROM book WHERE id = :id")
    suspend fun get(id: Long): BookEntity?

    @Query("SELECT * FROM book ORDER BY lastReadAt DESC")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("UPDATE book SET progressChapter = :chapter, progressOffset = :offset, lastReadAt = :now WHERE id = :id")
    suspend fun updateProgress(id: Long, chapter: Int, offset: Int, now: Long = System.currentTimeMillis())

    @Query("UPDATE book SET chapterTitles = :titles, chapterOffsets = :offsets, totalChars = :total WHERE id = :id")
    suspend fun updateChapters(id: Long, titles: String, offsets: String, total: Long)

    @Query("DELETE FROM book WHERE id = :id")
    suspend fun delete(id: Long)
}
