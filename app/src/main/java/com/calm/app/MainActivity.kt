package com.calm.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.calm.core.common.theme.CalmTheme
import com.calm.core.common.theme.CalmThemePreset
import com.calm.core.common.theme.ThemeStore
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var themeIdx by mutableIntStateOf(0)
    private var appearance by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeStore.init(this)
        themeIdx = ThemeStore.presetIndex
        appearance = ThemeStore.appearance

        setContent {
            val dark = when (appearance) {
                1 -> false
                2 -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            CalmTheme(preset = CalmThemePreset.entries[themeIdx], darkTheme = dark) {
                CalmRoot(
                    onThemeChanged = { idx, app ->
                        themeIdx = idx
                        appearance = app
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 返回后同步设置页可能产生的变更
        themeIdx = ThemeStore.presetIndex
        appearance = ThemeStore.appearance
    }
}
