package com.watchvideo.data.model

data class AggregatedDetail(
    val contentKey: String,
    val title: String,
    val preferredCoverUrl: String?,
    val preferredSummary: String?,
    val metadata: List<MetaBadge>,
    val sourceDetails: List<SourceDetail>,
)
