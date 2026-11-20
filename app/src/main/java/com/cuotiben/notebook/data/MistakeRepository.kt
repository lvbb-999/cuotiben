package com.cuotiben.notebook.data

import android.content.Context
import com.cuotiben.notebook.util.ImageStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class MistakeRepository(
    private val dao: MistakeDao,
    context: Context,
) {
    private val appContext = context.applicationContext
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun observeAll(): Flow<List<Mistake>> = dao.observeAll()

    fun observeById(id: Long): Flow<Mistake?> = dao.observeById(id)

    suspend fun get(id: Long): Mistake? = dao.getById(id)

    /** 导出备份时的一次性快照。 */
    suspend fun snapshot(): List<Mistake> = dao.getAll()

    suspend fun insert(mistake: Mistake): Long = dao.insert(mistake)

    suspend fun update(mistake: Mistake) {
        dao.update(mistake.copy(updatedAt = System.currentTimeMillis()))
    }

    /** 不依赖界面生命周期的一次性写入，用于离开页面时的最后保存。 */
    fun updateAsync(mistake: Mistake) {
        backgroundScope.launch {
            dao.update(mistake.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun delete(mistake: Mistake) {
        dao.delete(mistake)
        ImageStore.deleteQuietly(mistake.imagePath)
    }

    fun imageFile(path: String) = java.io.File(path)

    fun shareableFile(path: String) = ImageStore.shareableFile(appContext, path)
}
