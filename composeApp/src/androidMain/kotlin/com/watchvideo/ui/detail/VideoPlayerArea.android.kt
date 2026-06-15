package com.watchvideo.ui.detail

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

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
    VideoPlayer(
        m3u8Url = m3u8Url,
        title = title,
        isFullscreen = isFullscreen,
        onFullscreenToggle = onFullscreenToggle,
        onBack = onBack,
        onPrevEpisode = onPrevEpisode,
        onNextEpisode = onNextEpisode,
        onResolutionObserved = onResolutionObserved,
        modifier = modifier
    )
}
