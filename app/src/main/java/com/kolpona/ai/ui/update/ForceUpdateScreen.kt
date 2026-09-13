package com.kolpona.ai.ui.update

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kolpona.ai.BuildConfig
import com.kolpona.ai.R
import com.kolpona.ai.ui.components.KolponaMark
import com.kolpona.ai.ui.components.StudioBackdrop
import com.kolpona.ai.ui.theme.ElectricBlue
import com.kolpona.ai.ui.theme.GlassFill
import com.kolpona.ai.ui.theme.GlassStroke
import com.kolpona.ai.ui.theme.MutedGray
import com.kolpona.ai.ui.theme.NeonViolet
import com.kolpona.ai.ui.theme.SoftWhite
import com.kolpona.ai.update.AppUpdateManager
import com.kolpona.ai.update.UpdatePhase
import com.kolpona.ai.update.UpdateUiState

@Composable
fun ForceUpdateScreen(manager: AppUpdateManager) {
    val state by manager.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current as Activity
    BackHandler { }

    Box(Modifier.fillMaxSize()) {
        StudioBackdrop(Modifier.fillMaxSize())
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))
            KolponaMark(size = 72.dp)
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.app_name),
                color = SoftWhite,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(28.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(GlassFill)
                    .border(1.dp, GlassStroke, RoundedCornerShape(24.dp))
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.update_required_title),
                    color = SoftWhite,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.update_required_body),
                    color = MutedGray,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )

                val required = state as? UpdateUiState.Required
                if (required != null && required.versionName.isNotBlank()) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(
                            R.string.update_from_to,
                            BuildConfig.VERSION_NAME,
                            required.versionName
                        ),
                        color = ElectricBlue,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (required.notes.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = required.notes,
                            color = NeonViolet,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                when {
                    state is UpdateUiState.Checking || state is UpdateUiState.Idle -> {
                        CircularProgressIndicator(
                            color = ElectricBlue,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.update_checking),
                            color = MutedGray,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    required?.phase == UpdatePhase.Downloading -> {
                        LinearProgressIndicator(
                            progress = (required.progress.coerceIn(0, 100)) / 100f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            color = ElectricBlue,
                            trackColor = Color(0x33243A6B)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.update_downloading, required.progress.coerceIn(0, 99)),
                            color = SoftWhite,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    required?.error != null -> {
                        Text(
                            text = stringResource(R.string.update_failed),
                            color = Color(0xFFFFB4AB),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                    required?.phase == UpdatePhase.NeedsPermission -> {
                        Text(
                            text = stringResource(R.string.update_allow_install_body),
                            color = MutedGray,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                    required?.phase == UpdatePhase.Installing -> {
                        CircularProgressIndicator(
                            color = ElectricBlue,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(Modifier.height(22.dp))

                val enabled = required != null && required.phase != UpdatePhase.Downloading &&
                    required.phase != UpdatePhase.Installing
                val label = when {
                    required?.error != null -> stringResource(R.string.update_retry)
                    required?.phase == UpdatePhase.NeedsPermission -> stringResource(R.string.update_allow_install)
                    required?.phase == UpdatePhase.ReadyToInstall -> stringResource(R.string.update_install)
                    required?.phase == UpdatePhase.Installing -> stringResource(R.string.update_install)
                    required?.phase == UpdatePhase.Downloading -> stringResource(
                        R.string.update_downloading,
                        required.progress
                    )
                    else -> stringResource(R.string.update_download)
                }

                Button(
                    onClick = {
                        when (required?.phase) {
                            UpdatePhase.NeedsPermission -> manager.openInstallPermissionSettings(activity)
                            UpdatePhase.ReadyToInstall, UpdatePhase.Installing -> manager.install(activity)
                            UpdatePhase.ReadyToDownload -> {
                                if (required.error != null) manager.retry() else manager.download()
                            }
                            else -> manager.retry()
                        }
                    },
                    enabled = enabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricBlue,
                        contentColor = Color.White,
                        disabledContainerColor = ElectricBlue.copy(alpha = 0.4f),
                        disabledContentColor = Color.White.copy(alpha = 0.8f)
                    )
                ) {
                    Text(label, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.weight(1f))
        }
    }
}
