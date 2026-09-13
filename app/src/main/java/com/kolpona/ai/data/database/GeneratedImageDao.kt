package com.kolpona.ai.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GeneratedImageDao {
    @Query("SELECT * FROM generated_images WHERE ownerUid = :uid ORDER BY createdAtEpochMs DESC")
    fun observeForOwner(uid: String): Flow<List<GeneratedImageEntity>>

    @Query("SELECT * FROM generated_images WHERE ownerUid = :uid ORDER BY createdAtEpochMs DESC")
    suspend fun listForOwner(uid: String): List<GeneratedImageEntity>

    @Query("SELECT * FROM generated_images WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): GeneratedImageEntity?

    @Query("SELECT * FROM generated_images WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<GeneratedImageEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: GeneratedImageEntity)

    @Query("DELETE FROM generated_images WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM generated_images WHERE ownerUid = :uid")
    suspend fun deleteAllForOwner(uid: String)

    @Query("UPDATE generated_images SET ownerUid = :uid WHERE ownerUid = ''")
    suspend fun claimOrphans(uid: String)
}
