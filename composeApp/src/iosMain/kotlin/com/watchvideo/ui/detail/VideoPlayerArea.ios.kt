package com.watchvideo.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
actual fun VideoPlayerArea(
    m3u8Url: String?,
    title: String,
    isFullscreen: Boolean,
    onFullscreenToggle: () -> Unit,
    onBack: () -> Unit,
    onPrevEpisode: (() -> Unit)?,
    onNextEpisode: (() -> Unit)?,
    onResolutionObserved: (Int) -> Unit,
    modifier: Modifier
) {
    Box(
        modifier = modifier.then(
            if (!isFullscreen) Modifier.aspectRatio(16f / 9f) else Modifier
        ).background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Text("iOS 播放器待实现", color = Color.White)
    }
}
