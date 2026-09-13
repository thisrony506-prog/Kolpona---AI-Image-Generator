package com.kolpona.ai

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.kolpona.ai.di.AppContainer
import com.kolpona.ai.notify.KolponaNotifier
import com.kolpona.ai.notify.UpdateCheckWorker
import kotlinx.coroutines.launch

class KolponaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        runCatching { FirebaseApp.initializeApp(this) }
        container = AppContainer(this)
        container.adManager.initialize()
        UpdateCheckWorker.schedule(this)
        runCatching {
            FirebaseMessaging.getInstance().subscribeToTopic(KolponaNotifier.TOPIC_UPDATES)
        }
        ProcessLifecycleOwner.get().lifecycleScope.launch {
            container.creditManager.refreshDailyCredits()
        }
    }
}
