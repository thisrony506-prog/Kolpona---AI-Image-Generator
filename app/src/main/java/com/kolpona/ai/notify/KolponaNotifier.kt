package com.kolpona.ai.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kolpona.ai.MainActivity
import com.kolpona.ai.R
import com.kolpona.ai.data.prefs.AppPreferences

class KolponaNotifier(
    context: Context,
    private val preferences: AppPreferences
) {
    private val app = context.applicationContext

    init {
        ensureChannels()
    }

    fun notifyUpdate(versionName: String) {
        show(
            id = ID_UPDATE,
            channel = CHANNEL_UPDATES,
            title = app.getString(R.string.notify_update_title),
            body = app.getString(R.string.notify_update_body, versionName)
        )
    }

    suspend fun notifyUpdateIfNew(versionCode: Int, versionName: String) {
        if (versionCode <= 0) return
        if (preferences.getLastNotifiedUpdateCode() == versionCode) return
        preferences.setLastNotifiedUpdateCode(versionCode)
        notifyUpdate(versionName)
    }

    fun notifyCreationReady(isVideo: Boolean) {
        show(
            id = ID_GENERATION,
            channel = CHANNEL_GENERATION,
            title = app.getString(
                if (isVideo) R.string.notify_video_title else R.string.notify_image_title
            ),
            body = app.getString(
                if (isVideo) R.string.notify_video_body else R.string.notify_image_body
            )
        )
    }

    suspend fun welcomeIfNeeded(uid: String, name: String?) {
        if (uid.isBlank()) return
        if (preferences.getWelcomedUid() == uid) return
        preferences.setWelcomedUid(uid)
        val title = app.getString(R.string.notify_welcome_title)
        val body = if (name.isNullOrBlank()) {
            app.getString(R.string.notify_welcome_body)
        } else {
            app.getString(R.string.notify_welcome_body_named, name.trim())
        }
        show(ID_WELCOME, CHANNEL_ACCOUNT, title, body)
    }

    fun show(id: Int, channel: String, title: String, body: String) {
        if (!NotificationManagerCompat.from(app).areNotificationsEnabled()) return
        val intent = Intent(app, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            app,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(app, channel)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .build()
        runCatching { NotificationManagerCompat.from(app).notify(id, notification) }
    }

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        listOf(
            NotificationChannel(
                CHANNEL_UPDATES,
                app.getString(R.string.notify_channel_updates),
                NotificationManager.IMPORTANCE_HIGH
            ),
            NotificationChannel(
                CHANNEL_GENERATION,
                app.getString(R.string.notify_channel_generation),
                NotificationManager.IMPORTANCE_HIGH
            ),
            NotificationChannel(
                CHANNEL_ACCOUNT,
                app.getString(R.string.notify_channel_account),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        ).forEach { channel ->
            channel.description = app.getString(R.string.notify_channel_updates)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_UPDATES = "kolpona.updates"
        const val CHANNEL_GENERATION = "kolpona.generation"
        const val CHANNEL_ACCOUNT = "kolpona.account"
        const val ID_UPDATE = 1001
        const val ID_GENERATION = 1002
        const val ID_WELCOME = 1003
        const val TOPIC_UPDATES = "kolpona-updates"
    }
}
