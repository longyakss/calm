package com.calm.core.reader

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calm.data.local.BookDao
import com.calm.data.local.BookEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject

@HiltViewModel
class BookshelfViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val bookDao: BookDao,
) : ViewModel() {

    val books = bookDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var importing by mutableStateOf<String?>(null)
        private set

    fun importBook(uri: Uri) {
        viewModelScope.launch {
            importing = "解析中…"
            try {
                withContext(Dispatchers.IO) {
                    val resolver = context.contentResolver
                    val displayName = queryName(uri) ?: "book_${System.currentTimeMillis()}"
                    val booksDir = File(context.filesDir, "books").apply { mkdirs() }
                    val ext = displayName.substringAfterLast('.', "txt").lowercase()

                    if (ext == "epub") {
                        val tmp = File(booksDir, "$displayName.tmp").apply { deleteRecursively() }
                        resolver.openInputStream(uri)!!.use { ins -> tmp.outputStream().use { ins.copyTo(it) } }
                        val extractDir = File(booksDir, displayName.substringBeforeLast('.'))
                        val meta = BookParser.openEpub(tmp, extractDir)
                        tmp.delete()
                        if (meta != null) {                            // 生成章节标题（取每个 spine 文件的首个标题）
                            val titles = meta.spineFiles.mapIndexed { i, f ->
                                runCatching {
                                    org.jsoup.Jsoup.parse(File(f), "UTF-8")
                                        .select("h1, h2, h3, title").firstOrNull()?.text()?.trim()
                                }.getOrNull()?.takeIf { it.isNotBlank() } ?: "第 ${i + 1} 章"
                            }
                            bookDao.insert(
                                BookEntity(
                                    title = meta.title,
                                    author = meta.author,
                                    filePath = extractDir.absolutePath,
                                    format = "epub",
                                    chapterTitles = titles.joinToString("\n"),
                                    chapterOffsets = meta.spineFiles.indices.joinToString(","),
                                    totalChars = meta.spineFiles.size.toLong(),
                                    progressChapter = 0,
                                    progressOffset = 0,
                                    addedAt = System.currentTimeMillis(),
                                    lastReadAt = System.currentTimeMillis(),
                                ),
                            )
                        }
                        Unit
                    } else {
                        // txt（其它扩展名按 txt 尝试）
                        val local = File(booksDir, displayName)
                        resolver.openInputStream(uri)!!.use { ins -> local.outputStream().use { ins.copyTo(it) } }
                        val (text, chapters) = BookParser.parseTxt(local)
                        bookDao.insert(
                            BookEntity(
                                title = displayName.substringBeforeLast('.'),
                                author = "未知作者",
                                filePath = local.absolutePath,
                                format = "txt",
                                chapterTitles = chapters.titles.joinToString("\n"),
                                chapterOffsets = chapters.offsets.joinToString(","),
                                totalChars = chapters.totalChars,
                                progressChapter = 0,
                                progressOffset = 0,
                                addedAt = System.currentTimeMillis(),
                                lastReadAt = System.currentTimeMillis(),
                            ),
                        )
                    }
                }
            } finally {
                importing = null
            }
        }
    }

    private fun queryName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    }.getOrNull()

    fun deleteBook(book: BookEntity, deleteSource: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            bookDao.delete(book.id)
            if (deleteSource) {
                runCatching {
                    val f = File(book.filePath)
                    if (f.isDirectory) f.deleteRecursively() else f.delete()
                }
            }
        }
    }
}

/** 阅读排版偏好（TH-010：独立存储，SharedPreferences JSON）。 */
class ReaderPrefs @Inject constructor(@dagger.hilt.android.qualifiers.ApplicationContext private val context: Context) {

    private val sp = context.getSharedPreferences("reader_settings", Context.MODE_PRIVATE)

    var textSize by mutableStateOf(sp.getInt("textSize", 18))
        private set
    var lineSpacing by mutableStateOf(sp.getFloat("lineSpacing", 1.5f))
        private set
    var paragraphSpacing by mutableStateOf(sp.getFloat("paragraphSpacing", 1.0f))
        private set
    var indent by mutableStateOf(sp.getBoolean("indent", true))
        private set

    fun setTextSize(v: Int) { textSize = v.coerceIn(12, 32); sp.edit().putInt("textSize", textSize).apply() }
    fun setLineSpacing(v: Float) { lineSpacing = v.coerceIn(1.0f, 2.5f); sp.edit().putFloat("lineSpacing", lineSpacing).apply() }
    fun setParagraphSpacing(v: Float) { paragraphSpacing = v.coerceIn(0.5f, 2.0f); sp.edit().putFloat("paragraphSpacing", paragraphSpacing).apply() }
    fun setIndent(v: Boolean) { indent = v; sp.edit().putBoolean("indent", indent).apply() }

    // ---- 净化 / 替换规则（NR-018/019/021，per-book）----

    data class Rule(val id: Long, val find: String, val replace: String, val isRegex: Boolean, val enabled: Boolean)

    fun rules(bookId: Long): List<Rule> {
        val raw = context.getSharedPreferences("reader_rules_$bookId", Context.MODE_PRIVATE).getString("rules", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Rule(o.getLong("id"), o.getString("find"), o.getString("replace"), o.getBoolean("isRegex"), o.getBoolean("enabled"))
            }
        }.getOrDefault(emptyList())
    }

    fun saveRules(bookId: Long, rules: List<Rule>) {
        val arr = JSONArray()
        rules.forEach { r ->
            arr.put(JSONObject().apply {
                put("id", r.id)
                put("find", r.find)
                put("replace", r.replace)
                put("isRegex", r.isRegex)
                put("enabled", r.enabled)
            })
        }
        context.getSharedPreferences("reader_rules_$bookId", Context.MODE_PRIVATE)
            .edit().putString("rules", arr.toString()).apply()
    }
}
