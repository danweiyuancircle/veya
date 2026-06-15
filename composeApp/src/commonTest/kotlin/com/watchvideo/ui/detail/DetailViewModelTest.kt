package com.watchvideo.ui.detail

import com.watchvideo.data.SiteParser
import com.watchvideo.data.model.MetaBadge
import com.watchvideo.data.model.PlaybackCandidate
import com.watchvideo.data.model.PlaybackEpisode
import com.watchvideo.data.model.PlaybackRoute
import com.watchvideo.data.model.SourceDetail
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModelTest {
    @Test
    fun select_episode_failure_keeps_previous_play_state() = runTest {
        val parser = FakeSiteParser()
        val viewModel = DetailViewModel(
            parserLookup = { parser },
            scope = this,
        )

        viewModel.selectEpisode("modu", routeIndex = 0, episodeIndex = 0, playUrl = "good")
        advanceUntilIdle()

        val initialPlayInfo = viewModel.playInfo.value
        assertNotNull(initialPlayInfo)
        assertEquals(0, viewModel.currentRouteIndex.value)
        assertEquals(0, viewModel.currentEpisodeIndex.value)

        viewModel.selectEpisode("modu", routeIndex = 0, episodeIndex = 1, playUrl = "bad")
        advanceUntilIdle()

        assertEquals(0, viewModel.currentRouteIndex.value)
        assertEquals(0, viewModel.currentEpisodeIndex.value)
        assertEquals(initialPlayInfo, viewModel.playInfo.value)
        assertEquals("获取播放地址失败: play failed", viewModel.error.value)
    }

    private class FakeSiteParser : SiteParser {
        override val siteKey: String = "modu"
        override val siteName: String = "模板影视"
        override val baseUrl: String = "http://localhost"

        override suspend fun search(keyword: String) = emptyList<com.watchvideo.data.model.SourceSearchItem>()

        override suspend fun detail(id: String): SourceDetail = SourceDetail(
            sourceKey = siteKey,
            sourceName = siteName,
            contentKey = "$siteKey:$id",
            sourceContentId = id,
            title = "",
            coverUrl = null,
            summary = null,
            metadata = emptyList<MetaBadge>(),
            routes = listOf(
                PlaybackRoute(
                    routeKey = "r1",
                    routeName = "线路1",
                    episodes = listOf(
                        PlaybackEpisode("good", "第1集", "good"),
                        PlaybackEpisode("bad", "第2集", "bad"),
                    ),
                )
            ),
        )

        override suspend fun playInfo(playPageUrl: String): PlaybackCandidate {
            if (playPageUrl == "bad") throw IllegalStateException("play failed")
            return PlaybackCandidate(
                sourceKey = siteKey,
                routeKey = "r1",
                episodeKey = playPageUrl,
                title = "播放中",
                streamUrl = "http://localhost/$playPageUrl.m3u8",
                headers = mapOf("Referer" to baseUrl),
            )
        }
    }
}
