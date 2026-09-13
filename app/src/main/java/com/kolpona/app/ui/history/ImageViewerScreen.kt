package com.kolpona.app.ui.history

import android.Manifest
import android.os.Build
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kolpona.app.R
import com.kolpona.app.ui.components.GeneratingDialog
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageViewerScreen(
    imageId: String,
    viewModel: HistoryViewModel,
    onBack: () -> Unit,
    onGenerateAgainId: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val images by viewModel.images.collectAsStateWithLifecycle()
    val image = images.find { it.id == imageId }
    val context = LocalContext.current
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    val generating by viewModel.generating.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                HistoryEvent.Saved -> Toast.makeText(context, context.getString(R.string.image_saved), Toast.LENGTH_SHORT).show()
                HistoryEvent.SaveFailed -> Toast.makeText(context, context.getString(R.string.error_save), Toast.LENGTH_SHORT).show()
                is HistoryEvent.Regenerated -> onGenerateAgainId(event.imageId)
                is HistoryEvent.GenerateFailed -> Toast.makeText(context, context.getString(R.string.generation_failed), Toast.LENGTH_LONG).show()
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (image != null && (granted || Build.VERSION.SDK_INT >= 29)) {
            viewModel.download(image)
        }
    }

    if (generating) GeneratingDialog()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = { Text(stringResource(R.string.history_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            if (image != null) {
                if (image.isVideo) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize().background(Color.Black),
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                setVideoPath(image.localPath)
                                setOnPreparedListener { player ->
                                    player.isLooping = true
                                    start()
                                }
                            }
                        }
                    )
                } else {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(File(image.localPath)).build(),
                        contentDescription = stringResource(R.string.cd_generated_image),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        if (image != null) {
            Text(
                text = image.prompt,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TextButton(onClick = {
                    if (Build.VERSION.SDK_INT < 29) {
                        permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    } else {
                        viewModel.download(image)
                    }
                }, modifier = Modifier.height(48.dp)) { Text(stringResource(R.string.download)) }
                TextButton(
                    onClick = { viewModel.share(image, context.getString(R.string.share_image)) },
                    modifier = Modifier.height(48.dp)
                ) { Text(stringResource(R.string.share)) }
                TextButton(
                    onClick = { viewModel.generateAgain(image) },
                    enabled = !generating,
                    modifier = Modifier.height(48.dp)
                ) {
                    Text(stringResource(R.string.generate_again))
                }
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.height(48.dp)) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (confirmDelete && image != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_image_title)) },
            text = { Text(stringResource(R.string.delete_image_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(image)
                    confirmDelete = false
                    onBack()
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }
}
