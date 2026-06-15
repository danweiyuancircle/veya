package com.watchvideo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 源分级：优先 / 可用 / 其他，颜色与文案在此集中定义。 */
enum class SourceTier(val label: String, val shortLabel: String) {
    Priority("优先源", "优先"),
    Available("可用源", "可用"),
    Other("其他源", "其他"),
}

@Composable
fun SourceTier.color(): Color = when (this) {
    SourceTier.Priority -> MaterialTheme.colorScheme.primary
    SourceTier.Available -> SourceTierAvailable
    SourceTier.Other -> SourceTierOther
}

/** 按源评分值分级，阈值在此唯一定义。 */
fun tierOf(rankValue: Double): SourceTier = when {
    rankValue >= 60.0 -> SourceTier.Priority
    rankValue > 0.0 -> SourceTier.Available
    else -> SourceTier.Other
}
