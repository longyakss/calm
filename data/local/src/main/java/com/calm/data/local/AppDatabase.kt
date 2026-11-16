package com.calm.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Singleton

@Database(
    entities = [
        DownloadTaskEntity::class,
        BookmarkEntity::class,
        HistoryEntity::class,
        BookEntity::class,
        LogEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun historyDao(): HistoryDao
    abstract fun bookDao(): BookDao
    abstract fun logDao(): LogDao
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE bookmark ADD COLUMN inHome INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `app_log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`time` INTEGER NOT NULL, `tag` TEXT NOT NULL, `message` TEXT NOT NULL)",
        )
    }
}

/** 操作日志中心（BR-001/002/003）。写入异步、非阻塞；崩溃时同步写入。 */
@Singleton
class LogStore @javax.inject.Inject constructor(private val dao: LogDao) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun observeAll() = dao.observeAll()

    fun log(tag: String, message: String) {
        scope.launch {
            runCatching { dao.insert(LogEntity(time = System.currentTimeMillis(), tag = tag, message = message.take(2000))) }
        }
    }

    /** 仅用于崩溃处理器等即将终止的场景。 */
    fun logSync(tag: String, message: String) {
        runBlocking {
            runCatching { dao.insert(LogEntity(time = System.currentTimeMillis(), tag = tag, message = message.take(2000))) }
        }
    }

    /** 每次启动清理 24 小时前的日志（BR-003，失败不阻塞启动）。 */
    fun purgeOldLogs() {
        scope.launch {
            runCatching { dao.purgeBefore(System.currentTimeMillis() - 24 * 3600 * 1000L) }
        }
    }

    fun clearAll() {
        scope.launch { runCatching { dao.clearAll() } }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "calm.db")
            .addMigrations(MIGRATION_1_2)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideDownloadDao(db: AppDatabase): DownloadDao = db.downloadDao()

    @Provides
    fun provideBookmarkDao(db: AppDatabase): BookmarkDao = db.bookmarkDao()

    @Provides
    fun provideHistoryDao(db: AppDatabase): HistoryDao = db.historyDao()

    @Provides
    fun provideBookDao(db: AppDatabase): BookDao = db.bookDao()

    @Provides
    fun provideLogDao(db: AppDatabase): LogDao = db.logDao()
}
