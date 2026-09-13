package com.kolpona.ai

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.kolpona.ai.domain.model.ThemeMode
import com.kolpona.ai.ui.auth.AuthNav
import com.kolpona.ai.ui.components.KolponaMark
import com.kolpona.ai.ui.components.StudioBackdrop
import com.kolpona.ai.ui.navigation.KolponaNav
import com.kolpona.ai.ui.theme.KolponaTheme
import com.kolpona.ai.ui.update.ForceUpdateScreen
import com.kolpona.ai.update.UpdateUiState
import kotlinx.coroutines.delay
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
            val updateState by app.container.updateManager.state
                .collectAsStateWithLifecycle()
            val currentUser by app.container.authRepository.user
                .collectAsStateWithLifecycle(initialValue = app.container.authRepository.currentUser)
            var ready by remember { mutableStateOf(false) }
            var showOnboarding by remember { mutableStateOf(true) }
            var gateOpen by remember { mutableStateOf(false) }
            val notificationPermission = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                showOnboarding = !app.container.preferences.onboardingComplete.first()
                ready = true
            }

            LaunchedEffect(currentUser?.uid) {
                val user = currentUser ?: return@LaunchedEffect
                app.container.notifier.welcomeIfNeeded(user.uid, user.displayName)
            }

            LaunchedEffect(Unit) {
                app.container.updateManager.check()
                val start = System.currentTimeMillis()
                while (System.currentTimeMillis() - start < 8_000) {
                    val s = app.container.updateManager.state.value
                    if (s is UpdateUiState.Current || s is UpdateUiState.Required) break
                    delay(50)
                }
                gateOpen = true
            }

            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        app.container.updateManager.onForeground()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            KolponaTheme(themeMode = themeMode) {
                when {
                    updateState is UpdateUiState.Required -> {
                        ForceUpdateScreen(app.container.updateManager)
                    }
                    !ready || !gateOpen -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            StudioBackdrop(Modifier.fillMaxSize())
                            KolponaMark(size = 72.dp)
                        }
                    }
                    currentUser == null -> AuthNav(onSignedIn = { })
                    else -> KolponaNav(
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
