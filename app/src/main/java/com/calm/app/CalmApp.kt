package com.calm.app

import android.app.Application
import com.calm.data.local.LogStore
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class CalmApp : Application() {

    @Inject
    lateinit var logStore: LogStore

    @Inject
    lateinit var githubProxy: com.calm.core.browser.GithubProxyManager

    override fun onCreate() {
        super.onCreate()
        // BR-003：启动时清理 24 小时前日志（异步，失败不阻塞）
        logStore.purgeOldLogs()
        // 恢复上次的 GitHub 直连加速开关（WebView 代理不跨进程存活）
        if (githubProxy.restoreOnStartup()) {
            logStore.log("GitHub加速", "启动恢复：本地代理运行于 127.0.0.1:${githubProxy.port}")
        }
        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (_: Exception) {
            "?"
        }
        logStore.log(
            "App",
            "启动 v$versionName · Android ${android.os.Build.VERSION.RELEASE}(${android.os.Build.VERSION.SDK_INT}) · ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
        )
        // 崩溃捕获写入日志后交回原处理器
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            logStore.logSync("Crash", e.stackTraceToString().take(3000))
            previous?.uncaughtException(t, e)
        }
    }
}
