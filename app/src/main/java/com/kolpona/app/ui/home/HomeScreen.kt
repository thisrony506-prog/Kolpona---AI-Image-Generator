@file:OptIn(ExperimentalMaterial3Api::class)

package com.kolpona.app.ui.home

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.speech.RecognizerIntent
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kolpona.app.R
import com.kolpona.app.domain.manager.CreditConfig
import com.kolpona.app.domain.model.AspectRatio
import com.kolpona.app.domain.model.GeneratedImage
import com.kolpona.app.domain.model.GenerationError
import com.kolpona.app.domain.model.ImageStyle
import com.kolpona.app.domain.model.MediaKind
import com.kolpona.app.ui.components.KolponaMark
import com.kolpona.app.ui.theme.PinkAccent
import com.kolpona.app.utils.formatArgs
import com.kolpona.app.utils.messageRes
import java.io.File
import java.util.Locale

private enum class OptionSheet { None, Category, Ratio }

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
    val listState = rememberLazyListState()
    var showCreditsSheet by rememberSaveable { mutableStateOf(false) }
    var optionSheet by rememberSaveable { mutableStateOf(OptionSheet.None) }

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
    ) { }

    val audioPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            try {
                voiceLauncher.launch(speechIntent(context.getString(R.string.voice_prompt)))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, context.getString(R.string.voice_unavailable), Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(context, context.getString(R.string.voice_unavailable), Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        activity?.let { host ->
            host.window.decorView.post { viewModel.adManager.preload(host) }
        }
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
                    Toast.makeText(context, context.getString(R.string.error_ad_unavailable), Toast.LENGTH_LONG).show()
                }
                HomeEvent.NeedCredits -> showCreditsSheet = true
                HomeEvent.ShowInterstitial -> activity?.let { viewModel.adManager.showInterstitial(it) }
                HomeEvent.Saved -> Toast.makeText(context, context.getString(R.string.image_saved), Toast.LENGTH_SHORT).show()
                HomeEvent.SaveFailed -> Toast.makeText(context, context.getString(R.string.error_save), Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(state.messages.size, state.isGenerating) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    fun send() {
        keyboard?.hide()
        focusManager.clearFocus()
        viewModel.send()
    }

    fun startVoice() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            try {
                voiceLauncher.launch(speechIntent(context.getString(R.string.voice_prompt)))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, context.getString(R.string.voice_unavailable), Toast.LENGTH_LONG).show()
            }
        } else {
            audioPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
    ) {
        Column(Modifier.fillMaxSize()) {
            ChatHeader(
                credits = state.credits,
                onCredits = { showCreditsSheet = true },
                onNewChat = viewModel::newChat,
                onSettings = onOpenSettings
            )

            if (state.messages.isEmpty()) {
                EmptyChat(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    onSuggestion = viewModel::onPromptChange
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
                    items(state.messages, key = { it.key }) { item ->
                        when (item) {
                            is ChatItem.User -> UserBubble(item.text)
                            is ChatItem.AssistantText -> AssistantTextBubble(item.text)
                            is ChatItem.Image -> ResultCard(
                                image = item.image,
                                onOpen = { onOpenImage(item.image.id) },
                                onDownload = {
                                    if (Build.VERSION.SDK_INT < 29) {
                                        storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                    }
                                    viewModel.download(item.image)
                                },
                                onShare = { viewModel.share(item.image, context.getString(R.string.share_image)) },
                                onRegenerate = { viewModel.regenerate(item.image) },
                                onVariation = { viewModel.variation(item.image) },
                                enabled = !state.isGenerating
                            )
                            is ChatItem.Pending -> PendingBubble(video = state.mediaType == MediaKind.VIDEO)
                            is ChatItem.Error -> ErrorBubble(error = item.error, onRetry = viewModel::retry)
                        }
                    }
                }
            }

            MediaTypeSelector(
                selected = state.mediaType,
                onSelect = viewModel::onMediaType,
                enabled = !state.isGenerating
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OptionPill(
                    label = stringResource(R.string.category),
                    value = stringResource(state.style.labelRes),
                    onClick = { optionSheet = OptionSheet.Category },
                    modifier = Modifier.weight(1f),
                    enabled = !state.isGenerating
                )
                OptionPill(
                    label = stringResource(R.string.ratio),
                    value = state.aspectRatio.shortLabel,
                    onClick = { optionSheet = OptionSheet.Ratio },
                    modifier = Modifier.weight(1f),
                    enabled = !state.isGenerating
                )
            }

            ChatComposer(
                prompt = state.prompt,
                onPromptChange = viewModel::onPromptChange,
                onClear = viewModel::clearPrompt,
                enabled = !state.isGenerating,
                canSend = state.prompt.isNotBlank() && !state.isGenerating,
                generateLabel = stringResource(
                    if (state.mediaType == MediaKind.VIDEO) R.string.generate_video_short
                    else R.string.generate_image_short
                ),
                onVoice = { startVoice() },
                onSend = { send() }
            )

            PromptSuggestionChips(
                video = state.mediaType == MediaKind.VIDEO,
                enabled = !state.isGenerating,
                onPick = viewModel::onPromptChange
            )
        }

        if (state.watchingAd) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(enabled = false, onClick = {}),
                contentAlignment = Alignment.Center
            ) {
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                    Row(
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = PinkAccent
                        )
                        Spacer(Modifier.size(12.dp))
                        Text(stringResource(R.string.ad_loading))
                    }
                }
            }
        }
    }

    if (optionSheet != OptionSheet.None) {
        ModalBottomSheet(
            onDismissRequest = { optionSheet = OptionSheet.None },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            when (optionSheet) {
                OptionSheet.Category -> OptionList(
                    title = stringResource(R.string.category),
                    options = ImageStyle.entries.map { stringResource(it.labelRes) to it },
                    selected = state.style,
                    onPick = {
                        viewModel.onStyleSelected(it)
                        optionSheet = OptionSheet.None
                    }
                )
                OptionSheet.Ratio -> OptionList(
                    title = stringResource(R.string.ratio),
                    options = AspectRatio.entries.map { it.shortLabel to it },
                    selected = state.aspectRatio,
                    onPick = {
                        viewModel.onAspectSelected(it)
                        optionSheet = OptionSheet.None
                    }
                )
                OptionSheet.None -> Unit
            }
        }
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
                    if (host == null || host.isFinishing) {
                        viewModel.onAdUnavailable()
                        return@CreditsSheet
                    }
                    showCreditsSheet = false
                    viewModel.setWatchingAd(true)
                    host.window.decorView.postDelayed({
                        if (host.isFinishing || host.isDestroyed) {
                            viewModel.onAdUnavailable()
                            return@postDelayed
                        }
                        viewModel.adManager.showRewarded(
                            activity = host,
                            onRewarded = viewModel::onAdRewarded,
                            onUnavailable = viewModel::onAdUnavailable,
                            onClosed = viewModel::onAdClosed
                        )
                    }, 500)
                }
            )
        }
    }
}

