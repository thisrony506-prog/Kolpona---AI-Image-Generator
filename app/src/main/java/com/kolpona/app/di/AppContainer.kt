package com.kolpona.app.di

import android.app.Application
import com.kolpona.app.ads.StartIoAdManager
import com.kolpona.app.data.api.AuthInterceptor
import com.kolpona.app.data.api.PollinationsApiService
import com.kolpona.app.data.database.KolponaDatabase
import com.kolpona.app.data.prefs.AppPreferences
import com.kolpona.app.data.repository.GenerateImageUseCase
import com.kolpona.app.data.repository.HistoryRepository
import com.kolpona.app.domain.manager.CreditManager
import com.kolpona.app.utils.ImageFileStore
import com.kolpona.app.utils.ImageSaver
import com.kolpona.app.utils.ImageShare
import com.kolpona.app.utils.NetworkMonitor
import com.kolpona.app.utils.PromptEnhancer
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppContainer(app: Application) {
    val preferences: AppPreferences = AppPreferences(app)
    val creditManager: CreditManager = CreditManager(preferences)
    val database: KolponaDatabase = KolponaDatabase.create(app)
    val imageStore: ImageFileStore = ImageFileStore(app)
    val historyRepository: HistoryRepository = HistoryRepository(database.generatedImageDao(), imageStore)
    val networkMonitor: NetworkMonitor = NetworkMonitor(app)
    val imageSaver: ImageSaver = ImageSaver(app)
    val imageShare: ImageShare = ImageShare(app)
    val adManager: StartIoAdManager = StartIoAdManager(app)

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(130, TimeUnit.SECONDS)
        .addInterceptor(AuthInterceptor())
        .build()

    val pollinationsApi: PollinationsApiService = PollinationsApiService(okHttpClient)

    val generateImageUseCase: GenerateImageUseCase = GenerateImageUseCase(
        api = pollinationsApi,
        history = historyRepository,
        credits = creditManager,
        files = imageStore,
        enhancer = PromptEnhancer(),
        networkMonitor = networkMonitor
    )
}
