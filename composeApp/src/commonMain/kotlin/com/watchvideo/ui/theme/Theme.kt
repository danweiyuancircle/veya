package com.watchvideo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 原型配色：近黑深蓝背景、亮蓝主色、深灰卡片
private val Background = Color(0xFF0A0E14)
private val Surface = Color(0xFF161B22)
private val SurfaceVariant = Color(0xFF1F2630)
private val Primary = Color(0xFF3B82F6)
private val OnPrimary = Color(0xFFFFFFFF)
private val OnBackground = Color(0xFFE6EAF0)
private val OnSurfaceVariant = Color(0xFF9AA4B2)
private val ErrorRed = Color(0xFFEF4444)
private val Outline = Color(0xFF2A323D)

private val VeyaDarkColors = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnBackground,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    error = ErrorRed,
    outline = Outline,
)

/** 源状态色：优先=蓝、可用=绿、其他=灰 */
val SourceTierAvailable = Color(0xFF22C55E)
val SourceTierOther = Color(0xFF6B7280)

@Composable
fun VeyaTheme(content: @Composable () -> Unit) {
    // 原型为深色，统一使用深色主题（暂不跟随系统浅色）
    MaterialTheme(
        colorScheme = VeyaDarkColors,
        content = content,
    )
}
