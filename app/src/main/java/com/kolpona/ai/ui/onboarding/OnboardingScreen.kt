package com.kolpona.ai.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kolpona.ai.R
import com.kolpona.ai.ui.components.KolponaMark
import com.kolpona.ai.ui.theme.ElectricBlue
import com.kolpona.ai.ui.theme.MidnightDeep
import com.kolpona.ai.ui.theme.MutedGray
import com.kolpona.ai.ui.theme.NeonMagenta
import com.kolpona.ai.ui.theme.NeonViolet
import com.kolpona.ai.ui.theme.PinkAccent
import com.kolpona.ai.ui.theme.SoftWhite
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val title: Int,
    val quotes: List<Int>
)

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pages = listOf(
        OnboardingPage(
            R.string.onboarding_1_title,
            listOf(
                R.string.onboarding_1_quote_1,
                R.string.onboarding_1_quote_2,
                R.string.onboarding_1_quote_3
            )
        ),
        OnboardingPage(
            R.string.onboarding_2_title,
            listOf(
                R.string.onboarding_2_quote_1,
                R.string.onboarding_2_quote_2,
                R.string.onboarding_2_quote_3
            )
        ),
        OnboardingPage(
            R.string.onboarding_3_title,
            listOf(
                R.string.onboarding_3_quote_1,
                R.string.onboarding_3_quote_2
            )
        )
    )
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val last = pagerState.currentPage == pages.lastIndex
    val titleBrush = Brush.linearGradient(listOf(ElectricBlue, NeonViolet, PinkAccent))

    Box(modifier.fillMaxSize()) {
        OnboardingGlow(Modifier.fillMaxSize())
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (!last) {
                    TextButton(onClick = onFinished, modifier = Modifier.height(48.dp)) {
                        Text(stringResource(R.string.skip), color = MutedGray)
                    }
                } else {
                    Spacer(Modifier.height(48.dp))
                }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                val item = pages[page]
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
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
                    Spacer(Modifier.height(32.dp))
                    Text(
                        text = stringResource(item.title),
                        style = TextStyle(
                            brush = titleBrush,
                            fontWeight = FontWeight.Bold,
                            fontSize = 34.sp,
                            letterSpacing = (-0.4).sp
                        ),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(18.dp))
                    LiveQuote(
                        quotes = item.quotes.map { stringResource(it) },
                        active = pagerState.currentPage == page,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                pages.indices.forEach { index ->
                    val selected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .size(if (selected) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(if (selected) ElectricBlue else Color(0xFF3A455C))
                    )
                }
            }
            Button(
                onClick = {
                    if (last) onFinished()
                    else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
            ) {
                Text(
                    text = stringResource(if (last) R.string.start_creating else R.string.next),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun LiveQuote(
    quotes: List<String>,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    var index by remember { mutableIntStateOf(0) }
    var shown by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(true) }
    var typing by remember { mutableStateOf(true) }
    val cursorPulse = rememberInfiniteTransition(label = "cursor")
    val showCursor by cursorPulse.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(480), RepeatMode.Reverse),
        label = "cursorOn"
    )

    LaunchedEffect(active, quotes) {
        if (!active || quotes.isEmpty()) return@LaunchedEffect
        index = 0
        while (true) {
            val text = quotes[index % quotes.size]
            visible = true
            typing = true
            shown = ""
            text.forEach { ch ->
                shown += ch
                delay(24)
            }
            typing = false
            delay(2800)
            visible = false
            delay(320)
            index = (index + 1) % quotes.size
        }
    }

    Box(modifier, contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(220))
        ) {
            val cursor = if (typing && showCursor > 0.45f) "▍" else if (typing) " " else ""
            Text(
                text = (shown + cursor).ifBlank { " " },
                color = SoftWhite,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun OnboardingGlow(modifier: Modifier = Modifier) {
    val motion = rememberInfiniteTransition(label = "onboard-glow")
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
            color = ElectricBlue.copy(alpha = 0.18f),
            radius = w * 0.46f,
            center = Offset(w * (0.12f + shift * 0.04f), h * 0.08f)
        )
        drawCircle(
            color = NeonViolet.copy(alpha = 0.14f),
            radius = w * 0.40f,
            center = Offset(w * (0.94f - shift * 0.03f), h * 0.28f)
        )
        drawCircle(
            color = NeonMagenta.copy(alpha = 0.10f),
            radius = w * 0.36f,
            center = Offset(w * 0.5f, h * (0.92f - shift * 0.03f))
        )
    }
}
