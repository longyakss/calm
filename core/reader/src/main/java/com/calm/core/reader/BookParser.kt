package com.calm.core.reader

import org.jsoup.Jsoup
import java.io.File
import java.util.zip.ZipFile

/** 书籍解析（对应设计方案 16.5：txt 自研 / epub Zip+XML）。 */
object BookParser {

    data class Chapters(
        val titles: List<String>,
        /** txt：字符偏移（start,end）成对；epub：spine 文件序号 */
        val offsets: List<Int>,
        val totalChars: Long,
    )

    private val chapterRegex =
        Regex(
            "^\\s*(第[0-9零一二三四五六七八九十百千万两]+[章节卷回部集]|序章|序言|前言|楔子|尾声|后记|番外|Chapter\\s+\\d+).{0,60}$",
            RegexOption.MULTILINE,
        )

    fun decode(bytes: ByteArray): String = try {
        String(bytes, Charsets.UTF_8)
    } catch (_: Exception) {
        String(bytes, java.nio.charset.Charset.forName("GBK"))
    }

    /** txt → 全文 + 章节偏移。无章节标记时按 ~6000 字符切节。 */
    fun parseTxt(file: File): Pair<String, Chapters> {
        val text = decode(file.readBytes())
        val marks = chapterRegex.findAll(text).toList()
        val titles = mutableListOf<String>()
        val offsets = mutableListOf<Int>()
        if (marks.size >= 2) {
            // 正文开头（第一章之前的部分）作为「开头」
            val firstStart = marks.first().range.first
            if (firstStart > 500) {
                titles.add("开头")
                offsets.add(0)
            }
            marks.forEachIndexed { i, m ->
                titles.add(m.value.trim())
                offsets.add(m.range.first)
            }
        }
        if (offsets.isEmpty()) {
            val chunk = 6000
            var i = 0
            var idx = 0
            while (idx < text.length) {
                titles.add(if (marks.size == 1) (marks.first().value.trim() + if (i > 0) "（续$i）" else "") else "第 ${i + 1} 节")
                offsets.add(idx)
                idx += chunk
                i++
            }
        }
        return text to Chapters(titles, offsets, text.length.toLong())
    }

    data class EpubMeta(val title: String, val author: String, val spineFiles: List<String>, val opfDir: String)

    fun openEpub(file: File, extractDir: File): EpubMeta? {
        extractDir.mkdirs()
        ZipFile(file).use { zip ->
            // container.xml → opf
            val container = zip.getInputStream(zip.getEntry("META-INF/container.xml"))?.use {
                it.readBytes().toString(Charsets.UTF_8)
            } ?: return null
            val opfPath = Regex("full-path=\"([^\"]+)\"").find(container)?.groupValues?.get(1) ?: return null
            val opf = zip.getInputStream(zip.getEntry(opfPath))?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return null
            val doc = Jsoup.parse(opf, "", org.jsoup.parser.Parser.xmlParser())
            val opfDir = opfPath.substringBeforeLast('/', "")

            val title = Regex("<dc:title[^>]*>([^<]+)</dc:title>").find(opf)?.groupValues?.get(1) ?: "未知书名"
            val author = Regex("<dc:creator[^>]*>([^<]+)</dc:creator>").find(opf)?.groupValues?.get(1) ?: "未知作者"

            // manifest id → href
            val manifest = mutableMapOf<String, String>()
            Regex("<item[^>]*id=\"([^\"]+)\"[^>]*href=\"([^\"]+)\"").findAll(opf).forEach { m ->
                manifest[m.groupValues[1]] = m.groupValues[2]
            }
            // 顺序可能 href 在前
            Regex("<item[^>]*href=\"([^\"]+)\"[^>]*id=\"([^\"]+)\"").findAll(opf).forEach { m ->
                manifest.putIfAbsent(m.groupValues[2], m.groupValues[1])
            }

            val spine = Regex("<itemref[^>]*idref=\"([^\"]+)\"").findAll(opf).mapNotNull { m -> manifest[m.groupValues[1]] }.toList()
            val files = mutableListOf<String>()
            spine.forEachIndexed { i, href ->
                val entry = if (opfDir.isBlank()) href else "$opfDir/$href"
                val zEntry = zip.getEntry(entry) ?: return@forEachIndexed
                val outFile = File(extractDir, "ch$i.html")
                zip.getInputStream(zEntry).use { ins -> outFile.outputStream().use { ins.copyTo(it) } }
                files.add(outFile.absolutePath)
            }
            return EpubMeta(title, author, files, opfDir)
        }
    }

    /** 解析单个 epub 章节 html → 段落列表。 */
    fun epubChapter(paragraphsFile: String): List<String> = try {
        val doc = Jsoup.parse(File(paragraphsFile), "UTF-8")
        val body = doc.body()
        val out = mutableListOf<String>()
        body.select("p, h1, h2, h3, h4, div").forEach { el ->
            val t = el.ownText().trim()
            if (t.length > 10 || (el.tagName() in setOf("h1", "h2", "h3") && t.isNotBlank())) {
                if (t.isNotBlank()) out.add(t)
            }
        }
        if (out.isEmpty()) out.add(body.text().trim())
        out
    } catch (_: Exception) {
        listOf("（章节解析失败）")
    }

    /** 读取 txt 章节（按字符偏移；整本解码后切片，章节切换时才调用）。 */
    fun txtChapter(file: File, start: Int, end: Int): List<String> {
        val text = decode(file.readBytes())
        val s = start.coerceIn(0, text.length)
        val e = if (end > start) end.coerceAtMost(text.length) else s
        return text.substring(s, e).lines().filter { it.isNotBlank() }
    }
}
