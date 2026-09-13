package com.kolpona.ai.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GeneratedImageDao {
    @Query("SELECT * FROM generated_images ORDER BY createdAtEpochMs DESC")
    fun observeAll(): Flow<List<GeneratedImageEntity>>

    @Query("SELECT * FROM generated_images WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): GeneratedImageEntity?

    @Query("SELECT * FROM generated_images WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<GeneratedImageEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: GeneratedImageEntity)

    @Query("DELETE FROM generated_images WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM generated_images")
    suspend fun deleteAll()
}
