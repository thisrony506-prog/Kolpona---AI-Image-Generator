package com.kolpona.ai.notify

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.kolpona.ai.KolponaApp
import com.kolpona.ai.R

class KolponaMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val notifier = (application as? KolponaApp)?.container?.notifier ?: return
        val title = message.notification?.title
            ?: message.data["title"]
            ?: getString(R.string.app_name)
        val body = message.notification?.body
            ?: message.data["body"]
            ?: return
        val type = message.data["type"].orEmpty()
        val channel = when (type) {
            "update" -> KolponaNotifier.CHANNEL_UPDATES
            "generation" -> KolponaNotifier.CHANNEL_GENERATION
            else -> KolponaNotifier.CHANNEL_ACCOUNT
        }
        val id = when (type) {
            "update" -> KolponaNotifier.ID_UPDATE
            "generation" -> KolponaNotifier.ID_GENERATION
            else -> KolponaNotifier.ID_WELCOME
        }
        notifier.show(id, channel, title, body)
    }

    override fun onNewToken(token: String) {
        runCatching {
            com.google.firebase.messaging.FirebaseMessaging.getInstance()
                .subscribeToTopic(KolponaNotifier.TOPIC_UPDATES)
        }
    }
}
