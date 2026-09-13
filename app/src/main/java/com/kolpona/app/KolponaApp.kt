package com.kolpona.app

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.kolpona.app.di.AppContainer
import kotlinx.coroutines.launch

class KolponaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.adManager.initialize()
        ProcessLifecycleOwner.get().lifecycleScope.launch {
            container.creditManager.refreshDailyCredits()
        }
    }
}
