package com.kolpona.ai.data.repository

import com.kolpona.ai.data.database.ChatDao
import com.kolpona.ai.data.database.ChatMessageEntity
import com.kolpona.ai.data.database.ChatSessionEntity
import com.kolpona.ai.domain.model.GenerationError
import com.kolpona.ai.ui.home.ChatItem
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class ChatRepository(
    private val dao: ChatDao,
    private val history: HistoryRepository
) {
    fun observeSessions(): Flow<List<ChatSessionEntity>> = dao.observeSessions()

    suspend fun latestOrCreate(): ChatSessionEntity = dao.latestSession() ?: createSession()

    suspend fun createSession(): ChatSessionEntity {
        val session = ChatSessionEntity(
            id = UUID.randomUUID().toString(),
            title = "New chat",
            updatedAtEpochMs = System.currentTimeMillis(),
            lastPrompt = ""
        )
        dao.upsertSession(session)
        return session
    }

    suspend fun rename(id: String, title: String) {
        val current = dao.getSession(id) ?: return
        dao.upsertSession(current.copy(title = title.take(80).ifBlank { current.title }))
    }

    suspend fun delete(id: String) {
        dao.deleteMessages(id)
        dao.deleteSession(id)
    }

    suspend fun loadMessages(sessionId: String): Pair<List<ChatItem>, String> {
        val rows = dao.messagesFor(sessionId)
        val items = mutableListOf<ChatItem>()
        var lastPrompt = ""
        for (row in rows) {
            when (row.kind) {
                "user" -> {
                    items += ChatItem.User(row.text, row.id)
                    lastPrompt = row.text
                }
                "assistant" -> items += ChatItem.AssistantText(row.text, row.id)
                "error" -> {
                    val error = runCatching { GenerationError.valueOf(row.text) }.getOrNull()
                        ?: GenerationError.UNKNOWN
                    items += ChatItem.Error(error, row.id)
                }
                "media" -> {
                    val media = row.mediaId?.let { history.getById(it) }
                    if (media != null) items += ChatItem.Image(media, row.id)
                }
            }
        }
        val stored = dao.getSession(sessionId)?.lastPrompt.orEmpty()
        return items to stored.ifBlank { lastPrompt }
    }

    suspend fun replaceMessages(
        sessionId: String,
        titleHint: String,
        lastPrompt: String,
        messages: List<ChatItem>
    ) {
        dao.deleteMessages(sessionId)
        val now = System.currentTimeMillis()
        messages.filterNot { it is ChatItem.Pending }.forEachIndexed { index, item ->
            val row = when (item) {
                is ChatItem.User -> ChatMessageEntity(item.key, sessionId, "user", item.text, null, now + index)
                is ChatItem.AssistantText -> ChatMessageEntity(item.key, sessionId, "assistant", item.text, null, now + index)
                is ChatItem.Error -> ChatMessageEntity(item.key, sessionId, "error", item.error.name, null, now + index)
                is ChatItem.Image -> ChatMessageEntity(item.key, sessionId, "media", item.image.prompt, item.image.id, now + index)
                is ChatItem.Pending -> null
            } ?: return@forEachIndexed
            dao.insertMessage(row)
        }
        val title = titleHint.trim().ifBlank { "New chat" }.take(48)
        dao.upsertSession(
            ChatSessionEntity(
                id = sessionId,
                title = title,
                updatedAtEpochMs = now,
                lastPrompt = lastPrompt
            )
        )
    }
}
