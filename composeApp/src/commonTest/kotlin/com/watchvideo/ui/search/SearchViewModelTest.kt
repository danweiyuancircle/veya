package com.watchvideo.ui.search

import com.russhwolf.settings.Settings
import com.watchvideo.data.SiteParser
import com.watchvideo.data.model.MetaBadge
import com.watchvideo.data.model.PlaybackCandidate
import com.watchvideo.data.model.PlaybackRoute
import com.watchvideo.data.model.SourceDetail
import com.watchvideo.data.model.SourceSearchItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    @Test
    fun latest_search_result_wins() = runTest {
        val releaseOldSearch = CompletableDeferred<Unit>()
        val parser = FakeSiteParser(
            siteKey = "modu",
            siteName = "模板影视",
            searchHandler = { keyword ->
                when (keyword) {
                    "old" -> {
                        releaseOldSearch.await()
                        listOf(searchItem(keyword, "旧结果"))
                    }
                    "new" -> listOf(searchItem(keyword, "新结果"))
                    else -> emptyList()
                }
            }
        )
        val viewModel = SearchViewModel(
            settings = FakeSettings(),
            parserProvider = { listOf(parser) },
            scope = this,
        )

        viewModel.onQueryChange("old")
        viewModel.search()
        runCurrent()

        viewModel.onQueryChange("new")
        viewModel.search()
        advanceUntilIdle()

        assertEquals("新结果", viewModel.groups.value.single().results.single().title)

        releaseOldSearch.complete(Unit)
        advanceUntilIdle()

        assertEquals("新结果", viewModel.groups.value.single().results.single().title)
    }

    @Test
    fun search_errors_are_aggregated_in_parser_order() = runTest {
        val first = FakeSiteParser(
            siteKey = "a",
            siteName = "站点A",
            searchHandler = { throw IllegalStateException("boomA") }
        )
        val second = FakeSiteParser(
            siteKey = "b",
            siteName = "站点B",
            searchHandler = { throw IllegalArgumentException("boomB") }
        )
        val viewModel = SearchViewModel(
            settings = FakeSettings(),
            parserProvider = { listOf(first, second) },
            scope = this,
        )

        viewModel.onQueryChange("hero")
        viewModel.search()
        advanceUntilIdle()

        assertTrue(viewModel.groups.value.isEmpty())
        assertEquals(
            "站点A失败\n类型:IllegalStateException\n原因:boomA\n\n站点B失败\n类型:IllegalArgumentException\n原因:boomB",
            viewModel.error.value
        )
    }

    private fun searchItem(id: String, title: String) = SourceSearchItem(
        sourceKey = "modu",
        sourceName = "模板影视",
        contentKey = "modu:$id",
        sourceContentId = id,
        title = title,
        coverUrl = null,
        subtitle = null,
        tags = emptyList(),
        detailUrl = null,
    )

    private class FakeSiteParser(
        override val siteKey: String,
        override val siteName: String,
        private val searchHandler: suspend (String) -> List<SourceSearchItem>,
    ) : SiteParser {
        override val baseUrl: String = "http://localhost"

        override suspend fun search(keyword: String): List<SourceSearchItem> = searchHandler(keyword)

        override suspend fun detail(id: String): SourceDetail = SourceDetail(
            sourceKey = siteKey,
            sourceName = siteName,
            contentKey = "$siteKey:$id",
            sourceContentId = id,
            title = "",
            coverUrl = null,
            summary = null,
            metadata = emptyList<MetaBadge>(),
            routes = emptyList<PlaybackRoute>(),
        )

        override suspend fun playInfo(playPageUrl: String): PlaybackCandidate = PlaybackCandidate(
            sourceKey = siteKey,
            routeKey = "",
            episodeKey = "",
            title = "",
            streamUrl = playPageUrl,
            headers = emptyMap(),
        )
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
