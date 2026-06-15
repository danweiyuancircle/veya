package com.watchvideo.data.local

import com.russhwolf.settings.Settings
import com.watchvideo.data.model.SourceScoreRecord
import kotlin.math.max
import kotlin.math.min

class SourceScoreStore(
    settings: Settings = Settings(),
    private val nowEpochMsProvider: () -> Long,
) {
    private val store = JsonStore(
        settings = settings,
        key = SOURCE_SCORE_KEY,
        serializer = SourceScoreRecord.serializer(),
    )

    fun get(sourceKey: String): SourceScoreRecord? = records()[sourceKey]

    fun save(record: SourceScoreRecord) {
        val updated = records().toMutableMap()
        updated[record.sourceKey] = record
        persist(updated.values.toList())
    }

    fun recordPlaybackResolution(sourceKey: String, firstFrameMs: Long, stableHeight: Int) {
        val current = get(sourceKey) ?: SourceScoreRecord(sourceKey = sourceKey)
        save(
            current.copy(
                avgFirstFrameMs = if (firstFrameMs > 0) firstFrameMs else current.avgFirstFrameMs,
                stableObservedHeight = stableHeight,
                maxObservedHeight = max(current.maxObservedHeight ?: 0, stableHeight),
                playbackStartSuccessCount = current.playbackStartSuccessCount + 1,
                consecutiveFailureCount = 0,
            )
        )
    }

    fun rank(sourceKeys: List<String>): List<String> = sourceKeys.sortedWith(
        compareByDescending<String> { key -> rankValue(records()[key]) }
            .thenBy { key -> sourceKeys.indexOf(key) }
    )

    fun clear() {
        store.write(emptyList())
    }

    fun rankValue(record: SourceScoreRecord?): Double {
        if (record == null) return 0.0

        val stabilityScore = buildStabilityScore(record)
        val clarityScore = normalizeHeight(record.stableObservedHeight)
        val speedScore = buildSpeedScore(record.avgFirstFrameMs)
        val baseScore = clamp(record.baseScore.toDouble(), 0.0, 100.0)
        val totalScore = stabilityScore * 0.50 +
            clarityScore * 0.25 +
            speedScore * 0.15 +
            baseScore * 0.10

        return if (isCoolingDown(record)) totalScore - 1_000.0 else totalScore
    }

    private fun buildStabilityScore(record: SourceScoreRecord): Double {
        val playbackStartRate = ratio(
            success = record.playbackStartSuccessCount,
            failure = record.playbackStartFailureCount,
        )
        val recentSuccessRate = ratio(
            success = record.searchSuccessCount +
                record.detailSuccessCount +
                record.playCandidateSuccessCount +
                record.playbackStartSuccessCount,
            failure = record.searchFailureCount +
                record.detailFailureCount +
                record.playCandidateFailureCount +
                record.playbackStartFailureCount,
        )
        val failurePenalty = min(record.consecutiveFailureCount * 10.0, 50.0)
        return clamp(playbackStartRate * 60.0 + recentSuccessRate * 40.0 - failurePenalty, 0.0, 100.0)
    }

    private fun buildSpeedScore(avgFirstFrameMs: Long?): Double {
        val firstFrameMs = avgFirstFrameMs ?: return 0.0
        val clamped = firstFrameMs.coerceIn(500L, 5_000L)
        return ((5_000L - clamped).toDouble() / (5_000L - 500L).toDouble()) * 100.0
    }

    private fun normalizeHeight(height: Int?): Double {
        val resolvedHeight = height ?: return 0.0
        return clamp(resolvedHeight.toDouble(), 0.0, 2160.0) / 2160.0 * 100.0
    }

    private fun ratio(success: Int, failure: Int): Double {
        val total = success + failure
        if (total <= 0) return 0.0
        return success.toDouble() / total.toDouble() * 100.0
    }

    private fun isCoolingDown(record: SourceScoreRecord): Boolean {
        val cooldownUntil = record.cooldownUntilEpochMs ?: return false
        return cooldownUntil > nowEpochMsProvider()
    }

    private fun clamp(value: Double, minValue: Double, maxValue: Double): Double {
        return max(minValue, min(value, maxValue))
    }

    private fun records(): Map<String, SourceScoreRecord> = store.read().associateBy { it.sourceKey }

    private fun persist(items: List<SourceScoreRecord>) {
        store.write(items.sortedBy { it.sourceKey })
    }

    private companion object {
        const val SOURCE_SCORE_KEY = "source_score_records"
    }
}
