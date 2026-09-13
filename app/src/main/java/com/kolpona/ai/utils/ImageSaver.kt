package com.kolpona.ai.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class ImageSaver(private val context: Context) {
    suspend fun saveToGallery(sourcePath: String, displayName: String): Uri = withContext(Dispatchers.IO) {
        val source = File(sourcePath)
        if (!source.exists()) throw IOException("missing")
        val video = sourcePath.endsWith(".mp4", true) || sourcePath.endsWith(".webm", true)
        val values = ContentValues().apply {
            put(if (video) MediaStore.Video.Media.DISPLAY_NAME else MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(
                if (video) MediaStore.Video.Media.MIME_TYPE else MediaStore.Images.Media.MIME_TYPE,
                if (video) "video/mp4" else "image/jpeg"
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    if (video) MediaStore.Video.Media.RELATIVE_PATH else MediaStore.Images.Media.RELATIVE_PATH,
                    (if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES) + "/Kolpona"
                )
                put(if (video) MediaStore.Video.Media.IS_PENDING else MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val collection = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val uri = resolver.insert(collection, values) ?: throw IOException("insert")
        resolver.openOutputStream(uri)?.use { output ->
            source.inputStream().use { input -> input.copyTo(output) }
        } ?: throw IOException("stream")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            if (video) values.put(MediaStore.Video.Media.IS_PENDING, 0)
            else values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        uri
    }
}
