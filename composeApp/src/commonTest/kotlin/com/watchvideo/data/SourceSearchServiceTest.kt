package com.watchvideo.data

import com.russhwolf.settings.Settings
import com.watchvideo.data.local.SourceScoreStore
import com.watchvideo.data.model.PlaybackCandidate
import com.watchvideo.data.model.SearchResult
import com.watchvideo.data.model.SourceScoreRecord
import com.watchvideo.data.model.SourceSearchItem
import com.watchvideo.data.model.SourceDetail
import com.watchvideo.data.model.toLegacySearchResult
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SourceSearchServiceTest {
    @Test
    fun normalized_search_item_keeps_source_identity() {
        val item = SourceSearchItem(
            sourceKey = "modu",
            sourceName = "模板影视",
            contentKey = "the-hero-2026",
            sourceContentId = "123",
            title = "The Hero",
            coverUrl = null,
            subtitle = null,
            tags = emptyList(),
            detailUrl = null,
        )

        assertEquals("modu", item.sourceKey)
        assertEquals("123", item.sourceContentId)
    }

    @Test
    fun source_search_item_is_not_the_same_runtime_type_as_legacy_search_result() {
        val item = SourceSearchItem(
            sourceKey = "modu",
            sourceName = "模板影视",
            contentKey = "the-hero-2026",
            sourceContentId = "123",
            title = "The Hero",
            coverUrl = "http://localhost/poster.jpg",
            subtitle = null,
            tags = emptyList(),
            detailUrl = null,
        )

        val legacy = item.toLegacySearchResult()

        assertEquals("123", legacy.id)
        assertEquals("modu", legacy.siteKey)
        assertFalse(SearchResult::class.isInstance(item))
    }

    // ── SourceSearchService TDD tests ──────────────────────────────────────────

    private fun fakeItem(sourceKey: String, sourceName: String, id: String, title: String) =
        SourceSearchItem(
            sourceKey = sourceKey,
            sourceName = sourceName,
            contentKey = "$sourceKey-$id",
            sourceContentId = id,
            title = title,
            coverUrl = null,
            subtitle = null,
            tags = emptyList(),
            detailUrl = null,
        )

    private class FakeParser(
        override val siteKey: String,
        override val siteName: String,
        private val items: List<SourceSearchItem>,
    ) : SiteParser {
        override val baseUrl: String = "http://fake/$siteKey"
        override suspend fun search(keyword: String): List<SourceSearchItem> = items
        override suspend fun detail(id: String): com.watchvideo.data.model.SourceDetail = throw NotImplementedError()
        override suspend fun playInfo(playPageUrl: String): com.watchvideo.data.model.PlaybackCandidate = throw NotImplementedError()
    }

    @Test
    fun searchGrouped_orders_groups_by_score_descending() = runBlocking {
        val store = SourceScoreStore(FakeSettings(), nowEpochMsProvider = { 0L })
        // alpha 有高成功率 → 高分；beta 无历史记录 → 0 分
        store.save(SourceScoreRecord(
            sourceKey = "alpha",
            searchSuccessCount = 20,
            playbackStartSuccessCount = 20,
            stableObservedHeight = 1080,
            avgFirstFrameMs = 600,
        ))

        val parsers = listOf(
            FakeParser("beta", "Beta站", listOf(fakeItem("beta", "Beta站", "b1", "Movie B"))),
            FakeParser("alpha", "Alpha站", listOf(fakeItem("alpha", "Alpha站", "a1", "Movie A"))),
        )

        val service = SourceSearchService(parsers, store)
        val result = service.searchGrouped("movie")

        assertEquals(2, result.groups.size)
        assertEquals("alpha", result.groups[0].siteKey)
        assertEquals("beta", result.groups[1].siteKey)
    }

    @Test
    fun searchGrouped_filters_blank_title_and_empty_sourceContentId() = runBlocking {
        val store = SourceScoreStore(FakeSettings(), nowEpochMsProvider = { 0L })
        val parser = FakeParser(
            siteKey = "s1",
            siteName = "站1",
            items = listOf(
                fakeItem("s1", "站1", "id1", "正常标题"),
                fakeItem("s1", "站1", "id2", "   "),      // 空白标题
                fakeItem("s1", "站1", "", "有标题"),         // 空 sourceContentId
            ),
        )

        val service = SourceSearchService(listOf(parser), store)
        val result = service.searchGrouped("test")

        assertEquals(1, result.groups.size)
        assertEquals(1, result.groups[0].results.size)
        assertEquals("正常标题", result.groups[0].results[0].title)
    }

    @Test
    fun searchGrouped_omits_group_when_all_results_filtered() = runBlocking {
        val store = SourceScoreStore(FakeSettings(), nowEpochMsProvider = { 0L })
        val parser = FakeParser(
            siteKey = "empty",
            siteName = "空站",
            items = listOf(
                fakeItem("empty", "空站", "", "有标题但无ID"),
            ),
        )

        val service = SourceSearchService(listOf(parser), store)
        val result = service.searchGrouped("x")

        assertTrue(result.groups.isEmpty())
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
