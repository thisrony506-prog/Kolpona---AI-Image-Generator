package com.kolpona.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kolpona.app.ads.StartIoAdManager
import com.kolpona.app.data.prefs.AppPreferences
import com.kolpona.app.data.repository.GenerateImageUseCase
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
import com.kolpona.app.domain.model.MediaKind
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
    val lastPrompt: String = ""
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
}

class HomeViewModel(
    private val generateImage: GenerateImageUseCase,
    private val creditManager: CreditManager,
    private val preferences: AppPreferences,
    private val imageSaver: ImageSaver,
    private val imageShare: ImageShare,
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
                preferences.enhancePrompts
            ) { credits, quality, enhance -> Triple(credits, quality, enhance) }
                .collect { (credits, quality, enhance) ->
                    _state.update {
                        it.copy(credits = credits, quality = quality, enhance = enhance)
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

    fun onMediaType(type: MediaKind) {
        _state.update { it.copy(mediaType = type) }
    }

    fun clearPrompt() {
        _state.update { it.copy(prompt = "") }
    }

    fun newChat() {
        _state.update {
            it.copy(
                messages = emptyList(),
                pendingPrompt = "",
                error = null,
                lastPrompt = "",
                prompt = "",
                isGenerating = false
            )
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
        val resolved = resolvePrompt(typed, snapshot.lastPrompt)
        _state.update { it.copy(prompt = "", pendingPrompt = typed, error = null) }
        generateInternal(displayText = typed, generationPrompt = resolved, snapshot = snapshot)
    }

    fun retry() {
        val snapshot = _state.value
        val text = snapshot.pendingPrompt.ifBlank { snapshot.prompt }.trim()
        if (text.isEmpty() || snapshot.isGenerating) return
        val resolved = resolvePrompt(text, snapshot.lastPrompt)
        generateInternal(displayText = text, generationPrompt = resolved, snapshot = snapshot)
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
        viewModelScope.launch {
            val followUp = snapshot.lastPrompt.isNotBlank() && generationPrompt != displayText
            val withoutError = snapshot.messages.filterNot { it is ChatItem.Error || it is ChatItem.Pending }
            val withUser = withoutError + ChatItem.User(displayText, "u-${System.currentTimeMillis()}")
            val withHint = if (followUp) {
                withUser + ChatItem.AssistantText(
                    "Updating the previous creation with that.",
                    "t-${System.currentTimeMillis()}"
                )
            } else {
                withUser
            }
            _state.update {
                it.copy(
                    isGenerating = true,
                    error = null,
                    pendingPrompt = displayText,
                    messages = withHint + ChatItem.Pending(displayText)
                )
            }
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
                            lastPrompt = generationPrompt,
                            messages = ready
                        )
                    }
                    if (count % 2 == 0) {
                        _events.emit(HomeEvent.ShowInterstitial)
                    }
                }
                is GenerationOutcome.Failure -> {
                    _state.update {
                        it.copy(
                            isGenerating = false,
                            error = outcome.error,
                            messages = withHint + ChatItem.Error(outcome.error)
                        )
                    }
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

    private fun resolvePrompt(typed: String, last: String): String {
        if (last.isBlank()) return typed
        val words = typed.split(Regex("\\s+")).filter { it.isNotBlank() }
        val follow = words.size <= 6 && typed.length <= 64 &&
            !typed.contains("create", ignoreCase = true) &&
            !typed.contains("generate", ignoreCase = true) &&
            !typed.contains("make a", ignoreCase = true)
        return if (follow) "$last, $typed" else typed
    }

    companion object {
        fun create(container: AppContainer) = HomeViewModel(
            generateImage = container.generateImageUseCase,
            creditManager = container.creditManager,
            preferences = container.preferences,
            imageSaver = container.imageSaver,
            imageShare = container.imageShare,
            adManager = container.adManager
        )
    }
}
