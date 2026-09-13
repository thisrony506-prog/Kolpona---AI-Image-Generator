package com.kolpona.ai.di

import android.app.Application
import com.kolpona.ai.ads.StartIoAdManager
import com.kolpona.ai.data.auth.AuthRepository
import com.kolpona.ai.data.api.AuthInterceptor
import com.kolpona.ai.data.api.GenerationRouter
import com.kolpona.ai.data.api.HuggingFaceApiService
import com.kolpona.ai.data.api.PollinationsApiService
import com.kolpona.ai.data.api.TextNormalizeService
import com.kolpona.ai.data.database.KolponaDatabase
import com.kolpona.ai.data.prefs.AppPreferences
import com.kolpona.ai.data.repository.ChatRepository
import com.kolpona.ai.data.repository.GenerateImageUseCase
import com.kolpona.ai.data.repository.HistoryRepository
import com.kolpona.ai.domain.manager.CreditManager
import com.kolpona.ai.update.AppUpdateManager
import com.kolpona.ai.utils.ImageFileStore
import com.kolpona.ai.utils.ImageSaver
import com.kolpona.ai.utils.ImageShare
import com.kolpona.ai.utils.NetworkMonitor
import com.kolpona.ai.utils.PromptEnhancer
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppContainer(app: Application) {
    val preferences: AppPreferences = AppPreferences(app)
    val creditManager: CreditManager = CreditManager(preferences)
    val database: KolponaDatabase = KolponaDatabase.create(app)
    val imageStore: ImageFileStore = ImageFileStore(app)
    val historyRepository: HistoryRepository = HistoryRepository(database.generatedImageDao(), imageStore)
    val chatRepository: ChatRepository = ChatRepository(database.chatDao(), historyRepository)
    val networkMonitor: NetworkMonitor = NetworkMonitor(app)
    val authRepository: AuthRepository = AuthRepository(app, networkMonitor)
    val imageSaver: ImageSaver = ImageSaver(app)
    val imageShare: ImageShare = ImageShare(app)
    val adManager: StartIoAdManager = StartIoAdManager(app)
    val updateManager: AppUpdateManager = AppUpdateManager(app, preferences, networkMonitor)

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(150, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(160, TimeUnit.SECONDS)
        .addInterceptor(AuthInterceptor())
        .build()

    val pollinationsApi: PollinationsApiService = PollinationsApiService(okHttpClient)
    val huggingFaceApi: HuggingFaceApiService = HuggingFaceApiService(okHttpClient)
    val textNormalize: TextNormalizeService = TextNormalizeService(okHttpClient)
    val generationRouter: GenerationRouter = GenerationRouter(huggingFaceApi, pollinationsApi)

    val generateImageUseCase: GenerateImageUseCase = GenerateImageUseCase(
        router = generationRouter,
        history = historyRepository,
        credits = creditManager,
        files = imageStore,
        enhancer = PromptEnhancer(textNormalize),
        networkMonitor = networkMonitor
    )
}
