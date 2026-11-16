package com.calm.app

import android.app.Application
import com.calm.data.local.LogStore
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class CalmApp : Application() {

    @Inject
    lateinit var logStore: LogStore

    override fun onCreate() {
        super.onCreate()
        // BR-003：启动时清理 24 小时前日志（异步，失败不阻塞）
        logStore.purgeOldLogs()
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
