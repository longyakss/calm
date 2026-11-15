package com.calm.core.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 设置分区骨架（对应产品设计方案 5.15 设置页），各入口随 Phase 2-7 逐步接通。 */
private data class SettingsSection(val title: String, val items: List<String>)

private val sections = listOf(
    SettingsSection("外观", listOf("主题色设置", "深色模式")),
    SettingsSection("浏览器", listOf("搜索引擎管理", "书签与历史", "广告拦截", "脚本管理", "浏览器夜间模式")),
    SettingsSection("下载", listOf("下载设置", "保存位置")),
    SettingsSection("阅读", listOf("阅读器设置", "皮肤主题库", "文本替换规则")),
    SettingsSection("视频", listOf("播放器设置")),
    SettingsSection("隐私与安全", listOf("密码管理器", "隐私设置", "清除数据")),
    SettingsSection("系统", listOf("防杀设置（后台保护）", "自动更新", "关于")),
)

@Composable
fun SettingsScreen() {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                text = "设置",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
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
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                )
                Divider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}
