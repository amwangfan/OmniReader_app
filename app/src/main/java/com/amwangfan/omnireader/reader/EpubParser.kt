package com.amwangfan.omnireader.reader

import java.io.File
import java.io.StringReader
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource

data class EpubDocument(
    val title: String,
    val author: String,
    val chapters: List<EpubChapter>,
)

data class EpubChapter(
    val title: String,
    val text: String,
)

class EpubParser {
    fun parse(file: File): EpubDocument {
        ZipFile(file).use { zip ->
            val container = parseXml(zip.readTextEntry("META-INF/container.xml"))
            val opfPath = container.elementsByLocalName("rootfile")
                .firstOrNull()
                ?.getAttribute("full-path")
                ?.takeIf { it.isNotBlank() }
                ?: error("EPUB rootfile is missing")
            val opf = parseXml(zip.readTextEntry(opfPath))
            val title = opf.elementsByLocalName("title")
                .firstOrNull()
                ?.textContent
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: file.nameWithoutExtension
            val author = opf.elementsByLocalName("creator")
                .firstOrNull()
                ?.textContent
                ?.trim()
                .orEmpty()

            val manifest = opf.elementsByLocalName("item").associate { item ->
                item.getAttribute("id") to ManifestItem(
                    href = item.getAttribute("href"),
                    mediaType = item.getAttribute("media-type"),
                )
            }
            val chapters = opf.elementsByLocalName("itemref")
                .mapNotNull { manifest[it.getAttribute("idref")] }
                .filter { it.href.isNotBlank() && it.mediaType.contains("html", ignoreCase = true) }
                .mapNotNull { item ->
                    val path = resolveZipPath(opfPath, item.href)
                    val html = runCatching { zip.readTextEntry(path) }.getOrNull() ?: return@mapNotNull null
                    htmlToChapter(html)
                }
                .filter { it.text.isNotBlank() }

            return EpubDocument(
                title = title,
                author = author,
                chapters = chapters.ifEmpty {
                    listOf(EpubChapter(title = title, text = "This EPUB has no readable XHTML spine yet."))
                },
            )
        }
    }

    private fun htmlToChapter(html: String): EpubChapter {
        val doc = runCatching { parseXml(html) }.getOrNull()
        if (doc == null) {
            return EpubChapter(title = "Chapter", text = stripTags(html))
        }
        val chapterTitle = listOf("h1", "h2", "h3", "title")
            .firstNotNullOfOrNull { name ->
                doc.elementsByLocalName(name).firstOrNull()?.textContent?.cleanText()?.takeIf { it.isNotBlank() }
            }
            ?: "Chapter"
        val body = doc.elementsByLocalName("body").firstOrNull() ?: doc.documentElement
        val text = buildString { appendNodeText(body, this) }
            .lineSequence()
            .map { it.cleanText() }
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
        return EpubChapter(title = chapterTitle, text = text)
    }

    private fun parseXml(text: String): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        return factory.newDocumentBuilder().parse(InputSource(StringReader(text)))
    }

    private fun Document.elementsByLocalName(name: String): List<Element> =
        documentElement.walkElements().filter { it.elementName() == name }

    private fun Element.walkElements(): List<Element> {
        val result = mutableListOf<Element>()
        fun visit(node: Node) {
            if (node is Element) {
                result += node
            }
            val children = node.childNodes
            for (index in 0 until children.length) {
                visit(children.item(index))
            }
        }
        visit(this)
        return result
    }

    private fun appendNodeText(node: Node, output: StringBuilder) {
        if (node.nodeType == Node.TEXT_NODE) {
            output.append(node.nodeValue)
            output.append(' ')
            return
        }
        if (node is Element && node.elementName() in blockElements) {
            output.append('\n')
        }
        val children = node.childNodes
        for (index in 0 until children.length) {
            appendNodeText(children.item(index), output)
        }
        if (node is Element && node.elementName() in blockElements) {
            output.append('\n')
        }
    }

    private fun Element.elementName(): String = localName ?: tagName.substringAfter(':')

    private fun stripTags(html: String): String =
        html.replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .cleanText()

    private fun String.cleanText(): String = replace(Regex("\\s+"), " ").trim()

    private fun resolveZipPath(opfPath: String, href: String): String {
        val base = opfPath.substringBeforeLast('/', missingDelimiterValue = "")
        val combined = if (base.isBlank()) href else "$base/$href"
        val segments = ArrayDeque<String>()
        combined.replace('\\', '/').split('/').forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> if (segments.isNotEmpty()) segments.removeLast()
                else -> segments.addLast(part)
            }
        }
        return segments.joinToString("/")
    }

    private fun ZipFile.readTextEntry(path: String): String {
        val entry = getEntry(path) ?: error("EPUB entry not found: $path")
        return getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private data class ManifestItem(
        val href: String,
        val mediaType: String,
    )

    private companion object {
        val blockElements = setOf(
            "body",
            "section",
            "article",
            "div",
            "p",
            "br",
            "li",
            "h1",
            "h2",
            "h3",
            "h4",
            "h5",
            "h6",
        )
    }
}
