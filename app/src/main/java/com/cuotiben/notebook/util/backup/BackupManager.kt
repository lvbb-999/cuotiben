package com.cuotiben.notebook.util.backup

import android.content.Context
import android.net.Uri
import com.cuotiben.notebook.data.Mistake
import com.cuotiben.notebook.data.Subjects
import com.cuotiben.notebook.util.ImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 备份格式：一个 zip，里面是 backup.json + images/xxx.jpg。
 * 只包含本应用自己的数据，导出和导入都在本机完成。
 */
object BackupManager {

    private const val ENTRY_JSON = "backup.json"
    private const val APP_TAG = "CuoTiBen"
    private const val FORMAT_VERSION = 1

    /** 导出成功返回题目数量。 */
    suspend fun export(
        context: Context,
        mistakes: List<Mistake>,
        uri: Uri,
        onProgress: (done: Int, total: Int) -> Unit,
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val images = mistakes.mapIndexedNotNull { index, mistake ->
                if (mistake.imagePath.isBlank()) return@mapIndexedNotNull null
                val file = File(mistake.imagePath)
                if (!file.exists()) return@mapIndexedNotNull null
                index to file
            }
            val total = images.size + 1
            var done = 0

            val root = JSONObject().apply {
                put("app", APP_TAG)
                put("version", FORMAT_VERSION)
                put("exportedAt", System.currentTimeMillis())
                put("mistakes", JSONArray(mistakes.mapIndexed { index, mistake ->
                    val hasImage = images.any { it.first == index }
                    mistake.toJson(if (hasImage) imageName(index) else null)
                }))
            }

            context.contentResolver.openOutputStream(uri)?.use { raw ->
                ZipOutputStream(BufferedOutputStream(raw)).use { zip ->
                    zip.putNextEntry(ZipEntry(ENTRY_JSON))
                    zip.write(root.toString().toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                    done++
                    onProgress(done, total)

                    images.forEach { (index, file) ->
                        zip.putNextEntry(ZipEntry(imageName(index)))
                        file.inputStream().use { input -> input.copyTo(zip) }
                        zip.closeEntry()
                        done++
                        onProgress(done, total)
                    }
                }
            } ?: error("无法写入所选文件")

            mistakes.size
        }
    }

    /**
     * 读取备份并落盘图片，返回可直接插入数据库的题目。
     * 题目 id 不保留，导入后由 Room 重新生成，避免覆盖现有数据。
     */
    suspend fun read(
        context: Context,
        uri: Uri,
        onProgress: (done: Int, total: Int) -> Unit,
    ): Result<List<Mistake>> = withContext(Dispatchers.IO) {
        val temp = File(context.cacheDir, "backup-import")
        runCatching {
            temp.deleteRecursively()
            require(temp.mkdirs() || temp.isDirectory) { "无法创建临时目录" }

            var payload: JSONObject? = null
            val imageFiles = HashMap<String, File>()

            context.contentResolver.openInputStream(uri)?.use { raw ->
                ZipInputStream(BufferedInputStream(raw)).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        if (entry.isDirectory) {
                            zip.closeEntry()
                            continue
                        }
                        val name = entry.name
                        require(!name.contains("..")) { "备份文件内容不合法" }
                        when {
                            name == ENTRY_JSON -> {
                                payload = JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                            }

                            name.startsWith("images/") -> {
                                val safeName = File(name).name
                                require(safeName.isNotBlank()) { "备份文件内容不合法" }
                                val target = File(temp, safeName)
                                require(target.canonicalPath.startsWith(temp.canonicalPath)) {
                                    "备份文件内容不合法"
                                }
                                FileOutputStream(target).use { output -> zip.copyTo(output) }
                                imageFiles[name] = target
                            }
                        }
                        zip.closeEntry()
                    }
                }
            } ?: error("无法读取所选文件")

            val root = payload ?: error("备份文件不完整（缺少 backup.json）")
            require(root.optString("app") == APP_TAG) { "这不是错题本的备份文件" }
            require(root.optInt("version", 0) in 1..FORMAT_VERSION) {
                "备份版本过新，请升级应用后再导入"
            }

            val array = root.optJSONArray("mistakes") ?: JSONArray()
            val total = array.length()
            val result = ArrayList<Mistake>(total)
            for (index in 0 until total) {
                val obj = array.optJSONObject(index) ?: continue
                val imageName = obj.optString("image", "").takeIf { it.isNotBlank() }
                val source = imageName?.let { imageFiles[it] }
                var imagePath = ""
                if (source != null && source.exists()) {
                    val destination = ImageStore.newQuestionFile(context)
                    source.copyTo(destination, overwrite = true)
                    imagePath = destination.absolutePath
                }
                result += obj.toMistake(imagePath)
                onProgress(index + 1, total)
            }
            result
        }.onFailure {
            temp.deleteRecursively()
        }.onSuccess {
            temp.deleteRecursively()
        }
    }

    private fun imageName(index: Int): String = "images/img_$index.jpg"

    private fun Mistake.toJson(image: String?): JSONObject = JSONObject().apply {
        put("image", image ?: JSONObject.NULL)
        put("subject", subject)
        put("questionNo", questionNo)
        put("title", title)
        put("text", text)
        put("answer", answer)
        put("analysis", analysis)
        put("reason", reason)
        put("tags", tags)
        put("status", status)
        put("favorite", favorite)
        put("reviewCount", reviewCount)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("completedAt", completedAt ?: JSONObject.NULL)
        put("nextReviewAt", nextReviewAt ?: JSONObject.NULL)
    }

    private fun JSONObject.toMistake(imagePath: String): Mistake {
        val now = System.currentTimeMillis()
        return Mistake(
            imagePath = imagePath,
            subject = optString("subject", Subjects.UNCATEGORIZED)
                .ifBlank { Subjects.UNCATEGORIZED },
            questionNo = optString("questionNo", ""),
            title = optString("title", ""),
            text = optString("text", ""),
            answer = optString("answer", ""),
            analysis = optString("analysis", ""),
            reason = optString("reason", ""),
            tags = optString("tags", ""),
            status = optInt("status", Mistake.STATUS_TODO).coerceIn(
                Mistake.STATUS_TODO,
                Mistake.STATUS_DONE,
            ),
            favorite = optBoolean("favorite", false),
            reviewCount = optInt("reviewCount", 0).coerceAtLeast(0),
            createdAt = optLong("createdAt", now),
            updatedAt = optLong("updatedAt", now),
            completedAt = nullableLong("completedAt"),
            nextReviewAt = nullableLong("nextReviewAt"),
        )
    }

    private fun JSONObject.nullableLong(key: String): Long? =
        if (isNull(key)) null else optLong(key, 0L).takeIf { it > 0L }
}