private fun speechIntent(prompt: String): Intent =
    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }

@Composable
private fun ChatHeader(
    credits: Int,
    onCredits: () -> Unit,
    onNewChat: () -> Unit,
    onSettings: () -> Unit
) {
    val creditsDesc = stringResource(R.string.cd_credits)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
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
            Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.credits_count, credits),
                    color = PinkAccent,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
        IconButton(onClick = onNewChat, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.cd_new_chat))
        }
        IconButton(onClick = onSettings, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.cd_settings))
        }
    }
}

@Composable
private fun EmptyChat(onSuggestion: (String) -> Unit, modifier: Modifier = Modifier) {
    val suggestions = listOf(
        stringResource(R.string.chat_suggest_1),
        stringResource(R.string.chat_suggest_2),
        stringResource(R.string.chat_suggest_3)
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
        suggestions.forEach { text ->
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
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
private fun AssistantTextBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        KolponaMark(size = 28.dp)
        Spacer(Modifier.size(8.dp))
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun ResultCard(
    image: GeneratedImage,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onRegenerate: () -> Unit,
    onVariation: () -> Unit,
    enabled: Boolean
) {
    val ratio = (image.width.toFloat() / image.height.coerceAtLeast(1).toFloat()).coerceIn(0.5f, 1.9f)
    val category = ImageStyle.fromId(image.styleId)
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        KolponaMark(size = 28.dp)
        Spacer(Modifier.size(8.dp))
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.widthIn(max = 340.dp)
        ) {
            Column(Modifier.padding(10.dp)) {
                Text(
                    text = stringResource(if (image.isVideo) R.string.chat_video_ready else R.string.chat_image_ready),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                if (image.isVideo) {
                    LoopingVideo(
                        path = image.localPath,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(ratio)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(onClick = onOpen)
                    )
                } else {
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
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(onClick = onOpen)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = image.prompt,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.meta_category, stringResource(category.labelRes)) +
                        "  ·  " +
                        stringResource(R.string.meta_ratio, image.aspectRatioId) +
                        if (image.isVideo) "  ·  " + stringResource(R.string.duration_seconds, 5) else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    CardAction(Icons.Outlined.FileDownload, stringResource(R.string.download), onDownload, enabled)
                    CardAction(Icons.Outlined.Share, stringResource(R.string.share), onShare, enabled)
                    CardAction(Icons.Outlined.Refresh, stringResource(R.string.regenerate), onRegenerate, enabled)
                    if (!image.isVideo) {
                        CardAction(Icons.Outlined.AutoAwesome, stringResource(R.string.variation), onVariation, enabled)
                    }
                }
            }
        }
    }
}

@Composable
private fun CardAction(icon: ImageVector, label: String, onClick: () -> Unit, enabled: Boolean) {
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.height(40.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.size(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
private fun LoopingVideo(path: String, modifier: Modifier = Modifier) {
    DisposableEffect(path) { onDispose { } }
    AndroidView(
        modifier = modifier.background(Color.Black),
        factory = { ctx ->
            VideoView(ctx).apply {
                setVideoPath(path)
                setOnPreparedListener { player ->
                    player.isLooping = true
                    start()
                }
            }
        }
    )
}

@Composable
private fun PendingBubble(video: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        KolponaMark(size = 28.dp)
        Spacer(Modifier.size(8.dp))
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = PinkAccent)
                Spacer(Modifier.size(10.dp))
                Text(
                    text = stringResource(if (video) R.string.creating_video else R.string.creating_image),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
private fun ErrorBubble(error: GenerationError, onRetry: () -> Unit) {
    val args = error.formatArgs()
    val message = if (args != null) stringResource(error.messageRes(), *args) else stringResource(error.messageRes())
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
private fun MediaTypeSelector(
    selected: MediaKind,
    onSelect: (MediaKind) -> Unit,
    enabled: Boolean
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(Modifier.padding(4.dp)) {
            MediaTab(
                label = stringResource(R.string.media_image),
                icon = Icons.Outlined.Image,
                selected = selected == MediaKind.IMAGE,
                onClick = { onSelect(MediaKind.IMAGE) },
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )
            MediaTab(
                label = stringResource(R.string.media_video),
                icon = Icons.Outlined.Videocam,
                selected = selected == MediaKind.VIDEO,
                onClick = { onSelect(MediaKind.VIDEO) },
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MediaTab(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) PinkAccent else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.size(6.dp))
            Text(
                text = label,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun OptionPill(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.height(44.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun <T> OptionList(
    title: String,
    options: List<Pair<String, T>>,
    selected: T,
    onPick: (T) -> Unit
) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        options.forEach { (label, value) ->
            val active = value == selected
            Surface(
                onClick = { onPick(value) },
                shape = RoundedCornerShape(14.dp),
                color = if (active) PinkAccent.copy(alpha = 0.16f) else Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (active) PinkAccent else MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PromptSuggestionChips(
    video: Boolean,
    enabled: Boolean,
    onPick: (String) -> Unit
) {
    val chips = if (video) {
        listOf(
            stringResource(R.string.chip_cat) to stringResource(R.string.chip_prompt_cat_video),
            stringResource(R.string.chip_cinematic) to stringResource(R.string.chip_prompt_cinematic_video),
            stringResource(R.string.chip_castle) to stringResource(R.string.chip_prompt_castle_video),
            stringResource(R.string.chip_nature) to stringResource(R.string.chip_prompt_nature_video)
        )
    } else {
        listOf(
            stringResource(R.string.chip_cat) to stringResource(R.string.chip_prompt_cat),
            stringResource(R.string.chip_cinematic) to stringResource(R.string.chip_prompt_cinematic),
            stringResource(R.string.chip_castle) to stringResource(R.string.chip_prompt_castle),
            stringResource(R.string.chip_nature) to stringResource(R.string.chip_prompt_nature)
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 12.dp, end = 12.dp, bottom = 8.dp, top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        chips.forEach { (label, prompt) ->
            SuggestionChip(
                onClick = { onPick(prompt) },
                enabled = enabled,
                label = {
                    Text(label, maxLines = 1, style = MaterialTheme.typography.labelMedium)
                },
                modifier = Modifier.height(32.dp)
            )
        }
    }
}

@Composable
private fun ChatComposer(
    prompt: String,
    onPromptChange: (String) -> Unit,
    onClear: () -> Unit,
    enabled: Boolean,
    canSend: Boolean,
    generateLabel: String,
    onVoice: () -> Unit,
    onSend: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Column(Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                IconButton(onClick = onVoice, enabled = enabled, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Outlined.Mic, contentDescription = stringResource(R.string.voice_input), tint = PinkAccent)
                }
                BasicTextField(
                    value = prompt,
                    onValueChange = onPromptChange,
                    enabled = enabled,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 12.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(PinkAccent),
                    maxLines = 6,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    keyboardActions = KeyboardActions(),
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
                if (prompt.isNotEmpty()) {
                    IconButton(onClick = onClear, enabled = enabled, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.clear_prompt))
                    }
                }
            }
            Surface(
                onClick = onSend,
                enabled = canSend,
                shape = RoundedCornerShape(16.dp),
                color = if (canSend) PinkAccent else PinkAccent.copy(alpha = 0.35f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = generateLabel,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
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
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
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
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = PinkAccent)
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
