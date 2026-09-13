package com.kolpona.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kolpona.app.BuildConfig
import com.kolpona.app.R
import com.kolpona.app.domain.manager.CreditConfig
import com.kolpona.app.domain.model.AspectRatio
import com.kolpona.app.domain.model.ImageQuality
import com.kolpona.app.domain.model.ImageStyle
import com.kolpona.app.domain.model.ThemeMode
import com.kolpona.app.ui.theme.PinkAccent

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onPrivacy: () -> Unit,
    onTerms: () -> Unit,
    onAbout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme by viewModel.themeMode.collectAsStateWithLifecycle()
    val quality by viewModel.quality.collectAsStateWithLifecycle()
    val style by viewModel.style.collectAsStateWithLifecycle()
    val aspect by viewModel.aspectRatio.collectAsStateWithLifecycle()
    val enhance by viewModel.enhance.collectAsStateWithLifecycle()
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(20.dp))

        SectionTitle(stringResource(R.string.settings_appearance))
        Text(stringResource(R.string.dark_mode), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = theme == mode,
                    onClick = { viewModel.setTheme(mode) },
                    modifier = Modifier.padding(end = 8.dp),
                    label = {
                        Text(
                            stringResource(
                                when (mode) {
                                    ThemeMode.SYSTEM -> R.string.theme_system
                                    ThemeMode.LIGHT -> R.string.theme_light
                                    ThemeMode.DARK -> R.string.theme_dark
                                }
                            )
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PinkAccent,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionTitle(stringResource(R.string.settings_credits))
        ReadOnlyRow(stringResource(R.string.daily_credits_setting), CreditConfig.DAILY_INITIAL_CREDITS.toString())
        ReadOnlyRow(stringResource(R.string.generation_cost_setting), CreditConfig.GENERATION_COST.toString())
        ReadOnlyRow(stringResource(R.string.ad_reward_setting), CreditConfig.REWARDED_VIDEO_REWARD.toString())
        Text(
            text = stringResource(R.string.developer_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        SectionTitle(stringResource(R.string.settings_generation))
        Text(stringResource(R.string.image_quality), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row {
            ImageQuality.entries.forEach { q ->
                FilterChip(
                    selected = quality == q,
                    onClick = { viewModel.setQuality(q) },
                    modifier = Modifier.padding(end = 8.dp),
                    label = { Text(stringResource(q.labelRes)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PinkAccent,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.default_style), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Column {
            ImageStyle.entries.chunked(3).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    row.forEach { s ->
                        FilterChip(
                            selected = style == s,
                            onClick = { viewModel.setStyle(s) },
                            modifier = Modifier.padding(end = 8.dp, bottom = 8.dp),
                            label = { Text(stringResource(s.labelRes)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PinkAccent,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            }
        }
        Text(stringResource(R.string.default_aspect_ratio), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            AspectRatio.entries.forEach { r ->
                FilterChip(
                    selected = aspect == r,
                    onClick = { viewModel.setAspect(r) },
                    modifier = Modifier.padding(end = 8.dp),
                    label = { Text(r.shortLabel) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PinkAccent,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        SettingRow(
            title = stringResource(R.string.enhance_prompts),
            subtitle = stringResource(R.string.enhance_prompts_subtitle),
            trailing = {
                Switch(
                    checked = enhance,
                    onCheckedChange = viewModel::setEnhance,
                    colors = SwitchDefaults.colors(checkedTrackColor = PinkAccent)
                )
            }
        )

        Spacer(Modifier.height(12.dp))
        SectionTitle(stringResource(R.string.settings_data))
        SettingRow(
            title = stringResource(R.string.clear_history),
            onClick = { confirmClear = true }
        )

        SectionTitle(stringResource(R.string.settings_about))
        SettingRow(stringResource(R.string.privacy_policy), onClick = onPrivacy)
        SettingRow(stringResource(R.string.terms_of_use), onClick = onTerms)
        SettingRow(stringResource(R.string.about_kolpona), onClick = onAbout)
        Text(
            text = stringResource(R.string.version_format, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 16.dp)
        )
        Spacer(Modifier.height(24.dp))
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.clear_history_confirm_title)) },
            text = { Text(stringResource(R.string.clear_history_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearHistory()
                    confirmClear = false
                }) { Text(stringResource(R.string.clear)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = PinkAccent,
        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
    )
}

@Composable
private fun ReadOnlyRow(title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing?.invoke()
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
}
