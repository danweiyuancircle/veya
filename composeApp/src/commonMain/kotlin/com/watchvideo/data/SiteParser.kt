package com.watchvideo.data

import com.watchvideo.data.model.PlaybackCandidate
import com.watchvideo.data.model.SourceDetail
import com.watchvideo.data.model.SourceSearchItem

interface SiteParser {
    val siteKey: String
    val siteName: String
    val baseUrl: String

    suspend fun search(keyword: String): List<SourceSearchItem>
    suspend fun detail(id: String): SourceDetail
    suspend fun playInfo(playPageUrl: String): PlaybackCandidate
}
