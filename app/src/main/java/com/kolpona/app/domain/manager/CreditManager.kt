package com.kolpona.app.domain.manager

import com.kolpona.app.data.prefs.AppPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZoneId

/**
 * Owns the local credit wallet.
 *
 * Daily reset compares calendar dates in the device time zone and runs at most once per local day.
 * If the clock / time zone moves backwards, credits are not granted again.
 *
 * Generation deductions and rewarded grants are idempotent per transaction id so rotation,
 * retries, and duplicate SDK callbacks cannot apply the same transaction twice.
 */
class CreditManager(
    private val preferences: AppPreferences
) {
    private val mutex = Mutex()
    private val processedGenerationIds = LinkedHashSet<String>()
    private val processedRewardTokens = LinkedHashSet<String>()

    val currentCredits: Flow<Int> = preferences.credits

    val dailyInitialCredits: Int get() = CreditConfig.DAILY_INITIAL_CREDITS
    val generationCost: Int get() = CreditConfig.GENERATION_COST
    val rewardedVideoReward: Int get() = CreditConfig.REWARDED_VIDEO_REWARD

    suspend fun refreshDailyCredits() {
        mutex.withLock {
            val today = LocalDate.now(ZoneId.systemDefault()).toEpochDay()
            val last = preferences.getLastResetEpochDay()
            when {
                last == 0L -> {
                    preferences.setCredits(CreditConfig.DAILY_INITIAL_CREDITS)
                    preferences.setLastResetEpochDay(today)
                }
                today > last -> {
                    preferences.setCredits(CreditConfig.DAILY_INITIAL_CREDITS)
                    preferences.setLastResetEpochDay(today)
                }
                // today == last → already reset today
                // today < last → clock/timezone moved backwards; do not grant extra credits
            }
        }
    }

    suspend fun canGenerate(): Boolean {
        refreshDailyCredits()
        return preferences.getCredits() >= CreditConfig.GENERATION_COST
    }

    suspend fun deductForSuccessfulGeneration(generationId: String): Boolean = mutex.withLock {
        if (!processedGenerationIds.add(generationId)) return false
        trim(processedGenerationIds)
        val current = preferences.getCredits()
        if (current < CreditConfig.GENERATION_COST) {
            processedGenerationIds.remove(generationId)
            return false
        }
        preferences.setCredits(current - CreditConfig.GENERATION_COST)
        true
    }

    suspend fun grantRewarded(token: String): Boolean = mutex.withLock {
        if (!processedRewardTokens.add(token)) return false
        trim(processedRewardTokens)
        val current = preferences.getCredits()
        preferences.setCredits(current + CreditConfig.REWARDED_VIDEO_REWARD)
        true
    }

    private fun trim(set: LinkedHashSet<String>) {
        while (set.size > 64) {
            val first = set.first()
            set.remove(first)
        }
    }
}
