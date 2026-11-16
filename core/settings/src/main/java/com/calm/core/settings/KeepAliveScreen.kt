package com.calm.core.settings

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** 品牌识别（PS-011）。 */
fun detectBrand(): String {
    val manufacturer = android.os.Build.MANUFACTURER.lowercase()
    return when {
        listOf("huawei", "honor").any { manufacturer.contains(it) } -> "huawei"
        listOf("xiaomi", "redmi").any { manufacturer.contains(it) } -> "xiaomi"
        manufacturer.contains("oppo") -> "oppo"
        manufacturer.contains("vivo") || manufacturer.contains("iqoo") -> "vivo"
        manufacturer.contains("samsung") -> "samsung"
        manufacturer.contains("oneplus") || manufacturer.contains("oneplus") -> "oneplus"
        else -> "generic"
    }
}

object PermCheck {
    /** 电池优化白名单：true 绿 / false 红。 */
    fun batteryWhitelisted(context: Context): Boolean {
        val pm = context.getSystemService(PowerManager::class.java)
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** 通知权限：true 绿 / false 黄（不算致命）。 */
    fun notificationEnabled(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < 33

    /** 无障碍（非必须）：true 绿 / false 黄。 */
    fun accessibilityEnabled(context: Context): Boolean {
        val s = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return s?.contains(context.packageName) == true
    }
}

/** 跳转入口（对应设计方案 4.4 品牌适配表）。 */
object PermJump {
    fun battery(context: Context) = runCatching {
        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    fun batteryDirect(context: Context) = runCatching {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
        )
    }

    fun notifications(context: Context) = runCatching {
        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        })
    }

    fun accessibility(context: Context) = runCatching {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    fun appDetails(context: Context) = runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
        )
    }

    fun autostart(context: Context) {
        // 各厂商自启动页无公开 API，统一跳应用详情，引导文案按品牌显示（PS-004）
        appDetails(context)
    }
}

/** 防杀设置（PS-001~009）：红黄绿状态总览 + 逐项引导。 */
@Composable
fun KeepAliveScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableStateOf(0) }

    // 进入页面自动刷新（PS-002），返回时也刷新
    val battery = remember(refresh) { PermCheck.batteryWhitelisted(context) }
    val notif = remember(refresh) { PermCheck.notificationEnabled(context) }
    val a11y = remember(refresh) { PermCheck.accessibilityEnabled(context) }
    val brand = remember { detectBrand() }

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
            Text("防杀设置", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { refresh++ }) { Text("刷新") }
        }

        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            Text(
                "为保证后台下载 / 播放稳定，请尽量开启以下权限。无障碍为非必须项，不开启不影响任何核心功能。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(12.dp),
            )
        }

        PermCard(
            title = "电池优化白名单",
            statusGreen = battery,
            statusDesc = if (battery) "已忽略电池优化（绿）" else "未加入白名单，后台可能被杀（红）",
            guide = when (brand) {
                "huawei" -> "设置 → 电池 → 应用启动管理 → 关闭「自动管理」"
                "xiaomi" -> "设置 → 电池与性能 → 应用电池管理 → 无限制"
                "oppo" -> "设置 → 电池 → 省电策略 → 允许后台运行"
                "vivo" -> "设置 → 电池 → 高耗电允许后台运行"
                else -> "设置 → 电池 → 忽略电池优化"
            },
            actionLabel = "去设置",
            onAction = {
                PermJump.batteryDirect(context)
                refresh++
            },
        )

        PermCard(
            title = "通知权限",
            statusGreen = notif,
            warnNotRed = true,
            statusDesc = if (notif) "已授权（绿）" else "未授权，下载进度通知不显示（黄）",
            guide = "下载完成提醒与前台保活通知依赖通知权限",
            actionLabel = "去设置",
            onAction = {
                PermJump.notifications(context)
                refresh++
            },
        )

        PermCard(
            title = "自启动",
            statusGreen = null,
            warnNotRed = true,
            statusDesc = "无通用检测 API（黄 = 未知），请按品牌路径自查",
            guide = when (brand) {
                "huawei" -> "设置 → 应用 → 应用启动管理 → 允许自启动"
                "xiaomi" -> "设置 → 应用设置 → 应用启动管理"
                "oppo" -> "设置 → 应用 → 应用启动管理"
                "vivo" -> "设置 → 应用与权限 → 自启动"
                "samsung" -> "设置 → 应用程序 → 自动运行"
                else -> "设置 → 应用 → Calm → 权限管理"
            },
            actionLabel = "去设置",
            onAction = { PermJump.autostart(context) },
        )

        PermCard(
            title = "无障碍服务（非必须）",
            statusGreen = a11y,
            warnNotRed = true,
            statusDesc = if (a11y) "已开启（绿）" else "未开启（非必须，不影响下载/播放）",
            guide = "仅用于个别页面的自动化操作，绝大多数用户无需开启",
            actionLabel = "去设置",
            onAction = {
                PermJump.accessibility(context)
                refresh++
            },
        )

        Spacer(Modifier.height(16.dp))
        Text(
            "诊断信息",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        OutlinedButton(
            onClick = {
                val diag = "品牌:$brand 电池白名单:$battery 通知:$notif 无障碍:$a11y " +
                    "Android:${Build.VERSION.RELEASE}(${Build.VERSION.SDK_INT}) 型号:${Build.MODEL}"
                val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                cm.setPrimaryClip(android.content.ClipData.newPlainText("diag", diag))
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) { Text("复制诊断信息（PS-012）") }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PermCard(
    title: String,
    statusGreen: Boolean?,
    statusDesc: String,
    guide: String,
    actionLabel: String,
    onAction: () -> Unit,
    warnNotRed: Boolean = false,
) {
    val color = when {
        statusGreen == true -> Color(0xFF4CAF50)
        warnNotRed || statusGreen == null -> Color(0xFFFFC107)
        else -> Color(0xFFF44336)
    }
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .padding(0.dp),
            ) {
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(color = color)
                }
            }
            Text(
                "  $title",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(statusDesc, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "路径：$guide",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}
