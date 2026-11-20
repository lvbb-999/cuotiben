package com.cuotiben.notebook.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.RectF
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * 所有题目图片都存放在应用私有目录中，卸载应用才会清除。
 */
object ImageStore {

    private const val MAX_DIM = 2600
    private const val JPEG_QUALITY = 92

    fun photosDir(context: Context): File =
        File(context.filesDir, "photos").apply { if (!exists()) mkdirs() }

    fun questionsDir(context: Context): File =
        File(context.filesDir, "questions").apply { if (!exists()) mkdirs() }

    fun newPhotoFile(context: Context, prefix: String = "photo"): File =
        File(photosDir(context), "${prefix}_${UUID.randomUUID()}.jpg")

    fun newQuestionFile(context: Context): File =
        File(questionsDir(context), "q_${UUID.randomUUID()}.jpg")

    fun photoFile(context: Context, name: String): File =
        File(photosDir(context), name)

    /** 读取图片，按 EXIF 摆正，并限制最长边。 */
    suspend fun loadNormalized(file: File, maxDim: Int = MAX_DIM): Bitmap? =
        withContext(Dispatchers.IO) {
            if (!file.exists()) return@withContext null

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxDim * 1.4f) {
                sample *= 2
            }

            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            var bitmap = BitmapFactory.decodeFile(file.absolutePath, options)
                ?: return@withContext null

            val rotation = runCatching { ExifInterface(file).rotationDegrees }.getOrDefault(0)
            if (rotation != 0) {
                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                val rotated = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true,
                )
                if (rotated != bitmap) bitmap.recycle()
                bitmap = rotated
            }

            val longest = maxOf(bitmap.width, bitmap.height)
            if (longest > maxDim) {
                val scale = maxDim.toFloat() / longest
                val scaled = Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1),
                    true,
                )
                if (scaled != bitmap) bitmap.recycle()
                bitmap = scaled
            }
            bitmap
        }

    /** 把拍照结果摆正、压缩后覆盖原文件，后续所有处理统一使用它。 */
    suspend fun normalizeAndSave(file: File, maxDim: Int = MAX_DIM): File =
        withContext(Dispatchers.IO) {
            val bitmap = loadNormalized(file, maxDim) ?: return@withContext file
            try {
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                }
            } finally {
                bitmap.recycle()
            }
            file
        }

    /** 一次解码，批量裁剪，避免多题时反复解码大图。 */
    suspend fun cropMany(
        bitmap: Bitmap,
        requests: List<Pair<RectF, File>>,
    ): List<Boolean> = withContext(Dispatchers.IO) {
        if (requests.isEmpty()) return@withContext emptyList()
        requests.map { (rect, dest) ->
            runCatching {
                val left = (rect.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
                val top = (rect.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
                val right = (rect.right * bitmap.width).toInt().coerceIn(left + 1, bitmap.width)
                val bottom = (rect.bottom * bitmap.height).toInt().coerceIn(top + 1, bitmap.height)
                val cropped = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
                dest.parentFile?.mkdirs()
                FileOutputStream(dest).use { out ->
                    cropped.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                }
                if (cropped != bitmap) cropped.recycle()
                true
            }.getOrDefault(false)
        }
    }

    suspend fun copyFromUri(context: Context, uri: Uri, dest: File): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    dest.parentFile?.mkdirs()
                    FileOutputStream(dest).use { output -> input.copyTo(output) }
                } != null
            }.getOrDefault(false)
        }

    fun deleteQuietly(path: String) {
        runCatching { File(path).delete() }
    }

    /** 分享用的临时副本，放在 cache/shared 下。 */
    fun shareableFile(context: Context, path: String): File? {
        val source = File(path)
        if (!source.exists()) return null
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val dest = File(dir, "share_${UUID.randomUUID()}.jpg")
        return runCatching { source.copyTo(dest, overwrite = true) }.getOrNull()
    }
}
