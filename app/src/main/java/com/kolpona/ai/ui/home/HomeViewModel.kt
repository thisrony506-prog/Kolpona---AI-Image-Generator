package com.kolpona.ai.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kolpona.ai.ads.StartIoAdManager
import com.kolpona.ai.data.cloud.UserSessionSync
import com.kolpona.ai.data.database.ChatSessionEntity
import com.kolpona.ai.data.prefs.AppPreferences
import com.kolpona.ai.data.repository.ChatRepository
import com.kolpona.ai.data.repository.GenerateImageUseCase
import com.kolpona.ai.di.AppContainer
import com.kolpona.ai.domain.manager.CreditConfig
import com.kolpona.ai.domain.manager.CreditManager
import com.kolpona.ai.domain.model.AspectRatio
import com.kolpona.ai.domain.model.GeneratedImage
import com.kolpona.ai.domain.model.GenerationError
import com.kolpona.ai.domain.model.GenerationInput
import com.kolpona.ai.domain.model.GenerationOutcome
import com.kolpona.ai.domain.model.ImageModels
import com.kolpona.ai.domain.model.ImageQuality
import com.kolpona.ai.domain.model.ImageStyle
import com.kolpona.ai.domain.model.MediaKind
import com.kolpona.ai.utils.ImageSaver
import com.kolpona.ai.utils.ImageShare
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val prompt: String = "",
    val pendingPrompt: String = "",
    val style: ImageStyle = ImageStyle.ALL,
    val aspectRatio: AspectRatio = AspectRatio.SQUARE,
    val quality: ImageQuality = ImageQuality.HIGH,
    val modelId: String = ImageModels.default().id,
    val enhance: Boolean = true,
    val credits: Int = CreditConfig.DAILY_INITIAL_CREDITS,
    val isGenerating: Boolean = false,
    val error: GenerationError? = null,
    val watchingAd: Boolean = false,
    val successfulGenerations: Int = 0,
    val mediaType: MediaKind = MediaKind.IMAGE,
    val messages: List<ChatItem> = emptyList(),
    val lastPrompt: String = "",
    val sessionId: String = "",
    val sessions: List<ChatSessionEntity> = emptyList(),
    val refreshing: Boolean = false
)

sealed class ChatItem {
    abstract val key: String

    data class User(val text: String, override val key: String) : ChatItem()
    data class AssistantText(val text: String, override val key: String) : ChatItem()
    data class Image(val image: GeneratedImage, override val key: String) : ChatItem()
    data class Pending(val text: String, override val key: String = "pending") : ChatItem()
    data class Error(val error: GenerationError, override val key: String = "error") : ChatItem()
}

sealed class HomeEvent {
    data object CreditsAdded : HomeEvent()
    data object AdUnavailable : HomeEvent()
    data object NeedCredits : HomeEvent()
    data object ShowInterstitial : HomeEvent()
    data object Saved : HomeEvent()
    data object SaveFailed : HomeEvent()
    data object Refreshed : HomeEvent()
    data object RefreshFailed : HomeEvent()
}

