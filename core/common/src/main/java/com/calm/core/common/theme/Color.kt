package com.calm.core.common.theme

import androidx.compose.ui.graphics.Color

/** 一套主题色的浅色 / 深色取值（对应产品设计方案 2.3 节，深色取 Material 200 色调）。 */
data class CalmThemeColors(val primary: Color, val accent: Color)

/** 8 套预置主题色 + 自定义位（自定义 HSV 选色在 Phase 7 定制功能接入）。 */
enum class CalmThemePreset(
    val label: String,
    val light: CalmThemeColors,
    val dark: CalmThemeColors,
) {
    BLUE(
        "静谧蓝",
        CalmThemeColors(Color(0xFF1976D2), Color(0xFF2196F3)),
        CalmThemeColors(Color(0xFF90CAF9), Color(0xFF64B5F6)),
    ),
    GREEN(
        "翡翠绿",
        CalmThemeColors(Color(0xFF2E7D32), Color(0xFF4CAF50)),
        CalmThemeColors(Color(0xFFA5D6A7), Color(0xFF81C784)),
    ),
    ORANGE(
        "琥珀橙",
        CalmThemeColors(Color(0xFFE65100), Color(0xFFFF9800)),
        CalmThemeColors(Color(0xFFFFCC80), Color(0xFFFFB74D)),
    ),
    RED(
        "玫瑰红",
        CalmThemeColors(Color(0xFFC62828), Color(0xFFF44336)),
        CalmThemeColors(Color(0xFFEF9A9A), Color(0xFFE57373)),
    ),
    PURPLE(
        "薰衣草紫",
        CalmThemeColors(Color(0xFF6A1B9A), Color(0xFF9C27B0)),
        CalmThemeColors(Color(0xFFCE93D8), Color(0xFFBA68C8)),
    ),
    BROWN(
        "巧克力棕",
        CalmThemeColors(Color(0xFF3E2723), Color(0xFF795548)),
        CalmThemeColors(Color(0xFFBCAAA4), Color(0xFFA1887F)),
    ),
    MINT(
        "薄荷绿",
        CalmThemeColors(Color(0xFF00695C), Color(0xFF26A69A)),
        CalmThemeColors(Color(0xFF80CBC4), Color(0xFF4DB6AC)),
    ),
    PINK(
        "樱花粉",
        CalmThemeColors(Color(0xFFAD1457), Color(0xFFEC407A)),
        CalmThemeColors(Color(0xFFF48FB1), Color(0xFFF06292)),
    ),
}
