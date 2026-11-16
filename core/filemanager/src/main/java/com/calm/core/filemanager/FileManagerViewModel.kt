package com.calm.core.filemanager

import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class FileManagerViewModel @Inject constructor() : ViewModel() {

    var path by mutableStateOf(rootDir().absolutePath)
        private set
    var entries by mutableStateOf<List<File>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var showHidden by mutableStateOf(false)
        private set
    var sortBy by mutableStateOf(0) // 0 名称 / 1 大小 / 2 时间 / 3 类型
        private set
    var filterKind by mutableStateOf<FileKind?>(null)
        private set

    // 选择模式
    var selectionMode by mutableStateOf(false)
        private set
    val selected = mutableStateOf(setOf<String>())

    // 剪贴板（复制 / 移动）
    var clipboardPaths by mutableStateOf<List<String>>(emptyList())
        private set
    var clipboardIsMove by mutableStateOf(false)
        private set

    // 操作进度
    var opProgress by mutableStateOf<String?>(null)
        private set

    fun rootDir(): File {
        val ext = Environment.getExternalStorageDirectory()
        return if (ext != null && ext.exists()) ext else File("/storage/emulated/0")
    }

    init {
        refresh()
    }

    fun refresh() {
        loading = true
        viewModelScope.launch(Dispatchers.IO) {
            val dir = File(path)
            val list = dir.listFiles()?.filter { it.exists() } ?: emptyList()
            val visible = list.filter { showHidden || !it.name.startsWith(".") }
                .filter { filterKind == null || FileTypes.kindOf(it) == filterKind }
                .sortedWith(
                    compareBy({ !it.isDirectory }, sortKey()),
                )
            entries = visible
            loading = false
        }
    }

    private fun sortKey(): (File) -> Comparable<*> = when (sortBy) {
        1 -> { f: File -> f.length() }
        2 -> { f: File -> f.lastModified() }
        3 -> { f: File -> f.extension }
        else -> { f: File -> f.name.lowercase() }
    }

    fun setSort(v: Int) { sortBy = v; refresh() }
    fun toggleHidden() { showHidden = !showHidden; refresh() }
    fun setFilter(k: FileKind?) { filterKind = k; refresh() }

    fun enter(file: File) {
        if (file.isDirectory) {
            path = file.absolutePath
            refresh()
        }
    }

    fun goUp(): Boolean {
        val parent = File(path).parentFile ?: return false
        if (!parent.absolutePath.startsWith("/storage")) return false
        path = parent.absolutePath
        refresh()
        return true
    }

    // ---- 选择 ----

    fun toggleSelect(file: File) {
        selectionMode = true
        selected.value = if (file.absolutePath in selected.value) {
            selected.value - file.absolutePath
        } else {
            selected.value + file.absolutePath
        }
        if (selected.value.isEmpty()) selectionMode = false
    }

    fun clearSelection() {
        selected.value = emptySet()
        selectionMode = false
    }

    fun selectAll() {
        selected.value = entries.map { it.absolutePath }.toSet()
        selectionMode = true
    }

    // ---- 剪贴板 ----

    fun copySelection(move: Boolean) {
        clipboardPaths = selected.value.toList()
        clipboardIsMove = move
        clearSelection()
    }

    fun paste() {
        if (clipboardPaths.isEmpty()) return
        val target = File(path)
        viewModelScope.launch(Dispatchers.IO) {
            clipboardPaths.forEachIndexed { idx, p ->
                val src = File(p)
                opProgress = "${idx + 1}/${clipboardPaths.size} ${src.name}"
                copyRecursive(src, target, clipboardIsMove)
            }
            clipboardPaths = emptyList()
            opProgress = null
            refresh()
        }
    }

    fun clearClipboard() {
        clipboardPaths = emptyList()
    }

    private fun copyRecursive(src: File, targetDir: File, move: Boolean) {
        if (src.isDirectory) {
            val dst = File(targetDir, src.name)
            dst.mkdirs()
            src.listFiles()?.forEach { copyRecursive(it, dst, false) }
            if (move && src.listFiles()?.isEmpty() == true) src.delete()
        } else {
            val dst = File(targetDir, src.name)
            if (dst.absolutePath == src.absolutePath) {
                val dst2 = File(targetDir, "副本_${src.name}")
                src.copyTo(dst2, overwrite = true)
            } else {
                src.copyTo(dst, overwrite = true)
                if (move) src.delete()
            }
        }
    }

    fun deleteSelection() {
        val targets = selected.value.map { File(it) }
        viewModelScope.launch(Dispatchers.IO) {
            targets.forEachIndexed { idx, f ->
                opProgress = "${idx + 1}/${targets.size} ${f.name}"
                f.deleteRecursively()
            }
            clearSelection()
            opProgress = null
            refresh()
        }
    }

    fun rename(file: File, newName: String) {
        val dst = File(file.parentFile, newName.replace(Regex("[\\\\/:*?\"<>|]"), "_"))
        viewModelScope.launch(Dispatchers.IO) {
            file.renameTo(dst)
            refresh()
        }
    }

    fun createDir(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            File(path, name.replace(Regex("[\\\\/:*?\"<>|]"), "_")).mkdirs()
            refresh()
        }
    }

    fun goHome() {
        path = rootDir().absolutePath
        refresh()
    }
}
