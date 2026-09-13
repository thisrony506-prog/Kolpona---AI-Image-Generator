package com.kolpona.ai.data.cloud

import android.app.Application
import android.net.Uri
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.kolpona.ai.data.database.ChatMessageEntity
import com.kolpona.ai.data.database.ChatSessionEntity
import com.kolpona.ai.domain.manager.CreditConfig
import com.kolpona.ai.domain.model.GeneratedImage
import com.kolpona.ai.domain.model.MediaKind
import com.kolpona.ai.utils.ImageFileStore
import com.kolpona.ai.utils.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File

data class CloudSettings(
    val themeMode: String,
    val style: String,
    val aspect: String,
    val quality: String,
    val enhance: Boolean
)

data class CloudWallet(
    val credits: Int,
    val lastResetEpochDay: Long
)

data class CloudProfile(
    val existed: Boolean,
    val settings: CloudSettings?,
    val wallet: CloudWallet?
)

data class CloudChatSnapshot(
    val session: ChatSessionEntity,
    val messages: List<ChatMessageEntity>
)

/**
 * Per-user cloud store: Firestore `users/{uid}` plus Storage `users/{uid}/media/{id}`.
 * Fail-soft: local Room/DataStore stay the source of truth when the network or
 * Firebase console (Firestore/Storage) is not ready.
 */
