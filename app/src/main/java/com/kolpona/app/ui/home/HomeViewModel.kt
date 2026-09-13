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
import com.kolpona.app.domain.model.GenerationError
import com.kolpona.app.domain.model.GenerationInput
import com.kolpona.app.domain.model.GenerationOutcome
import com.kolpona.app.domain.model.ImageModels
import com.kolpona.app.domain.model.ImageQuality
import com.kolpona.app.domain.model.ImageStyle
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
    val style: ImageStyle = ImageStyle.REALISTIC,
    val aspectRatio: AspectRatio = AspectRatio.SQUARE,
    val quality: ImageQuality = ImageQuality.HIGH,
    val modelId: String = ImageModels.default().id,
    val enhance: Boolean = true,
    val credits: Int = CreditConfig.DAILY_INITIAL_CREDITS,
    val isGenerating: Boolean = false,
    val error: GenerationError? = null,
    val watchingAd: Boolean = false
)

sealed class HomeEvent {
    data class NavigateToResult(val imageId: String) : HomeEvent()
    data object CreditsAdded : HomeEvent()
    data object AdUnavailable : HomeEvent()
}

class HomeViewModel(
    private val generateImage: GenerateImageUseCase,
    private val creditManager: CreditManager,
    private val preferences: AppPreferences,
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
            ) { credits, quality, enhance ->
                Triple(credits, quality, enhance)
            }.collect { (credits, quality, enhance) ->
                _state.update { it.copy(credits = credits, quality = quality, enhance = enhance) }
            }
        }
    }

    fun onPromptChange(value: String) {
        _state.update { it.copy(prompt = value, error = null) }
    }

    fun onStyleSelected(style: ImageStyle) {
        _state.update { it.copy(style = style) }
    }

    fun onAspectSelected(ratio: AspectRatio) {
        _state.update { it.copy(aspectRatio = ratio) }
    }

    fun onQualitySelected(quality: ImageQuality) {
        _state.update { it.copy(quality = quality) }
        viewModelScope.launch { preferences.setImageQuality(quality) }
    }

    fun generate() {
        val snapshot = _state.value
        if (snapshot.isGenerating) return
        viewModelScope.launch {
            _state.update { it.copy(isGenerating = true, error = null) }
            val outcome = generateImage(
                GenerationInput(
                    prompt = snapshot.prompt,
                    style = snapshot.style,
                    aspectRatio = snapshot.aspectRatio,
                    quality = snapshot.quality,
                    modelId = snapshot.modelId,
                    enhance = snapshot.enhance
                )
            )
            when (outcome) {
                is GenerationOutcome.Success -> {
                    _state.update { it.copy(isGenerating = false, error = null) }
                    _events.emit(HomeEvent.NavigateToResult(outcome.image.id))
                }
                is GenerationOutcome.Failure -> {
                    _state.update { it.copy(isGenerating = false, error = outcome.error) }
                }
            }
        }
    }

    fun retry() = generate()

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
            adManager = container.adManager
        )
    }
}
