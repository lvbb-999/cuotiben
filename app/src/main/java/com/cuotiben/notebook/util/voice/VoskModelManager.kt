package com.cuotiben.notebook.util.voice

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * Vosk 离线中文模型的管理。模型约 40MB，不随 APK 打包，
 * 用户从电脑下载官方 zip 后在应用内导入一次即可。
 */
object VoskModelManager {

    private const val DIR_NAME = "vosk-model"

    fun modelDir(context: Context): File = File(context.filesDir, DIR_NAME)

    fun isInstalled(context: Context): Boolean = findModelRoot(modelDir(context)) != null

    /**
     * 把用户选择的 zip 解压到应用私有目录。进度回调传入已处理条目数，
     * 结束时传 -1。整个过程在 IO 线程执行。
     */
    suspend fun importZip(
        context: Context,
        uri: Uri,
        onProgress: (Int) -> Unit,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val temp = File(context.cacheDir, "vosk-import")
        runCatching {
            temp.deleteRecursively()
            require(temp.mkdirs() || temp.isDirectory) { "无法创建解压目录" }

            context.contentResolver.openInputStream(uri)?.use { stream ->
                ZipInputStream(BufferedInputStream(stream)).use { zip ->
                    var entries = 0
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val name = entry.name
                        require(!name.contains("..")) { "压缩包内容不合法" }
                        val target = File(temp, name)
                        require(
                            target.canonicalPath.startsWith(temp.canonicalPath),
                        ) { "压缩包内容不合法" }
                        if (entry.isDirectory) {
                            target.mkdirs()
                        } else {
                            target.parentFile?.mkdirs()
                            FileOutputStream(target).use { output -> zip.copyTo(output) }
                        }
                        zip.closeEntry()
                        entries++
                        if (entries % 20 == 0) onProgress(entries)
                    }
                }
            } ?: error("无法读取所选文件")

            val root = findModelRoot(temp)
                ?: error("没有在压缩包里找到 Vosk 中文模型（缺少 am/conf 目录）")

            val destination = modelDir(context)
            destination.deleteRecursively()
            if (!root.renameTo(destination)) {
                root.copyRecursively(destination, overwrite = true)
                root.deleteRecursively()
            }
            onProgress(-1)
        }.onFailure {
            temp.deleteRecursively()
        }.recoverCatching {
            Unit
        }
    }

    private fun findModelRoot(dir: File): File? {
        if (looksLikeModel(dir)) return dir
        val children = dir.listFiles()?.filter { it.isDirectory } ?: return null
        return children.firstOrNull { looksLikeModel(it) }
    }

    private fun looksLikeModel(dir: File): Boolean =
        File(dir, "am").isDirectory && File(dir, "conf").isDirectory
}
