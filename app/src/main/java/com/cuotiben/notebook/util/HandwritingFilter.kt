package com.cuotiben.notebook.util

import android.graphics.Bitmap
import android.graphics.Rect
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 行级手写过滤。ML Kit 本身不区分印刷体和手写体，这里用两层启发式判断：
 *
 * 1. 彩色墨迹：学生常用蓝笔/彩笔作答，饱和度明显高于黑色印刷体；
 * 2. 结构特征：手写的笔画粗细更不均匀、基线更抖、倾斜更明显。
 *
 * 判定刻意保守：宁可漏掉手写，也不把印刷体误判成手写（后者会让题目文字丢失）。
 * 界面提供开关，用户可以一键关掉过滤。
 */
object HandwritingFilter {

    private const val MAX_DIM = 900

    /** 返回与 boxes 一一对应的判定结果，true 表示手写。 */
    fun classify(source: Bitmap, boxes: List<Rect>): List<Boolean> {
        if (boxes.isEmpty()) return emptyList()
        val scale = min(1f, MAX_DIM.toFloat() / max(source.width, source.height))
        val scaledWidth = max(1, (source.width * scale).toInt())
        val scaledHeight = max(1, (source.height * scale).toInt())
        val small = if (scaledWidth == source.width && scaledHeight == source.height) {
            source
        } else {
            Bitmap.createScaledBitmap(source, scaledWidth, scaledHeight, true)
        }
        val pixels = IntArray(scaledWidth * scaledHeight)
        small.getPixels(pixels, 0, scaledWidth, 0, 0, scaledWidth, scaledHeight)
        if (small !== source) small.recycle()
        return boxes.map { assess(pixels, scaledWidth, scaledHeight, it, scale) }
    }

    private fun assess(pixels: IntArray, width: Int, height: Int, box: Rect, scale: Float): Boolean {
        val left = (box.left * scale).toInt().coerceIn(0, width - 1)
        val top = (box.top * scale).toInt().coerceIn(0, height - 1)
        val right = (box.right * scale).toInt().coerceIn(left + 1, width)
        val bottom = (box.bottom * scale).toInt().coerceIn(top + 1, height)
        val boxWidth = right - left
        val boxHeight = bottom - top
        if (boxWidth < 8 || boxHeight < 6) return false

        val lum = IntArray(boxWidth * boxHeight)
        val hist = IntArray(256)
        for (y in 0 until boxHeight) {
            val rowStart = (top + y) * width + left
            val target = y * boxWidth
            for (x in 0 until boxWidth) {
                val p = pixels[rowStart + x]
                val r = p shr 16 and 0xFF
                val g = p shr 8 and 0xFF
                val b = p and 0xFF
                val value = (r * 299 + g * 587 + b * 114) / 1000
                lum[target + x] = value
                hist[value]++
            }
        }

        val threshold = otsu(hist, lum.size).coerceIn(40, 200)
        var inkCount = 0
        var coloredInk = 0
        for (y in 0 until boxHeight) {
            val rowStart = (top + y) * width + left
            val target = y * boxWidth
            for (x in 0 until boxWidth) {
                if (lum[target + x] >= threshold) continue
                inkCount++
                val p = pixels[rowStart + x]
                if (saturation(p) > 0.42f) coloredInk++
            }
        }
        if (inkCount < 20) return false

        // 第一层：彩色墨迹占比高，基本可以确定是笔迹。
        if (coloredInk >= 25 && coloredInk.toFloat() / inkCount >= 0.5f) return true

        // 第二层：笔画粗细、基线、倾斜。
        val stroke = strokeStats(lum, boxWidth, boxHeight, threshold)
        val geometry = baselineStats(lum, boxWidth, boxHeight, threshold)

        var strong = 0
        if (stroke > 0.62) strong++
        if (geometry.first > 0.12) strong++
        if (abs(geometry.second) > 7f && abs(geometry.second) < 40f) strong++
        return strong >= 2 && stroke > 0.45
    }

    /** 水平扫描线上墨迹游程长度的变异系数，印刷体更稳定。 */
    private fun strokeStats(
        lum: IntArray,
        width: Int,
        height: Int,
        threshold: Int,
    ): Double {
        var runs = 0
        var sum = 0.0
        var squareSum = 0.0
        val step = max(1, height / 12)
        var y = 0
        while (y < height) {
            var run = 0
            for (x in 0..width) {
                val ink = x < width && lum[y * width + x] < threshold
                if (ink) {
                    run++
                } else if (run > 0) {
                    if (run <= width * 0.3f) {
                        runs++
                        sum += run
                        squareSum += run.toDouble() * run
                    }
                    run = 0
                }
            }
            y += step
        }
        if (runs < 6 || sum <= 0.0) return 0.0
        val mean = sum / runs
        val variance = max(0.0, squareSum / runs - mean * mean)
        return sqrt(variance) / mean
    }

    /** 返回（基线残差比, 文字倾斜角度）。 */
    private fun baselineStats(
        lum: IntArray,
        width: Int,
        height: Int,
        threshold: Int,
    ): Pair<Double, Float> {
        val columns = ArrayList<Int>(width / 2 + 1)
        val centers = ArrayList<Double>(width / 2 + 1)
        var x = 0
        while (x < width) {
            var ink = 0
            var weighted = 0
            for (y in 0 until height) {
                if (lum[y * width + x] < threshold) {
                    ink++
                    weighted += y
                }
            }
            if (ink > 0) {
                columns.add(x)
                centers.add(weighted.toDouble() / ink)
            }
            x += 2
        }
        val n = columns.size
        if (n < 10) return 0.0 to 0f

        val meanX = columns.sum().toDouble() / n
        val meanY = centers.sum() / n
        var cov = 0.0
        var variance = 0.0
        for (i in 0 until n) {
            val dx = columns[i] - meanX
            cov += dx * (centers[i] - meanY)
            variance += dx * dx
        }
        if (variance < 1e-6) return 0.0 to 0f
        val slope = cov / variance
        var residual = 0.0
        for (i in 0 until n) {
            val expected = meanY + slope * (columns[i] - meanX)
            val diff = centers[i] - expected
            residual += diff * diff
        }
        val residualStd = sqrt(residual / n)
        val angle = Math.toDegrees(atan(slope).toDouble()).toFloat()
        return residualStd / height to angle
    }

    private fun saturation(pixel: Int): Float {
        val r = pixel shr 16 and 0xFF
        val g = pixel shr 8 and 0xFF
        val b = pixel and 0xFF
        val maxChannel = max(r, max(g, b))
        val minChannel = min(r, min(g, b))
        if (maxChannel == 0) return 0f
        return (maxChannel - minChannel).toFloat() / maxChannel
    }

    private fun otsu(hist: IntArray, total: Int): Int {
        var weightedSum = 0.0
        for (i in 0..255) weightedSum += i.toDouble() * hist[i]
        var backgroundWeight = 0
        var backgroundSum = 0.0
        var best = 128
        var bestVariance = -1.0
        for (t in 0..255) {
            backgroundWeight += hist[t]
            if (backgroundWeight == 0) continue
            val foregroundWeight = total - backgroundWeight
            if (foregroundWeight == 0) break
            backgroundSum += t.toDouble() * hist[t]
            val backgroundMean = backgroundSum / backgroundWeight
            val foregroundMean = (weightedSum - backgroundSum) / foregroundWeight
            val between = backgroundWeight.toDouble() * foregroundWeight *
                (backgroundMean - foregroundMean) * (backgroundMean - foregroundMean)
            if (between > bestVariance) {
                bestVariance = between
                best = t
            }
        }
        return best
    }
}