class UserCloudRepository(
    app: Application,
    private val network: NetworkMonitor,
    private val files: ImageFileStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db: FirebaseFirestore? = runCatching {
        if (com.google.firebase.FirebaseApp.getApps(app).isEmpty()) return@runCatching null
        FirebaseFirestore.getInstance()
    }.getOrNull()
    private val storage: FirebaseStorage? = runCatching {
        if (com.google.firebase.FirebaseApp.getApps(app).isEmpty()) return@runCatching null
        FirebaseStorage.getInstance()
    }.getOrNull()

    fun enqueue(block: suspend () -> Unit) {
        scope.launch { runCatching { block() } }
    }

    suspend fun ensureProfile(
        user: FirebaseUser,
        settings: CloudSettings,
        wallet: CloudWallet
    ): CloudProfile = withContext(Dispatchers.IO) {
        val firestore = readyDb() ?: return@withContext CloudProfile(false, null, null)
        val ref = firestore.collection(USERS).document(user.uid)
        val snap = withTimeout(20_000) { ref.get().await() }
        val now = System.currentTimeMillis()
        val provider = user.providerData
            .firstOrNull { it.providerId != "firebase" }
            ?.providerId
            ?: "password"
        if (!snap.exists()) {
            val doc = hashMapOf(
                "displayName" to (user.displayName ?: ""),
                "email" to (user.email ?: ""),
                "photoUrl" to (user.photoUrl?.toString() ?: ""),
                "provider" to provider,
                "createdAt" to now,
                "updatedAt" to now,
                "lastLoginAt" to now,
                "settings" to settingsMap(settings),
                "wallet" to walletMap(wallet)
            )
            withTimeout(20_000) { ref.set(doc).await() }
            CloudProfile(existed = false, settings = settings, wallet = wallet)
        } else {
            withTimeout(20_000) {
                ref.set(
                    mapOf(
                        "displayName" to (user.displayName ?: snap.getString("displayName").orEmpty()),
                        "email" to (user.email ?: snap.getString("email").orEmpty()),
                        "photoUrl" to (user.photoUrl?.toString() ?: snap.getString("photoUrl").orEmpty()),
                        "provider" to provider,
                        "lastLoginAt" to now,
                        "updatedAt" to now
                    ),
                    SetOptions.merge()
                ).await()
            }
            CloudProfile(
                existed = true,
                settings = readSettings(snap.get("settings")),
                wallet = readWallet(snap.get("wallet"))
            )
        }
    }

    suspend fun saveSettings(uid: String, settings: CloudSettings) {
        val firestore = readyDb() ?: return
        if (uid.isBlank()) return
        withTimeout(20_000) {
            firestore.collection(USERS).document(uid).set(
                mapOf(
                    "settings" to settingsMap(settings),
                    "updatedAt" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            ).await()
        }
    }

    suspend fun saveWallet(uid: String, wallet: CloudWallet) {
        val firestore = readyDb() ?: return
        if (uid.isBlank()) return
        withTimeout(20_000) {
            firestore.collection(USERS).document(uid).set(
                mapOf(
                    "wallet" to walletMap(wallet),
                    "updatedAt" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            ).await()
        }
    }

    suspend fun upsertHistory(uid: String, image: GeneratedImage) {
        val firestore = readyDb() ?: return
        if (uid.isBlank()) return
        val ext = if (image.isVideo) "mp4" else "jpg"
        val storagePath = "$USERS/$uid/media/${image.id}.$ext"
        uploadMedia(image, storagePath)
        val doc = hashMapOf(
            "prompt" to image.prompt,
            "enhancedPrompt" to image.enhancedPrompt,
            "styleId" to image.styleId,
            "aspectRatioId" to image.aspectRatioId,
            "model" to image.model,
            "width" to image.width,
            "height" to image.height,
            "createdAtEpochMs" to image.createdAtEpochMs,
            "creditsUsed" to image.creditsUsed,
            "mediaType" to image.mediaType,
            "durationMs" to image.durationMs,
            "storagePath" to storagePath
        )
        withTimeout(20_000) {
            firestore.collection(USERS).document(uid)
                .collection(HISTORY).document(image.id)
                .set(doc, SetOptions.merge())
                .await()
        }
    }

    suspend fun deleteHistory(uid: String, id: String, isVideo: Boolean) {
        val firestore = readyDb()
        if (uid.isBlank()) return
        runCatching {
            firestore?.collection(USERS)?.document(uid)?.collection(HISTORY)?.document(id)?.delete()?.await()
        }
        val ext = if (isVideo) "mp4" else "jpg"
        runCatching {
            storage?.reference?.child("$USERS/$uid/media/$id.$ext")?.delete()?.await()
        }
    }

    suspend fun deleteAllHistory(uid: String) {
        val firestore = readyDb() ?: return
        if (uid.isBlank()) return
        val snaps = withTimeout(30_000) {
            firestore.collection(USERS).document(uid).collection(HISTORY).get().await()
        }
        snaps.documents.forEach { doc ->
            val path = doc.getString("storagePath")
            val type = doc.getString("mediaType").orEmpty()
            runCatching { doc.reference.delete().await() }
            if (!path.isNullOrBlank()) {
                runCatching { storage?.reference?.child(path)?.delete()?.await() }
            } else {
                val ext = if (type == MediaKind.VIDEO.id) "mp4" else "jpg"
                runCatching {
                    storage?.reference?.child("$USERS/$uid/media/${doc.id}.$ext")?.delete()?.await()
                }
            }
        }
    }

    suspend fun pullHistory(uid: String): List<GeneratedImage> {
        val firestore = readyDb() ?: return emptyList()
        if (uid.isBlank()) return emptyList()
        val snaps = withTimeout(30_000) {
            firestore.collection(USERS).document(uid).collection(HISTORY).get().await()
        }
        return snaps.documents.mapNotNull { doc ->
            val id = doc.id
            val mediaType = doc.getString("mediaType") ?: MediaKind.IMAGE.id
            val ext = if (mediaType == MediaKind.VIDEO.id) "mp4" else "jpg"
            val storagePath = doc.getString("storagePath") ?: "$USERS/$uid/media/$id.$ext"
            val localPath = downloadMedia(storagePath, id, ext)
            GeneratedImage(
                id = id,
                prompt = doc.getString("prompt").orEmpty(),
                enhancedPrompt = doc.getString("enhancedPrompt").orEmpty(),
                styleId = doc.getString("styleId").orEmpty(),
                aspectRatioId = doc.getString("aspectRatioId").orEmpty(),
                model = doc.getString("model").orEmpty(),
                width = (doc.getLong("width") ?: 0L).toInt(),
                height = (doc.getLong("height") ?: 0L).toInt(),
                localPath = localPath,
                createdAtEpochMs = doc.getLong("createdAtEpochMs") ?: 0L,
                creditsUsed = (doc.getLong("creditsUsed") ?: CreditConfig.GENERATION_COST.toLong()).toInt(),
                mediaType = mediaType,
                durationMs = doc.getLong("durationMs") ?: 0L
            )
        }.sortedBy { it.createdAtEpochMs }
    }

    suspend fun upsertChat(uid: String, session: ChatSessionEntity, messages: List<ChatMessageEntity>) {
        val firestore = readyDb() ?: return
        if (uid.isBlank() || session.id.isBlank()) return
        val sessionRef = firestore.collection(USERS).document(uid)
            .collection(CHATS).document(session.id)
        withTimeout(20_000) {
            sessionRef.set(
                mapOf(
                    "title" to session.title,
                    "updatedAtEpochMs" to session.updatedAtEpochMs,
                    "lastPrompt" to session.lastPrompt
                ),
                SetOptions.merge()
            ).await()
        }
        val existing = withTimeout(20_000) { sessionRef.collection(MESSAGES).get().await() }
        val keep = messages.map { it.id }.toSet()
        existing.documents.forEach { doc ->
            if (doc.id !in keep) runCatching { doc.reference.delete().await() }
        }
        messages.forEach { message ->
            sessionRef.collection(MESSAGES).document(message.id).set(
                mapOf(
                    "kind" to message.kind,
                    "text" to message.text,
                    "mediaId" to message.mediaId,
                    "createdAtEpochMs" to message.createdAtEpochMs
                ),
                SetOptions.merge()
            ).await()
        }
    }

    suspend fun deleteChat(uid: String, sessionId: String) {
        val firestore = readyDb() ?: return
        if (uid.isBlank() || sessionId.isBlank()) return
        val sessionRef = firestore.collection(USERS).document(uid).collection(CHATS).document(sessionId)
        val messages = runCatching { sessionRef.collection(MESSAGES).get().await() }.getOrNull()
        messages?.documents?.forEach { runCatching { it.reference.delete().await() } }
        runCatching { sessionRef.delete().await() }
    }

    suspend fun pullChats(uid: String): List<CloudChatSnapshot> {
        val firestore = readyDb() ?: return emptyList()
        if (uid.isBlank()) return emptyList()
        val sessions = withTimeout(30_000) {
            firestore.collection(USERS).document(uid).collection(CHATS).get().await()
        }
        return sessions.documents.map { doc ->
            val session = ChatSessionEntity(
                id = doc.id,
                title = doc.getString("title") ?: "New chat",
                updatedAtEpochMs = doc.getLong("updatedAtEpochMs") ?: 0L,
                lastPrompt = doc.getString("lastPrompt").orEmpty(),
                ownerUid = uid
            )
            val rows = runCatching {
                doc.reference.collection(MESSAGES).get().await()
            }.getOrNull()
            val messages = rows?.documents.orEmpty().map { message ->
                ChatMessageEntity(
                    id = message.id,
                    sessionId = doc.id,
                    kind = message.getString("kind").orEmpty(),
                    text = message.getString("text").orEmpty(),
                    mediaId = message.getString("mediaId"),
                    createdAtEpochMs = message.getLong("createdAtEpochMs") ?: 0L,
                    ownerUid = uid
                )
            }.sortedBy { it.createdAtEpochMs }
            CloudChatSnapshot(session, messages)
        }
    }

    private suspend fun uploadMedia(image: GeneratedImage, storagePath: String) {
        if (!network.isOnline()) return
        val bucket = storage ?: return
        val file = File(image.localPath)
        if (!file.isFile || file.length() <= 0L) return
        val ref = bucket.reference.child(storagePath)
        val already = runCatching { withTimeout(15_000) { ref.metadata.await() } }.getOrNull()
        if (already != null && already.sizeBytes > 0L) return
        withTimeout(120_000) {
            ref.putFile(Uri.fromFile(file)).await()
        }
    }

    private suspend fun downloadMedia(storagePath: String, id: String, ext: String): String {
        val existing = files.file(id, ext)
        if (existing.isFile && existing.length() > 0L) return existing.absolutePath
        val bucket = storage ?: return ""
        val dest = files.file(id, ext)
        dest.parentFile?.mkdirs()
        return runCatching {
            withTimeout(120_000) {
                bucket.reference.child(storagePath).getFile(dest).await()
            }
            if (dest.isFile && dest.length() > 0L) dest.absolutePath else ""
        }.getOrDefault("")
    }

    private fun readyDb(): FirebaseFirestore? = db

    private fun settingsMap(settings: CloudSettings) = mapOf(
        "themeMode" to settings.themeMode,
        "style" to settings.style,
        "aspect" to settings.aspect,
        "quality" to settings.quality,
        "enhance" to settings.enhance
    )

    private fun walletMap(wallet: CloudWallet) = mapOf(
        "credits" to wallet.credits,
        "lastResetEpochDay" to wallet.lastResetEpochDay
    )

    @Suppress("UNCHECKED_CAST")
    private fun readSettings(raw: Any?): CloudSettings? {
        val map = raw as? Map<String, Any?> ?: return null
        return CloudSettings(
            themeMode = map["themeMode"] as? String ?: return null,
            style = map["style"] as? String ?: "all",
            aspect = map["aspect"] as? String ?: "1:1",
            quality = map["quality"] as? String ?: "high",
            enhance = map["enhance"] as? Boolean ?: true
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun readWallet(raw: Any?): CloudWallet? {
        val map = raw as? Map<String, Any?> ?: return null
        val credits = when (val value = map["credits"]) {
            is Number -> value.toInt()
            else -> return null
        }
        val reset = when (val value = map["lastResetEpochDay"]) {
            is Number -> value.toLong()
            else -> 0L
        }
        return CloudWallet(credits = credits.coerceAtLeast(0), lastResetEpochDay = reset)
    }

    companion object {
        const val USERS = "users"
        const val HISTORY = "history"
        const val CHATS = "chats"
        const val MESSAGES = "messages"
    }
}
