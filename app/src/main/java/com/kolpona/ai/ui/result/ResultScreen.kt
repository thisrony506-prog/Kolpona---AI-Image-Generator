package com.kolpona.ai.ui.result

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kolpona.ai.R
import com.kolpona.ai.domain.manager.CreditConfig
import com.kolpona.ai.ui.components.GeneratingDialog
import com.kolpona.ai.ui.theme.PinkAccent
import com.kolpona.ai.utils.formatArgs
import com.kolpona.ai.utils.messageRes
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    imageId: String,
    viewModel: ResultViewModel,
    onBack: () -> Unit,
    onCreateAnother: () -> Unit,
    onRegenerated: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val image by viewModel.image.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(imageId) { viewModel.setImageId(imageId) }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ResultEvent.Regenerated -> onRegenerated(event.imageId)
                ResultEvent.Saved -> Toast.makeText(context, context.getString(R.string.image_saved), Toast.LENGTH_SHORT).show()
                ResultEvent.SaveFailed -> Toast.makeText(context, context.getString(R.string.error_save), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted || Build.VERSION.SDK_INT >= 29) viewModel.download()
    }

    if (state.isGenerating) GeneratingDialog()

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.app_name)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.back)
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            val current = image
            if (current != null) {
                val ratio = current.width.toFloat() / current.height.coerceAtLeast(1).toFloat()
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(File(current.localPath))
                        .crossfade(true)
                        .build(),
                    contentDescription = stringResource(R.string.cd_generated_image),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(ratio.coerceIn(0.6f, 1.8f))
                        .clip(RoundedCornerShape(24.dp))
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.credits_used, current.creditsUsed),
                    style = MaterialTheme.typography.titleMedium,
                    color = PinkAccent,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = current.prompt,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            state.error?.let { error ->
                Spacer(Modifier.height(12.dp))
                val args = error.formatArgs()
                Text(
                    text = if (args != null) stringResource(error.messageRes(), *args)
                    else stringResource(error.messageRes()),
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        if (Build.VERSION.SDK_INT < 29) {
                            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        } else {
                            viewModel.download()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) { Text(stringResource(R.string.download)) }
                OutlinedButton(
                    onClick = { viewModel.share(context.getString(R.string.share_image)) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) { Text(stringResource(R.string.share)) }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = viewModel::generateAgain,
                enabled = !state.isGenerating && state.credits >= CreditConfig.GENERATION_COST,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PinkAccent)
            ) { Text(stringResource(R.string.generate_again)) }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onCreateAnother,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text(stringResource(R.string.create_another)) }
            Spacer(Modifier.height(24.dp))
        }
    }
}
