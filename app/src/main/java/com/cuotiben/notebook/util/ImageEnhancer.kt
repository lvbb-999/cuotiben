package com.cuotiben.notebook.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

/**
 * OCR 前的轻量图像增强：去彩、提高对比度。
 * 只在识别时使用，展示和裁剪仍然用原图，保证坐标一致。
 */
object ImageEnhancer {

    /**
     * 返回用于 OCR 的灰度高对比位图。尺寸与原图完全一致，方便把 OCR 坐标
     * 直接映射回原图。调用方负责回收返回的位图。
     */
    fun enhanceForOcr(source: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val matrix = ColorMatrix().apply { setSaturation(0f) }
        // 以 128 为中心拉伸对比度，压制阴影和泛黄纸张。
        val contrast = 1.35f
        val translate = (-0.5f * contrast + 0.5f) * 255f
        matrix.postConcat(
            ColorMatrix(
                floatArrayOf(
                    contrast, 0f, 0f, 0f, translate,
                    0f, contrast, 0f, 0f, translate,
                    0f, 0f, contrast, 0f, translate,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        Canvas(out).drawBitmap(source, 0f, 0f, paint)
        return out
    }
}
