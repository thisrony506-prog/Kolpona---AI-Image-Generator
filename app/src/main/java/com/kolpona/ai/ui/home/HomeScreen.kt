@file:OptIn(ExperimentalMaterial3Api::class)

package com.kolpona.ai.ui.home

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.speech.RecognizerIntent
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MovieFilter
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kolpona.ai.R
import com.kolpona.ai.data.database.ChatSessionEntity
import com.kolpona.ai.domain.manager.CreditConfig
import com.kolpona.ai.domain.model.AspectRatio
import com.kolpona.ai.domain.model.GeneratedImage
import com.kolpona.ai.domain.model.GenerationError
import com.kolpona.ai.domain.model.ImageQuality
import com.kolpona.ai.domain.model.ImageStyle
import com.kolpona.ai.domain.model.MediaKind
import com.kolpona.ai.ui.components.KolponaMark
import com.kolpona.ai.ui.theme.ElectricBlue
import com.kolpona.ai.ui.theme.GlassFill
import com.kolpona.ai.ui.theme.GlassStroke
import com.kolpona.ai.ui.theme.MidnightDeep
import com.kolpona.ai.ui.theme.MutedGray
import com.kolpona.ai.ui.theme.NeonMagenta
import com.kolpona.ai.ui.theme.NeonViolet
import com.kolpona.ai.ui.theme.PinkAccent
import com.kolpona.ai.ui.theme.SoftWhite
import com.kolpona.ai.utils.formatArgs
import com.kolpona.ai.utils.messageRes
import java.io.File
import java.util.Locale
import kotlinx.coroutines.delay

