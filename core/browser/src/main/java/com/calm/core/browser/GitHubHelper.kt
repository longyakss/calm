package com.calm.core.browser

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * GitHub 辅助访问（BR-028）：将 GitHub 文件类资源（raw/releases/gist/codeload）改写为镜像前缀。
 * 默认关闭；镜像模板可在设置中修改。仅加速静态资源，不影响登录等主站功能。
 */
@Singleton
class GitHubHelper @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val sp = context.getSharedPreferences("browser_settings", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = sp.getBoolean("ghEnabled", false)
        set(v) = sp.edit().putBoolean("ghEnabled", v).apply()

    var template: String
        get() = sp.getString("ghTemplate", DEFAULT_TEMPLATE) ?: DEFAULT_TEMPLATE
        set(v) = sp.edit().putString("ghTemplate", v).apply()

    /** 是否是可加速的 GitHub 资源 URL（raw/gist/codeload/releases 下载/页面 raw）。 */
    fun isAcceleratable(url: String): Boolean {
        if (!enabled) return false
        val u = url.substringBefore('#')
        val host = UrlUtil.hostOf(u)
        return host == "raw.githubusercontent.com" ||
            host == "gist.githubusercontent.com" ||
            host == "codeload.github.com" ||
            u.contains("github.com/") && (
                u.contains("/releases/download/") ||
                    Regex("github\\.com/[^/]+/[^/]+/(raw|blob)/").containsMatchIn(u)
                )
    }

    fun proxied(url: String): String = template.trimEnd('/') + "/" + url

    companion object {
        const val DEFAULT_TEMPLATE = "https://ghfast.top/"
    }
}
