package com.cuotiben.notebook.util

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.tasks.await
import kotlin.math.abs
import kotlin.math.max

/**
 * 一块被框选出来的题目区域，坐标均为相对图片宽高的 0~1 值。
 */
data class DetectedRegion(
    val rect: RectF,
    val number: Int? = null,
    val label: String,
    /** 该区域内的印刷体文字（已过滤手写），保存后可在详情页继续编辑。 */
    val text: String = "",
)

data class DetectionResult(
    val regions: List<DetectedRegion>,
    val handwritingLines: Int = 0,
    val totalLines: Int = 0,
)

/**
 * 用本地中文 OCR 做版面分析：
 * 1. 先在灰度增强图上识别文字，并按行判断是否为手写；
 * 2. 用行框做纵向投影，检测单栏 / 双栏；
 * 3. 每栏内找题号，配合「位置靠左、序号递增」筛掉误匹配；
 * 4. 按题号位置切出题目区域，同时收集区域内的印刷体文字。
 *
 * 完全离线，不上传任何图片。
 */
class QuestionDetector {

    private val recognizer =
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

    private val arabicNumber = Regex("""^\s*[（(]?\s*(\d{1,3})\s*[)）.、．:：]\s*""")
    private val chineseNumber = Regex("""^\s*[（(]?\s*([一二三四五六七八九十]{1,3})\s*[)）、.．]\s*""")
    private val diNumber = Regex("""^\s*第\s*(\d{1,3})\s*题\s*""")
    private val diChineseNumber = Regex("""^\s*第\s*([一二三四五六七八九十]{1,3})\s*题\s*""")
    private val circledNumber = Regex("""^\s*([①-⑳])""")

    suspend fun detect(bitmap: Bitmap, ignoreHandwriting: Boolean = true): DetectionResult {
        val ocrBitmap = ImageEnhancer.enhanceForOcr(bitmap)
        try {
            val result = recognizer.process(InputImage.fromBitmap(ocrBitmap, 0)).await()
            val width = bitmap.width
            val height = bitmap.height

            val rawLines = result.textBlocks
                .flatMap { it.lines }
                .mapNotNull { line ->
                    val box = line.boundingBox ?: return@mapNotNull null
                    if (box.width() <= 0 || box.height() <= 0) return@mapNotNull null
                    if (line.text.isBlank()) return@mapNotNull null
                    RawLine(line.text, box)
                }
                .sortedBy { it.box.top }

            if (rawLines.isEmpty()) {
                return DetectionResult(listOf(fullPage()), 0, 0)
            }

            val flags = runCatching {
                HandwritingFilter.classify(bitmap, rawLines.map { it.box })
            }.getOrDefault(List(rawLines.size) { false })

            val lines = rawLines.mapIndexed { index, raw ->
                LineData(raw.text, raw.box, flags.getOrElse(index) { false })
            }
            val usable = if (ignoreHandwriting) lines.filter { !it.handwriting } else lines
            val handwritingLines = lines.count { it.handwriting }

            if (usable.isEmpty()) {
                return DetectionResult(listOf(fullPage()), handwritingLines, lines.size)
            }

            val columns = splitColumns(usable, width)
            val regions = columns
                .flatMap { range ->
                    detectInColumn(
                        lines = usable,
                        columnLeft = range.first.toFloat(),
                        columnRight = (range.last + 1).toFloat(),
                        width = width,
                        height = height,
                        multiColumn = columns.size > 1,
                    )
                }
                .sortedBy { it.rect.top }

            val finalRegions = if (regions.isEmpty()) {
                listOf(
                    DetectedRegion(
                        rect = RectF(0f, 0f, 1f, 1f),
                        number = null,
                        label = "整页",
                        text = joinLines(usable),
                    ),
                )
            } else {
                regions
            }
            return DetectionResult(finalRegions, handwritingLines, lines.size)
        } finally {
            if (ocrBitmap !== bitmap) ocrBitmap.recycle()
        }
    }

    /**
     * 用文字行的水平覆盖情况找栏间空白。只支持切成两栏：
     * 满足「中间有大段空白 + 两侧都有若干行 + 每侧行都不宽」时才切。
     */
    private fun splitColumns(lines: List<LineData>, width: Int): List<IntRange> {
        val single = listOf(0 until width)
        if (lines.size < 8) return single

        val step = max(1, width / 400)
        val slots = (width + step - 1) / step
        val occupied = BooleanArray(slots)
        for (line in lines) {
            val left = (line.box.left / step).coerceIn(0, slots - 1)
            val right = (line.box.right / step).coerceIn(0, slots - 1)
            for (i in left..right) occupied[i] = true
        }

        var bestStart = -1
        var bestLength = 0
        var i = 0
        while (i < slots) {
            if (!occupied[i]) {
                val start = i
                while (i < slots && !occupied[i]) i++
                val length = i - start
                val center = (start + length / 2f) / slots
                if (length > bestLength && center in 0.22f..0.78f) {
                    bestLength = length
                    bestStart = start
                }
            } else {
                i++
            }
        }

        if (bestStart < 0 || bestLength < max(2, (slots * 0.045f).toInt())) return single
        val split = (bestStart + bestLength / 2) * step
        val leftLines = lines.filter { it.box.right <= split + step }
        val rightLines = lines.filter { it.box.left >= split - step }
        if (leftLines.size < 3 || rightLines.size < 3) return single
        if (leftLines.maxOf { it.box.width() } > width * 0.72f) return single
        if (rightLines.maxOf { it.box.width() } > width * 0.72f) return single

        return listOf(0 until split, split until width)
    }

