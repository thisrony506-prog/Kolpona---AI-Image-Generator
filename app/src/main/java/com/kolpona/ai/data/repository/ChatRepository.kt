package com.kolpona.ai.data.repository

import com.kolpona.ai.data.cloud.UserCloudRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.kolpona.ai.data.database.ChatDao
import com.kolpona.ai.data.database.ChatMessageEntity
import com.kolpona.ai.data.database.ChatSessionEntity
import com.kolpona.ai.domain.model.GenerationError
import com.kolpona.ai.ui.home.ChatItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class ChatRepository(
    private val dao: ChatDao,
    private val history: HistoryRepository,
    private val cloud: UserCloudRepository
) {
    private val ownerUid = MutableStateFlow("")

    fun setOwnerUid(uid: String) {
        ownerUid.value = uid
    }

    fun observeSessions(): Flow<List<ChatSessionEntity>> =
        ownerUid.flatMapLatest { uid ->
            if (uid.isBlank()) flowOf(emptyList()) else dao.observeSessions(uid)
        }

    suspend fun listLocal(): List<ChatSessionEntity> {
        val uid = ownerUid.value
        if (uid.isBlank()) return emptyList()
        return dao.listSessions(uid)
    }

    suspend fun rawMessages(sessionId: String): List<ChatMessageEntity> = dao.messagesFor(sessionId)

    suspend fun latestOrCreate(): ChatSessionEntity {
        val uid = ownerUid.value
        if (uid.isBlank()) return placeholder()
        return dao.latestSession(uid) ?: createSession()
    }

    suspend fun createSession(): ChatSessionEntity {
        val session = ChatSessionEntity(
            id = UUID.randomUUID().toString(),
            title = "New chat",
            updatedAtEpochMs = System.currentTimeMillis(),
            lastPrompt = "",
            ownerUid = ownerUid.value
        )
        dao.upsertSession(session)
        pushChat(session, emptyList())
        return session
    }

    suspend fun rename(id: String, title: String) {
        val current = dao.getSession(id) ?: return
        val updated = current.copy(title = title.take(80).ifBlank { current.title })
        dao.upsertSession(updated)
        pushChat(updated, dao.messagesFor(id))
    }

    suspend fun delete(id: String) {
        dao.deleteMessages(id)
        dao.deleteSession(id)
        val uid = ownerUid.value
        if (uid.isNotBlank()) cloud.enqueue { cloud.deleteChat(uid, id) }
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
        if (sessionId.isBlank()) return
        val uid = ownerUid.value
        dao.deleteMessages(sessionId)
        val now = System.currentTimeMillis()
        val rows = mutableListOf<ChatMessageEntity>()
        messages.filterNot { it is ChatItem.Pending }.forEachIndexed { index, item ->
            val row = when (item) {
                is ChatItem.User -> ChatMessageEntity(item.key, sessionId, "user", item.text, null, now + index, uid)
                is ChatItem.AssistantText -> ChatMessageEntity(item.key, sessionId, "assistant", item.text, null, now + index, uid)
                is ChatItem.Error -> ChatMessageEntity(item.key, sessionId, "error", item.error.name, null, now + index, uid)
                is ChatItem.Image -> ChatMessageEntity(item.key, sessionId, "media", item.image.prompt, item.image.id, now + index, uid)
                is ChatItem.Pending -> null
            } ?: return@forEachIndexed
            dao.insertMessage(row)
            rows += row
        }
        val title = titleHint.trim().ifBlank { "New chat" }.take(48)
        val session = ChatSessionEntity(
            id = sessionId,
            title = title,
            updatedAtEpochMs = now,
            lastPrompt = lastPrompt,
            ownerUid = uid
        )
        dao.upsertSession(session)
        pushChat(session, rows)
    }

    suspend fun replaceFromCloud(session: ChatSessionEntity, messages: List<ChatMessageEntity>) {
        dao.upsertSession(session.copy(ownerUid = ownerUid.value.ifBlank { session.ownerUid }))
        dao.deleteMessages(session.id)
        messages.forEach { dao.insertMessage(it.copy(ownerUid = ownerUid.value.ifBlank { it.ownerUid })) }
    }

    suspend fun claimOrphans(uid: String) {
        if (uid.isBlank()) return
        dao.claimSessionOrphans(uid)
        dao.claimMessageOrphans(uid)
    }

    private fun pushChat(session: ChatSessionEntity, messages: List<ChatMessageEntity>) {
        val uid = ownerUid.value
        if (uid.isBlank() || session.id.isBlank()) return
        cloud.enqueue { cloud.upsertChat(uid, session.copy(ownerUid = uid), messages) }
    }

    private fun placeholder() = ChatSessionEntity(
        id = "",
        title = "New chat",
        updatedAtEpochMs = 0L,
        lastPrompt = "",
        ownerUid = ""
    )
}
