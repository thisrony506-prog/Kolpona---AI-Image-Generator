package com.kolpona.ai.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ImageFileStore(context: Context) {
    private val imagesDir: File = File(context.applicationContext.filesDir, "images").also { it.mkdirs() }

    suspend fun save(id: String, bytes: ByteArray, extension: String = "jpg"): String = withContext(Dispatchers.IO) {
        val file = File(imagesDir, "$id.$extension")
        file.writeBytes(bytes)
        file.absolutePath
    }

    suspend fun delete(path: String) = withContext(Dispatchers.IO) {
        runCatching { File(path).takeIf { it.exists() }?.delete() }
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        imagesDir.listFiles()?.forEach { it.delete() }
    }
}
