package com.calm.core.common.theme

import android.content.Context

/** 主题持久化：主题色 8 预设 + 外观模式（TH-001 / TH-004）。 */
object ThemeStore {

    private const val SP = "theme_config"
    private lateinit var sp: android.content.SharedPreferences

    fun init(context: Context) {
        sp = context.getSharedPreferences(SP, Context.MODE_PRIVATE)
    }

    var presetIndex: Int
        get() = sp.getInt("presetIndex", 0).coerceIn(0, CalmThemePreset.entries.size - 1)
        set(v) = sp.edit().putInt("presetIndex", v).apply()

    /** 0 跟随系统 / 1 浅色 / 2 深色 */
    var appearance: Int
        get() = sp.getInt("appearance", 0).coerceIn(0, 2)
        set(v) = sp.edit().putInt("appearance", v).apply()
}
