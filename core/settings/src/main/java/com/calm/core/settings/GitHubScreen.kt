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
    val proxy: com.calm.core.browser.GithubProxyManager,
) : ViewModel()

/** GitHub 辅助访问设置（BR-028）：两种独立加速方式 + 均可关闭。 */
@Composable
fun GitHubScreen(onBack: () -> Unit, vm: GitHubViewModel = hiltViewModel()) {
    var enabled by remember { mutableStateOf(vm.helper.enabled) }
    var template by remember { mutableStateOf(vm.helper.template) }
    var directOn by remember { mutableStateOf(vm.proxy.prefEnabled) }
    var directRunning by remember { mutableStateOf(vm.proxy.isRunning) }

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

            // ---- 方式一：本地代理直连（免镜像） ----
            Text("方式一 · 本地代理直连（免镜像，实验）", style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("全局接管 WebView 流量走本地代理")
                    Text(
                        if (directOn && directRunning) "运行中 · 127.0.0.1:${vm.proxy.port}" else "已关闭",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = directOn, onCheckedChange = {
                    val ok = if (it) vm.proxy.enable() else { vm.proxy.disable(); true }
                    directOn = ok && it
                    directRunning = vm.proxy.isRunning
                })
            }

            // ---- 方式二：镜像改写 ----
            Spacer(Modifier.height(12.dp))
            Text("方式二 · 镜像前缀改写", style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("启用（默认关闭）", modifier = Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = {
                    enabled = it
                    vm.helper.enabled = it
                })
            }

            Spacer(Modifier.height(8.dp))
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
                        "方式一：本地透明代理 + GitHub 优选 IP 直连。不拆解 HTTPS，登录/Cookie 正常；" +
                            "非 GitHub 网站经代理透传不受影响；失败可随时关闭。\n\n" +
                            "方式二：仅改写 GitHub 静态资源链接（raw/gist/codeload/Releases）走镜像前缀，" +
                            "例如 https://ghfast.top/https://raw.githubusercontent.com/...；" +
                            "镜像站可用性随时变化，失败请换模板或关闭。\n\n" +
                            "两种方式可叠加使用（不推荐），均为非官方加速。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
