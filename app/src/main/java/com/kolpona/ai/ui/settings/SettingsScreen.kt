@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.kolpona.ai.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Brightness2
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.BrightnessHigh
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.kolpona.ai.ui.theme.PinkAccent
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
    val credits by viewModel.creditBalance.collectAsStateWithLifecycle()
    val accountName by viewModel.accountName.collectAsStateWithLifecycle()
    val accountEmail by viewModel.accountEmail.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val activity = LocalContext.current as android.app.Activity
    val context = LocalContext.current
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    val pullState = rememberPullToRefreshState()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            val message = when (event) {
                SettingsEvent.Refreshed -> context.getString(R.string.refreshed)
                SettingsEvent.RefreshFailed -> context.getString(R.string.error_network)
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    Box(modifier.fillMaxSize()) {
        StudioBackdrop(Modifier.fillMaxSize())
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = viewModel::refresh,
            state = pullState,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    modifier = Modifier.align(Alignment.TopCenter),
                    isRefreshing = refreshing,
                    state = pullState,
                    color = ElectricBlue,
                    containerColor = GlassFill
                )
            }
        ) {
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
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_title),
                            color = SoftWhite,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.settings_pull_hint),
                            color = MutedGray,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                ProfileCard(
                    name = accountName,
                    email = accountEmail,
                    credits = credits,
                    onLogout = { confirmLogout = true }
                )

                GlassSection(
                    title = stringResource(R.string.settings_studio),
                    subtitle = stringResource(R.string.settings_studio_body)
                ) {
                    Text(
                        text = stringResource(R.string.image_quality),
                        color = SoftWhite,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ImageQuality.entries.forEach { option ->
                            ChoiceCard(
                                modifier = Modifier.weight(1f),
                                title = stringResource(option.labelRes),
                                body = stringResource(
                                    when (option) {
                                        ImageQuality.STANDARD -> R.string.quality_standard_hint
                                        ImageQuality.HIGH -> R.string.quality_high_hint
                                        ImageQuality.ULTRA -> R.string.quality_ultra_hint
                                    }
                                ),
                                selected = quality == option,
                                accent = NeonViolet,
                                onClick = { viewModel.setQuality(option) }
                            )
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = stringResource(R.string.default_aspect_ratio),
                        color = SoftWhite,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AspectRatio.entries.forEach { ratio ->
                            RatioTile(
                                ratio = ratio,
                                selected = aspect == ratio,
                                onClick = { viewModel.setAspect(ratio) }
                            )
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = stringResource(R.string.default_style),
                        color = SoftWhite,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ImageStyle.entries.forEach { option ->
                            val selected = style == option
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (selected) PinkAccent.copy(alpha = 0.18f) else Color(0x33101827))
                                    .border(
                                        1.dp,
                                        if (selected) PinkAccent.copy(alpha = 0.7f) else GlassStroke,
                                        RoundedCornerShape(16.dp)
                                    )
                                    .clickable { viewModel.setStyle(option) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = stringResource(option.labelRes),
                                    color = if (selected) PinkAccent else SoftWhite,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
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

                GlassSection(title = stringResource(R.string.settings_look)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            ChoiceCard(
                                modifier = Modifier.weight(1f),
                                title = stringResource(
                                    when (mode) {
                                        ThemeMode.SYSTEM -> R.string.theme_system
                                        ThemeMode.LIGHT -> R.string.theme_light
                                        ThemeMode.DARK -> R.string.theme_dark
                                    }
                                ),
                                icon = when (mode) {
                                    ThemeMode.SYSTEM -> Icons.Outlined.BrightnessAuto
                                    ThemeMode.LIGHT -> Icons.Outlined.BrightnessHigh
                                    ThemeMode.DARK -> Icons.Outlined.Brightness2
                                },
                                selected = theme == mode,
                                accent = ElectricBlue,
                                onClick = { viewModel.setTheme(mode) }
                            )
                        }
                    }
                }

                GlassSection(title = stringResource(R.string.settings_credits)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CreditStat(
                            modifier = Modifier.weight(1f),
                            label = stringResource(R.string.daily_credits_setting),
                            value = CreditConfig.DAILY_INITIAL_CREDITS.toString()
                        )
                        CreditStat(
                            modifier = Modifier.weight(1f),
                            label = stringResource(R.string.generation_cost_setting),
                            value = CreditConfig.GENERATION_COST.toString()
                        )
                        CreditStat(
                            modifier = Modifier.weight(1f),
                            label = stringResource(R.string.ad_reward_setting),
                            value = "+${CreditConfig.REWARDED_VIDEO_REWARD}"
                        )
                    }
                    Text(
                        text = stringResource(R.string.developer_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedGray,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }

                GlassSection(title = stringResource(R.string.settings_data)) {
                    SettingRow(
                        title = stringResource(R.string.clear_history),
                        subtitle = stringResource(R.string.settings_clear_body),
                        leading = Icons.Outlined.DeleteOutline,
                        leadingTint = PinkAccent,
                        onClick = { confirmClear = true }
                    )
                }

                GlassSection(title = stringResource(R.string.settings_about)) {
                    SettingRow(
                        title = stringResource(R.string.privacy_policy),
                        leading = Icons.Outlined.Shield,
                        onClick = onPrivacy
                    )
                    SettingRow(
                        title = stringResource(R.string.terms_of_use),
                        leading = Icons.Outlined.Policy,
                        onClick = onTerms
                    )
                    SettingRow(
                        title = stringResource(R.string.about_kolpona),
                        leading = Icons.Outlined.Info,
                        onClick = onAbout
                    )
                    Text(
                        text = stringResource(R.string.version_format, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedGray,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            containerColor = Color(0xFF0E1424),
            title = { Text(stringResource(R.string.auth_logout_title), color = SoftWhite) },
            text = { Text(stringResource(R.string.auth_logout_body), color = MutedGray) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.signOut(activity)
                    confirmLogout = false
                }) { Text(stringResource(R.string.auth_logout), color = ElectricBlue) }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) {
                    Text(stringResource(R.string.cancel), color = MutedGray)
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
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
                }) { Text(stringResource(R.string.clear), color = PinkAccent) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel), color = MutedGray) }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@Composable
private fun ProfileCard(
    name: String?,
    email: String?,
    credits: Int,
    onLogout: () -> Unit
) {
    val display = name?.takeIf { it.isNotBlank() } ?: email ?: stringResource(R.string.auth_signed_in)
    val initial = display.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "K"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(GlassFill)
            .border(1.dp, GlassStroke, RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        Brush.linearGradient(listOf(ElectricBlue, NeonViolet, PinkAccent)),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(initial, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = display,
                    color = SoftWhite,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!email.isNullOrBlank() && email != display) {
                    Text(
                        text = email,
                        color = MutedGray,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.CloudDone, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.settings_synced), color = MutedGray, style = MaterialTheme.typography.labelMedium)
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(ElectricBlue.copy(alpha = 0.16f))
                    .border(1.dp, ElectricBlue.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = stringResource(R.string.credits_count, credits),
                    color = ElectricBlue,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        SettingRow(
            title = stringResource(R.string.auth_logout),
            leading = Icons.Outlined.Logout,
            leadingTint = PinkAccent,
            onClick = onLogout
        )
    }
}

@Composable
private fun GlassSection(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(GlassFill)
            .border(1.dp, GlassStroke, RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = NeonViolet,
            fontWeight = FontWeight.SemiBold
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MutedGray,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )
        } else {
            Spacer(Modifier.height(10.dp))
        }
        content()
    }
}

@Composable
private fun ChoiceCard(
    modifier: Modifier = Modifier,
    title: String,
    body: String? = null,
    icon: ImageVector? = null,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) accent.copy(alpha = 0.16f) else Color(0x33101827))
            .border(1.dp, if (selected) accent.copy(alpha = 0.75f) else GlassStroke, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (selected) accent else MutedGray, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(6.dp))
        }
        Text(
            text = title,
            color = if (selected) accent else SoftWhite,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
        if (body != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = body,
                color = MutedGray,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun RatioTile(ratio: AspectRatio, selected: Boolean, onClick: () -> Unit) {
    val preview = ratio.shortLabel.split(":").let {
        val w = it.getOrNull(0)?.toFloatOrNull() ?: 1f
        val h = it.getOrNull(1)?.toFloatOrNull() ?: 1f
        (w / h).coerceIn(0.45f, 2.1f)
    }
    Column(
        modifier = Modifier
            .width(72.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) ElectricBlue.copy(alpha = 0.16f) else Color(0x33101827))
            .border(1.dp, if (selected) ElectricBlue.copy(alpha = 0.7f) else GlassStroke, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .fillMaxHeight(0.9f)
                    .aspectRatio(preview)
                    .border(1.5.dp, if (selected) ElectricBlue else MutedGray, RoundedCornerShape(3.dp))
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = ratio.shortLabel,
            color = if (selected) ElectricBlue else SoftWhite,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun CreditStat(modifier: Modifier = Modifier, label: String, value: String) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x33101827))
            .border(1.dp, GlassStroke, RoundedCornerShape(14.dp))
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = ElectricBlue, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(label, color = MutedGray, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String? = null,
    leading: ImageVector? = null,
    leadingTint: Color = ElectricBlue,
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
        if (leading != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(leadingTint.copy(alpha = 0.16f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(leading, contentDescription = null, tint = leadingTint, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = SoftWhite)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MutedGray)
            }
        }
        if (trailing != null) trailing() else if (onClick != null) {
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MutedGray)
        }
    }
}
