package com.watchvideo.data

import com.watchvideo.data.local.SourceScoreStore
import com.watchvideo.data.model.AggregatedDetail
import com.watchvideo.data.model.SourceDetail

class AggregatedDetailService(
    private val scoreStore: SourceScoreStore,
) {
    fun merge(sourceDetails: List<SourceDetail>): AggregatedDetail {
        require(sourceDetails.isNotEmpty()) { "sourceDetails must not be empty" }
        val ranked = sourceDetails.sortedByDescending { scoreStore.rankValue(scoreStore.get(it.sourceKey)) }
        val head = ranked.first()
        return AggregatedDetail(
            contentKey = head.contentKey,
            title = head.title,
            preferredCoverUrl = ranked.firstNotNullOfOrNull { it.coverUrl },
            preferredSummary = ranked.firstNotNullOfOrNull { it.summary },
            metadata = head.metadata,
            sourceDetails = ranked,
        )
    }
}
