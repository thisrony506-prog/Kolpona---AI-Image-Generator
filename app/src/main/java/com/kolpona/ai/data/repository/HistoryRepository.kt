package com.kolpona.ai.data.repository

import com.kolpona.ai.data.cloud.UserCloudRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.kolpona.ai.data.database.GeneratedImageDao
import com.kolpona.ai.data.database.GeneratedImageEntity
import com.kolpona.ai.domain.model.GeneratedImage
import com.kolpona.ai.utils.ImageFileStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryRepository(
    private val dao: GeneratedImageDao,
    private val files: ImageFileStore,
    private val cloud: UserCloudRepository
) {
    private val ownerUid = MutableStateFlow("")

    fun setOwnerUid(uid: String) {
        ownerUid.value = uid
    }

    fun observeAll(): Flow<List<GeneratedImage>> =
        ownerUid.flatMapLatest { uid ->
            if (uid.isBlank()) flowOf(emptyList())
            else dao.observeForOwner(uid).map { list -> list.map { it.toModel() } }
        }

    fun observeById(id: String): Flow<GeneratedImage?> =
        dao.observeById(id).map { it?.toModel() }

    suspend fun getById(id: String): GeneratedImage? = dao.getById(id)?.toModel()

    suspend fun listLocal(): List<GeneratedImage> {
        val uid = ownerUid.value
        if (uid.isBlank()) return emptyList()
        return dao.listForOwner(uid).map { it.toModel() }
    }

    suspend fun insert(image: GeneratedImage) {
        insertLocal(image)
        val uid = ownerUid.value
        if (uid.isNotBlank()) {
            cloud.enqueue { cloud.upsertHistory(uid, image) }
        }
    }

    suspend fun insertLocal(image: GeneratedImage) {
        dao.insert(GeneratedImageEntity.from(image, ownerUid.value))
    }

    suspend fun delete(image: GeneratedImage) {
        dao.deleteById(image.id)
        files.delete(image.localPath)
        val uid = ownerUid.value
        if (uid.isNotBlank()) {
            cloud.enqueue { cloud.deleteHistory(uid, image.id, image.isVideo) }
        }
    }

    suspend fun clear() {
        val uid = ownerUid.value
        val items = if (uid.isBlank()) emptyList() else dao.listForOwner(uid)
        if (uid.isNotBlank()) dao.deleteAllForOwner(uid)
        items.forEach { files.delete(it.localPath) }
        if (uid.isNotBlank()) {
            cloud.enqueue { cloud.deleteAllHistory(uid) }
        }
    }

    suspend fun claimOrphans(uid: String) {
        if (uid.isBlank()) return
        dao.claimOrphans(uid)
    }
}
