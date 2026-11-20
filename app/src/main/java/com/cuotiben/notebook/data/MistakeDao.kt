package com.cuotiben.notebook.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MistakeDao {

    @Query("SELECT * FROM mistakes ORDER BY status ASC, createdAt DESC")
    fun observeAll(): Flow<List<Mistake>>

    @Query("SELECT * FROM mistakes WHERE id = :id")
    fun observeById(id: Long): Flow<Mistake?>

    @Query("SELECT * FROM mistakes WHERE id = :id")
    suspend fun getById(id: Long): Mistake?

    @Query("SELECT * FROM mistakes ORDER BY createdAt ASC")
    suspend fun getAll(): List<Mistake>

    @Insert
    suspend fun insert(mistake: Mistake): Long

    @Update
    suspend fun update(mistake: Mistake)

    @Delete
    suspend fun delete(mistake: Mistake)
}
