package com.watchvideo.ui.detail

import com.watchvideo.data.ParserRegistry
import com.watchvideo.data.SiteParser
import com.watchvideo.data.model.PlayInfo
import com.watchvideo.data.model.Route
import com.watchvideo.data.model.toLegacyPlayInfo
import com.watchvideo.data.model.toLegacyRoutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DetailViewModel(
    private val parserLookup: (String) -> SiteParser = ParserRegistry::get,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + Job()),
) {

    private val _routes = MutableStateFlow<List<Route>>(emptyList())
    val routes: StateFlow<List<Route>> = _routes.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _playInfo = MutableStateFlow<PlayInfo?>(null)
    val playInfo: StateFlow<PlayInfo?> = _playInfo.asStateFlow()

    private val _isLoadingPlay = MutableStateFlow(false)
    val isLoadingPlay: StateFlow<Boolean> = _isLoadingPlay.asStateFlow()

    private val _currentRouteIndex = MutableStateFlow(0)
    val currentRouteIndex: StateFlow<Int> = _currentRouteIndex.asStateFlow()

    private val _currentEpisodeIndex = MutableStateFlow(-1)
    val currentEpisodeIndex: StateFlow<Int> = _currentEpisodeIndex.asStateFlow()

    fun loadDetail(siteKey: String, id: String) {
        scope.launch {
            _isLoading.value = true
            _error.value = null
            _routes.value = emptyList()
            _playInfo.value = null
            _currentRouteIndex.value = 0
            _currentEpisodeIndex.value = -1
            try {
                val parser = parserLookup(siteKey)
                _routes.value = parser.detail(id).toLegacyRoutes()
            } catch (e: Exception) {
                _error.value = "加载失败: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun selectEpisode(siteKey: String, routeIndex: Int, episodeIndex: Int, playUrl: String) {
        val previousRouteIndex = _currentRouteIndex.value
        val previousEpisodeIndex = _currentEpisodeIndex.value
        val previousPlayInfo = _playInfo.value
        scope.launch {
            _isLoadingPlay.value = true
            _error.value = null
            try {
                val parser = parserLookup(siteKey)
                _playInfo.value = parser.playInfo(playUrl).toLegacyPlayInfo()
                _currentRouteIndex.value = routeIndex
                _currentEpisodeIndex.value = episodeIndex
            } catch (e: Exception) {
                _currentRouteIndex.value = previousRouteIndex
                _currentEpisodeIndex.value = previousEpisodeIndex
                _playInfo.value = previousPlayInfo
                _error.value = "获取播放地址失败: ${e.message}"
            } finally {
                _isLoadingPlay.value = false
            }
        }
    }
}