class HomeViewModel(
    private val generateImage: GenerateImageUseCase,
    private val creditManager: CreditManager,
    private val preferences: AppPreferences,
    private val imageSaver: ImageSaver,
    private val imageShare: ImageShare,
    private val chats: ChatRepository,
    private val sessionSync: UserSessionSync,
    val adManager: StartIoAdManager
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<HomeEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<HomeEvent> = _events.asSharedFlow()
    private var generateJob: Job? = null
    private var generateSeq = 0

    init {
        viewModelScope.launch { creditManager.refreshDailyCredits() }
        viewModelScope.launch {
            val style = preferences.defaultStyle.first()
            val ratio = preferences.defaultAspectRatio.first()
            val quality = preferences.imageQuality.first()
            val enhance = preferences.enhancePrompts.first()
            _state.update {
                it.copy(style = style, aspectRatio = ratio, quality = quality, enhance = enhance)
            }
        }
        viewModelScope.launch {
            combine(
                creditManager.currentCredits,
                preferences.imageQuality,
                preferences.enhancePrompts
            ) { credits, quality, enhance -> Triple(credits, quality, enhance) }
                .collect { (credits, quality, enhance) ->
                    _state.update {
                        it.copy(credits = credits, quality = quality, enhance = enhance)
                    }
                }
        }
        viewModelScope.launch {
            chats.observeSessions().collect { sessions ->
                _state.update { it.copy(sessions = sessions) }
            }
        }
        viewModelScope.launch {
            val session = chats.latestOrCreate()
            val (messages, lastPrompt) = chats.loadMessages(session.id)
            _state.update {
                it.copy(sessionId = session.id, messages = messages, lastPrompt = lastPrompt)
            }
        }
    }

    fun refresh() {
        if (_state.value.refreshing || _state.value.isGenerating) return
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            val ok = runCatching { sessionSync.refresh() }.getOrDefault(false)
            creditManager.refreshDailyCredits()
            val id = _state.value.sessionId
            if (id.isNotBlank()) {
                val (messages, lastPrompt) = chats.loadMessages(id)
                _state.update { it.copy(messages = messages, lastPrompt = lastPrompt) }
            }
            _state.update { it.copy(refreshing = false) }
            _events.emit(if (ok) HomeEvent.Refreshed else HomeEvent.RefreshFailed)
        }
    }

    fun onPromptChange(value: String) {
        _state.update { it.copy(prompt = value, error = if (it.isGenerating) it.error else null) }
    }

    fun onStyleSelected(style: ImageStyle) {
        _state.update { it.copy(style = style) }
        viewModelScope.launch { preferences.setDefaultStyle(style) }
    }

    fun onAspectSelected(ratio: AspectRatio) {
        _state.update { it.copy(aspectRatio = ratio) }
        viewModelScope.launch { preferences.setDefaultAspectRatio(ratio) }
    }

    fun onQualitySelected(quality: ImageQuality) {
        _state.update { it.copy(quality = quality) }
        viewModelScope.launch { preferences.setImageQuality(quality) }
    }

    fun onMediaType(type: MediaKind) {
        _state.update { it.copy(mediaType = type) }
    }

    fun clearPrompt() {
        _state.update { it.copy(prompt = "") }
    }

    fun cancelGeneration() {
        generateJob?.cancel()
    }

    fun newChat() {
        if (_state.value.isGenerating) return
        viewModelScope.launch {
            persistCurrent()
            val session = chats.createSession()
            _state.update {
                it.copy(
                    sessionId = session.id,
                    messages = emptyList(),
                    pendingPrompt = "",
                    error = null,
                    lastPrompt = "",
                    prompt = "",
                    isGenerating = false
                )
            }
        }
    }

    fun openChat(id: String) {
        if (_state.value.isGenerating || id == _state.value.sessionId) return
        viewModelScope.launch {
            persistCurrent()
            val (messages, lastPrompt) = chats.loadMessages(id)
            _state.update {
                it.copy(
                    sessionId = id,
                    messages = messages,
                    lastPrompt = lastPrompt,
                    pendingPrompt = "",
                    error = null,
                    prompt = "",
                    isGenerating = false
                )
            }
        }
    }

    fun renameChat(id: String, title: String) {
        viewModelScope.launch { chats.rename(id, title) }
    }

    fun deleteChat(id: String) {
        if (_state.value.isGenerating && id == _state.value.sessionId) return
        viewModelScope.launch {
            chats.delete(id)
            if (id == _state.value.sessionId) {
                val session = chats.latestOrCreate()
                val (messages, lastPrompt) = chats.loadMessages(session.id)
                _state.update {
                    it.copy(
                        sessionId = session.id,
                        messages = messages,
                        lastPrompt = lastPrompt,
                        pendingPrompt = "",
                        error = null,
                        prompt = "",
                        isGenerating = false
                    )
                }
            }
        }
    }

    fun send() {
        val snapshot = _state.value
        val typed = snapshot.prompt.trim()
        if (typed.isEmpty() || snapshot.isGenerating) return
        if (snapshot.credits < CreditConfig.GENERATION_COST) {
            _events.tryEmit(HomeEvent.NeedCredits)
            return
        }
        _state.update { it.copy(prompt = "", pendingPrompt = typed, error = null) }
        generateInternal(displayText = typed, generationPrompt = typed, snapshot = snapshot)
    }

    fun retry() {
        val snapshot = _state.value
        val text = snapshot.pendingPrompt.ifBlank { snapshot.prompt }.trim()
        if (text.isEmpty() || snapshot.isGenerating) return
        generateInternal(displayText = text, generationPrompt = text, snapshot = snapshot)
    }

    fun regenerate(image: GeneratedImage) {
        val snapshot = _state.value
        if (snapshot.isGenerating) return
        generateInternal(
            displayText = image.prompt,
            generationPrompt = image.prompt,
            snapshot = snapshot.copy(
                style = ImageStyle.fromId(image.styleId),
                aspectRatio = AspectRatio.fromId(image.aspectRatioId),
                mediaType = MediaKind.fromId(image.mediaType),
                modelId = image.model
            )
        )
    }

    fun variation(image: GeneratedImage) {
        regenerate(image)
    }

    private fun generateInternal(displayText: String, generationPrompt: String, snapshot: HomeUiState) {
        generateJob?.cancel()
        val seq = ++generateSeq
        generateJob = viewModelScope.launch {
            val withoutError = snapshot.messages.filterNot { it is ChatItem.Error || it is ChatItem.Pending }
            val withHint = withoutError + ChatItem.User(displayText, "u-${System.currentTimeMillis()}")
            _state.update {
                it.copy(
                    isGenerating = true,
                    error = null,
                    pendingPrompt = displayText,
                    messages = withHint + ChatItem.Pending(displayText)
                )
            }
            val fallback = if (snapshot.mediaType == MediaKind.VIDEO) {
                GenerationError.VIDEO_UNAVAILABLE
            } else {
                GenerationError.UNKNOWN
            }
            try {
                val outcome = generateImage(
                    GenerationInput(
                        prompt = generationPrompt,
                        style = snapshot.style,
                        aspectRatio = snapshot.aspectRatio,
                        quality = snapshot.quality,
                        modelId = snapshot.modelId,
                        enhance = snapshot.enhance,
                        mediaType = snapshot.mediaType
                    )
                )
                if (seq != generateSeq) return@launch
                when (outcome) {
                    is GenerationOutcome.Success -> {
                        val count = snapshot.successfulGenerations + 1
                        val ready = withHint + ChatItem.Image(outcome.image, "${outcome.image.id}-media")
                        _state.update {
                            it.copy(
                                isGenerating = false,
                                error = null,
                                pendingPrompt = "",
                                successfulGenerations = count,
                                lastPrompt = displayText,
                                messages = ready
                            )
                        }
                        runCatching { persist(ready, displayText, displayText) }
                        if (count % 2 == 0) {
                            _events.emit(HomeEvent.ShowInterstitial)
                        }
                    }
                    is GenerationOutcome.Failure -> {
                        val failed = withHint + ChatItem.Error(outcome.error, "e-${System.currentTimeMillis()}")
                        _state.update {
                            it.copy(
                                isGenerating = false,
                                error = outcome.error,
                                messages = failed
                            )
                        }
                        runCatching { persist(failed, snapshot.lastPrompt, displayText) }
                        if (outcome.error == GenerationError.INSUFFICIENT_CREDITS) {
                            _events.emit(HomeEvent.NeedCredits)
                        }
                    }
                }
            } catch (_: CancellationException) {
                if (seq == generateSeq) {
                    _state.update {
                        it.copy(
                            isGenerating = false,
                            messages = withHint
                        )
                    }
                }
            } catch (_: Throwable) {
                if (seq != generateSeq) return@launch
                val failed = withHint + ChatItem.Error(fallback, "e-${System.currentTimeMillis()}")
                _state.update {
                    it.copy(
                        isGenerating = false,
                        error = fallback,
                        messages = failed
                    )
                }
                runCatching { persist(failed, snapshot.lastPrompt, displayText) }
            }
        }
    }

    fun onAdRewarded(token: String) {
        viewModelScope.launch {
            val granted = creditManager.grantRewarded(token)
            _state.update { it.copy(watchingAd = false) }
            if (granted) {
                _events.emit(HomeEvent.CreditsAdded)
            }
        }
    }

    fun onAdUnavailable() {
        _state.update { it.copy(watchingAd = false) }
        _events.tryEmit(HomeEvent.AdUnavailable)
    }

    fun onAdClosed() {
        _state.update { it.copy(watchingAd = false) }
    }

    fun setWatchingAd(watching: Boolean) {
        _state.update { it.copy(watchingAd = watching) }
    }

    fun download(image: GeneratedImage) {
        viewModelScope.launch {
            val name = if (image.isVideo) {
                "Kolpona_${image.id.take(8)}.mp4"
            } else {
                "Kolpona_${image.id.take(8)}.jpg"
            }
            runCatching { imageSaver.saveToGallery(image.localPath, name) }
                .onSuccess { _events.emit(HomeEvent.Saved) }
                .onFailure { _events.emit(HomeEvent.SaveFailed) }
        }
    }

    fun share(image: GeneratedImage, title: String) {
        viewModelScope.launch { imageShare.share(image.localPath, title) }
    }

    private suspend fun persistCurrent() {
        val snapshot = _state.value
        if (snapshot.sessionId.isBlank()) return
        persist(snapshot.messages, snapshot.lastPrompt, snapshot.lastPrompt)
    }

    private suspend fun persist(messages: List<ChatItem>, lastPrompt: String, titleHint: String) {
        var id = _state.value.sessionId
        if (id.isBlank()) {
            val session = chats.createSession()
            id = session.id
            _state.update { it.copy(sessionId = id) }
        }
        val title = messages.filterIsInstance<ChatItem.User>().firstOrNull()?.text ?: titleHint
        chats.replaceMessages(id, title, lastPrompt, messages)
    }

    companion object {
        fun create(container: AppContainer) = HomeViewModel(
            generateImage = container.generateImageUseCase,
            creditManager = container.creditManager,
            preferences = container.preferences,
            imageSaver = container.imageSaver,
            imageShare = container.imageShare,
            chats = container.chatRepository,
            sessionSync = container.userSessionSync,
            adManager = container.adManager
        )
    }
}
