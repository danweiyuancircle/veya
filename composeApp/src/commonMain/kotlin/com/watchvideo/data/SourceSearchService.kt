package com.watchvideo.data

import com.watchvideo.data.local.SourceScoreStore
import com.watchvideo.data.model.toLegacySearchResult
import com.watchvideo.ui.search.SiteResultGroup
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

data class SearchGroupedResult(
    val groups: List<SiteResultGroup>,
    val errors: List<String>,
)

class SourceSearchService(
    private val parsers: List<SiteParser>,
    private val scoreStore: SourceScoreStore,
) {
    suspend fun searchGrouped(keyword: String): SearchGroupedResult = coroutineScope {
        data class Outcome(
            val group: SiteResultGroup? = null,
            val error: String? = null,
        )

        val outcomes = parsers
            .map { parser ->
                async {
                    try {
                        val items = parser.search(keyword)
                        val results = items
                            .filter { it.title.isNotBlank() && it.sourceContentId.isNotBlank() }
                            .map { it.toLegacySearchResult() }
                        Outcome(group = SiteResultGroup(parser.siteKey, parser.siteName, results))
                    } catch (e: Exception) {
                        Outcome(error = "${parser.siteName}失败\n类型:${e::class.simpleName}\n原因:${e.message}")
                    }
                }
            }
            .awaitAll()

        val rawGroups = outcomes.mapNotNull { it.group }.filter { it.results.isNotEmpty() }
        val errors = outcomes.mapNotNull { it.error }

        val rankedKeys = scoreStore.rank(rawGroups.map { it.siteKey })
        val groupByKey = rawGroups.associateBy { it.siteKey }

        SearchGroupedResult(
            groups = rankedKeys.mapNotNull { groupByKey[it] },
            errors = errors,
        )
    }
}
