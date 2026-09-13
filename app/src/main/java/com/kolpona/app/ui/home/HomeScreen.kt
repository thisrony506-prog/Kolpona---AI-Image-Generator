package com.kolpona.app.ui.home

import android.app.Activity
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kolpona.app.R
import com.kolpona.app.domain.manager.CreditConfig
import com.kolpona.app.domain.model.AspectRatio
import com.kolpona.app.domain.model.GenerationError
import com.kolpona.app.domain.model.ImageQuality
import com.kolpona.app.domain.model.ImageStyle
import com.kolpona.app.ui.components.GeneratingDialog
import com.kolpona.app.ui.components.KolponaMark
import com.kolpona.app.ui.components.SectionLabel
import com.kolpona.app.ui.theme.PinkAccent
import com.kolpona.app.utils.formatArgs
import com.kolpona.app.utils.messageRes

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenSettings: () -> Unit,
    onGenerated: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity

    LaunchedEffect(Unit) {
        activity?.let { viewModel.adManager.preload(it) }
        viewModel.events.collect { event ->
            when (event) {
                is HomeEvent.NavigateToResult -> onGenerated(event.imageId)
                HomeEvent.CreditsAdded -> {
                    Toast.makeText(
                        context,
                        context.getString(R.string.credits_added, CreditConfig.REWARDED_VIDEO_REWARD),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                HomeEvent.AdUnavailable -> {
                    Toast.makeText(
                        context,
                        context.getString(R.string.error_ad_unavailable),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    if (state.isGenerating) {
        GeneratingDialog()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        HomeHeader(
            credits = state.credits,
            onSettings = onOpenSettings
        )
        Spacer(Modifier.height(28.dp))
        Text(
            text = stringResource(R.string.hero_title),
            style = MaterialTheme.typography.displaySmall
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.hero_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = state.prompt,
            onValueChange = viewModel::onPromptChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            placeholder = { Text(stringResource(R.string.prompt_hint)) },
            shape = RoundedCornerShape(20.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PinkAccent,
                cursorColor = PinkAccent
            )
        )
        Spacer(Modifier.height(20.dp))
        SectionLabel(stringResource(R.string.style_label))
        StyleChipRow(selected = state.style, onSelect = viewModel::onStyleSelected)
        Spacer(Modifier.height(20.dp))
        SectionLabel(stringResource(R.string.aspect_ratio_label))
        AspectRow(selected = state.aspectRatio, onSelect = viewModel::onAspectSelected)
        Spacer(Modifier.height(16.dp))
        SectionLabel(stringResource(R.string.quality_label))
        QualityRow(selected = state.quality, onSelect = viewModel::onQualitySelected)
        Spacer(Modifier.height(24.dp))

        val canAfford = state.credits >= CreditConfig.GENERATION_COST
        Button(
            onClick = viewModel::generate,
            enabled = !state.isGenerating && canAfford,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PinkAccent,
                disabledContainerColor = PinkAccent.copy(alpha = 0.4f),
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text(
                text = if (canAfford) {
                    stringResource(R.string.generate_image, CreditConfig.GENERATION_COST)
                } else {
                    stringResource(R.string.not_enough_credits)
                },
                style = MaterialTheme.typography.titleMedium
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.credits_remaining, state.credits),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        if (state.error != null) {
            Spacer(Modifier.height(12.dp))
            ErrorCard(error = state.error, onRetry = viewModel::retry)
        }

        Spacer(Modifier.height(20.dp))
        CreditCard(credits = state.credits)
        Spacer(Modifier.height(16.dp))
        RewardCard(
            enabled = !state.isGenerating && !state.watchingAd,
            onWatch = {
                val host = activity
                if (host == null) {
                    viewModel.onAdUnavailable()
                    return@RewardCard
                }
                viewModel.setWatchingAd(true)
                viewModel.adManager.showRewarded(
                    activity = host,
                    onRewarded = viewModel::onAdRewarded,
                    onUnavailable = viewModel::onAdUnavailable,
                    onClosed = viewModel::onAdClosed
                )
            }
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun HomeHeader(credits: Int, onSettings: () -> Unit) {
    val creditsDesc = stringResource(R.string.cd_credits)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        KolponaMark(size = 40.dp)
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.credits_count, credits),
                style = MaterialTheme.typography.bodyMedium,
                color = PinkAccent,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { contentDescription = creditsDesc }
            )
        }
        IconButton(onClick = onSettings, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = stringResource(R.string.cd_settings)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StyleChipRow(selected: ImageStyle, onSelect: (ImageStyle) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ImageStyle.entries.forEach { style ->
            val isSelected = style == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(style) },
                label = { Text(stringResource(style.labelRes)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PinkAccent,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(20.dp)
            )
        }
    }
}

@Composable
private fun AspectRow(selected: AspectRatio, onSelect: (AspectRatio) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        AspectRatio.entries.forEach { ratio ->
            val isSelected = ratio == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(ratio) },
                modifier = Modifier.weight(1f),
                label = { Text(ratio.shortLabel) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PinkAccent,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

@Composable
private fun QualityRow(selected: ImageQuality, onSelect: (ImageQuality) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ImageQuality.entries.forEach { quality ->
            FilterChip(
                selected = quality == selected,
                onClick = { onSelect(quality) },
                label = { Text(stringResource(quality.labelRes)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PinkAccent,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

@Composable
private fun ErrorCard(error: GenerationError, onRetry: () -> Unit) {
    val args = error.formatArgs()
    val message = if (args != null) {
        stringResource(error.messageRes(), *args)
    } else {
        stringResource(error.messageRes())
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(text = message, color = MaterialTheme.colorScheme.onErrorContainer)
            if (error != GenerationError.EMPTY_PROMPT && error != GenerationError.INSUFFICIENT_CREDITS) {
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.try_again), color = PinkAccent)
                }
            }
        }
    }
}

@Composable
private fun CreditCard(credits: Int) {
    val max = CreditConfig.DAILY_INITIAL_CREDITS
    val progress = (credits.toFloat() / max.toFloat()).coerceIn(0f, 1f)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(stringResource(R.string.daily_credits), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            if (credits <= 0) {
                Text(
                    text = stringResource(R.string.daily_credits_used),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.watch_video_to_earn),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PinkAccent
                )
            } else {
                Text(
                    text = stringResource(R.string.daily_credits_progress, credits, max),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(8.dp)),
                color = PinkAccent,
                trackColor = PinkAccent.copy(alpha = 0.12f)
            )
        }
    }
}

@Composable
private fun RewardCard(enabled: Boolean, onWatch: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(stringResource(R.string.need_more_credits), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.watch_video_card_body, CreditConfig.REWARDED_VIDEO_REWARD),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = onWatch,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(stringResource(R.string.watch_video_reward, CreditConfig.REWARDED_VIDEO_REWARD))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.reward_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
