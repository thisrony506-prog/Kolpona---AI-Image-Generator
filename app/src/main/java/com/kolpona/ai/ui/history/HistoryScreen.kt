package com.kolpona.ai.ui.history

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kolpona.ai.KolponaApp
import com.kolpona.ai.R
import com.kolpona.ai.ads.StartIoBanner
import com.kolpona.ai.domain.model.GeneratedImage
import com.kolpona.ai.ui.components.KolponaMark
import com.kolpona.ai.ui.components.StudioBackdrop
import com.kolpona.ai.ui.theme.ElectricBlue
import com.kolpona.ai.ui.theme.GlassFill
import com.kolpona.ai.ui.theme.GlassStroke
import com.kolpona.ai.ui.theme.MutedGray
import com.kolpona.ai.ui.theme.NeonViolet
import com.kolpona.ai.ui.theme.PinkAccent
import com.kolpona.ai.ui.theme.SoftWhite
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val historyDateFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a")

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onOpenImage: (String) -> Unit,
    onCreateFirst: () -> Unit,
    modifier: Modifier = Modifier
) {
    val images by viewModel.images.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                HistoryEvent.Saved -> Toast.makeText(context, context.getString(R.string.image_saved), Toast.LENGTH_SHORT).show()
                HistoryEvent.SaveFailed -> Toast.makeText(context, context.getString(R.string.error_save), Toast.LENGTH_SHORT).show()
                is HistoryEvent.Regenerated -> onOpenImage(event.imageId)
                is HistoryEvent.GenerateFailed -> Unit
            }
        }
    }

    Box(modifier.fillMaxSize()) {
        StudioBackdrop(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                KolponaMark(size = 36.dp)
                Spacer(Modifier.size(10.dp))
                Text(
                    text = stringResource(R.string.history_title),
                    color = SoftWhite,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            if (images.isEmpty()) {
                EmptyHistory(
                    onCreateFirst = onCreateFirst,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(images, key = { it.id }) { image ->
                        HistoryCard(image = image, onClick = { onOpenImage(image.id) })
                    }
                }
            }
            StartIoBanner(adManager = (context.applicationContext as KolponaApp).container.adManager)
        }
    }
}

@Composable
private fun HistoryCard(image: GeneratedImage, onClick: () -> Unit) {
    val formatted = Instant.ofEpochMilli(image.createdAtEpochMs)
        .atZone(ZoneId.systemDefault())
        .format(historyDateFormat)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(GlassFill)
            .border(1.dp, GlassStroke, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (image.isVideo) {
                Icon(
                    Icons.Outlined.Videocam,
                    contentDescription = stringResource(R.string.cd_history_item),
                    tint = SoftWhite,
                    modifier = Modifier.size(36.dp)
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(44.dp)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        .border(1.dp, ElectricBlue.copy(alpha = 0.7f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White)
                }
            } else {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(File(image.localPath))
                        .crossfade(true)
                        .size(512)
                        .build(),
                    contentDescription = stringResource(R.string.cd_history_item),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Column(Modifier.padding(12.dp)) {
            Text(
                text = formatted,
                style = MaterialTheme.typography.bodyMedium,
                color = MutedGray
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = image.prompt,
                style = MaterialTheme.typography.bodyMedium,
                color = SoftWhite,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EmptyHistory(onCreateFirst: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(horizontal = 28.dp), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(GlassFill)
                .border(1.dp, GlassStroke, RoundedCornerShape(22.dp))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            KolponaMark(size = 64.dp)
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.history_empty_title),
                color = SoftWhite,
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.history_empty_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MutedGray
            )
            Spacer(Modifier.height(24.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(ElectricBlue, NeonViolet, PinkAccent)))
                    .clickable(onClick = onCreateFirst),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.create_first_image),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
