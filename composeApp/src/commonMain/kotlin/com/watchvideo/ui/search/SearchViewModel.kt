package com.watchvideo.ui.search

import com.russhwolf.settings.Settings
import com.watchvideo.data.ParserRegistry
import com.watchvideo.data.SiteParser
import com.watchvideo.data.model.SearchResult
import com.watchvideo.data.model.toLegacySearchResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

    private val _history = MutableStateFlow(loadHistory())
    val history: StateFlow<List<String>> = _history.asStateFlow()

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
            _groups.value = emptyList()

            val parsers = try {
                parserProvider()
            } catch (e: Exception) {
                if (requestId != latestSearchRequestId) return@launch
                _error.value = "初始化失败(${e::class.simpleName}): ${e.message}"
                _isLoading.value = false
                return@launch
            }

            // 每个站点各自一组，并发搜索；只保留有结果的分组
            val outcomes = parsers
                .map { parser ->
                    async {
                        try {
                            SearchOutcome(
                                group = SiteResultGroup(
                                    parser.siteKey,
                                    parser.siteName,
                                    parser.search(keyword).map { it.toLegacySearchResult() }
                                )
                            )
                        } catch (e: Exception) {
                            SearchOutcome(
                                error = "${parser.siteName}失败\n类型:${e::class.simpleName}\n原因:${e.message}"
                            )
                        }
                    }
                }
                .awaitAll()

            if (requestId != latestSearchRequestId) return@launch

            val groups = outcomes
                .mapNotNull { it.group }
                .filter { it.results.isNotEmpty() }
            val errors = outcomes.mapNotNull { it.error }

            _groups.value = groups
            if (groups.isEmpty()) {
                _error.value = if (errors.isNotEmpty()) errors.joinToString("\n\n")
                               else "未找到结果(parsers=${parsers.size}, kw=$keyword)"
            }
            _isLoading.value = false
        }
    }

    private companion object {
        const val HISTORY_KEY = "search_history"
        const val MAX_HISTORY = 20
        val historySerializer = ListSerializer(String.serializer())
    }

    private data class SearchOutcome(
        val group: SiteResultGroup? = null,
        val error: String? = null,
    )
}
