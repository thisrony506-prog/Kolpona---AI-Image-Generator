package com.kolpona.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kolpona.app.R
import com.kolpona.app.ui.theme.ElectricBlue
import com.kolpona.app.ui.theme.MidnightDeep
import com.kolpona.app.ui.theme.NeonMagenta
import com.kolpona.app.ui.theme.NeonViolet
import com.kolpona.app.ui.theme.PinkAccent

@Composable
fun StudioBackdrop(modifier: Modifier = Modifier) {
    val motion = rememberInfiniteTransition(label = "studio-glow")
    val shift by motion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shift"
    )
    Canvas(modifier.background(MidnightDeep).fillMaxSize()) {
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
fun KolponaMark(size: Dp = 36.dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ic_kolpona_mark),
        contentDescription = stringResource(R.string.cd_logo),
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
    )
}

@Composable
fun GeneratingDialog() {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    color = PinkAccent,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.creating_image),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.padding(bottom = 8.dp)
    )
}

@Composable
fun HorizontalSpacer(width: Dp) {
    Spacer(Modifier.width(width))
}
