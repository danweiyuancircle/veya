package com.watchvideo.data

import com.russhwolf.settings.Settings
import com.watchvideo.data.local.SourceScoreStore
import com.watchvideo.data.model.AggregatedDetail
import com.watchvideo.data.model.MetaBadge
import com.watchvideo.data.model.PlaybackEpisode
import com.watchvideo.data.model.PlaybackRoute
import com.watchvideo.data.model.SourceDetail
import com.watchvideo.data.model.SourceScoreRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaybackRecoveryPolicyTest {

    private fun makeStore() = SourceScoreStore(
        settings = FakeSettings(),
        nowEpochMsProvider = { 0L },
    )

    private fun makeAggregated(
        sourceDetails: List<SourceDetail>,
        scoreStore: SourceScoreStore,
    ): AggregatedDetail {
        return AggregatedDetailService(scoreStore = scoreStore)
            .merge(sourceDetails)
    }

    private fun detail(
        sourceKey: String,
        routes: List<PlaybackRoute>,
    ) = SourceDetail(
        sourceKey = sourceKey,
        sourceName = "源-$sourceKey",
        contentKey = "hero-2026",
        sourceContentId = "id-$sourceKey",
        title = "The Hero",
        coverUrl = null,
        summary = null,
        metadata = emptyList<MetaBadge>(),
        routes = routes,
    )

    private fun route(routeKey: String, episodeKeys: List<String>) = PlaybackRoute(
        routeKey = routeKey,
        routeName = "线路-$routeKey",
        episodes = episodeKeys.map { k ->
            PlaybackEpisode(episodeKey = k, episodeLabel = k, playPageUrl = "http://x/$k")
        },
    )

    // 降级 1：同线路下一集
    @Test
    fun next_episode_in_same_route_is_first_fallback() {
        val store = makeStore()
        val aggregated = makeAggregated(
            listOf(detail("src1", listOf(route("r1", listOf("ep1", "ep2", "ep3"))))),
            store,
        )

        val next = PlaybackRecoveryPolicy.nextCandidate(
            aggregated = aggregated,
            currentSourceKey = "src1",
            currentRouteKey = "r1",
            currentEpisodeKey = "ep1",
            tried = setOf(Triple("src1", "r1", "ep1")),
        )

        assertEquals(Triple("src1", "r1", "ep2"), next)
    }

    // 降级 2：同源其他线路（从第一集开始）
    @Test
    fun other_route_in_same_source_is_second_fallback() {
        val store = makeStore()
        val aggregated = makeAggregated(
            listOf(
                detail(
                    "src1",
                    listOf(
                        route("r1", listOf("ep1")),
                        route("r2", listOf("ep1", "ep2")),
                    )
                )
            ),
            store,
        )

        val next = PlaybackRecoveryPolicy.nextCandidate(
            aggregated = aggregated,
            currentSourceKey = "src1",
            currentRouteKey = "r1",
            currentEpisodeKey = "ep1",
            tried = setOf(Triple("src1", "r1", "ep1")),
        )

        assertEquals(Triple("src1", "r2", "ep1"), next)
    }

    // 降级 3：下一高分源
    @Test
    fun next_higher_ranked_source_is_third_fallback() {
        val store = makeStore()
        store.save(SourceScoreRecord(sourceKey = "src1", playbackStartSuccessCount = 10))
        store.save(SourceScoreRecord(sourceKey = "src2", playbackStartSuccessCount = 5))

        val aggregated = makeAggregated(
            listOf(
                detail("src1", listOf(route("r1", listOf("ep1")))),
                detail("src2", listOf(route("r1", listOf("ep1")))),
            ),
            store,
        )

        val next = PlaybackRecoveryPolicy.nextCandidate(
            aggregated = aggregated,
            currentSourceKey = "src1",
            currentRouteKey = "r1",
            currentEpisodeKey = "ep1",
            tried = setOf(Triple("src1", "r1", "ep1")),
        )

        assertEquals(Triple("src2", "r1", "ep1"), next)
    }

    // 降级 3：三源时尊重评分顺序，优先选第二高分源而非第三
    @Test
    fun third_fallback_respects_score_order_with_three_sources() {
        val store = makeStore()
        store.save(SourceScoreRecord(sourceKey = "src1", playbackStartSuccessCount = 10))
        store.save(SourceScoreRecord(sourceKey = "src2", playbackStartSuccessCount = 5))
        store.save(SourceScoreRecord(sourceKey = "src3", playbackStartSuccessCount = 1))

        val aggregated = makeAggregated(
            listOf(
                detail("src1", listOf(route("r1", listOf("ep1")))),
                detail("src2", listOf(route("r1", listOf("ep1")))),
                detail("src3", listOf(route("r1", listOf("ep1")))),
            ),
            store,
        )

        // src1 所有线路集数都已 tried，应降级到 src2（第二高分源）而非 src3
        val next = PlaybackRecoveryPolicy.nextCandidate(
            aggregated = aggregated,
            currentSourceKey = "src1",
            currentRouteKey = "r1",
            currentEpisodeKey = "ep1",
            tried = setOf(Triple("src1", "r1", "ep1")),
        )

        assertEquals(Triple("src2", "r1", "ep1"), next)
    }

    // 降级 4：全部失败 → null
    @Test
    fun returns_null_when_all_candidates_tried() {
        val store = makeStore()
        val aggregated = makeAggregated(
            listOf(
                detail("src1", listOf(route("r1", listOf("ep1")))),
                detail("src2", listOf(route("r1", listOf("ep1")))),
            ),
            store,
        )

        val next = PlaybackRecoveryPolicy.nextCandidate(
            aggregated = aggregated,
            currentSourceKey = "src1",
            currentRouteKey = "r1",
            currentEpisodeKey = "ep1",
            tried = setOf(
                Triple("src1", "r1", "ep1"),
                Triple("src2", "r1", "ep1"),
            ),
        )

        assertNull(next)
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
