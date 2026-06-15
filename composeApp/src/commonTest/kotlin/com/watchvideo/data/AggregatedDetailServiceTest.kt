package com.watchvideo.data

import com.russhwolf.settings.Settings
import com.watchvideo.data.local.SourceScoreStore
import com.watchvideo.data.model.MetaBadge
import com.watchvideo.data.model.PlaybackEpisode
import com.watchvideo.data.model.PlaybackRoute
import com.watchvideo.data.model.SourceDetail
import com.watchvideo.data.model.SourceScoreRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AggregatedDetailServiceTest {

    private fun makeScoreStore() = SourceScoreStore(
        settings = FakeSettings(),
        nowEpochMsProvider = { 0L },
    )

    private fun sampleSourceDetail(sourceKey: String) = SourceDetail(
        sourceKey = sourceKey,
        sourceName = "源-$sourceKey",
        contentKey = "hero-2026",
        sourceContentId = "id-$sourceKey",
        title = "The Hero ($sourceKey)",
        coverUrl = "http://example.com/cover-$sourceKey.jpg",
        summary = "A great movie from $sourceKey",
        metadata = listOf(MetaBadge(label = "年份", value = "2026")),
        routes = listOf(
            PlaybackRoute(
                routeKey = "r1",
                routeName = "线路1",
                episodes = listOf(
                    PlaybackEpisode(episodeKey = "ep1", episodeLabel = "第01集", playPageUrl = "http://example.com/$sourceKey/ep1"),
                ),
            )
        ),
    )

    @Test
    fun merged_detail_keeps_all_source_variants() {
        val aggregated = AggregatedDetailService(
            scoreStore = makeScoreStore(),
        ).merge(
            listOf(sampleSourceDetail("a"), sampleSourceDetail("b"))
        )
        assertEquals(2, aggregated.sourceDetails.size)
    }

    @Test
    fun merged_detail_title_comes_from_highest_ranked_source() {
        val store = makeScoreStore()
        // "b" 更高分（高清晰度 + 快速），应排第一；"a" 无高度/速度信息
        store.save(SourceScoreRecord(sourceKey = "b", stableObservedHeight = 1080, avgFirstFrameMs = 700))
        store.save(SourceScoreRecord(sourceKey = "a", stableObservedHeight = null, avgFirstFrameMs = null))


        val aggregated = AggregatedDetailService(
            scoreStore = store,
        ).merge(
            listOf(sampleSourceDetail("a"), sampleSourceDetail("b"))
        )

        assertEquals("The Hero (b)", aggregated.title)
        assertEquals("hero-2026", aggregated.contentKey)
    }

    @Test
    fun merged_detail_preferred_cover_is_first_non_null_from_ranked_order() {
        val store = makeScoreStore()
        // "a" 排第一（高清晰度 + 快速），但 coverUrl = null，fallback 到 "b"
        store.save(SourceScoreRecord(sourceKey = "a", stableObservedHeight = 1080, avgFirstFrameMs = 700))
        store.save(SourceScoreRecord(sourceKey = "b", stableObservedHeight = null, avgFirstFrameMs = null))

        val detailNoCover = sampleSourceDetail("a").copy(coverUrl = null)
        val detailWithCover = sampleSourceDetail("b")

        val aggregated = AggregatedDetailService(
            scoreStore = store,
        ).merge(listOf(detailNoCover, detailWithCover))

        assertNotNull(aggregated.preferredCoverUrl)
        assertEquals("http://example.com/cover-b.jpg", aggregated.preferredCoverUrl)
    }

    private class FakeSettings : Settings {
        private val data = mutableMapOf<String, Any>()
        override val keys: Set<String> get() = data.keys
        override val size: Int get() = data.size
        override fun clear() = data.clear()
        override fun remove(key: String) { data.remove(key) }
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
