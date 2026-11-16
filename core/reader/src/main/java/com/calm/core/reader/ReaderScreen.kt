package com.calm.core.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material.icons.filled.FormatClear
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun ReaderScreen(
    bookId: Long,
    onBack: () -> Unit,
    vm: ReaderViewModel = hiltViewModel(),
) {
    LaunchedEffect(bookId) { vm.open(bookId) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var sheet by remember { mutableStateOf<String?>(null) }
    val density = LocalDensity.current

    // 进度保存：滚动时记录首个可见段落（NR-012）
    val firstVisible by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    LaunchedEffect(firstVisible) {
        if (vm.paragraphs.isNotEmpty()) vm.saveProgress(firstVisible)
    }
    LaunchedEffect(vm.restoreOffset) {
        val off = vm.restoreOffset
        if (off >= 0 && vm.paragraphs.isNotEmpty()) {
            listState.scrollToItem(off.coerceAtMost(vm.paragraphs.size - 1))
            vm.consumeRestore()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        vm.book?.title ?: "…",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${vm.chapterIndex + 1}/${vm.chapterCount} · " +
                            (vm.paragraphs.getOrNull(firstVisible)?.take(18) ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }

            // 正文
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(enabled = false) {},
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            ) {
                items(vm.paragraphs) { para ->
                    Text(
                        text = para,
                        fontSize = vm.prefs.textSize.sp,
                        lineHeight = (vm.prefs.textSize * vm.prefs.lineSpacing).sp,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(
                            bottom = (vm.prefs.paragraphSpacing * 10).dp,
                            start = if (vm.prefs.indent && para.length > 3) 0.dp else 0.dp,
                        ),
                    )
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        if (vm.chapterIndex > 0) {
                            TextButton(onClick = { vm.jumpChapter(vm.chapterIndex - 1) }) { Text("上一章") }
                        }
                        if (vm.chapterIndex < vm.chapterCount - 1) {
                            TextButton(onClick = { vm.jumpChapter(vm.chapterIndex + 1) }) { Text("下一章") }
                        }
                    }
                }
            }
        }

        // 底部操作栏
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .height(52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vm.jumpChapter(vm.chapterIndex - 1) }) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "上一章")
            }
            IconButton(onClick = { sheet = "toc" }) {
                Icon(Icons.Filled.List, contentDescription = "目录")
            }
            IconButton(onClick = { vm.toggleSpeak() }) {
                Icon(
                    if (vm.speakEnabled) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "听书",
                )
            }
            IconButton(onClick = { sheet = "settings" }) {
                Icon(Icons.Filled.Settings, contentDescription = "设置")
            }
            IconButton(onClick = { sheet = "rules" }) {
                Icon(Icons.Filled.FormatClear, contentDescription = "净化规则")
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { vm.jumpChapter(vm.chapterIndex + 1) }) {
                Icon(Icons.Filled.SkipNext, contentDescription = "下一章")
            }
            Spacer(Modifier.width(8.dp))
        }
    }

    // ---- 目录 ----
    if (sheet == "toc") {
        ModalBottomSheet(onDismissRequest = { sheet = null }) {
            Text(
                "目录（${vm.chapterCount} 章）",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            val titles = vm.book?.chapterTitles?.split("\n") ?: emptyList()
            LazyColumn(modifier = Modifier.height(420.dp)) {
                items(titles.withIndex().toList()) { (i, t) ->
                    Text(
                        t,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (i == vm.chapterIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { vm.jumpChapter(i); sheet = null }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // ---- 排版设置 ----
    if (sheet == "settings") {
        ModalBottomSheet(onDismissRequest = { sheet = null }) {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text("字号 ${vm.prefs.textSize}sp", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = vm.prefs.textSize.toFloat(),
                    onValueChange = { vm.prefs.setTextSize(it.toInt()) },
                    valueRange = 12f..32f,
                )
                Text("行间距 ${vm.prefs.lineSpacing}x", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = vm.prefs.lineSpacing,
                    onValueChange = { vm.prefs.setLineSpacing(it) },
                    valueRange = 1.0f..2.5f,
                )
                Text("段间距 ${vm.prefs.paragraphSpacing}x", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = vm.prefs.paragraphSpacing,
                    onValueChange = { vm.prefs.setParagraphSpacing(it) },
                    valueRange = 0.5f..2.0f,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("首行缩进", modifier = Modifier.weight(1f))
                    Switch(checked = vm.prefs.indent, onCheckedChange = { vm.prefs.setIndent(it) })
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    // ---- 净化 / 替换规则 ----
    if (sheet == "rules") {
        ModalBottomSheet(onDismissRequest = { sheet = null }) {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text("净化规则（仅显示层替换，不改原文件）", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                vm.ruleError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                AddRuleRow(vm)
                Spacer(Modifier.height(8.dp))
                val rules = vm.rules()
                LazyColumn(modifier = Modifier.height(280.dp)) {
                    items(rules) { r ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "「${r.find}」 → 「${r.replace}」${if (r.isRegex) "（正则）" else ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            TextButton(onClick = { vm.removeRule(r) }) { Text("删除") }
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun AddRuleRow(vm: ReaderViewModel) {
    var find by remember { mutableStateOf("") }
    var replace by remember { mutableStateOf("") }
    var isRegex by remember { mutableStateOf(false) }
    Column {
        OutlinedTextField(
            value = find,
            onValueChange = { find = it },
            label = { Text("查找内容") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = replace,
            onValueChange = { replace = it },
            label = { Text("替换为") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("正则", modifier = Modifier.weight(1f))
            Switch(checked = isRegex, onCheckedChange = { isRegex = it })
            TextButton(onClick = {
                if (find.isNotBlank()) vm.addRule(find, replace, isRegex)
                find = ""
                replace = ""
                isRegex = false
            }) { Text("添加") }
        }
    }
}
