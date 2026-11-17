package com.calm.core.browser

import android.content.Context
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import com.calm.core.downloader.GithubDns
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.Dns
import java.io.BufferedInputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.thread

/**
 * GitHub 免镜像直连加速（BR-028 增强版，按《github加速.md》方案裁剪）：
 * 本地回环 HTTP 代理（CONNECT 透明隧道）+ DNS 纠偏（优选 IP / DoH）+ WebView ProxyController 接管。
 *
 * 与原方案的差异（安全取舍）：不做 MITM 证书解密——透明隧道下 TLS 端到端保留，
 * 登录 / Cookie / 2FA / OAuth 天然可用，无需引导用户安装 CA 证书；
 * SNI 随原始 ClientHello 原样透传。非 GitHub 域名同样走隧道直连，零改写零额外依赖（Netty/BC 均不需要）。
 */

/**
 * 本地回环代理：仅监听 127.0.0.1。
 * CONNECT → DNS 纠偏后与远端建连，字节透传（TLS 不解密）；
 * absolute-form HTTP → 转为 origin-form 转发（WebView 基本只用 CONNECT/HTTPS，此路径兜底）。
 */
class GithubProxyServer(private val dns: Dns) {

    private var serverSocket: ServerSocket? = null

    @Volatile
    private var running = false

    val port: Int get() = serverSocket?.localPort ?: -1
    val isRunning: Boolean get() = running

    fun start() {
        if (running) return
        val ss = ServerSocket(0, 64, InetAddress.getByName("127.0.0.1"))
        serverSocket = ss
        running = true
        thread(name = "calm-gh-proxy", isDaemon = true) {
            while (running) {
                try {
                    val client = ss.accept()
                    thread(isDaemon = true) { runCatching { handle(client) } }
                } catch (_: Exception) {
                    if (!running) break
                }
            }
        }
    }

    fun stop() {
        running = false
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    private fun connectRemote(host: String, port: Int): Socket {
        val addr = try {
            dns.lookup(host).first()
        } catch (_: Exception) {
            InetAddress.getByName(host)
        }
        return Socket().apply {
            tcpNoDelay = true
            connect(InetSocketAddress(addr, port), 10_000)
        }
    }

    private fun handle(client: Socket) {
        client.soTimeout = 15_000
        val input = BufferedInputStream(client.getInputStream())
        val out = client.getOutputStream()

        val requestLine = readLine(input) ?: return
        val parts = requestLine.split(" ")
        if (parts.size < 3) return

        if (parts[0] == "CONNECT") {
            val host = parts[1].substringBefore(':')
            val port = parts[1].substringAfter(':', "443").toIntOrNull() ?: 443
            val remote = connectRemote(host, port)
            out.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
            out.flush()
            client.soTimeout = 0
            pump(client, remote)
        } else {
            // absolute-form → origin-form
            val afterScheme = parts[1].substringAfter("://", "")
            if (afterScheme.isEmpty()) return
            val authority = afterScheme.substringBefore('/')
            val pathAndQuery = afterScheme.substringAfter('/', "")
            val host = authority.substringBefore(':')
            val port = when {
                parts[1].startsWith("https") -> 443
                else -> authority.substringAfter(':', "80").toIntOrNull() ?: 80
            }
            val headers = StringBuilder()
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                headers.append(line).append("\r\n")
            }
            val remote = connectRemote(host, port)
            remote.getOutputStream().apply {
                write("${parts[0]} /$pathAndQuery ${parts[2]}\r\n".toByteArray())
                write(headers.toString().toByteArray())
                write("\r\n".toByteArray())
                flush()
            }
            client.soTimeout = 0
            pump(client, remote)
        }
    }

    private fun pump(a: Socket, b: Socket) {
        val t = thread(isDaemon = true) {
            runCatching { a.getInputStream().copyTo(b.getOutputStream()) }
            runCatching { b.close() }
            runCatching { a.close() }
        }
        runCatching { b.getInputStream().copyTo(a.getOutputStream()) }
        runCatching { b.close() }
        runCatching { a.close() }
        runCatching { t.join(500) }
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val c = input.read()
            if (c < 0) return if (sb.isEmpty()) null else sb.toString()
            if (c == '\n'.code) return sb.toString().trim()
            if (c != '\r'.code) sb.append(c.toChar())
            if (sb.length > 16 * 1024) return null
        }
    }
}

/** 直连加速总管：开关持久化、代理生命周期、WebView 接管/还原。 */
@Singleton
class GithubProxyManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val sp = context.getSharedPreferences("browser_settings", Context.MODE_PRIVATE)

    private var server: GithubProxyServer? = null

    val prefEnabled: Boolean get() = sp.getBoolean("ghDirect", false)
    val isRunning: Boolean get() = server?.isRunning == true
    val port: Int get() = server?.port ?: -1

    /** 开启：启动本地代理 + WebView 全局接管（非 GitHub 域名隧道透传，零改写）。 */
    fun enable(): Boolean {
        return runCatching {
            if (server?.isRunning != true) {
                server = GithubProxyServer(GithubDns()).also { it.start() }
            }
            applyWebViewProxy(port)
            sp.edit().putBoolean("ghDirect", true).apply()
            true
        }.getOrElse {
            disable()
            false
        }
    }

    /** 关闭：还原 WebView 直连 + 停止代理。 */
    fun disable() {
        runCatching {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                ProxyController.getInstance().clearProxyOverride(Runnable { })
            }
        }
        server?.stop()
        server = null
        sp.edit().putBoolean("ghDirect", false).apply()
    }

    private fun applyWebViewProxy(port: Int) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) return
        val config = ProxyConfig.Builder()
            .addProxyRule("127.0.0.1:$port")
            .addBypassRule("<-loopback>")
            .build()
        ProxyController.getInstance().setProxyOverride(
            config,
            Executors.newSingleThreadExecutor(),
            Runnable { },
        )
    }

    /** App 启动时恢复上次开启状态（setProxyOverride 不跨进程存活）。 */
    fun restoreOnStartup(): Boolean {
        if (!prefEnabled) return false
        return enable()
    }
}
