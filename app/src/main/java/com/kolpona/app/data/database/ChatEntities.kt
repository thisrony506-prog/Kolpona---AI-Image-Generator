package com.kolpona.app.data.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val updatedAtEpochMs: Long,
    val lastPrompt: String
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val kind: String,
    val text: String,
    val mediaId: String?,
    val createdAtEpochMs: Long
)

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_sessions ORDER BY updatedAtEpochMs DESC")
    fun observeSessions(): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_sessions WHERE id = :id LIMIT 1")
    suspend fun getSession(id: String): ChatSessionEntity?

    @Query("SELECT * FROM chat_sessions ORDER BY updatedAtEpochMs DESC LIMIT 1")
    suspend fun latestSession(): ChatSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: ChatSessionEntity)

    @Query("DELETE FROM chat_sessions WHERE id = :id")
    suspend fun deleteSession(id: String)

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY createdAtEpochMs ASC")
    suspend fun messagesFor(sessionId: String): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessages(sessionId: String)
}
