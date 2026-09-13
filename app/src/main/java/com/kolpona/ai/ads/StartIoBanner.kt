package com.kolpona.ai.ads

import android.app.Activity
import android.view.View
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun StartIoBanner(
    adManager: StartIoAdManager,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
        factory = { ctx ->
            val activity = ctx as? Activity
            adManager.createBanner(activity ?: ctx) ?: View(ctx)
        }
    )
}