    private fun detectInColumn(
        lines: List<LineData>,
        columnLeft: Float,
        columnRight: Float,
        width: Int,
        height: Int,
        multiColumn: Boolean,
    ): List<DetectedRegion> {
        val columnWidth = (columnRight - columnLeft).coerceAtLeast(1f)
        val columnLines = lines
            .filter { it.box.centerX().toFloat() in columnLeft..columnRight }
            .sortedBy { it.box.top }
        if (columnLines.size < 2) return emptyList()

        val anchors = mutableListOf<Anchor>()
        val chineseAnchors = mutableListOf<Anchor>()
        for (line in columnLines) {
            val parsed = parseQuestionNumber(line.text) ?: continue
            if (parsed.first !in 1..200) continue
            if (line.box.height() < height * 0.008f) continue
            val nearColumnStart = line.box.left - columnLeft < columnWidth * 0.16f
            val shortLine = line.box.width() < columnWidth * 0.3f
            if (!nearColumnStart && !shortLine) continue
            if (anchors.any { abs(it.box.top - line.box.top) < max(6, line.box.height() / 2) }) continue
            val anchor = Anchor(parsed.first, line.box)
            if (parsed.second == NumberKind.CHINESE) {
                chineseAnchors += anchor
            } else {
                anchors += anchor
            }
        }

        // 中文序号（一、二、）多半是「一、选择题」这类大题标题，
        // 只有在没有阿拉伯题号时才当成题目编号。
        val candidates = if (anchors.size >= 2) anchors else anchors + chineseAnchors
        if (candidates.isEmpty()) return emptyList()

        val kept = mutableListOf<Anchor>()
        for (anchor in candidates.sortedBy { it.box.top }) {
            val previous = kept.lastOrNull()
            if (previous != null && anchor.number <= previous.number) continue
            if (previous != null && anchor.number - previous.number > 30) continue
            kept += anchor
        }
        if (kept.isEmpty()) return emptyList()

        val contentBottom = columnLines.maxOf { it.box.bottom }.toFloat()
        val left = if (multiColumn) (columnLeft - 2f).coerceIn(0f, width.toFloat()) else 0f
        val right = if (multiColumn) {
            (columnRight + 2f).coerceIn(0f, width.toFloat())
        } else {
            width.toFloat()
        }

        val regions = mutableListOf<DetectedRegion>()
        kept.forEachIndexed { index, anchor ->
            val top = (anchor.box.top - height * 0.008f).coerceIn(0f, height - 1f)
            val rawBottom = if (index < kept.lastIndex) {
                kept[index + 1].box.top - height * 0.004f
            } else {
                (contentBottom + height * 0.01f)
            }
            val bottom = max(rawBottom, top + height * 0.025f).coerceAtMost(height.toFloat())
            if (bottom - top < height * 0.015f) return@forEachIndexed
            val rect = RectF(left / width, top / height, right / width, bottom / height)
            regions += DetectedRegion(
                rect = rect,
                number = anchor.number,
                label = "第${anchor.number}题",
                text = collectText(lines, top, bottom, columnLeft, columnRight),
            )
        }
        return regions
    }

    private fun collectText(
        lines: List<LineData>,
        top: Float,
        bottom: Float,
        columnLeft: Float,
        columnRight: Float,
    ): String {
        return lines.asSequence()
            .filter { it.box.centerY().toFloat() in top..bottom }
            .filter { it.box.centerX().toFloat() in columnLeft..columnRight }
            .sortedBy { it.box.top }
            .joinToString("\n") { it.text.trim() }
            .trim()
    }

    private fun joinLines(lines: List<LineData>): String =
        lines.sortedBy { it.box.top }.joinToString("\n") { it.text.trim() }.trim()

    private fun fullPage() = DetectedRegion(RectF(0f, 0f, 1f, 1f), null, "整页")

    private fun parseQuestionNumber(text: String): Pair<Int, NumberKind>? {
        val trimmed = text.trim().replace('\u00A0', ' ')
        diNumber.find(trimmed)?.let { match ->
            match.groupValues[1].toIntOrNull()?.let { return it to NumberKind.ARABIC }
        }
        diChineseNumber.find(trimmed)?.let { match ->
            chineseToInt(match.groupValues[1])?.let { return it to NumberKind.CHINESE }
        }
        arabicNumber.find(trimmed)?.let { match ->
            match.groupValues[1].toIntOrNull()?.let { return it to NumberKind.ARABIC }
        }
        chineseNumber.find(trimmed)?.let { match ->
            chineseToInt(match.groupValues[1])?.let { return it to NumberKind.CHINESE }
        }
        circledNumber.find(trimmed)?.let { match ->
            val value = match.groupValues[1].first().code - '①'.code + 1
            if (value in 1..20) return value to NumberKind.CIRCLED
        }
        return null
    }

    private fun chineseToInt(text: String): Int? {
        val digits = mapOf(
            '一' to 1, '二' to 2, '三' to 3, '四' to 4,
            '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9,
        )
        if (text.isEmpty()) return null
        if (text == "十") return 10
        val tenIndex = text.indexOf('十')
        if (tenIndex >= 0) {
            val tens = if (tenIndex == 0) 1 else digits[text[0]] ?: return null
            val ones = if (tenIndex == text.lastIndex) {
                0
            } else {
                digits[text[tenIndex + 1]] ?: return null
            }
            return tens * 10 + ones
        }
        return digits[text[0]]
    }

    fun close() {
        recognizer.close()
    }

    private enum class NumberKind { ARABIC, CHINESE, CIRCLED }

    private data class RawLine(val text: String, val box: Rect)

    private data class LineData(val text: String, val box: Rect, val handwriting: Boolean)

    private data class Anchor(val number: Int, val box: Rect)
}
