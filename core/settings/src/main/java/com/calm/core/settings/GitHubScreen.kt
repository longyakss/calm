package com.calm.core.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.calm.core.browser.GitHubHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class GitHubViewModel @Inject constructor(
    val helper: GitHubHelper,
) : ViewModel()

/** GitHub 辅助访问设置（BR-028）：开关 + 镜像模板。 */
@Composable
fun GitHubScreen(onBack: () -> Unit, vm: GitHubViewModel = hiltViewModel()) {
    var enabled by remember { mutableStateOf(vm.helper.enabled) }
    var template by remember { mutableStateOf(vm.helper.template) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
            Text("GitHub 辅助访问", style = MaterialTheme.typography.titleMedium)
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("启用加速（默认关闭）", modifier = Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = {
                    enabled = it
                    vm.helper.enabled = it
                })
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = template,
                onValueChange = { template = it },
                label = { Text("镜像前缀模板") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = { vm.helper.template = template }) { Text("保存模板") }

            Spacer(Modifier.height(16.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("说明", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "仅加速以下 GitHub 静态资源（不影响登录等主站功能）：\n" +
                            "· raw.githubusercontent.com\n" +
                            "· gist.githubusercontent.com\n" +
                            "· codeload.github.com（源码打包）\n" +
                            "· Releases 附件下载\n" +
                            "· 页面 raw/blob 文件链接\n\n" +
                            "镜像前缀会在原地址前拼接，例如：\n" +
                            "https://ghfast.top/https://raw.githubusercontent.com/...\n\n" +
                            "镜像站可用性随时可能变化，如加速失败请更换模板或关闭开关。此功能为非官方能力。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
