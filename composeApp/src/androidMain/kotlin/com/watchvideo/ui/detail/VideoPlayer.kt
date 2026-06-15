package com.watchvideo.ui.detail

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun VideoPlayer(
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
    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()

    // ExoPlayer
    val exoPlayer = remember { ExoPlayer.Builder(context).build() }
    val latestOnResolutionObserved by rememberUpdatedState(onResolutionObserved)
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                if (videoSize.height > 0) latestOnResolutionObserved(videoSize.height)
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // 当 m3u8Url 变化时换源
    LaunchedEffect(m3u8Url) {
        if (m3u8Url != null) {
            exoPlayer.setMediaItem(
                MediaItem.Builder().setUri(m3u8Url).setMimeType(MimeTypes.APPLICATION_M3U8).build()
            )
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }
    }

    // 播放状态
    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    LaunchedEffect(exoPlayer) {
        while (true) {
            isPlaying = exoPlayer.isPlaying
            currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
            duration = exoPlayer.duration.coerceAtLeast(0L)
            delay(500)
        }
    }

    // 控制栏显隐（3秒自动隐藏）
    var showControls by remember { mutableStateOf(true) }
    var autoHideJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun scheduleHide() {
        autoHideJob?.cancel()
        autoHideJob = scope.launch { delay(3000); showControls = false }
    }
    LaunchedEffect(showControls) { if (showControls) scheduleHide() }

    // 手势状态
    var seekDelta by remember { mutableLongStateOf(0L) }   // 快进/快退偏移量(ms)
    var isSeeking by remember { mutableStateOf(false) }
    var isLongPressing by remember { mutableStateOf(false) } // 长按2×速

    // 全屏时的系统栏控制
    LaunchedEffect(isFullscreen) {
        val insetsController = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        if (isFullscreen) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    Box(
        modifier = modifier.then(
            if (!isFullscreen) Modifier.aspectRatio(16f / 9f) else Modifier
        ).background(Color.Black)
    ) {
        // 视频画面
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 手势层：单一状态机，三种手势互斥
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)

                        // 阶段一：2秒内等待——判断是拖动还是静止按住
                        val decision = withTimeoutOrNull(2000L) {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) return@withTimeoutOrNull "TAP"
                                val dx = change.position.x - down.position.x
                                val dy = change.position.y - down.position.y
                                if (kotlin.math.abs(dx) > viewConfiguration.touchSlop &&
                                    kotlin.math.abs(dx) > kotlin.math.abs(dy)
                                ) {
                                    return@withTimeoutOrNull "DRAG"
                                }
                                change.consume()
                            }
                            "TAP"
                        }

                        when (decision) {
                            null -> {
                                // 按住 2 秒未松手：进入倍速模式
                                isLongPressing = true
                                exoPlayer.setPlaybackSpeed(2f)
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                    if (change == null || !change.pressed) break
                                    change.consume()
                                }
                                exoPlayer.setPlaybackSpeed(1f)
                                isLongPressing = false
                            }
                            "TAP" -> {
                                // 2秒内松手：单击，切换控制栏
                                showControls = !showControls
                            }
                            "DRAG" -> {
                                // 识别为水平拖动：收集位移，松手再 seek
                                isSeeking = true
                                showControls = false
                                seekDelta = 0L
                                var lastX = down.position.x
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                    if (change == null || !change.pressed) break
                                    seekDelta += ((change.position.x - lastX) * 300).toLong()
                                    lastX = change.position.x
                                    change.consume()
                                }
                                val newPos = (currentPosition + seekDelta).coerceIn(0L, duration)
                                exoPlayer.seekTo(newPos)
                                seekDelta = 0L
                                isSeeking = false
                                showControls = true
                            }
                        }
                    }
                }
        )

        // 长按提示
        if (isLongPressing) {
            Box(
                modifier = Modifier.align(Alignment.Center)
                    .background(Color(0xAA000000), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("2× 快速播放", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        // 快进/快退提示
        if (isSeeking) {
            val sec = seekDelta / 1000
            val sign = if (sec >= 0) "+" else ""
            Box(
                modifier = Modifier.align(Alignment.Center)
                    .background(Color(0xAA000000), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("$sign${sec}s", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }

        // 控制层（淡入淡出）
        AnimatedVisibility(
            visible = showControls && !isSeeking,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 顶部渐变 + 栏
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xCC000000), Color.Transparent)
                            )
                        )
                        .align(Alignment.TopCenter)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            if (isFullscreen) onFullscreenToggle() else onBack()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
                        }
                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 14.sp,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 中间播放控制
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onPrevEpisode != null) {
                        IconButton(onClick = onPrevEpisode) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "上一集",
                                tint = Color.White, modifier = Modifier.size(36.dp))
                        }
                    }
                    // 播放/暂停
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color(0x55FFFFFF), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(onClick = {
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                        }) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "暂停" else "播放",
                                tint = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                    if (onNextEpisode != null) {
                        IconButton(onClick = onNextEpisode) {
                            Icon(Icons.Default.SkipNext, contentDescription = "下一集",
                                tint = Color.White, modifier = Modifier.size(36.dp))
                        }
                    }
                }

                // 底部渐变 + 栏
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0xCC000000))
                            )
                        )
                        .align(Alignment.BottomCenter)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        // 进度条
                        if (duration > 0) {
                            Slider(
                                value = currentPosition.toFloat() / duration.toFloat(),
                                onValueChange = { frac ->
                                    exoPlayer.seekTo((frac * duration).toLong())
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = Color(0x88FFFFFF)
                                ),
                                modifier = Modifier.fillMaxWidth().height(20.dp)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${formatTime(currentPosition)} / ${formatTime(duration)}",
                                color = Color.White,
                                fontSize = 12.sp
                            )
                            IconButton(onClick = onFullscreenToggle) {
                                Icon(
                                    if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    contentDescription = if (isFullscreen) "退出全屏" else "全屏",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

}

private fun formatTime(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
