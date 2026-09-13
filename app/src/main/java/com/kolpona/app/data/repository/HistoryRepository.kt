package com.kolpona.app.data.repository

import com.kolpona.app.data.database.GeneratedImageDao
import com.kolpona.app.data.database.GeneratedImageEntity
import com.kolpona.app.domain.model.GeneratedImage
import com.kolpona.app.utils.ImageFileStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class HistoryRepository(
    private val dao: GeneratedImageDao,
    private val files: ImageFileStore
) {
    fun observeAll(): Flow<List<GeneratedImage>> =
        dao.observeAll().map { list -> list.map { it.toModel() } }

    fun observeById(id: String): Flow<GeneratedImage?> =
        dao.observeById(id).map { it?.toModel() }

    suspend fun getById(id: String): GeneratedImage? = dao.getById(id)?.toModel()

    suspend fun insert(image: GeneratedImage) {
        dao.insert(GeneratedImageEntity.from(image))
    }

    suspend fun delete(image: GeneratedImage) {
        dao.deleteById(image.id)
        files.delete(image.localPath)
    }

    suspend fun clear() {
        dao.deleteAll()
        files.clearAll()
    }
}