private enum class StudioSheet { None, Ratio, Quality, Category }

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
    var showChatsSheet by rememberSaveable { mutableStateOf(false) }
    var showPlusMenu by rememberSaveable { mutableStateOf(false) }
    var studioSheet by rememberSaveable { mutableStateOf(StudioSheet.None) }
    var renameId by rememberSaveable { mutableStateOf<String?>(null) }
    var renameText by rememberSaveable { mutableStateOf("") }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }

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

    BackHandler(enabled = showPlusMenu || studioSheet != StudioSheet.None || showChatsSheet || showCreditsSheet) {
        showPlusMenu = false
        studioSheet = StudioSheet.None
        showChatsSheet = false
        if (!state.watchingAd) showCreditsSheet = false
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
                HomeEvent.Refreshed -> Toast.makeText(context, context.getString(R.string.refreshed), Toast.LENGTH_SHORT).show()
                HomeEvent.RefreshFailed -> Toast.makeText(context, context.getString(R.string.error_refresh), Toast.LENGTH_SHORT).show()
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
        showPlusMenu = false
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
        StudioBackground(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize()) {
            StudioHeader(
                credits = state.credits,
                onCredits = { showCreditsSheet = true },
                onChats = { showChatsSheet = true },
                onNewChat = viewModel::newChat
            )

            val pullState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = { if (!state.isGenerating) viewModel.refresh() },
                state = pullState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        modifier = Modifier.align(Alignment.TopCenter),
                        isRefreshing = state.refreshing,
                        state = pullState,
                        color = ElectricBlue,
                        containerColor = GlassFill
                    )
                }
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = if (state.messages.isEmpty()) {
                        Arrangement.Center
                    } else {
                        Arrangement.spacedBy(12.dp)
                    }
                ) {
                    if (state.messages.isEmpty()) {
                        item(key = "empty-studio") {
                            EmptyStudio(Modifier.fillParentMaxHeight())
                        }
                    } else {
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
                                    onCopy = {
                                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                                        clipboard?.setPrimaryClip(ClipData.newPlainText("prompt", item.image.prompt))
                                        Toast.makeText(context, context.getString(R.string.prompt_copied), Toast.LENGTH_SHORT).show()
                                    },
                                    enabled = !state.isGenerating
                                )
                                is ChatItem.Pending -> GeneratingCard(
                                    video = state.mediaType == MediaKind.VIDEO,
                                    aspect = state.aspectRatio,
                                    onCancel = viewModel::cancelGeneration
                                )
                                is ChatItem.Error -> ErrorBubble(error = item.error, onRetry = viewModel::retry)
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = showPlusMenu,
                enter = fadeIn(tween(160)) + expandVertically(tween(180)),
                exit = fadeOut(tween(120)) + shrinkVertically(tween(140))
            ) {
                FeatureMenu(
                    mediaType = state.mediaType,
                    onImage = {
                        viewModel.onMediaType(MediaKind.IMAGE)
                        showPlusMenu = false
                    },
                    onVideo = {
                        viewModel.onMediaType(MediaKind.VIDEO)
                        showPlusMenu = false
                    },
                    onRatio = {
                        showPlusMenu = false
                        studioSheet = StudioSheet.Ratio
                    },
                    onQuality = {
                        showPlusMenu = false
                        studioSheet = StudioSheet.Quality
                    },
                    onStyle = {
                        showPlusMenu = false
                        studioSheet = StudioSheet.Category
                    }
                )
            }

            Text(
                text = stringResource(
                    R.string.studio_mode,
                    stringResource(if (state.mediaType == MediaKind.VIDEO) R.string.media_video else R.string.media_image),
                    state.aspectRatio.shortLabel,
                    stringResource(state.quality.labelRes)
                ),
                color = MutedGray,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp)
            )

            StudioComposer(
                prompt = state.prompt,
                onPromptChange = viewModel::onPromptChange,
                enabled = !state.isGenerating,
                canSend = state.prompt.isNotBlank() && !state.isGenerating,
                plusOpen = showPlusMenu,
                onPlus = { showPlusMenu = !showPlusMenu },
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
                Surface(shape = RoundedCornerShape(20.dp), color = GlassFill) {
                    Row(
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = ElectricBlue
                        )
                        Spacer(Modifier.size(12.dp))
                        Text(stringResource(R.string.ad_loading), color = SoftWhite)
                    }
                }
            }
        }
    }

    if (studioSheet != StudioSheet.None) {
        ModalBottomSheet(
            onDismissRequest = { studioSheet = StudioSheet.None },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color(0xFF0E1424)
        ) {
            when (studioSheet) {
                StudioSheet.Ratio -> RatioPicker(
                    selected = state.aspectRatio,
                    onPick = {
                        viewModel.onAspectSelected(it)
                        studioSheet = StudioSheet.None
                    }
                )
                StudioSheet.Quality -> QualityPicker(
                    selected = state.quality,
                    onPick = {
                        viewModel.onQualitySelected(it)
                        studioSheet = StudioSheet.None
                    }
                )
                StudioSheet.Category -> OptionList(
                    title = stringResource(R.string.category),
                    options = ImageStyle.entries.map { stringResource(it.labelRes) to it },
                    selected = state.style,
                    onPick = {
                        viewModel.onStyleSelected(it)
                        studioSheet = StudioSheet.None
                    }
                )
                StudioSheet.None -> Unit
            }
        }
    }

    if (showChatsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showChatsSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color(0xFF0E1424)
        ) {
            ChatSessionsSheet(
                sessions = state.sessions,
                currentId = state.sessionId,
                onOpen = {
                    viewModel.openChat(it)
                    showChatsSheet = false
                },
                onNew = {
                    viewModel.newChat()
                    showChatsSheet = false
                },
                onRename = { id, title ->
                    renameId = id
                    renameText = title
                },
                onDelete = { deleteId = it }
            )
        }
    }

    val renaming = renameId
    if (renaming != null) {
        AlertDialog(
            onDismissRequest = { renameId = null },
            title = { Text(stringResource(R.string.rename_chat)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.renameChat(renaming, renameText)
                        renameId = null
                    }
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { renameId = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    val deleting = deleteId
    if (deleting != null) {
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text(stringResource(R.string.delete_chat)) },
            text = { Text(stringResource(R.string.delete_chat_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteChat(deleting)
                        deleteId = null
                    }
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteId = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showCreditsSheet) {
        ModalBottomSheet(
            onDismissRequest = { if (!state.watchingAd) showCreditsSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color(0xFF0E1424)
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
private fun StudioBackground(modifier: Modifier = Modifier) {
    val motion = rememberInfiniteTransition(label = "glow")
    val shift by motion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shift"
    )
    Canvas(modifier.background(MidnightDeep)) {
        val w = size.width
        val h = size.height
        drawCircle(
            color = ElectricBlue.copy(alpha = 0.16f),
            radius = w * 0.42f,
            center = Offset(w * (0.08f + shift * 0.04f), h * 0.06f)
        )
        drawCircle(
            color = NeonViolet.copy(alpha = 0.14f),
            radius = w * 0.38f,
            center = Offset(w * (0.96f - shift * 0.03f), h * 0.22f)
        )
        drawCircle(
            color = NeonMagenta.copy(alpha = 0.10f),
            radius = w * 0.34f,
            center = Offset(w * 0.55f, h * (0.92f - shift * 0.03f))
        )
    }
}

@Composable
private fun StudioHeader(
    credits: Int,
    onCredits: () -> Unit,
    onChats: () -> Unit,
    onNewChat: () -> Unit
) {
    val creditsDesc = stringResource(R.string.cd_credits)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        KolponaMark(size = 38.dp)
        Spacer(Modifier.size(10.dp))
        Text(
            text = stringResource(R.string.app_name),
            color = SoftWhite,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .height(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(ElectricBlue.copy(alpha = 0.16f))
                .border(1.dp, ElectricBlue.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                .clickable(onClick = onCredits)
                .padding(horizontal = 12.dp)
                .semantics { contentDescription = creditsDesc },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.credits_count, credits),
                color = ElectricBlue,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelLarge
            )
        }
        IconButton(onClick = onChats, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = stringResource(R.string.cd_chats), tint = SoftWhite)
        }
        IconButton(onClick = onNewChat, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.cd_new_chat), tint = SoftWhite)
        }
    }
}

@Composable
private fun EmptyStudio(modifier: Modifier = Modifier) {
    val titleBrush = Brush.linearGradient(listOf(ElectricBlue, NeonViolet, PinkAccent))
    Column(
        modifier = modifier.padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(108.dp)
                .border(1.dp, ElectricBlue.copy(alpha = 0.35f), RoundedCornerShape(32.dp))
                .background(ElectricBlue.copy(alpha = 0.08f), RoundedCornerShape(32.dp)),
            contentAlignment = Alignment.Center
        ) {
            KolponaMark(size = 72.dp)
        }
        Spacer(Modifier.height(28.dp))
        Text(
            text = stringResource(R.string.chat_greeting),
            style = TextStyle(
                brush = titleBrush,
                fontWeight = FontWeight.Bold,
                fontSize = 34.sp,
                letterSpacing = (-0.4).sp
            ),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.chat_greeting_body),
            color = MutedGray,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun FeatureMenu(
    mediaType: MediaKind,
    onImage: () -> Unit,
    onVideo: () -> Unit,
    onRatio: () -> Unit,
    onQuality: () -> Unit,
    onStyle: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(GlassFill)
            .border(1.dp, GlassStroke, RoundedCornerShape(22.dp))
            .padding(8.dp)
    ) {
        FeatureRow(
            icon = Icons.Outlined.Image,
            tint = ElectricBlue,
            title = stringResource(R.string.media_image),
            body = stringResource(R.string.feature_image_body),
            selected = mediaType == MediaKind.IMAGE,
            onClick = onImage
        )
        FeatureRow(
            icon = Icons.Outlined.Videocam,
            tint = NeonViolet,
            title = stringResource(R.string.media_video),
            body = stringResource(R.string.feature_video_body),
            selected = mediaType == MediaKind.VIDEO,
            onClick = onVideo
        )
        FeatureRow(
            icon = Icons.Outlined.Crop,
            tint = NeonMagenta,
            title = stringResource(R.string.ratio),
            body = stringResource(R.string.feature_ratio_body),
            onClick = onRatio
        )
        FeatureRow(
            icon = Icons.Outlined.HighQuality,
            tint = ElectricBlue,
            title = stringResource(R.string.quality_label),
            body = stringResource(R.string.feature_quality_body),
            onClick = onQuality
        )
        FeatureRow(
            icon = Icons.Outlined.Palette,
            tint = PinkAccent,
            title = stringResource(R.string.category),
            body = stringResource(R.string.feature_style_body),
            onClick = onStyle
        )
    }
}

@Composable
private fun FeatureRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    body: String,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) tint.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(tint.copy(alpha = 0.18f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = SoftWhite, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
            Text(body, color = MutedGray, style = MaterialTheme.typography.bodyMedium)
        }
        Icon(Icons.Outlined.KeyboardArrowRight, contentDescription = null, tint = MutedGray)
    }
}

@Composable
private fun StudioComposer(
    prompt: String,
    onPromptChange: (String) -> Unit,
    enabled: Boolean,
    canSend: Boolean,
    plusOpen: Boolean,
    onPlus: () -> Unit,
    onVoice: () -> Unit,
    onSend: () -> Unit
) {
    val addDesc = stringResource(R.string.add_options)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .heightIn(min = 58.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(Color(0xE6101827))
            .border(1.dp, ElectricBlue.copy(alpha = 0.45f), RoundedCornerShape(30.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .border(1.5.dp, ElectricBlue, CircleShape)
                .clip(CircleShape)
                .clickable(enabled = enabled, onClick = onPlus)
                .semantics { contentDescription = addDesc },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (plusOpen) Icons.Outlined.Close else Icons.Outlined.Add,
                contentDescription = addDesc,
                tint = ElectricBlue
            )
        }
        BasicTextField(
            value = prompt,
            onValueChange = onPromptChange,
            enabled = enabled,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp, vertical = 10.dp),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = SoftWhite),
            cursorBrush = SolidColor(ElectricBlue),
            maxLines = 6,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
            keyboardActions = KeyboardActions(),
            decorationBox = { inner ->
                if (prompt.isEmpty()) {
                    Text(
                        text = stringResource(R.string.prompt_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MutedGray
                    )
                }
                inner()
            }
        )
        IconButton(onClick = onVoice, enabled = enabled, modifier = Modifier.size(42.dp)) {
            Icon(Icons.Outlined.Mic, contentDescription = stringResource(R.string.voice_input), tint = MutedGray)
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(ElectricBlue, NeonViolet, PinkAccent)))
                .then(if (canSend) Modifier else Modifier.background(Color.Black.copy(alpha = 0.45f)))
                .clickable(enabled = canSend, onClick = onSend),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.ArrowUpward, contentDescription = stringResource(R.string.send), tint = Color.White)
        }
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
            Triple(Icons.Outlined.Pets, stringResource(R.string.chip_cat), stringResource(R.string.chip_prompt_cat_video)),
            Triple(Icons.Outlined.MovieFilter, stringResource(R.string.chip_cinematic), stringResource(R.string.chip_prompt_cinematic_video)),
            Triple(Icons.Outlined.AccountBalance, stringResource(R.string.chip_castle), stringResource(R.string.chip_prompt_castle_video)),
            Triple(Icons.Outlined.Landscape, stringResource(R.string.chip_nature), stringResource(R.string.chip_prompt_nature_video))
        )
    } else {
        listOf(
            Triple(Icons.Outlined.Pets, stringResource(R.string.chip_cat), stringResource(R.string.chip_prompt_cat)),
            Triple(Icons.Outlined.MovieFilter, stringResource(R.string.chip_cinematic), stringResource(R.string.chip_prompt_cinematic)),
            Triple(Icons.Outlined.AccountBalance, stringResource(R.string.chip_castle), stringResource(R.string.chip_prompt_castle)),
            Triple(Icons.Outlined.Landscape, stringResource(R.string.chip_nature), stringResource(R.string.chip_prompt_nature))
        )
    }
    val tints = listOf(ElectricBlue, NeonViolet, PinkAccent, ElectricBlue)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 12.dp, end = 12.dp, bottom = 10.dp, top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        chips.forEachIndexed { index, (icon, label, prompt) ->
            val tint = tints[index % tints.size]
            Row(
                modifier = Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xCC101827))
                    .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                    .clickable(enabled = enabled) { onPick(prompt) }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(label, color = SoftWhite, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp))
                .background(Brush.linearGradient(listOf(ElectricBlue, NeonViolet)))
        ) {
            Text(
                text = text,
                color = Color.White,
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
        Text(
            text = text,
            color = SoftWhite,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .widthIn(max = 320.dp)
                .padding(top = 4.dp)
        )
    }
}

