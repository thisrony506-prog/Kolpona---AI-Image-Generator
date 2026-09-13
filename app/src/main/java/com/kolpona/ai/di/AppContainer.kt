package com.kolpona.ai.di

import android.app.Application
import com.kolpona.ai.ads.StartIoAdManager
import com.kolpona.ai.data.auth.AuthRepository
import com.kolpona.ai.data.api.AuthInterceptor
import com.kolpona.ai.data.api.CloudflareApiService
import com.kolpona.ai.data.api.GenerationRouter
import com.kolpona.ai.data.api.HuggingFaceApiService
import com.kolpona.ai.data.api.TextNormalizeService
import com.kolpona.ai.data.cloud.UserCloudRepository
import com.kolpona.ai.data.cloud.UserSessionSync
import com.kolpona.ai.data.database.KolponaDatabase
import com.kolpona.ai.data.prefs.AppPreferences
import com.kolpona.ai.data.repository.ChatRepository
import com.kolpona.ai.data.repository.GenerateImageUseCase
import com.kolpona.ai.data.repository.HistoryRepository
import com.kolpona.ai.domain.manager.CreditManager
import com.kolpona.ai.notify.KolponaNotifier
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
    val networkMonitor: NetworkMonitor = NetworkMonitor(app)
    val authRepository: AuthRepository = AuthRepository(app, networkMonitor)
    val imageStore: ImageFileStore = ImageFileStore(app)
    val cloudRepository: UserCloudRepository = UserCloudRepository(app, networkMonitor, imageStore)
    val creditManager: CreditManager = CreditManager(
        preferences = preferences,
        cloud = cloudRepository,
        uid = { authRepository.currentUser?.uid }
    )
    val database: KolponaDatabase = KolponaDatabase.create(app)
    val historyRepository: HistoryRepository = HistoryRepository(
        database.generatedImageDao(),
        imageStore,
        cloudRepository
    )
    val chatRepository: ChatRepository = ChatRepository(
        database.chatDao(),
        historyRepository,
        cloudRepository
    )
    val imageSaver: ImageSaver = ImageSaver(app)
    val imageShare: ImageShare = ImageShare(app)
    val adManager: StartIoAdManager = StartIoAdManager(app)
    val notifier: KolponaNotifier = KolponaNotifier(app, preferences)
    val updateManager: AppUpdateManager = AppUpdateManager(app, preferences, networkMonitor, notifier)
    val userSessionSync: UserSessionSync = UserSessionSync(
        auth = authRepository,
        cloud = cloudRepository,
        preferences = preferences,
        history = historyRepository,
        chats = chatRepository,
        credits = creditManager
    )

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(150, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(160, TimeUnit.SECONDS)
        .addInterceptor(AuthInterceptor())
        .build()

    val huggingFaceApi: HuggingFaceApiService = HuggingFaceApiService(okHttpClient)
    val cloudflareApi: CloudflareApiService = CloudflareApiService(okHttpClient)
    val textNormalize: TextNormalizeService = TextNormalizeService(huggingFaceApi, cloudflareApi)
    val generationRouter: GenerationRouter = GenerationRouter(huggingFaceApi, cloudflareApi)

    val generateImageUseCase: GenerateImageUseCase = GenerateImageUseCase(
        router = generationRouter,
        history = historyRepository,
        credits = creditManager,
        files = imageStore,
        enhancer = PromptEnhancer(textNormalize),
        networkMonitor = networkMonitor,
        notifier = notifier
    )

    init {
        val uid = authRepository.currentUser?.uid.orEmpty()
        if (uid.isNotBlank()) {
            historyRepository.setOwnerUid(uid)
            chatRepository.setOwnerUid(uid)
            preferences.bindUid(uid)
        }
    }
}
