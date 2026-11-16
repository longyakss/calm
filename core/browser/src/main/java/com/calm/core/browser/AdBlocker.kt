package com.calm.core.browser

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 广告拦截（BR-017/018）：内置常见广告/追踪域名 hosts 规则，
 * 在 shouldInterceptRequest 阶段直接返回空响应。规则后续版本支持导入。
 */
@Singleton
class AdBlocker @Inject constructor() {

    var enabled by mutableStateOf(false)
        private set
    var blockedCount by mutableStateOf(0)
        private set

    fun setEnabled(v: Boolean) {
        enabled = v
    }

    private val hosts: Set<String> by lazy { buildSet {
        // 广告联盟 / 统计追踪 / 弹窗推送（精简内置，可扩充）
        addAll(
            listOf(
                "ad.doubleclick.net", "googleads.g.doubleclick.net", "pagead2.googlesyndication.com",
                "googleadservices.com", "tpc.googlesyndication.com", "www.google-analytics.com",
                "analytics.google.com", "ssl.google-analytics.com", "googletagmanager.com",
                "cdn.onesignal.com", "onesignal.com", "push.zhanzhang.baidu.com",
                "hm.baidu.com", "pos.baidu.com", "cpro.baidustatic.com", "eclick.baidu.com",
                "union.baidu.com", "mobads.baidu.com", "mobads-logs.baidu.com",
                "ad.qq.com", "mi.gdt.qq.com", "isdspeed.qq.com", "tbs.qq.com",
                "adsmind.ugdtimg.com", "win.gdt.qq.com",
                "adxserver.ad.cmcm.com", "adcrawler.m.cn", "adnew.suning.com",
                "ads.unitychina.cn", "config.unityads.unity3d.com", "cdnpkads.com",
                "adcolony.com", "ads-app.com", "cdn.admob.com",
                "applovin.com", "rs2.duapps.com", "a.duapps.com",
                "adcdn.com", "admob-cn.com", "adsame.com", "adm.10jqka.com.cn",
                "atanx.alicdn.com", "gm.mmstat.com", "log.mmstat.com", "ynuf.aliapp.org",
                "amdc.m.taobao.com", "fourier.taobao.com", "acs4baichuan.m.taobao.com",
                "count.wk2.com", "ad.12306.cn", "adp.cn107.com", "adx.adpx.cn",
                "ads.zhihu.com", "zhihu-web-analytics.zhihu.com", "sugar.zhihu.com",
                "bdstatic.com/hm.js", "e.suning.com", "ads.rekmob.com",
                "tap_icings_mlxlg_h.cn.miaozhen.com", "cdn.miaozhen.com",
                "adview.cn", "admaster.com", "adsame.com.cdn.dnsv1.com",
                "pronhub.com", "addthis.com", "adservice.google.com",
                "app-measurement.com", "crashlytics.com", "appcenter.ms",
                "branch.io", "app.link", "mopub.com", "inmobi.com",
                "scorecardresearch.com", "quantserve.com", "adnxs.com", "criteo.com",
                "taboola.com", "outbrain.com", "zedo.com", "pubmatic.com",
                "rubiconproject.com", "openx.net", "smartadserver.com", "casalemedia.com",
            ),
        )
    } }

    /** 二级域名匹配（a.b.ad.com → 命中 ad.com 规则）。 */
    fun shouldBlock(url: String): Boolean {
        if (!enabled) return false
        val host = UrlUtil.hostOf(url).lowercase()
        if (host.isEmpty()) return false
        if (host in hosts) return true
        val parts = host.split('.')
        for (i in 0 until parts.size - 1) {
            if (parts.drop(i).joinToString(".") in hosts) return true
        }
        return false
    }

    /** 返回空响应拦截请求；未命中返回 null 走正常加载。 */
    fun intercept(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        if (!shouldBlock(request.url.toString())) return null
        blockedCount++
        return WebResourceResponse("text/plain", "utf-8", java.io.ByteArrayInputStream(ByteArray(0)))
    }

    fun resetCount() {
        blockedCount = 0
    }
}
