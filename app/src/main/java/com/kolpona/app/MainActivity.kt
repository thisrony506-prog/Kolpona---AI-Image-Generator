package com.kolpona.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.kolpona.app.domain.model.ThemeMode
import com.kolpona.app.ui.navigation.KolponaNav
import com.kolpona.app.ui.theme.KolponaTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as KolponaApp
        setContent {
            val themeMode by app.container.preferences.themeMode
                .collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            var ready by remember { mutableStateOf(false) }
            var showOnboarding by remember { mutableStateOf(true) }

            LaunchedEffect(Unit) {
                showOnboarding = !app.container.preferences.onboardingComplete.first()
                ready = true
            }

            KolponaTheme(themeMode = themeMode) {
                if (!ready) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    )
                } else {
                    KolponaNav(
                        showOnboarding = showOnboarding,
                        onOnboardingFinished = {
                            showOnboarding = false
                            lifecycleScope.launch {
                                app.container.preferences.setOnboardingComplete(true)
                            }
                        }
                    )
                }
            }
        }
    }
}
