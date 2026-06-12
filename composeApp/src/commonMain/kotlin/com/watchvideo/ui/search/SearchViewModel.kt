package com.watchvideo.ui.search

import com.watchvideo.data.ParserRegistry
import com.watchvideo.data.model.SearchResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SearchViewModel {
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<SearchResult>>(emptyList())
    val results: StateFlow<List<SearchResult>> = _results.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun onQueryChange(query: String) {
        _query.value = query
    }

    fun search() {
        val keyword = _query.value.trim()
        if (keyword.isEmpty()) return

        scope.launch {
            _isLoading.value = true
            _error.value = null
            _results.value = emptyList()

            val errors = mutableListOf<String>()
            val allResults = ParserRegistry.all()
                .map { parser ->
                    async {
                        try {
                            parser.search(keyword)
                        } catch (e: Exception) {
                            errors.add("${parser.siteName}(${e::class.simpleName}): ${e.message}")
                            emptyList()
                        }
                    }
                }
                .awaitAll()
                .flatten()

            _results.value = allResults
            if (allResults.isEmpty()) {
                _error.value = if (errors.isNotEmpty()) errors.joinToString("\n")
                               else "未找到结果"
            }
            _isLoading.value = false
        }
    }
}
