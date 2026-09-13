package com.kolpona.app.utils

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ImageShare(private val context: Context) {
    suspend fun share(sourcePath: String, chooserTitle: String) = withContext(Dispatchers.IO) {
        val source = File(sourcePath)
        if (!source.exists()) return@withContext

        val cacheDir = File(context.cacheDir, "shared").also { it.mkdirs() }
        val shareFile = File(cacheDir, source.name)
        source.copyTo(shareFile, overwrite = true)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            shareFile
        )
        val mime = when {
            sourcePath.endsWith(".mp4", true) -> "video/mp4"
            sourcePath.endsWith(".webm", true) -> "video/webm"
            else -> "image/jpeg"
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
