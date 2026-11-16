package com.calm.core.reader

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calm.data.local.BookDao
import com.calm.data.local.BookEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ReaderViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val bookDao: BookDao,
    val prefs: ReaderPrefs,
) : ViewModel() {

    var book by mutableStateOf<BookEntity?>(null)
        private set
    var chapterIndex by mutableStateOf(0)
        private set
    var paragraphs = mutableStateListOf<String>()
        private set
    var loading by mutableStateOf(false)
        private set
    var ruleError by mutableStateOf<String?>(null)
        private set
    var speakEnabled by mutableStateOf(false)
        private set

    /** 一次性恢复标记：章节加载后由 UI 消费并清除。 */
    var restoreOffset by mutableStateOf(-1)
        private set

    fun consumeRestore() {
        restoreOffset = -1
    }

    val chapterCount: Int get() = book?.chapterOffsets?.split(",")?.size ?: 0

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var saveJob: Job? = null

    fun open(bookId: Long) {
        if (book?.id == bookId && paragraphs.isNotEmpty()) return
        viewModelScope.launch {
            val b = bookDao.get(bookId) ?: return@launch
            book = b
            val target = b.progressChapter.coerceIn(0, (chapterCount - 1).coerceAtLeast(0))
            loadChapter(target, restoreOffset = b.progressOffset)
            initTts()
        }
    }

    fun jumpChapter(index: Int) {
        val n = chapterCount
        if (index !in 0 until n) return
        viewModelScope.launch { loadChapter(index, restoreOffset = 0) }
    }

    private suspend fun loadChapter(target: Int, restoreOffset: Int) {
        val b = book ?: return
        loading = true
        val loaded = withContext(Dispatchers.IO) {
            try {
                if (b.format == "txt") {
                    val offs = b.chapterOffsets.split(",").map { it.trim().toInt() }
                    val start = offs.getOrNull(target) ?: 0
                    val end = offs.getOrNull(target + 1) ?: Int.MAX_VALUE
                    val paras = BookParser.txtChapter(File(b.filePath), start, end)
                    applyRules(paras)
                } else {
                    val f = File(b.filePath, "ch$target.html")
                    applyRules(BookParser.epubChapter(f.absolutePath))
                }
            } catch (e: Exception) {
                listOf("章节加载失败：${e.message}")
            }
        }
        paragraphs.clear()
        paragraphs.addAll(loaded)
        chapterIndex = target
        restoreOffset = restoreOffsetArg
        loading = false
        // 立即落一次进度
        bookDao.updateProgress(b.id, target, restoreOffsetArg)
        if (speakEnabled) speakFrom(0)
    }

    fun saveProgress(offset: Int) {
        val b = book ?: return
        saveJob?.cancel()
        saveJob = viewModelScope.launch(Dispatchers.IO) {
            bookDao.updateProgress(b.id, chapterIndex, offset)
        }
    }

    // ---- 规则（NR-018/019/021）----

    fun rules(): List<ReaderPrefs.Rule> = prefs.rules(book?.id ?: 0)

    fun addRule(find: String, replace: String, isRegex: Boolean) {
        val id = book?.id ?: return
        if (isRegex) {
            runCatching { Regex(find) }.getOrElse {
                ruleError = "正则语法错误：${it.message}"
                return
            }
        }
        ruleError = null
        val list = rules() + ReaderPrefs.Rule(System.currentTimeMillis(), find, replace, isRegex, true)
        prefs.saveRules(id, list)
        // 重新渲染当前章节
        viewModelScope.launch { loadChapter(chapterIndex, restoreOffset = 0) }
    }

    fun removeRule(rule: ReaderPrefs.Rule) {
        prefs.saveRules(book?.id ?: 0, rules() - rule)
        viewModelScope.launch { loadChapter(chapterIndex, restoreOffset = 0) }
    }

    private fun applyRules(input: List<String>): List<String> {
        var out = input
        rules().filter { it.enabled }.forEach { rule ->
            out = out.map { para ->
                runCatching {
                    if (rule.isRegex) para.replace(Regex(rule.find), rule.replace)
                    else para.replace(rule.find, rule.replace)
                }.getOrDefault(para)
            }
        }
        return out
    }

    // ---- TTS 听书（NR-022/023）----

    private fun initTts() {
        if (tts != null) return
        tts = TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.language = Locale.CHINA
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        if (utteranceId?.startsWith("calm_last_") == true) {
                            // 本章读完 → 自动下一章
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                if (speakEnabled) {
                                    val next = chapterIndex + 1
                                    if (next < chapterCount) {
                                        viewModelScope.launch { loadChapter(next, restoreOffset = 0) }
                                    } else {
                                        speakEnabled = false
                                    }
                                }
                            }
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {}
                })
            }
        }
    }

    fun toggleSpeak() {
        if (speakEnabled) {
            speakEnabled = false
            tts?.stop()
        } else {
            speakEnabled = true
            speakFrom(0)
        }
    }

    private fun speakFrom(fromParagraph: Int) {
        if (!ttsReady) return
        val text = paragraphs.drop(fromParagraph).joinToString("\n")
        if (text.isBlank()) return
        tts?.stop()
        // 分段朗读（TTS 单次长度限制）
        val pieces = text.chunked(2800)
        pieces.forEachIndexed { i, piece ->
            val id = if (i == pieces.lastIndex) "calm_last_${chapterIndex}_$i" else "calm_${chapterIndex}_$i"
            tts?.speak(piece, TextToSpeech.QUEUE_ADD, null, id)
        }
    }

    override fun onCleared() {
        tts?.stop()
        tts?.shutdown()
        super.onCleared()
    }
}
