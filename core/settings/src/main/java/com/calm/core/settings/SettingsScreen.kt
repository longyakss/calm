package com.calm.core.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.calm.core.common.theme.CalmThemePreset
import com.calm.core.common.theme.ThemeStore

private data class SettingsSection(val title: String, val items: List<String>)

private val sections = listOf(
    SettingsSection("浏览器", listOf("搜索引擎管理", "书签与历史", "广告拦截", "脚本管理", "浏览器夜间模式")),
    SettingsSection("下载", listOf("下载设置", "保存位置")),
    SettingsSection("阅读", listOf("阅读器设置", "皮肤主题库", "文本替换规则")),
    SettingsSection("视频", listOf("播放器设置")),
    SettingsSection("隐私与安全", listOf("密码管理器", "隐私设置", "清除数据")),
    SettingsSection("系统", listOf("防杀设置（后台保护）", "自动更新", "关于")),
)

/** 设置页（对应设计方案 5.15）。已实装：主题色/外观模式/防杀/阅读器设置/播放器提示。 */
@Composable
fun SettingsScreen(
    onThemeChanged: (Int, Int) -> Unit = { _, _ -> },
    onOpenKeepAlive: () -> Unit = {},
    onOpenReaderSettings: () -> Unit = {},
) {
    var presetIdx by remember { mutableIntStateOf(ThemeStore.presetIndex) }
    var appearance by remember { mutableIntStateOf(ThemeStore.appearance) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                text = "设置",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }

        item {
            Text(
                text = "外观",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
            )
            Text(
                text = "主题色",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
            ) {
                items(CalmThemePreset.entries.toList()) { preset ->
                    val idx = CalmThemePreset.entries.indexOf(preset)
                    val color = preset.light.primary
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (idx == presetIdx) 3.dp else 1.dp,
                                color = if (idx == presetIdx) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                                shape = CircleShape,
                            )
                            .clickable {
                                presetIdx = idx
                                ThemeStore.presetIndex = idx
                                onThemeChanged(idx, appearance)
                            },
                    )
                }
            }
            Text(
                text = "外观模式",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                items(listOf("跟随系统", "浅色", "深色")) { label ->
                    val idx = listOf("跟随系统", "浅色", "深色").indexOf(label)
                    FilterChip(
                        selected = appearance == idx,
                        onClick = {
                            appearance = idx
                            ThemeStore.appearance = idx
                            onThemeChanged(presetIdx, idx)
                        },
                        label = { Text(label) },
                    )
                }
            }
        }

        sections.forEach { section ->
            item(key = "header_${section.title}") {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                )
            }
            items(section.items) { label ->
                val clickable = label.startsWith("防杀") || label == "阅读器设置"
                Text(
                    text = label + if (clickable) "  ›" else "",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (clickable) Modifier.clickable {
                                when {
                                    label.startsWith("防杀") -> onOpenKeepAlive()
                                    label == "阅读器设置" -> onOpenReaderSettings()
                                }
                            } else Modifier,
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                )
                androidx.compose.material3.Divider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}
