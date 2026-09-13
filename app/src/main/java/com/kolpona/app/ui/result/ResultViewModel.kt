package com.kolpona.app.ui.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import com.kolpona.app.domain.model.ImageStyle
import com.kolpona.app.utils.ImageSaver
import com.kolpona.app.utils.ImageShare
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ResultUiState(
    val isGenerating: Boolean = false,
    val error: GenerationError? = null,
    val credits: Int = CreditConfig.DAILY_INITIAL_CREDITS
)

sealed class ResultEvent {
    data class Regenerated(val imageId: String) : ResultEvent()
    data object Saved : ResultEvent()
    data object SaveFailed : ResultEvent()
}

@OptIn(ExperimentalCoroutinesApi::class)
class ResultViewModel(
    private val history: HistoryRepository,
    private val generateImage: GenerateImageUseCase,
    creditManager: CreditManager,
    private val preferences: AppPreferences,
    private val saver: ImageSaver,
    private val share: ImageShare
) : ViewModel() {

    private val imageId = MutableStateFlow<String?>(null)

    val image: StateFlow<GeneratedImage?> = imageId
        .flatMapLatest { id -> if (id == null) flowOf(null) else history.observeById(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _state = MutableStateFlow(ResultUiState())
    val state: StateFlow<ResultUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<ResultEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<ResultEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            creditManager.currentCredits.collect { credits ->
                _state.update { it.copy(credits = credits) }
            }
        }
    }

    fun setImageId(id: String) {
        imageId.value = id
    }

    fun download() {
        val current = image.value ?: return
        viewModelScope.launch {
            runCatching {
                saver.saveToGallery(current.localPath, "Kolpona_${current.id.take(8)}.jpg")
            }.onSuccess { _events.emit(ResultEvent.Saved) }
                .onFailure { _events.emit(ResultEvent.SaveFailed) }
        }
    }

    fun share(title: String) {
        val current = image.value ?: return
        viewModelScope.launch { share.share(current.localPath, title) }
    }

    fun generateAgain() {
        val current = image.value ?: return
        if (_state.value.isGenerating) return
        viewModelScope.launch {
            _state.update { it.copy(isGenerating = true, error = null) }
            val quality = preferences.imageQuality.first()
            val enhance = preferences.enhancePrompts.first()
            val outcome = generateImage(
                GenerationInput(
                    prompt = current.prompt,
                    style = ImageStyle.fromId(current.styleId),
                    aspectRatio = AspectRatio.fromId(current.aspectRatioId),
                    quality = quality,
                    modelId = current.model,
                    enhance = enhance
                )
            )
            when (outcome) {
                is GenerationOutcome.Success -> {
                    _state.update { it.copy(isGenerating = false) }
                    _events.emit(ResultEvent.Regenerated(outcome.image.id))
                }
                is GenerationOutcome.Failure -> {
                    _state.update { it.copy(isGenerating = false, error = outcome.error) }
                }
            }
        }
    }

    companion object {
        fun create(container: AppContainer) = ResultViewModel(
            history = container.historyRepository,
            generateImage = container.generateImageUseCase,
            creditManager = container.creditManager,
            preferences = container.preferences,
            saver = container.imageSaver,
            share = container.imageShare
        )
    }
}
