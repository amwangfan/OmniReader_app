package com.amwangfan.omnireader.reader

import com.amwangfan.omnireader.data.ReadingLocator
import org.junit.Assert.assertEquals
import org.junit.Test

class LocatorResolverTest {
    private val chapters = listOf(
        chapter("a.xhtml", "alpha", "shared"),
        chapter("b.xhtml", "beta", "gamma", "delta"),
    )

    @Test fun locatorFor_calculatesHashesQuotesAndProgress() {
        val locator = LocatorResolver.locatorFor(chapters, "rev", 1, 1, 2)
        assertEquals("b.xhtml", locator.chapterHref)
        assertEquals(chapters[1].blocks[1].textHash, locator.textHash)
        assertEquals(0.5, locator.chapterProgress, 0.001)
        assertEquals(0.6, locator.bookProgress, 0.001)
    }

    @Test fun resolve_prefersExactHrefAndHash() {
        assertEquals(LocatorResolutionReason.EXACT, resolve(locator(href="b.xhtml", index=1, hash=chapters[1].blocks[1].textHash)).reason)
    }

    @Test fun resolve_searchesHashWithinChapter() {
        val result = resolve(locator(href="a.xhtml", index=0, hash=chapters[0].blocks[1].textHash))
        assertEquals(1, result.blockIndex)
        assertEquals(LocatorResolutionReason.HASH_IN_CHAPTER, result.reason)
    }

    @Test fun resolve_fallsBackThroughIndexChapterProgressAndBookProgress() {
        assertEquals(LocatorResolutionReason.HREF_INDEX, resolve(locator(hash="missing")).reason)
        assertEquals(LocatorResolutionReason.CHAPTER_PROGRESS, resolve(locator(href="missing", chapter=1, progress=.8)).reason)
        assertEquals(LocatorResolutionReason.BOOK_PROGRESS, resolve(locator(href="missing", chapter=99, bookProgress=.9)).reason)
    }

    @Test fun revisionMismatch_isReportedWithoutDiscardingPosition() {
        val result = LocatorResolver.resolve(chapters, locator(hash="missing", revision="old"), "new")
        assertEquals(LocatorResolutionReason.HREF_INDEX, result.reason)
        assertEquals(true, result.revisionMismatch)
    }

    @Test fun resolve_usesClosestChapterStartWhenNoProgressHintSurvives() {
        val result = resolve(locator(href="missing", chapter=99, bookProgress=0.0))
        assertEquals(1, result.chapterIndex)
        assertEquals(LocatorResolutionReason.CHAPTER_START, result.reason)
    }

    private fun resolve(locator: ReadingLocator) = LocatorResolver.resolve(chapters, locator, "rev")
    private fun locator(href: String="a.xhtml", chapter: Int=0, index: Int=0, hash: String="", progress: Double=0.0, bookProgress: Double=0.0, revision: String="rev") =
        ReadingLocator(contentRevision=revision, chapterHref=href, chapterIndex=chapter, blockIndex=index, textHash=hash, chapterProgress=progress, bookProgress=bookProgress)
    private fun chapter(href: String, vararg texts: String) = EpubChapter(href, href, texts.mapIndexed { i, text -> EpubBlock(i, "p", text, normalizedTextHash(text)) })
}
