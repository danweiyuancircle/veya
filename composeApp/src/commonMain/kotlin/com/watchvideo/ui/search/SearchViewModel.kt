package com.watchvideo.ui.search

import com.russhwolf.settings.Settings
import com.watchvideo.data.ParserRegistry
import com.watchvideo.data.SiteParser
import com.watchvideo.data.SourceSearchService
import com.watchvideo.data.local.SourceScoreStore
import com.watchvideo.data.model.SearchResult
import com.watchvideo.platformEpochMs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** 按站点分组的搜索结果 */
data class SiteResultGroup(
    val siteKey: String,
    val siteName: String,
    val results: List<SearchResult>
)

class SearchViewModel(
    private val settings: Settings = Settings(),
    private val parserProvider: () -> List<SiteParser> = ParserRegistry::all,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + Job()),
    private val scoreStore: SourceScoreStore = SourceScoreStore(
        settings,
        nowEpochMsProvider = { platformEpochMs() },
    ),
) {
    private var latestSearchRequestId = 0L

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _groups = MutableStateFlow<List<SiteResultGroup>>(emptyList())
    val groups: StateFlow<List<SiteResultGroup>> = _groups.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** 部分源失败提示：有结果但某些源搜索失败时展示，便于诊断。 */
    private val _warning = MutableStateFlow<String?>(null)
    val warning: StateFlow<String?> = _warning.asStateFlow()

    private val _history = MutableStateFlow(loadHistory())
    val history: StateFlow<List<String>> = _history.asStateFlow()

    /** 站点当前评分值，UI 用于分级展示（优先/可用/其他） */
    fun rankValueOf(siteKey: String): Double = scoreStore.rankValue(scoreStore.get(siteKey))

    fun onQueryChange(query: String) {
        _query.value = query
    }

    fun removeHistory(keyword: String) {
        updateHistory(_history.value.filter { it != keyword })
    }

    private fun loadHistory(): List<String> {
        val raw = settings.getStringOrNull(HISTORY_KEY) ?: return emptyList()
        return runCatching { Json.decodeFromString(historySerializer, raw) }.getOrDefault(emptyList())
    }

    private fun updateHistory(list: List<String>) {
        _history.value = list
        settings.putString(HISTORY_KEY, Json.encodeToString(historySerializer, list))
    }

    fun search() {
        val keyword = _query.value.trim()
        if (keyword.isEmpty()) return
        val requestId = ++latestSearchRequestId

        // 更新历史（去重，新的排最前，最多保留 20 条）
        updateHistory((listOf(keyword) + _history.value.filter { it != keyword }).take(MAX_HISTORY))

        scope.launch {
            if (requestId != latestSearchRequestId) return@launch
            _isLoading.value = true
            _error.value = null
            _warning.value = null
            _groups.value = emptyList()

            val parsers = try {
                parserProvider()
            } catch (e: Exception) {
                if (requestId != latestSearchRequestId) return@launch
                _error.value = "初始化失败(${e::class.simpleName}): ${e.message}"
                _isLoading.value = false
                return@launch
            }

            val service = SourceSearchService(parsers, scoreStore)
            val result = service.searchGrouped(keyword)

            if (requestId != latestSearchRequestId) return@launch

            _groups.value = result.groups
            if (result.groups.isEmpty()) {
                _error.value = if (result.errors.isNotEmpty()) result.errors.joinToString("\n\n")
                               else "未找到结果(parsers=${parsers.size}, kw=$keyword)"
            } else if (result.errors.isNotEmpty()) {
                // 有结果但部分源失败：提示而非报错，便于诊断哪个源不可用
                _warning.value = result.errors.joinToString("\n\n")
            }
            _isLoading.value = false
        }
    }

    private companion object {
        const val HISTORY_KEY = "search_history"
        const val MAX_HISTORY = 20
        val historySerializer = ListSerializer(String.serializer())
    }
}

/** 进程级单例，使搜索结果在底部 tab 切换后仍保留（离开 search 页不销毁状态）。 */
object SearchViewModelHolder {
    val instance: SearchViewModel by lazy { SearchViewModel() }
}