@Composable
private fun ResultCard(
    image: GeneratedImage,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onRegenerate: () -> Unit,
    onCopy: () -> Unit,
    enabled: Boolean
) {
    val ratio = (image.width.toFloat() / image.height.coerceAtLeast(1).toFloat()).coerceIn(0.5f, 1.9f)
    val category = ImageStyle.fromId(image.styleId)
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        KolponaMark(size = 28.dp)
        Spacer(Modifier.size(8.dp))
        Column(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(GlassFill)
                .border(1.dp, GlassStroke, RoundedCornerShape(22.dp))
                .padding(10.dp)
        ) {
            Text(
                text = stringResource(if (image.isVideo) R.string.chat_video_ready else R.string.chat_image_ready),
                style = MaterialTheme.typography.bodyMedium,
                color = MutedGray,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onOpen)
            ) {
                if (image.isVideo) {
                    LoopingVideo(path = image.localPath, modifier = Modifier.fillMaxSize())
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(52.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                            .border(1.dp, ElectricBlue.copy(alpha = 0.7f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = stringResource(R.string.play_video),
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                } else {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(File(image.localPath))
                            .crossfade(true)
                            .build(),
                        contentDescription = stringResource(R.string.cd_generated_image),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = image.prompt,
                style = MaterialTheme.typography.bodyMedium,
                color = MutedGray,
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
                color = MutedGray
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                CardAction(Icons.Outlined.FileDownload, stringResource(R.string.download), onDownload, enabled)
                CardAction(Icons.Outlined.Share, stringResource(R.string.share), onShare, enabled)
                CardAction(Icons.Outlined.Refresh, stringResource(R.string.regenerate), onRegenerate, enabled)
                CardAction(Icons.Outlined.ContentCopy, stringResource(R.string.copy_prompt), onCopy, enabled)
            }
        }
    }
}

@Composable
private fun CardAction(icon: ImageVector, label: String, onClick: () -> Unit, enabled: Boolean) {
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.height(40.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = ElectricBlue)
        Spacer(Modifier.size(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, color = SoftWhite)
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
private fun GeneratingCard(video: Boolean, aspect: AspectRatio, onCancel: () -> Unit) {
    val preview = aspect.shortLabel.split(":").let {
        val w = it.getOrNull(0)?.toFloatOrNull() ?: 1f
        val h = it.getOrNull(1)?.toFloatOrNull() ?: 1f
        (w / h).coerceIn(0.55f, 1.85f)
    }
    val motion = rememberInfiniteTransition(label = "generating")
    val sweep by motion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label = "sweep"
    )
    val pulse by motion.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "pulse"
    )
    val spin by motion.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing)),
        label = "spin"
    )
    val stages = if (video) {
        listOf(R.string.creating_stage_video_1, R.string.creating_stage_video_2, R.string.creating_stage_video_3)
    } else {
        listOf(R.string.creating_stage_image_1, R.string.creating_stage_image_2, R.string.creating_stage_image_3)
    }
    var stage by remember { mutableIntStateOf(0) }
    LaunchedEffect(video) {
        while (true) {
            delay(2200)
            stage = (stage + 1) % stages.size
        }
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        KolponaMark(size = 28.dp)
        Spacer(Modifier.size(8.dp))
        Column(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(GlassFill)
                .border(1.dp, GlassStroke, RoundedCornerShape(22.dp))
                .padding(10.dp)
        ) {
            Text(
                text = stringResource(if (video) R.string.creating_video else R.string.creating_image),
                style = MaterialTheme.typography.bodyMedium,
                color = MutedGray,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(preview)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF070B16)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    rotate(spin) {
                        drawCircle(
                            brush = Brush.sweepGradient(listOf(ElectricBlue, NeonViolet, PinkAccent, ElectricBlue)),
                            radius = minOf(w, h) * 0.28f,
                            center = center,
                            style = Stroke(width = 5.dp.toPx())
                        )
                    }
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.5f to ElectricBlue.copy(alpha = 0.35f),
                            1f to Color.Transparent
                        ),
                        topLeft = Offset(0f, h * sweep - 28.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(w, 56.dp.toPx())
                    )
                }
                KolponaMark(size = 64.dp, modifier = Modifier.scale(pulse))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(stages[stage]),
                style = MaterialTheme.typography.bodyLarge,
                color = SoftWhite,
                fontWeight = FontWeight.Medium
            )
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.cancel_generation), color = ElectricBlue)
            }
        }
    }
}

