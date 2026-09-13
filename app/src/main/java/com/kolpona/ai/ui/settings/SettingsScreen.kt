package com.kolpona.ai.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kolpona.ai.BuildConfig
import com.kolpona.ai.R
import com.kolpona.ai.domain.manager.CreditConfig
import com.kolpona.ai.domain.model.AspectRatio
import com.kolpona.ai.domain.model.ImageQuality
import com.kolpona.ai.domain.model.ImageStyle
import com.kolpona.ai.domain.model.ThemeMode
import com.kolpona.ai.ui.components.KolponaMark
import com.kolpona.ai.ui.components.StudioBackdrop
import com.kolpona.ai.ui.theme.ElectricBlue
import com.kolpona.ai.ui.theme.GlassFill
import com.kolpona.ai.ui.theme.GlassStroke
import com.kolpona.ai.ui.theme.MutedGray
import com.kolpona.ai.ui.theme.NeonViolet
import com.kolpona.ai.ui.theme.SoftWhite

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
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = ElectricBlue,
        selectedLabelColor = Color.White,
        containerColor = Color(0x33101827),
        labelColor = SoftWhite
    )

    Box(modifier.fillMaxSize()) {
        StudioBackdrop(Modifier.fillMaxSize())
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                KolponaMark(size = 36.dp)
                Spacer(Modifier.size(10.dp))
                Text(
                    text = stringResource(R.string.settings_title),
                    color = SoftWhite,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            GlassSection(stringResource(R.string.settings_appearance)) {
                Text(stringResource(R.string.dark_mode), color = SoftWhite, style = MaterialTheme.typography.titleMedium)
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
                            colors = chipColors
                        )
                    }
                }
            }

            GlassSection(stringResource(R.string.settings_credits)) {
                ReadOnlyRow(stringResource(R.string.daily_credits_setting), CreditConfig.DAILY_INITIAL_CREDITS.toString())
                ReadOnlyRow(stringResource(R.string.generation_cost_setting), CreditConfig.GENERATION_COST.toString())
                ReadOnlyRow(stringResource(R.string.ad_reward_setting), CreditConfig.REWARDED_VIDEO_REWARD.toString())
                Text(
                    text = stringResource(R.string.developer_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedGray,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            GlassSection(stringResource(R.string.settings_generation)) {
                Text(stringResource(R.string.image_quality), color = SoftWhite, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row {
                    ImageQuality.entries.forEach { q ->
                        FilterChip(
                            selected = quality == q,
                            onClick = { viewModel.setQuality(q) },
                            modifier = Modifier.padding(end = 8.dp),
                            label = { Text(stringResource(q.labelRes)) },
                            colors = chipColors
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.default_style), color = SoftWhite, style = MaterialTheme.typography.titleMedium)
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
                                    colors = chipColors
                                )
                            }
                        }
                    }
                }
                Text(stringResource(R.string.default_aspect_ratio), color = SoftWhite, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    AspectRatio.entries.forEach { r ->
                        FilterChip(
                            selected = aspect == r,
                            onClick = { viewModel.setAspect(r) },
                            modifier = Modifier.padding(end = 8.dp),
                            label = { Text(r.shortLabel) },
                            colors = chipColors
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
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = ElectricBlue,
                                checkedThumbColor = Color.White
                            )
                        )
                    }
                )
            }

            GlassSection(stringResource(R.string.settings_data)) {
                SettingRow(
                    title = stringResource(R.string.clear_history),
                    onClick = { confirmClear = true }
                )
            }

            GlassSection(stringResource(R.string.settings_about)) {
                SettingRow(stringResource(R.string.privacy_policy), onClick = onPrivacy)
                SettingRow(stringResource(R.string.terms_of_use), onClick = onTerms)
                SettingRow(stringResource(R.string.about_kolpona), onClick = onAbout)
                Text(
                    text = stringResource(R.string.version_format, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedGray,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = Color(0xFF0E1424),
            title = { Text(stringResource(R.string.clear_history_confirm_title), color = SoftWhite) },
            text = { Text(stringResource(R.string.clear_history_confirm_body), color = MutedGray) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearHistory()
                    confirmClear = false
                }) { Text(stringResource(R.string.clear), color = ElectricBlue) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel), color = MutedGray) }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@Composable
private fun GlassSection(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(GlassFill)
            .border(1.dp, GlassStroke, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = NeonViolet,
            modifier = Modifier.padding(bottom = 10.dp)
        )
        content()
    }
}

@Composable
private fun ReadOnlyRow(title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = SoftWhite)
        Text(value, style = MaterialTheme.typography.titleMedium, color = ElectricBlue)
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
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = SoftWhite)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MutedGray)
            }
        }
        trailing?.invoke()
    }
}
