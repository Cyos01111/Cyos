package com.cyosplay.player.util

import androidx.annotation.OptIn
import androidx.media3.common.Metadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.id3.ChapterFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets

/**
 * Pure-Kotlin, bounded-I/O chapter extractor for Auto-Skip Intros & Credits.
 *
 * Extracts real chapter markers without ever fabricating arbitrary timestamps:
 * 1. Embedded Matroska / WebM EBML `Chapters` (`0x1043A770`) via O(1) top-level element skipping.
 * 2. Embedded MP4 / M4V / MOV `moov -> udta -> chpl` chapter atoms via O(1) box skipping.
 * 3. Sidecar chapter files alongside the video (`.chapters.txt`, `.chp`, `.ogm`, `.chapters.xml`, `.xml`).
 * 4. Media3 runtime ID3 `ChapterFrame` (`CHAP`) metadata emitted via `Player.Listener.onMetadata`.
 */
object MediaChapterExtractor {

    private const val MAX_SIDECAR_BYTES = 512 * 1024
    private const val MAX_CHAPTERS_PAYLOAD_BYTES = 2 * 1024 * 1024
    private const val MAX_TOP_LEVEL_ELEMENTS = 4_096

    // EBML Matroska Chapter element IDs
    private const val ID_EBML_HEADER = 0x1A45DFA3L
    private const val ID_SEGMENT = 0x18538067L
    private const val ID_CHAPTERS = 0x1043A770L
    private const val ID_EDITION_ENTRY = 0x45B9L
    private const val ID_CHAPTER_ATOM = 0xB6L
    private const val ID_CHAPTER_TIME_START = 0x91L
    private const val ID_CHAPTER_TIME_END = 0x92L
    private const val ID_CHAPTER_DISPLAY = 0x80L
    private const val ID_CHAP_STRING = 0x85L

