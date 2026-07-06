package com.amwangfan.omnireader.reader

import com.amwangfan.omnireader.data.ReadingLocator

enum class LocatorResolutionReason { EXACT, HASH_IN_CHAPTER, HREF_INDEX, CHAPTER_PROGRESS, BOOK_PROGRESS, CHAPTER_START }

data class LocatorResolution(
    val chapterIndex: Int,
    val blockIndex: Int,
    val charOffset: Int,
    val reason: LocatorResolutionReason,
    val revisionMismatch: Boolean,
)

object LocatorResolver {
    fun locatorFor(chapters: List<EpubChapter>, revision: String, chapterIndex: Int, blockIndex: Int, charOffset: Int = 0): ReadingLocator {
        require(chapters.isNotEmpty())
        val ci = chapterIndex.coerceIn(chapters.indices)
        val chapter = chapters[ci]
        val bi = blockIndex.coerceIn(0, chapter.blocks.lastIndex.coerceAtLeast(0))
        val block = chapter.blocks.getOrNull(bi)
        val before = chapters.take(ci).sumOf { it.blocks.size }
        val total = chapters.sumOf { it.blocks.size }.coerceAtLeast(1)
        return ReadingLocator(
            contentRevision = revision,
            chapterHref = chapter.href,
            chapterIndex = ci,
            blockIndex = bi,
            charOffset = charOffset.coerceIn(0, block?.text?.length ?: 0),
            textQuote = block?.text.orEmpty().take(160),
            textHash = block?.textHash.orEmpty(),
            chapterProgress = if (chapter.blocks.size <= 1) 0.0 else bi.toDouble() / chapter.blocks.lastIndex,
            bookProgress = (before + bi).toDouble() / total,
        )
    }

    fun resolve(chapters: List<EpubChapter>, locator: ReadingLocator, currentRevision: String): LocatorResolution {
        require(chapters.isNotEmpty())
        val mismatch = locator.contentRevision.isNotBlank() && currentRevision.isNotBlank() && locator.contentRevision != currentRevision
        val hrefIndex = chapters.indexOfFirst { it.href == locator.chapterHref }
        if (hrefIndex >= 0) {
            val chapter = chapters[hrefIndex]
            val indexed = chapter.blocks.getOrNull(locator.blockIndex)
            if (locator.textHash.isNotBlank() && indexed?.textHash == locator.textHash) return result(hrefIndex, locator.blockIndex, locator.charOffset, LocatorResolutionReason.EXACT, mismatch, indexed)
            val hashIndex = chapter.blocks.indexOfFirst { locator.textHash.isNotBlank() && it.textHash == locator.textHash }
            if (hashIndex >= 0) return result(hrefIndex, hashIndex, locator.charOffset, LocatorResolutionReason.HASH_IN_CHAPTER, mismatch, chapter.blocks[hashIndex])
            if (chapter.blocks.isNotEmpty()) {
                val bi = locator.blockIndex.coerceIn(chapter.blocks.indices)
                return result(hrefIndex, bi, locator.charOffset, LocatorResolutionReason.HREF_INDEX, mismatch, chapter.blocks[bi])
            }
        }
        if (locator.chapterIndex in chapters.indices) {
            val chapter = chapters[locator.chapterIndex]
            val bi = progressIndex(chapter.blocks.size, locator.chapterProgress)
            return result(locator.chapterIndex, bi, 0, LocatorResolutionReason.CHAPTER_PROGRESS, mismatch, chapter.blocks.getOrNull(bi))
        }
        val total = chapters.sumOf { it.blocks.size }
        if (total > 0 && locator.bookProgress > 0.0 && locator.bookProgress <= 1.0) {
            var absolute = (locator.bookProgress * (total - 1)).toInt()
            chapters.forEachIndexed { ci, chapter ->
                if (absolute < chapter.blocks.size) return result(ci, absolute, 0, LocatorResolutionReason.BOOK_PROGRESS, mismatch, chapter.blocks[absolute])
                absolute -= chapter.blocks.size
            }
        }
        val ci = locator.chapterIndex.coerceIn(chapters.indices)
        return result(ci, 0, 0, LocatorResolutionReason.CHAPTER_START, mismatch, chapters[ci].blocks.firstOrNull())
    }

    private fun progressIndex(size: Int, progress: Double) = if (size <= 1) 0 else (progress.coerceIn(0.0, 1.0) * (size - 1)).toInt()
    private fun result(ci: Int, bi: Int, offset: Int, reason: LocatorResolutionReason, mismatch: Boolean, block: EpubBlock?) =
        LocatorResolution(ci, bi, offset.coerceIn(0, block?.text?.length ?: 0), reason, mismatch)
}
