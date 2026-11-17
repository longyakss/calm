package com.calm.core.downloader

import okhttp3.Dns
import java.net.InetAddress

/** GitHub 优选 IP 池（公开维护值，后续版本做成可在线更新）。 */
object GithubIpPool {
    val map: Map<String, List<String>> = mapOf(
        "github.com" to listOf("140.82.112.3", "140.82.113.4", "140.82.114.3", "20.205.243.166"),
        "api.github.com" to listOf("140.82.112.5", "140.82.113.6", "20.205.243.168"),
        "codeload.github.com" to listOf("140.82.112.9", "140.82.113.10", "20.205.243.164"),
        "raw.githubusercontent.com" to listOf("185.199.108.133", "185.199.109.133", "185.199.110.133", "185.199.111.133"),
        "objects.githubusercontent.com" to listOf("185.199.108.133", "185.199.109.133", "185.199.110.133", "185.199.111.133"),
        "release-assets.githubusercontent.com" to listOf("185.199.108.133", "185.199.109.133", "185.199.110.133"),
        "avatars.githubusercontent.com" to listOf("185.199.108.133", "185.199.109.133"),
        "assets-cdn.github.com" to listOf("185.199.108.153", "185.199.109.153", "185.199.110.153", "185.199.111.153"),
        "github.githubassets.com" to listOf("185.199.108.153", "185.199.109.154", "185.199.110.153"),
        "gist.githubusercontent.com" to listOf("185.199.108.133", "185.199.109.133"),
    )

    private const val USERCONTENT_IP = "185.199.108.133"

    fun ipsFor(host: String): List<String>? {
        map[host]?.let { return it }
        if (host.endsWith(".githubusercontent.com") || host.endsWith(".githubassets.com")) {
            return listOf(USERCONTENT_IP)
        }
        return null
    }
}

/**
 * GitHub DNS 纠偏：GitHub 域名用优选 IP（InetAddress 携带原 hostname，TLS SNI/证书校验不受影响），
 * 其余域名 DoH → 系统 DNS。下载引擎全局挂载：非 GitHub 域名行为与系统一致。
 */
class GithubDns(private val doh: Dns? = null) : Dns {

    override fun lookup(hostname: String): List<InetAddress> {
        val ips = GithubIpPool.ipsFor(hostname)
        if (!ips.isNullOrEmpty()) {
            val resolved = ips.mapNotNull { ip ->
                runCatching {
                    InetAddress.getByAddress(hostname, InetAddress.getByName(ip).address)
                }.getOrNull()
            }
            if (resolved.isNotEmpty()) return resolved
        }
        return try {
            doh?.lookup(hostname) ?: Dns.SYSTEM.lookup(hostname)
        } catch (_: Exception) {
            Dns.SYSTEM.lookup(hostname)
        }
    }
}
