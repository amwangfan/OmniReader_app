package com.amwangfan.omnireader.reader

import java.io.File
import java.io.StringReader
import java.security.MessageDigest
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource

data class EpubDocument(val title: String, val author: String, val chapters: List<EpubChapter>)

data class EpubChapter(val href: String, val title: String, val blocks: List<EpubBlock>) {
    val text: String get() = blocks.joinToString("\n\n") { it.text }
}

data class EpubBlock(val index: Int, val kind: String, val text: String, val textHash: String)

class EpubParser {
    fun parse(file: File): EpubDocument = ZipFile(file).use { zip ->
        val container = parseXml(zip.readTextEntry("META-INF/container.xml"))
        val opfPath = container.elements("rootfile").firstOrNull()?.getAttribute("full-path")
            ?.takeIf(String::isNotBlank) ?: error("EPUB rootfile is missing")
        val opf = parseXml(zip.readTextEntry(opfPath))
        val title = opf.elements("title").firstOrNull()?.textContent?.normalized()
            ?.takeIf(String::isNotBlank) ?: file.nameWithoutExtension
        val author = opf.elements("creator").firstOrNull()?.textContent?.normalized().orEmpty()
        val manifest = opf.elements("item").associate { it.getAttribute("id") to ManifestItem(it.getAttribute("href"), it.getAttribute("media-type")) }
        val chapters = opf.elements("itemref").mapNotNull { manifest[it.getAttribute("idref")] }
            .filter { it.href.isNotBlank() && it.mediaType.contains("html", true) }
            .mapNotNull { item ->
                val href = resolveZipPath(opfPath, item.href)
                runCatching { htmlToChapter(href, zip.readTextEntry(href)) }.getOrNull()
            }.filter { it.blocks.isNotEmpty() }
        EpubDocument(title, author, chapters.ifEmpty {
            listOf(chapterFromText("", title, "This EPUB has no readable XHTML spine yet."))
        })
    }

    private fun htmlToChapter(href: String, html: String): EpubChapter {
        val doc = runCatching { parseXml(html) }.getOrNull() ?: return chapterFromText(href, "Chapter", stripTags(html))
        val title = listOf("h1", "h2", "h3", "title").firstNotNullOfOrNull { name ->
            doc.elements(name).firstOrNull()?.textContent?.normalized()?.takeIf(String::isNotBlank)
        } ?: "Chapter"
        val body = doc.elements("body").firstOrNull() ?: doc.documentElement
        val blocks = body.walkElements().filter { it.elementName() in readableElements }.mapNotNull { element ->
            val text = element.textContent.normalized().takeIf(String::isNotBlank) ?: return@mapNotNull null
            element.elementName() to text
        }.mapIndexed { index, (kind, text) -> EpubBlock(index, kind, text, normalizedTextHash(text)) }
        return if (blocks.isEmpty()) chapterFromText(href, title, body.textContent.normalized()) else EpubChapter(href, title, blocks)
    }

    private fun chapterFromText(href: String, title: String, text: String): EpubChapter {
        val clean = text.normalized()
        return EpubChapter(href, title, if (clean.isBlank()) emptyList() else listOf(EpubBlock(0, "p", clean, normalizedTextHash(clean))))
    }

    private fun parseXml(text: String): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        return factory.newDocumentBuilder().parse(InputSource(StringReader(text)))
    }

    private fun Document.elements(name: String) = documentElement.walkElements().filter { it.elementName() == name }
    private fun Element.walkElements(): List<Element> = buildList {
        fun visit(node: Node) {
            if (node is Element) add(node)
            for (i in 0 until node.childNodes.length) visit(node.childNodes.item(i))
        }
        visit(this@walkElements)
    }
    private fun Element.elementName() = (localName ?: tagName.substringAfter(':')).lowercase()
    private fun String.normalized() = replace('\u00a0', ' ').replace(Regex("\\s+"), " ").trim()
    private fun stripTags(html: String) = html.replace(Regex("<[^>]+>"), " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").normalized()
    private fun resolveZipPath(opfPath: String, href: String): String {
        val base = opfPath.substringBeforeLast('/', "")
        val parts = ArrayDeque<String>()
        (if (base.isBlank()) href else "$base/$href").replace('\\', '/').split('/').forEach {
            when (it) { "", "." -> Unit; ".." -> if (parts.isNotEmpty()) parts.removeLast(); else -> parts.addLast(it) }
        }
        return parts.joinToString("/")
    }
    private fun ZipFile.readTextEntry(path: String) = getInputStream(getEntry(path) ?: error("EPUB entry not found: $path")).bufferedReader().use { it.readText() }
    private data class ManifestItem(val href: String, val mediaType: String)
    private companion object { val readableElements = setOf("p", "h1", "h2", "h3", "h4", "h5", "h6", "li", "blockquote", "pre") }
}

fun normalizedTextHash(text: String): String {
    val normalized = text.replace('\u00a0', ' ').replace(Regex("\\s+"), " ").trim()
    return MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
