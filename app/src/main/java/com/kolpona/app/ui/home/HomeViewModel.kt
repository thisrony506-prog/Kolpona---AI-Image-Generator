package com.kolpona.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kolpona.app.ads.StartIoAdManager
import com.kolpona.app.data.prefs.AppPreferences
import com.kolpona.app.data.repository.GenerateImageUseCase
import com.kolpona.app.data.repository.HistoryRepository
import com.kolpona.app.di.AppContainer
import com.kolpona.app.domain.manager.CreditConfig
import com.kolpona.app.domain.manager.CreditManager
import com.kolpona.app.domain.model.AspectRatio
import com.kolpona.app.domain.model.GeneratedImage
import com.kolpona.app.domain.model.GenerationError
import com.kolpona.app.domain.model.GenerationInput
import com.kolpona.app.domain.model.GenerationOutcome
import com.kolpona.app.domain.model.ImageModels
import com.kolpona.app.domain.model.ImageQuality
import com.kolpona.app.domain.model.ImageStyle
import com.kolpona.app.utils.ImageSaver
import com.kolpona.app.utils.ImageShare
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
    val style: ImageStyle = ImageStyle.REALISTIC,
    val aspectRatio: AspectRatio = AspectRatio.SQUARE,
    val quality: ImageQuality = ImageQuality.HIGH,
    val modelId: String = ImageModels.default().id,
    val enhance: Boolean = true,
    val credits: Int = CreditConfig.DAILY_INITIAL_CREDITS,
    val images: List<GeneratedImage> = emptyList(),
    val isGenerating: Boolean = false,
    val error: GenerationError? = null,
    val watchingAd: Boolean = false,
    val successfulGenerations: Int = 0
)

sealed class ChatItem {
    abstract val key: String

    data class User(val text: String, override val key: String) : ChatItem()
    data class Image(val image: GeneratedImage, override val key: String) : ChatItem()
    data class Pending(val text: String, override val key: String = "pending") : ChatItem()
    data class Error(val error: GenerationError, override val key: String = "error") : ChatItem()
}

fun HomeUiState.chatItems(): List<ChatItem> = buildList {
    images.asReversed().forEach { image ->
        add(ChatItem.User(image.prompt, "${image.id}-user"))
        add(ChatItem.Image(image, "${image.id}-image"))
    }
    if (isGenerating && pendingPrompt.isNotBlank()) {
        add(ChatItem.User(pendingPrompt, "pending-user"))
        add(ChatItem.Pending(pendingPrompt))
    } else if (error != null && pendingPrompt.isNotBlank()) {
        add(ChatItem.User(pendingPrompt, "error-user"))
        add(ChatItem.Error(error))
    }
}

sealed class HomeEvent {
    data object CreditsAdded : HomeEvent()
    data object AdUnavailable : HomeEvent()
    data object NeedCredits : HomeEvent()
    data object ShowInterstitial : HomeEvent()
    data object Saved : HomeEvent()
    data object SaveFailed : HomeEvent()
}

class HomeViewModel(
    private val generateImage: GenerateImageUseCase,
    private val creditManager: CreditManager,
    private val preferences: AppPreferences,
    private val history: HistoryRepository,
    private val saver: com.kolpona.app.utils.ImageSaver,
    private val share: com.kolpona.app.utils.ImageShare,
    val adManager: StartIoAdManager
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<HomeEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<HomeEvent> = _events.asSharedFlow()

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
                preferences.enhancePrompts,
                history.observeAll()
            ) { credits, quality, enhance, images ->
                HomeExtras(credits, quality, enhance, images)
            }.collect { extras ->
                _state.update {
                    it.copy(
                        credits = extras.credits,
                        quality = extras.quality,
                        enhance = extras.enhance,
                        images = extras.images
                    )
                }
            }
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

    fun sendSuggestion(text: String) {
        _state.update { it.copy(prompt = text) }
        send()
    }

    fun send() {
        val snapshot = _state.value
        val text = snapshot.prompt.trim()
        if (text.isEmpty() || snapshot.isGenerating) return
        if (snapshot.credits < CreditConfig.GENERATION_COST) {
            _events.tryEmit(HomeEvent.NeedCredits)
            return
        }
        _state.update { it.copy(prompt = "", pendingPrompt = text, error = null) }
        generateInternal(text)
    }

    fun retry() {
        val text = _state.value.pendingPrompt.ifBlank { _state.value.prompt }.trim()
        if (text.isEmpty() || _state.value.isGenerating) return
        generateInternal(text)
    }

    private fun generateInternal(prompt: String) {
        val snapshot = _state.value
        viewModelScope.launch {
            _state.update { it.copy(isGenerating = true, error = null, pendingPrompt = prompt) }
            val outcome = generateImage(
                GenerationInput(
                    prompt = prompt,
                    style = snapshot.style,
                    aspectRatio = snapshot.aspectRatio,
                    quality = snapshot.quality,
                    modelId = snapshot.modelId,
                    enhance = snapshot.enhance
                )
            )
            when (outcome) {
                is GenerationOutcome.Success -> {
                    val count = snapshot.successfulGenerations + 1
                    _state.update {
                        it.copy(
                            isGenerating = false,
                            error = null,
                            pendingPrompt = "",
                            successfulGenerations = count
                        )
                    }
                    if (count % 2 == 0) {
                        _events.emit(HomeEvent.ShowInterstitial)
                    }
                }
                is GenerationOutcome.Failure -> {
                    _state.update { it.copy(isGenerating = false, error = outcome.error) }
                    if (outcome.error == GenerationError.INSUFFICIENT_CREDITS) {
                        _events.emit(HomeEvent.NeedCredits)
                    }
                }
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

    companion object {
        fun create(container: AppContainer) = HomeViewModel(
            generateImage = container.generateImageUseCase,
            creditManager = container.creditManager,
            preferences = container.preferences,
            history = container.historyRepository,
            adManager = container.adManager
        )
    }

    private data class HomeExtras(
        val credits: Int,
        val quality: ImageQuality,
        val enhance: Boolean,
        val images: List<GeneratedImage>
    )
}
