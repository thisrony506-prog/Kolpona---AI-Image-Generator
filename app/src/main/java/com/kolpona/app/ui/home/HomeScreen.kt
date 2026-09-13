package com.kolpona.app.ui.home

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kolpona.app.R
import com.kolpona.app.ads.StartIoBanner
import com.kolpona.app.domain.manager.CreditConfig
import com.kolpona.app.domain.model.AspectRatio
import com.kolpona.app.domain.model.GeneratedImage
import com.kolpona.app.domain.model.GenerationError
import com.kolpona.app.domain.model.ImageStyle
import com.kolpona.app.ui.components.KolponaMark
import com.kolpona.app.ui.theme.PinkAccent
import com.kolpona.app.utils.formatArgs
import com.kolpona.app.utils.messageRes
import java.io.File
import java.util.Locale

private enum class ChatToolTab { Category, Ratio }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenSettings: () -> Unit,
    onOpenImage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val chatItems = state.chatItems()
    val listState = rememberLazyListState()
    var showCreditsSheet by rememberSaveable { mutableStateOf(false) }
    var toolTab by rememberSaveable { mutableStateOf(ChatToolTab.Category) }

    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.trim()
        if (!spoken.isNullOrBlank()) {
            viewModel.onPromptChange(
                if (state.prompt.isBlank()) spoken else "${state.prompt.trim()} $spoken"
            )
        }
    }

    val storagePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted || Build.VERSION.SDK_INT >= 29) {
            // Download is triggered from the image row after permission.
        }
    }

    LaunchedEffect(Unit) {
        activity?.let { viewModel.adManager.preload(it) }
        viewModel.events.collect { event ->
            when (event) {
                HomeEvent.CreditsAdded -> {
                    showCreditsSheet = false
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
                HomeEvent.NeedCredits -> showCreditsSheet = true
                HomeEvent.ShowInterstitial -> activity?.let { viewModel.adManager.showInterstitial(it) }
                HomeEvent.Saved -> Toast.makeText(context, context.getString(R.string.image_saved), Toast.LENGTH_SHORT).show()
                HomeEvent.SaveFailed -> Toast.makeText(context, context.getString(R.string.error_save), Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(chatItems.size, state.isGenerating) {
        if (chatItems.isNotEmpty()) {
            listState.animateScrollToItem(chatItems.lastIndex)
        }
    }

    fun send() {
        keyboard?.hide()
        focusManager.clearFocus()
        viewModel.send()
    }

    fun launchVoice() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.voice_prompt))
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            voiceLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, context.getString(R.string.voice_unavailable), Toast.LENGTH_LONG).show()
        }
    }

    val audioPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchVoice()
        else Toast.makeText(context, context.getString(R.string.voice_unavailable), Toast.LENGTH_LONG).show()
    }

    fun startVoice() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) launchVoice() else audioPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
    ) {
        ChatHeader(
            credits = state.credits,
            onCredits = { showCreditsSheet = true },
            onSettings = onOpenSettings
        )

        if (chatItems.isEmpty()) {
            EmptyChat(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                onSuggestion = { viewModel.sendSuggestion(it) }
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(chatItems, key = { it.key }) { item ->
                    when (item) {
                        is ChatItem.User -> UserBubble(item.text)
                        is ChatItem.Image -> AssistantImageBubble(
                            image = item.image,
                            onOpen = { onOpenImage(item.image.id) },
                            onDownload = {
                                if (Build.VERSION.SDK_INT < 29) {
                                    storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                }
                                viewModel.download(item.image)
                            },
                            onShare = { viewModel.share(item.image, context.getString(R.string.share_image)) }
                        )
                        is ChatItem.Pending -> PendingBubble()
                        is ChatItem.Error -> ErrorBubble(
                            error = item.error,
                            onRetry = viewModel::retry
                        )
                    }
                }
            }
        }

        ChatToolTabs(
            tab = toolTab,
            onTab = { toolTab = it },
            style = state.style,
            onStyle = viewModel::onStyleSelected,
            aspectRatio = state.aspectRatio,
            onAspect = viewModel::onAspectSelected,
            enabled = !state.isGenerating
        )

        ChatComposer(
            prompt = state.prompt,
            onPromptChange = viewModel::onPromptChange,
            enabled = !state.isGenerating,
            canSend = state.prompt.isNotBlank() && !state.isGenerating,
            onVoice = { startVoice() },
            onSend = { send() }
        )

        StartIoBanner(adManager = viewModel.adManager)
    }

    if (showCreditsSheet) {
        ModalBottomSheet(
            onDismissRequest = { if (!state.watchingAd) showCreditsSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            CreditsSheet(
                credits = state.credits,
                loading = state.watchingAd,
                enabled = !state.isGenerating && !state.watchingAd,
                onWatch = {
                    val host = activity
                    if (host == null) {
                        viewModel.onAdUnavailable()
                        return@CreditsSheet
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
        }
    }
}

@Composable
private fun ChatHeader(credits: Int, onCredits: () -> Unit, onSettings: () -> Unit) {
    val creditsDesc = stringResource(R.string.cd_credits)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        KolponaMark(size = 36.dp)
        Spacer(Modifier.size(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.chat_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = PinkAccent.copy(alpha = 0.14f),
            modifier = Modifier
                .height(40.dp)
                .clickable(onClick = onCredits)
                .semantics { contentDescription = creditsDesc }
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.credits_count, credits),
                    color = PinkAccent,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
        IconButton(onClick = onSettings, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = stringResource(R.string.cd_settings)
            )
        }
    }
}

@Composable
private fun EmptyChat(onSuggestion: (String) -> Unit, modifier: Modifier = Modifier) {
    val suggestions = listOf(
        stringResource(R.string.chat_suggest_1),
        stringResource(R.string.chat_suggest_2),
        stringResource(R.string.chat_suggest_3),
        stringResource(R.string.chat_suggest_4)
    )
    Column(
        modifier = modifier.padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        KolponaMark(size = 72.dp)
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.chat_greeting),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.chat_greeting_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        suggestions.chunked(2).forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { text ->
                    SuggestionChip(
                        onClick = { onSuggestion(text) },
                        label = {
                            Text(
                                text = text,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            shape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp),
            color = PinkAccent,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun AssistantImageBubble(
    image: GeneratedImage,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit
) {
    val ratio = (image.width.toFloat() / image.height.coerceAtLeast(1).toFloat()).coerceIn(0.6f, 1.8f)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        KolponaMark(size = 28.dp)
        Spacer(Modifier.size(8.dp))
        Column(modifier = Modifier.widthIn(max = 320.dp)) {
            Text(
                text = stringResource(R.string.chat_image_ready),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(File(image.localPath))
                    .crossfade(true)
                    .build(),
                contentDescription = stringResource(R.string.cd_generated_image),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(onClick = onOpen)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextButton(onClick = onDownload, modifier = Modifier.height(44.dp)) {
                    Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.download))
                }
                TextButton(onClick = onShare, modifier = Modifier.height(44.dp)) {
                    Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.share))
                }
            }
        }
    }
}

@Composable
private fun PendingBubble() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        KolponaMark(size = 28.dp)
        Spacer(Modifier.size(8.dp))
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = PinkAccent
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    text = stringResource(R.string.creating_image),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
private fun ErrorBubble(error: GenerationError, onRetry: () -> Unit) {
    val args = error.formatArgs()
    val message = if (args != null) {
        stringResource(error.messageRes(), *args)
    } else {
        stringResource(error.messageRes())
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        KolponaMark(size = 28.dp)
        Spacer(Modifier.size(8.dp))
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(text = message, color = MaterialTheme.colorScheme.onErrorContainer)
                if (error != GenerationError.EMPTY_PROMPT) {
                    TextButton(onClick = onRetry) {
                        Text(stringResource(R.string.try_again), color = PinkAccent)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatToolTabs(
    tab: ChatToolTab,
    onTab: (ChatToolTab) -> Unit,
    style: ImageStyle,
    onStyle: (ImageStyle) -> Unit,
    aspectRatio: AspectRatio,
    onAspect: (AspectRatio) -> Unit,
    enabled: Boolean
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.background)
            ) {
                ToolTab(
                    label = stringResource(R.string.tab_category),
                    selected = tab == ChatToolTab.Category,
                    onClick = { onTab(ChatToolTab.Category) },
                    modifier = Modifier.weight(1f)
                )
                ToolTab(
                    label = stringResource(R.string.tab_ratio),
                    selected = tab == ChatToolTab.Ratio,
                    onClick = { onTab(ChatToolTab.Ratio) },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(10.dp))
            when (tab) {
                ChatToolTab.Category -> {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ImageStyle.entries.toList(), key = { it.id }) { item ->
                            FilterChip(
                                selected = item == style,
                                onClick = { onStyle(item) },
                                enabled = enabled,
                                label = { Text(stringResource(item.labelRes)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PinkAccent,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }
                }
                ChatToolTab.Ratio -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AspectRatio.entries.forEach { ratio ->
                            FilterChip(
                                selected = ratio == aspectRatio,
                                onClick = { onAspect(ratio) },
                                enabled = enabled,
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
            }
        }
    }
}

@Composable
private fun ToolTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) PinkAccent else MaterialTheme.colorScheme.background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun ChatComposer(
    prompt: String,
    onPromptChange: (String) -> Unit,
    enabled: Boolean,
    canSend: Boolean,
    onVoice: () -> Unit,
    onSend: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 2.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            IconButton(
                onClick = onVoice,
                enabled = enabled,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Mic,
                    contentDescription = stringResource(R.string.voice_input),
                    tint = PinkAccent
                )
            }
            BasicTextField(
                value = prompt,
                onValueChange = onPromptChange,
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(PinkAccent),
                maxLines = 5,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                decorationBox = { inner ->
                    if (prompt.isEmpty()) {
                        Text(
                            text = stringResource(R.string.prompt_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    inner()
                }
            )
            FilledIconButton(
                onClick = onSend,
                enabled = canSend,
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = PinkAccent,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = PinkAccent.copy(alpha = 0.35f)
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowUpward,
                    contentDescription = stringResource(R.string.send)
                )
            }
        }
    }
}

@Composable
private fun CreditsSheet(
    credits: Int,
    loading: Boolean,
    enabled: Boolean,
    onWatch: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Text(stringResource(R.string.need_more_credits), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.credits_remaining, credits),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.watch_video_card_body, CreditConfig.REWARDED_VIDEO_REWARD),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        OutlinedButton(
            onClick = onWatch,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = PinkAccent
                )
                Spacer(Modifier.size(10.dp))
                Text(stringResource(R.string.ad_loading))
            } else {
                Text(stringResource(R.string.watch_video_reward, CreditConfig.REWARDED_VIDEO_REWARD))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.reward_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(28.dp))
    }
}
