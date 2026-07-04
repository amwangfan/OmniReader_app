package com.amwangfan.omnireader.reader

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubParserTest {
    @Test
    fun parse_readsTitleAndSpineChapters() {
        val epub = File.createTempFile("omnireader-test", ".epub")
        epub.deleteOnExit()
        ZipOutputStream(epub.outputStream()).use { zip ->
            zip.putText(
                "META-INF/container.xml",
                """
                <?xml version="1.0"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles>
                    <rootfile full-path="OPS/content.opf" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>
                """.trimIndent(),
            )
            zip.putText(
                "OPS/content.opf",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" unique-identifier="bookid" version="3.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>Sample EPUB</dc:title>
                  </metadata>
                  <manifest>
                    <item id="c1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
                    <item id="c2" href="text/chapter2.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine>
                    <itemref idref="c1"/>
                    <itemref idref="c2"/>
                  </spine>
                </package>
                """.trimIndent(),
            )
            zip.putText(
                "OPS/chapter1.xhtml",
                """
                <html xmlns="http://www.w3.org/1999/xhtml">
                  <body><h1>One</h1><p>Hello reader.</p></body>
                </html>
                """.trimIndent(),
            )
            zip.putText(
                "OPS/text/chapter2.xhtml",
                """
                <html xmlns="http://www.w3.org/1999/xhtml">
                  <body><h2>Two</h2><p>Second chapter.</p></body>
                </html>
                """.trimIndent(),
            )
        }

        val document = EpubParser().parse(epub)

        assertEquals("Sample EPUB", document.title)
        assertEquals(listOf("One", "Two"), document.chapters.map { it.title })
        assertTrue(document.chapters[0].text.contains("Hello reader."))
        assertTrue(document.chapters[1].text.contains("Second chapter."))
    }

    private fun ZipOutputStream.putText(path: String, text: String) {
        putNextEntry(ZipEntry(path))
        write(text.toByteArray(Charsets.UTF_8))
        closeEntry()
    }
}