@Composable
private fun ErrorBubble(error: GenerationError, onRetry: () -> Unit) {
    val args = error.formatArgs()
    val message = if (args != null) stringResource(error.messageRes(), *args) else stringResource(error.messageRes())
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        KolponaMark(size = 28.dp)
        Spacer(Modifier.size(8.dp))
        Column(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(GlassFill)
                .border(1.dp, PinkAccent.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text(
                text = message,
                color = SoftWhite,
                style = MaterialTheme.typography.bodyLarge
            )
            if (error != GenerationError.EMPTY_PROMPT) {
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.try_again), color = ElectricBlue)
                }
            }
        }
    }
}

@Composable
private fun RatioPicker(selected: AspectRatio, onPick: (AspectRatio) -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.ratio), color = SoftWhite, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        AspectRatio.entries.forEach { ratio ->
            val active = ratio == selected
            val preview = (ratio.shortLabel.split(":").let {
                val w = it.getOrNull(0)?.toFloatOrNull() ?: 1f
                val h = it.getOrNull(1)?.toFloatOrNull() ?: 1f
                (w / h).coerceIn(0.4f, 2.2f)
            })
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (active) ElectricBlue.copy(alpha = 0.16f) else Color.Transparent)
                    .clickable { onPick(ratio) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight(0.82f)
                            .aspectRatio(preview)
                            .border(
                                1.5.dp,
                                if (active) ElectricBlue else MutedGray,
                                RoundedCornerShape(5.dp)
                            )
                    )
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    text = ratio.shortLabel,
                    color = if (active) ElectricBlue else SoftWhite,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun QualityPicker(selected: ImageQuality, onPick: (ImageQuality) -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.quality_label), color = SoftWhite, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        ImageQuality.entries.forEach { quality ->
            val active = quality == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (active) NeonViolet.copy(alpha = 0.16f) else Color.Transparent)
                    .clickable { onPick(quality) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(quality.labelRes),
                    color = if (active) NeonViolet else SoftWhite,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                if (active) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(NeonViolet, CircleShape)
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
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
        Text(title, color = SoftWhite, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        options.forEach { (label, value) ->
            val active = value == selected
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (active) PinkAccent.copy(alpha = 0.16f) else Color.Transparent)
                    .clickable { onPick(value) }
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (active) PinkAccent else SoftWhite
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ChatSessionsSheet(
    sessions: List<ChatSessionEntity>,
    currentId: String,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit
) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.chats), color = SoftWhite, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onNew) {
            Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp), tint = ElectricBlue)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.new_chat), color = ElectricBlue)
        }
        if (sessions.isEmpty()) {
            Text(
                text = stringResource(R.string.chat_untitled),
                style = MaterialTheme.typography.bodyMedium,
                color = MutedGray,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        } else {
            sessions.forEach { session ->
                val active = session.id == currentId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (active) ElectricBlue.copy(alpha = 0.16f) else Color.Transparent)
                        .clickable { onOpen(session.id) }
                        .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = session.title.ifBlank { stringResource(R.string.chat_untitled) },
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (active) ElectricBlue else SoftWhite
                    )
                    IconButton(onClick = { onRename(session.id, session.title) }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.rename_chat), tint = MutedGray)
                    }
                    IconButton(onClick = { onDelete(session.id) }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.delete_chat), tint = MutedGray)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
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
        Text(stringResource(R.string.need_more_credits), color = SoftWhite, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.credits_remaining, credits),
            style = MaterialTheme.typography.bodyLarge,
            color = MutedGray
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.watch_video_card_body, CreditConfig.REWARDED_VIDEO_REWARD),
            style = MaterialTheme.typography.bodyMedium,
            color = MutedGray
        )
        Spacer(Modifier.height(20.dp))
        OutlinedButton(
            onClick = onWatch,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = ElectricBlue)
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
            color = MutedGray
        )
        Spacer(Modifier.height(28.dp))
    }
}
