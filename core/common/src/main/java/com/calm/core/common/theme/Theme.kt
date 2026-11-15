package com.calm.core.common.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Calm 全局主题。
 * 外观模式：跟随系统 / 浅色 / 深色（对应产品设计方案 TH-004）。
 * 主题色选择与持久化（TH-001/002）在设置中心完成后接入。
 */
@Composable
fun CalmTheme(
    preset: CalmThemePreset = CalmThemePreset.BLUE,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = preset.dark.primary,
            secondary = preset.dark.accent,
        )
    } else {
        lightColorScheme(
            primary = preset.light.primary,
            secondary = preset.light.accent,
        )
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = CalmTypography,
        content = content,
    )
}