    private val OGM_TIME_REGEX = Regex("""^CHAPTER(\d+)\s*=\s*(\d{1,2}):(\d{2}):(\d{2})(?:[.,](\d{1,3}))?$""", RegexOption.IGNORE_CASE)
    private val OGM_NAME_REGEX = Regex("""^CHAPTER(\d+)NAME\s*=\s*(.*)$""", RegexOption.IGNORE_CASE)
    private val SIMPLE_LINE_REGEX = Regex("""^(\d{1,2}:)?(\d{1,2}):(\d{2})(?:[.,](\d{1,3}))?\s+[-–—:]?\s*(.+)$""")
    private val XML_ATOM_REGEX = Regex("""<ChapterAtom\b[^>]*>(.*?)</ChapterAtom>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val XML_START_REGEX = Regex("""<ChapterTimeStart>\s*([^<]+?)\s*</ChapterTimeStart>""", RegexOption.IGNORE_CASE)
    private val XML_END_REGEX = Regex("""<ChapterTimeEnd>\s*([^<]+?)\s*</ChapterTimeEnd>""", RegexOption.IGNORE_CASE)
    private val XML_TITLE_REGEX = Regex("""<ChapterString>\s*([^<]+?)\s*</ChapterString>""", RegexOption.IGNORE_CASE)

    /**
     * Extracts real chapter segments for a local video file path (runs safely on `Dispatchers.IO`).
     * Never invents chapters when none exist in the container or sidecar files.
     */
    fun extractChaptersForLocalVideo(
        filePath: String,
        totalDurationMs: Long = 0L
    ): List<PlayerGestureControlHelper.ChapterSegment> {
        if (filePath.isBlank()) return emptyList()
        val videoFile = File(filePath)
        if (!videoFile.exists() || !videoFile.isFile) return emptyList()

        // 1. Prefer explicit sidecar chapter files when present next to the video
        val sidecar = findAndParseSidecarChapters(videoFile, totalDurationMs)
        if (sidecar.isNotEmpty()) return sidecar

        // 2. Inspect binary container headers via O(1) element/box seeking
        val ext = videoFile.extension.lowercase()
        val embedded = when {
            ext in setOf("mkv", "mka", "mks", "webm") || hasEbmlMagic(videoFile) -> {
                parseMkvEbmlChapters(videoFile, totalDurationMs)
            }
            ext in setOf("mp4", "m4v", "mov", "3gp", "f4v") -> {
                parseMp4ChplChapters(videoFile, totalDurationMs)
            }
            else -> emptyList()
        }
        return finalizeChapterEndTimes(embedded, totalDurationMs)
    }

    @OptIn(UnstableApi::class)
    fun extractChaptersFromMedia3Metadata(
        metadata: Metadata,
        totalDurationMs: Long = 0L
    ): List<PlayerGestureControlHelper.ChapterSegment> {
        val raw = mutableListOf<PlayerGestureControlHelper.ChapterSegment>()
        for (i in 0 until metadata.length()) {
            val entry = metadata.get(i)
            if (entry is ChapterFrame) {
                var title = entry.chapterId.orEmpty()
                for (j in 0 until entry.subFrameCount) {
                    val sub = entry.getSubFrame(j)
                    if (sub is TextInformationFrame && sub.values.isNotEmpty()) {
                        title = sub.values.first()
                        break
                    }
                }
                val startMs = entry.startTimeMs.toLong().coerceAtLeast(0L)
                val endMs = entry.endTimeMs.toLong().coerceAtLeast(startMs)
                if (title.isNotBlank() && endMs > startMs) {
                    raw.add(PlayerGestureControlHelper.ChapterSegment(title.trim(), startMs, endMs))
                }
            }
        }
        return finalizeChapterEndTimes(raw, totalDurationMs)
    }

    fun findAndParseSidecarChapters(
        videoFile: File,
        totalDurationMs: Long = 0L
    ): List<PlayerGestureControlHelper.ChapterSegment> {
        val parent = videoFile.parentFile ?: return emptyList()
        val baseName = videoFile.nameWithoutExtension
        val candidates = listOf(
            File(parent, "$baseName.chapters.txt"),
            File(parent, "$baseName.chp"),
            File(parent, "$baseName.ogm"),
            File(parent, "$baseName.chapters.xml"),
            File(parent, "$baseName.xml")
        )
        for (candidate in candidates) {
            if (candidate.exists() && candidate.isFile && candidate.length() in 1L..MAX_SIDECAR_BYTES) {
                val parsed = runCatching {
                    val text = candidate.readText(StandardCharsets.UTF_8)
                    parseSidecarChapterText(text, totalDurationMs)
                }.getOrDefault(emptyList())
                if (parsed.isNotEmpty()) return parsed
            }
        }
        return emptyList()
    }

    fun parseSidecarChapterText(
        rawText: String,
        totalDurationMs: Long = 0L
    ): List<PlayerGestureControlHelper.ChapterSegment> {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return emptyList()

        // 1. Matroska XML chapters
        if (trimmed.contains("<ChapterAtom", ignoreCase = true)) {
            val xmlChapters = mutableListOf<PlayerGestureControlHelper.ChapterSegment>()
            XML_ATOM_REGEX.findAll(trimmed).forEach { match ->
                val block = match.groupValues[1]
                val startStr = XML_START_REGEX.find(block)?.groupValues?.get(1) ?: return@forEach
                val endStr = XML_END_REGEX.find(block)?.groupValues?.get(1)
                val title = XML_TITLE_REGEX.find(block)?.groupValues?.get(1)?.trim().orEmpty()
                val startMs = parseTimestampToMs(startStr) ?: return@forEach
                val endMs = endStr?.let { parseTimestampToMs(it) } ?: 0L
                if (title.isNotEmpty()) {
                    xmlChapters.add(PlayerGestureControlHelper.ChapterSegment(title, startMs, endMs))
                }
            }
            if (xmlChapters.isNotEmpty()) {
                return finalizeChapterEndTimes(xmlChapters, totalDurationMs)
            }
        }

        // 2. OGG / OGM chapter text (CHAPTER01=00:00:00.000 / CHAPTER01NAME=Intro)
        val ogmTimes = linkedMapOf<Int, Long>()
        val ogmNames = linkedMapOf<Int, String>()
        val simpleEntries = mutableListOf<Pair<Long, String>>()

        trimmed.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            val timeMatch = OGM_TIME_REGEX.matchEntire(line)
            if (timeMatch != null) {
                val idx = timeMatch.groupValues[1].toIntOrNull() ?: return@forEach
                val h = timeMatch.groupValues[2].toLongOrNull() ?: 0L
                val m = timeMatch.groupValues[3].toLongOrNull() ?: 0L
                val s = timeMatch.groupValues[4].toLongOrNull() ?: 0L
                val msStr = timeMatch.groupValues[5].padEnd(3, '0').take(3)
                val ms = msStr.toLongOrNull() ?: 0L
                ogmTimes[idx] = ((h * 3600L + m * 60L + s) * 1000L) + ms
                return@forEach
            }
            val nameMatch = OGM_NAME_REGEX.matchEntire(line)
            if (nameMatch != null) {
                val idx = nameMatch.groupValues[1].toIntOrNull() ?: return@forEach
                ogmNames[idx] = nameMatch.groupValues[2].trim()
                return@forEach
            }
            val simpleMatch = SIMPLE_LINE_REGEX.matchEntire(line)
            if (simpleMatch != null) {
                val h = simpleMatch.groupValues[1].trimEnd(':').toLongOrNull() ?: 0L
                val m = simpleMatch.groupValues[2].toLongOrNull() ?: 0L
                val s = simpleMatch.groupValues[3].toLongOrNull() ?: 0L
                val msStr = simpleMatch.groupValues[4].padEnd(3, '0').take(3)
                val ms = msStr.toLongOrNull() ?: 0L
                val title = simpleMatch.groupValues[5].trim()
                if (title.isNotEmpty()) {
                    simpleEntries.add((((h * 3600L + m * 60L + s) * 1000L) + ms) to title)
                }
            }
        }

        if (ogmTimes.isNotEmpty()) {
            val segments = ogmTimes.keys.sorted().mapNotNull { idx ->
                val startMs = ogmTimes[idx] ?: return@mapNotNull null
                val title = ogmNames[idx]?.takeIf { it.isNotBlank() } ?: "Chapter $idx"
                PlayerGestureControlHelper.ChapterSegment(title, startMs, 0L)
            }
            return finalizeChapterEndTimes(segments, totalDurationMs)
        }

        if (simpleEntries.isNotEmpty()) {
            val segments = simpleEntries.map { (startMs, title) ->
                PlayerGestureControlHelper.ChapterSegment(title, startMs, 0L)
            }
            return finalizeChapterEndTimes(segments, totalDurationMs)
        }

        return emptyList()
    }

    /**
     * Scans Matroska/WebM EBML top-level Segment children via O(1) seeks to locate the
     * `Chapters` (`0x1043A770`) element without decoding any video/audio cluster payloads.
     */
    fun parseMkvEbmlChapters(
        file: File,
        totalDurationMs: Long = 0L
    ): List<PlayerGestureControlHelper.ChapterSegment> {
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val fileLen = raf.length()
                if (fileLen < 16L) return@use emptyList()

                val firstId = readEbmlElementId(raf) ?: return@use emptyList()
                if (firstId != ID_EBML_HEADER) return@use emptyList()
                val headerSize = readEbmlVintSize(raf) ?: return@use emptyList()
                val afterHeader = raf.filePointer + headerSize
                if (afterHeader >= fileLen) return@use emptyList()
                raf.seek(afterHeader)

                val segId = readEbmlElementId(raf) ?: return@use emptyList()
                if (segId != ID_SEGMENT) return@use emptyList()
                val segSize = readEbmlVintSize(raf) ?: return@use emptyList()
                val segEnd = if (segSize < 0L) fileLen else minOf(fileLen, raf.filePointer + segSize)

                var count = 0
                while (raf.filePointer < segEnd && count < MAX_TOP_LEVEL_ELEMENTS) {
                    count++
                    val elemId = readEbmlElementId(raf) ?: break
                    val elemSize = readEbmlVintSize(raf) ?: break
                    val payloadStart = raf.filePointer
                    if (elemSize < 0L) break
                    if (elemId == ID_CHAPTERS) {
                        if (elemSize > MAX_CHAPTERS_PAYLOAD_BYTES) break
                        val payload = ByteArray(elemSize.toInt())
                        raf.readFully(payload)
                        return@use parseEbmlChaptersPayload(payload, totalDurationMs)
                    }
                    val nextPos = payloadStart + elemSize
                    if (nextPos <= payloadStart || nextPos > segEnd) break
                    raf.seek(nextPos)
                }
                emptyList()
            }
        }.getOrDefault(emptyList())
    }

    private fun parseEbmlChaptersPayload(
        payload: ByteArray,
        totalDurationMs: Long
    ): List<PlayerGestureControlHelper.ChapterSegment> {
        val chapters = mutableListOf<PlayerGestureControlHelper.ChapterSegment>()
        parseEbmlContainer(payload, 0, payload.size) { id, start, len ->
            if (id == ID_EDITION_ENTRY) {
                parseEbmlContainer(payload, start, start + len) { childId, childStart, childLen ->
                    if (childId == ID_CHAPTER_ATOM) {
                        var timeStartNs = -1L
                        var timeEndNs = -1L
                        var chapTitle = ""
                        parseEbmlContainer(payload, childStart, childStart + childLen) { atomId, aStart, aLen ->
                            when (atomId) {
                                ID_CHAPTER_TIME_START -> timeStartNs = readBigEndianUnsignedLong(payload, aStart, aLen)
                                ID_CHAPTER_TIME_END -> timeEndNs = readBigEndianUnsignedLong(payload, aStart, aLen)
                                ID_CHAPTER_DISPLAY -> {
                                    parseEbmlContainer(payload, aStart, aStart + aLen) { dispId, dStart, dLen ->
                                        if (dispId == ID_CHAP_STRING && chapTitle.isEmpty()) {
                                            chapTitle = String(payload, dStart, dLen, StandardCharsets.UTF_8).trim()
                                        }
                                    }
                                }
                            }
                        }
                        if (timeStartNs >= 0L && chapTitle.isNotEmpty()) {
                            val startMs = timeStartNs / 1_000_000L
                            val endMs = if (timeEndNs > timeStartNs) timeEndNs / 1_000_000L else 0L
                            chapters.add(PlayerGestureControlHelper.ChapterSegment(chapTitle, startMs, endMs))
                        }
                    }
                }
            }
        }
        return finalizeChapterEndTimes(chapters, totalDurationMs)
    }

    /**
     * Scans MP4 / M4V / MOV top-level boxes via O(1) seeks for `moov -> udta -> chpl` (Nero chapters).
     */
    fun parseMp4ChplChapters(
        file: File,
        totalDurationMs: Long = 0L
    ): List<PlayerGestureControlHelper.ChapterSegment> {
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val fileLen = raf.length()
                val moovBounds = findMp4Box(raf, 0L, fileLen, "moov") ?: return@use emptyList()
                val udtaBounds = findMp4Box(raf, moovBounds.first, moovBounds.second, "udta") ?: return@use emptyList()
                val chplBounds = findMp4Box(raf, udtaBounds.first, udtaBounds.second, "chpl") ?: return@use emptyList()
                val payloadLen = (chplBounds.second - chplBounds.first).toInt()
                if (payloadLen < 9 || payloadLen > MAX_CHAPTERS_PAYLOAD_BYTES) return@use emptyList()

                raf.seek(chplBounds.first)
                val buf = ByteArray(payloadLen)
                raf.readFully(buf)

                // chpl FullBox: [1B version][3B flags][4B reserved (if v1)][1B count (or 4B)]
                val version = buf[0].toInt() and 0xFF
                var cursor = if (version == 1) 8 else 4
                if (cursor >= buf.size) return@use emptyList()
                val entryCount = buf[cursor].toInt() and 0xFF
                cursor += 1

                val chapters = mutableListOf<PlayerGestureControlHelper.ChapterSegment>()
                repeat(entryCount) {
                    if (cursor + 9 > buf.size) return@repeat
                    // Timestamp is in 100-nanosecond units (10,000 ticks per millisecond)
                    val ticks100ns = readBigEndianUnsignedLong(buf, cursor, 8)
                    cursor += 8
                    val titleLen = buf[cursor].toInt() and 0xFF
                    cursor += 1
                    if (cursor + titleLen > buf.size) return@repeat
                    val title = String(buf, cursor, titleLen, StandardCharsets.UTF_8).trim()
                    cursor += titleLen
                    val startMs = (ticks100ns / 10_000L).coerceAtLeast(0L)
                    if (title.isNotEmpty()) {
                        chapters.add(PlayerGestureControlHelper.ChapterSegment(title, startMs, 0L))
                    }
                }
                finalizeChapterEndTimes(chapters, totalDurationMs)
            }
        }.getOrDefault(emptyList())
    }

    private fun findMp4Box(
        raf: RandomAccessFile,
        rangeStart: Long,
        rangeEnd: Long,
        targetType: String
    ): Pair<Long, Long>? {
        var pos = rangeStart
        var steps = 0
        val header = ByteArray(8)
        while (pos + 8L <= rangeEnd && steps < MAX_TOP_LEVEL_ELEMENTS) {
            steps++
            raf.seek(pos)
            raf.readFully(header)
            var boxSize = readBigEndianUnsignedLong(header, 0, 4)
            val boxType = String(header, 4, 4, StandardCharsets.US_ASCII)
            var headerLen = 8L
            if (boxSize == 1L) {
                if (pos + 16L > rangeEnd) break
                val ext = ByteArray(8)
                raf.readFully(ext)
                boxSize = readBigEndianUnsignedLong(ext, 0, 8)
                headerLen = 16L
            } else if (boxSize == 0L) {
                boxSize = rangeEnd - pos
            }
            if (boxSize < headerLen || pos + boxSize > rangeEnd) break
            if (boxType == targetType) {
                return (pos + headerLen) to (pos + boxSize)
            }
            pos += boxSize
        }
        return null
    }

    private fun hasEbmlMagic(file: File): Boolean {
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                if (raf.length() < 4L) return@use false
                raf.read() == 0x1A && raf.read() == 0x45 && raf.read() == 0xDF && raf.read() == 0xA3
            }
        }.getOrDefault(false)
    }

    private fun readEbmlElementId(raf: RandomAccessFile): Long? {
        val first = raf.read()
        if (first < 0) return null
        var mask = 0x80
        var length = 1
        while (length <= 4 && (first and mask) == 0) {
            mask = mask shr 1
            length++
        }
        if (length > 4) return null
        var value = first.toLong()
        repeat(length - 1) {
            val b = raf.read()
            if (b < 0) return null
            value = (value shl 8) or (b.toLong() and 0xFFL)
        }
        return value
    }

    private fun readEbmlVintSize(raf: RandomAccessFile): Long? {
        val first = raf.read()
        if (first < 0) return null
        var mask = 0x80
        var length = 1
        while (length <= 8 && (first and mask) == 0) {
            mask = mask shr 1
            length++
        }
        if (length > 8) return null
        var value = (first and (mask - 1)).toLong()
        var allOnes = (first and (mask - 1)) == (mask - 1)
        repeat(length - 1) {
            val b = raf.read()
            if (b < 0) return null
            if (b != 0xFF) allOnes = false
            value = (value shl 8) or (b.toLong() and 0xFFL)
        }
        return if (allOnes) -1L else value
    }

    private inline fun parseEbmlContainer(
        data: ByteArray,
        startOffset: Int,
        endOffset: Int,
        onElement: (id: Long, payloadStart: Int, payloadLen: Int) -> Unit
    ) {
        var pos = startOffset
        while (pos < endOffset) {
            val firstIdByte = data[pos].toInt() and 0xFF
            var idMask = 0x80
            var idLen = 1
            while (idLen <= 4 && (firstIdByte and idMask) == 0) {
                idMask = idMask shr 1
                idLen++
            }
            if (idLen > 4 || pos + idLen >= endOffset) break
            var id = firstIdByte.toLong()
            for (i in 1 until idLen) {
                id = (id shl 8) or (data[pos + i].toLong() and 0xFFL)
            }
            pos += idLen

            val firstSizeByte = data[pos].toInt() and 0xFF
            var sizeMask = 0x80
            var sizeLen = 1
            while (sizeLen <= 8 && (firstSizeByte and sizeMask) == 0) {
                sizeMask = sizeMask shr 1
                sizeLen++
            }
            if (sizeLen > 8 || pos + sizeLen > endOffset) break
            var size = (firstSizeByte and (sizeMask - 1)).toLong()
            for (i in 1 until sizeLen) {
                size = (size shl 8) or (data[pos + i].toLong() and 0xFFL)
            }
            pos += sizeLen
            val intSize = size.toInt()
            if (intSize < 0 || pos + intSize > endOffset) break
            onElement(id, pos, intSize)
            pos += intSize
        }
    }

    private fun readBigEndianUnsignedLong(data: ByteArray, offset: Int, length: Int): Long {
        var value = 0L
        val safeLen = length.coerceIn(0, 8)
        for (i in 0 until safeLen) {
            value = (value shl 8) or (data[offset + i].toLong() and 0xFFL)
        }
        return value
    }

    private fun parseTimestampToMs(raw: String): Long? {
        val parts = raw.trim().split(":")
        if (parts.size !in 2..3) return null
        val h = if (parts.size == 3) parts[0].toLongOrNull() ?: return null else 0L
        val m = parts[parts.size - 2].toLongOrNull() ?: return null
        val secAndFrac = parts[parts.size - 1].replace(',', '.').split(".")
        val s = secAndFrac[0].toLongOrNull() ?: return null
        val fracStr = secAndFrac.getOrNull(1)?.padEnd(3, '0')?.take(3) ?: "000"
        val ms = fracStr.toLongOrNull() ?: 0L
        return ((h * 3600L + m * 60L + s) * 1000L) + ms
    }

    fun finalizeChapterEndTimes(
        chapters: List<PlayerGestureControlHelper.ChapterSegment>,
        totalDurationMs: Long
    ): List<PlayerGestureControlHelper.ChapterSegment> {
        if (chapters.isEmpty()) return emptyList()
        val sorted = chapters.sortedBy { it.startMs }
        return sorted.mapIndexedNotNull { idx, ch ->
            val nextStart = sorted.getOrNull(idx + 1)?.startMs ?: totalDurationMs
            val resolvedEnd = when {
                ch.endMs > ch.startMs -> ch.endMs
                nextStart > ch.startMs -> nextStart
                totalDurationMs > ch.startMs -> totalDurationMs
                else -> ch.startMs
            }
            if (resolvedEnd > ch.startMs) {
                ch.copy(endMs = resolvedEnd)
            } else {
                null
            }
        }
    }
}
