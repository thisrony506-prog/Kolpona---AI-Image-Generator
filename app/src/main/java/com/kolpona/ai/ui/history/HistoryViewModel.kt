package com.kolpona.ai.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kolpona.ai.data.cloud.UserSessionSync
import com.kolpona.ai.data.prefs.AppPreferences
import com.kolpona.ai.data.repository.GenerateImageUseCase
import com.kolpona.ai.data.repository.HistoryRepository
import com.kolpona.ai.di.AppContainer
import com.kolpona.ai.domain.model.AspectRatio
import com.kolpona.ai.domain.model.GeneratedImage
import com.kolpona.ai.domain.model.GenerationError
import com.kolpona.ai.domain.model.GenerationInput
import com.kolpona.ai.domain.model.GenerationOutcome
import com.kolpona.ai.domain.model.ImageStyle
import com.kolpona.ai.domain.model.MediaKind
import com.kolpona.ai.utils.ImageSaver
import com.kolpona.ai.utils.ImageShare
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class HistoryEvent {
    data object Saved : HistoryEvent()
    data object SaveFailed : HistoryEvent()
    data class Regenerated(val imageId: String) : HistoryEvent()
    data class GenerateFailed(val error: GenerationError) : HistoryEvent()
    data object Refreshed : HistoryEvent()
    data object RefreshFailed : HistoryEvent()
}

class HistoryViewModel(
    private val history: HistoryRepository,
    private val generateImage: GenerateImageUseCase,
    private val preferences: AppPreferences,
    private val saver: ImageSaver,
    private val share: ImageShare,
    private val sessionSync: UserSessionSync
) : ViewModel() {

    val images: StateFlow<List<GeneratedImage>> = history.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _generating = MutableStateFlow(false)
    val generating: StateFlow<Boolean> = _generating.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _events = MutableSharedFlow<HistoryEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<HistoryEvent> = _events.asSharedFlow()

    fun refresh() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            val ok = runCatching { sessionSync.refresh() }.getOrDefault(false)
            _refreshing.value = false
            _events.emit(if (ok) HistoryEvent.Refreshed else HistoryEvent.RefreshFailed)
        }
    }

    fun delete(image: GeneratedImage) {
        viewModelScope.launch { history.delete(image) }
    }

    fun download(image: GeneratedImage) {
        viewModelScope.launch {
            runCatching {
                saver.saveToGallery(
                    image.localPath,
                    if (image.isVideo) "Kolpona_${image.id.take(8)}.mp4" else "Kolpona_${image.id.take(8)}.jpg"
                )
            }.onSuccess { _events.emit(HistoryEvent.Saved) }
                .onFailure { _events.emit(HistoryEvent.SaveFailed) }
        }
    }

    fun share(image: GeneratedImage, title: String) {
        viewModelScope.launch { share.share(image.localPath, title) }
    }

    fun generateAgain(image: GeneratedImage) {
        if (_generating.value) return
        viewModelScope.launch {
            _generating.value = true
            val quality = preferences.imageQuality.first()
            val enhance = preferences.enhancePrompts.first()
            val outcome = generateImage(
                GenerationInput(
                    prompt = image.prompt,
                    style = ImageStyle.fromId(image.styleId),
                    aspectRatio = AspectRatio.fromId(image.aspectRatioId),
                    quality = quality,
                    modelId = image.model,
                    enhance = enhance,
                    mediaType = MediaKind.fromId(image.mediaType)
                )
            )
            _generating.value = false
            when (outcome) {
                is GenerationOutcome.Success -> _events.emit(HistoryEvent.Regenerated(outcome.image.id))
                is GenerationOutcome.Failure -> _events.emit(HistoryEvent.GenerateFailed(outcome.error))
            }
        }
    }

    companion object {
        fun create(container: AppContainer) = HistoryViewModel(
            history = container.historyRepository,
            generateImage = container.generateImageUseCase,
            preferences = container.preferences,
            saver = container.imageSaver,
            share = container.imageShare,
            sessionSync = container.userSessionSync
        )
    }
}
