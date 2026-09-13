package com.kolpona.ai.ui.settings

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kolpona.ai.data.auth.AuthRepository
import com.kolpona.ai.data.prefs.AppPreferences
import com.kolpona.ai.data.repository.HistoryRepository
import com.kolpona.ai.di.AppContainer
import com.kolpona.ai.domain.manager.CreditConfig
import com.kolpona.ai.domain.model.AspectRatio
import com.kolpona.ai.domain.model.ImageQuality
import com.kolpona.ai.domain.model.ImageStyle
import com.kolpona.ai.domain.model.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val quality: ImageQuality = ImageQuality.HIGH,
    val style: ImageStyle = ImageStyle.ALL,
    val aspectRatio: AspectRatio = AspectRatio.SQUARE,
    val enhance: Boolean = true,
    val dailyCredits: Int = CreditConfig.DAILY_INITIAL_CREDITS,
    val generationCost: Int = CreditConfig.GENERATION_COST,
    val adReward: Int = CreditConfig.REWARDED_VIDEO_REWARD
)

class SettingsViewModel(
    private val preferences: AppPreferences,
    private val history: HistoryRepository,
    private val auth: AuthRepository
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = preferences.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)
    val quality: StateFlow<ImageQuality> = preferences.imageQuality
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ImageQuality.HIGH)
    val style: StateFlow<ImageStyle> = preferences.defaultStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ImageStyle.ALL)
    val aspectRatio: StateFlow<AspectRatio> = preferences.defaultAspectRatio
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AspectRatio.SQUARE)
    val enhance: StateFlow<Boolean> = preferences.enhancePrompts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val accountEmail: StateFlow<String?> = auth.user
        .map { it?.email?.takeIf { email -> email.isNotBlank() } ?: it?.displayName }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), auth.currentUser?.email)

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { preferences.setThemeMode(mode) }
    fun setQuality(quality: ImageQuality) = viewModelScope.launch { preferences.setImageQuality(quality) }
    fun setStyle(style: ImageStyle) = viewModelScope.launch { preferences.setDefaultStyle(style) }
    fun setAspect(ratio: AspectRatio) = viewModelScope.launch { preferences.setDefaultAspectRatio(ratio) }
    fun setEnhance(enabled: Boolean) = viewModelScope.launch { preferences.setEnhancePrompts(enabled) }
    fun clearHistory() = viewModelScope.launch { history.clear() }
    fun signOut(activity: Activity) = viewModelScope.launch { auth.signOut(activity) }

    companion object {
        fun create(container: AppContainer) = SettingsViewModel(
            preferences = container.preferences,
            history = container.historyRepository,
            auth = container.authRepository
        )
    }
}
