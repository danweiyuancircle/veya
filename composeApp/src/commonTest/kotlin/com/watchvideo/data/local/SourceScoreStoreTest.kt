package com.watchvideo.data.local

import com.russhwolf.settings.Settings
import com.watchvideo.data.model.FailureType
import com.watchvideo.data.model.SourceScoreRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SourceScoreStoreTest {
    @Test
    fun higher_stable_resolution_and_success_rate_ranks_first() {
        val now = 1_000L
        val store = SourceScoreStore(
            settings = FakeSettings(),
            nowEpochMsProvider = { now },
        )

        val higher = SourceScoreRecord(
            sourceKey = "higher",
            baseScore = 55,
            searchSuccessCount = 10,
            searchFailureCount = 1,
            detailSuccessCount = 10,
            detailFailureCount = 1,
            playCandidateSuccessCount = 10,
            playCandidateFailureCount = 1,
            playbackStartSuccessCount = 10,
            playbackStartFailureCount = 1,
            consecutiveFailureCount = 0,
            avgFirstFrameMs = 700,
            stableObservedHeight = 1080,
            maxObservedHeight = 1080,
        )
        val lower = SourceScoreRecord(
            sourceKey = "lower",
            baseScore = 80,
            searchSuccessCount = 8,
            searchFailureCount = 3,
            detailSuccessCount = 8,
            detailFailureCount = 3,
            playCandidateSuccessCount = 8,
            playCandidateFailureCount = 3,
            playbackStartSuccessCount = 8,
            playbackStartFailureCount = 3,
            consecutiveFailureCount = 1,
            avgFirstFrameMs = 1_800,
            stableObservedHeight = 720,
            maxObservedHeight = 1080,
        )
        val cooldown = SourceScoreRecord(
            sourceKey = "cooldown",
            baseScore = 90,
            searchSuccessCount = 12,
            searchFailureCount = 0,
            detailSuccessCount = 12,
            detailFailureCount = 0,
            playCandidateSuccessCount = 12,
            playCandidateFailureCount = 0,
            playbackStartSuccessCount = 12,
            playbackStartFailureCount = 0,
            consecutiveFailureCount = 0,
            avgFirstFrameMs = 500,
            stableObservedHeight = 1440,
            maxObservedHeight = 1440,
            lastFailureType = FailureType.PLAYBACK_INTERRUPTED,
            lastFailureAtEpochMs = 900,
            cooldownUntilEpochMs = 2_000,
        )

        store.save(lower)
        store.save(higher)
        store.save(cooldown)

        assertEquals(listOf("higher", "lower", "cooldown"), store.rank(listOf("lower", "higher", "cooldown")))
        assertTrue(store.rankValue(higher) > store.rankValue(lower))
        assertTrue(store.rankValue(lower) > store.rankValue(cooldown))
        assertEquals(higher, store.get("higher"))
    }

    @Test
    fun clear_removes_all_records() {
        val store = SourceScoreStore(
            settings = FakeSettings(),
            nowEpochMsProvider = { 1_000L },
        )
        store.save(SourceScoreRecord(sourceKey = "a", baseScore = 60))
        store.save(SourceScoreRecord(sourceKey = "b", baseScore = 70))

        store.clear()

        assertEquals(null, store.get("a"))
        assertEquals(null, store.get("b"))
    }

    private class FakeSettings : Settings {
        private val data = mutableMapOf<String, Any>()

        override val keys: Set<String>
            get() = data.keys
        override val size: Int
            get() = data.size

        override fun clear() = data.clear()
        override fun remove(key: String) {
            data.remove(key)
        }

        override fun hasKey(key: String): Boolean = data.containsKey(key)
        override fun putInt(key: String, value: Int) { data[key] = value }
        override fun getInt(key: String, defaultValue: Int): Int = data[key] as? Int ?: defaultValue
        override fun getIntOrNull(key: String): Int? = data[key] as? Int
        override fun putLong(key: String, value: Long) { data[key] = value }
        override fun getLong(key: String, defaultValue: Long): Long = data[key] as? Long ?: defaultValue
        override fun getLongOrNull(key: String): Long? = data[key] as? Long
        override fun putString(key: String, value: String) { data[key] = value }
        override fun getString(key: String, defaultValue: String): String = data[key] as? String ?: defaultValue
        override fun getStringOrNull(key: String): String? = data[key] as? String
        override fun putFloat(key: String, value: Float) { data[key] = value }
        override fun getFloat(key: String, defaultValue: Float): Float = data[key] as? Float ?: defaultValue
        override fun getFloatOrNull(key: String): Float? = data[key] as? Float
        override fun putDouble(key: String, value: Double) { data[key] = value }
        override fun getDouble(key: String, defaultValue: Double): Double = data[key] as? Double ?: defaultValue
        override fun getDoubleOrNull(key: String): Double? = data[key] as? Double
        override fun putBoolean(key: String, value: Boolean) { data[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean): Boolean = data[key] as? Boolean ?: defaultValue
        override fun getBooleanOrNull(key: String): Boolean? = data[key] as? Boolean
    }
}
