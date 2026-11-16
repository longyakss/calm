package com.calm.core.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 阅读器全局排版设置（RS-003 / TH-010，独立存储，书内即时生效）。 */
@Composable
fun ReaderSettingsScreen(prefs: ReaderPrefs, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← 返回") }
            Text("阅读器设置", style = MaterialTheme.typography.titleLarge)
        }

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text("当前皮肤：默认白天（皮肤库后续版本）", style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(16.dp))
            Text("字号 ${prefs.textSize}sp", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = prefs.textSize.toFloat(),
                onValueChange = { prefs.setTextSize(it.toInt()) },
                valueRange = 12f..32f,
            )

            Text("行间距 ${"%.1f".format(prefs.lineSpacing)} 倍", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = prefs.lineSpacing,
                onValueChange = { prefs.setLineSpacing(it) },
                valueRange = 1.0f..2.5f,
            )

            Text("段间距 ${"%.1f".format(prefs.paragraphSpacing)} 倍", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = prefs.paragraphSpacing,
                onValueChange = { prefs.setParagraphSpacing(it) },
                valueRange = 0.5f..2.0f,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("首行缩进（2 字符）", modifier = Modifier.weight(1f))
                Switch(checked = prefs.indent, onCheckedChange = { prefs.setIndent(it) })
            }

            Spacer(Modifier.height(20.dp))
            Text("预览", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("第一章 示例", fontSize = prefs.textSize.sp, lineHeight = (prefs.textSize * prefs.lineSpacing).sp)
                    Spacer(Modifier.height((prefs.paragraphSpacing * 8).dp))
                    Text(
                        "　　这是一段预览文本。字号、行距、段距与首行缩进都会实时反映在这里，保存后对所有书籍生效。",
                        fontSize = prefs.textSize.sp,
                        lineHeight = (prefs.textSize * prefs.lineSpacing).sp,
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
