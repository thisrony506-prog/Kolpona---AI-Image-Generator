package com.kolpona.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kolpona.app.domain.manager.CreditConfig
import com.kolpona.app.domain.model.AspectRatio
import com.kolpona.app.domain.model.ImageQuality
import com.kolpona.app.domain.model.ImageStyle
import com.kolpona.app.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "kolpona_prefs")

class AppPreferences(context: Context) {
    private val dataStore = context.applicationContext.dataStore

    val credits: Flow<Int> = dataStore.data.map { it[KEY_CREDITS] ?: CreditConfig.DAILY_INITIAL_CREDITS }
    val onboardingComplete: Flow<Boolean> = dataStore.data.map { it[KEY_ONBOARDING] ?: false }
    val themeMode: Flow<ThemeMode> = dataStore.data.map { ThemeMode.fromId(it[KEY_THEME] ?: ThemeMode.SYSTEM.name) }
    val defaultStyle: Flow<ImageStyle> = dataStore.data.map { ImageStyle.fromId(it[KEY_STYLE] ?: ImageStyle.ALL.id) }
    val defaultAspectRatio: Flow<AspectRatio> = dataStore.data.map {
        AspectRatio.fromId(it[KEY_ASPECT] ?: AspectRatio.SQUARE.id)
    }
    val imageQuality: Flow<ImageQuality> = dataStore.data.map {
        ImageQuality.fromId(it[KEY_QUALITY] ?: ImageQuality.HIGH.id)
    }
    val enhancePrompts: Flow<Boolean> = dataStore.data.map { it[KEY_ENHANCE] ?: true }

    suspend fun getCredits(): Int = credits.first()

    suspend fun setCredits(value: Int) {
        dataStore.edit { it[KEY_CREDITS] = value.coerceAtLeast(0) }
    }

    suspend fun getLastResetEpochDay(): Long = dataStore.data.first()[KEY_LAST_RESET] ?: 0L

    suspend fun setLastResetEpochDay(epochDay: Long) {
        dataStore.edit { it[KEY_LAST_RESET] = epochDay }
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        dataStore.edit { it[KEY_ONBOARDING] = complete }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[KEY_THEME] = mode.name }
    }

    suspend fun setDefaultStyle(style: ImageStyle) {
        dataStore.edit { it[KEY_STYLE] = style.id }
    }

    suspend fun setDefaultAspectRatio(ratio: AspectRatio) {
        dataStore.edit { it[KEY_ASPECT] = ratio.id }
    }

    suspend fun setImageQuality(quality: ImageQuality) {
        dataStore.edit { it[KEY_QUALITY] = quality.id }
    }

    suspend fun setEnhancePrompts(enabled: Boolean) {
        dataStore.edit { it[KEY_ENHANCE] = enabled }
    }

    private companion object {
        val KEY_CREDITS = intPreferencesKey("credits")
        val KEY_LAST_RESET = longPreferencesKey("last_credit_reset_epoch_day")
        val KEY_ONBOARDING = booleanPreferencesKey("onboarding_complete")
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_STYLE = stringPreferencesKey("default_style")
        val KEY_ASPECT = stringPreferencesKey("default_aspect")
        val KEY_QUALITY = stringPreferencesKey("image_quality")
        val KEY_ENHANCE = booleanPreferencesKey("enhance_prompts")
    }
}
