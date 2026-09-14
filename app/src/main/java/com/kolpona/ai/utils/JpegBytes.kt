package com.kolpona.ai.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

object JpegBytes {
    fun ensure(bytes: ByteArray): ByteArray {
        if (bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) {
            return bytes
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
        val out = ByteArrayOutputStream()
        val ok = bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        bitmap.recycle()
        val jpeg = out.toByteArray()
        return if (ok && jpeg.size > 1000) jpeg else bytes
    }
}
