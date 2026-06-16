package com.watchvideo.data.parsers

import com.watchvideo.data.SiteParser
import com.watchvideo.data.model.PlaybackCandidate
import com.watchvideo.data.model.PlaybackEpisode
import com.watchvideo.data.model.PlaybackRoute
import com.watchvideo.data.model.SourceDetail
import com.watchvideo.data.model.SourceSearchItem
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ModuParser(private val client: HttpClient) : SiteParser {
    override val siteKey = "modu"
    override val siteName = "模板影视"
    override val baseUrl = "https://caiji.moduapi.cc"

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun search(keyword: String): List<SourceSearchItem> {
        // 用 ac=detail 而非 ac=list：list 接口不返回 vod_pic，会导致搜索结果无封面；
        // detail 接口支持 wd 关键词搜索且返回完整字段（含 vod_pic）
        val body = fetch("$baseUrl/api.php/provide/vod/?ac=detail&wd=${keyword.encodeURLParameter()}")
        val root = json.parseToJsonElement(body).jsonObject
        val list = root["list"]?.jsonArray ?: return emptyList()
        return list.map { el ->
            val obj = el.jsonObject
            val id = obj["vod_id"]?.jsonPrimitive?.int?.toString() ?: return@map null
            val title = obj["vod_name"]?.jsonPrimitive?.content ?: ""
            val cover = obj["vod_pic"]?.jsonPrimitive?.content ?: ""
            SourceSearchItem(
                sourceKey = siteKey,
                sourceName = siteName,
                contentKey = buildContentKey(id),
                sourceContentId = id,
                title = title,
                coverUrl = cover,
                subtitle = null,
                tags = emptyList(),
                detailUrl = "$baseUrl/api.php/provide/vod/?ac=detail&ids=$id",
            )
        }.filterNotNull()
    }

    override suspend fun detail(id: String): SourceDetail {
        val body = fetch("$baseUrl/api.php/provide/vod/?ac=detail&ids=$id")
        val root = json.parseToJsonElement(body).jsonObject
        val vod = root["list"]?.jsonArray?.firstOrNull()?.jsonObject ?: return emptyDetail(id)

        val fromNames = vod["vod_play_from"]?.jsonPrimitive?.content?.split("\$\$\$") ?: emptyList()
        val playUrlBlock = vod["vod_play_url"]?.jsonPrimitive?.content ?: return emptyDetail(id)
        val routes = playUrlBlock.split("\$\$\$")

        val playbackRoutes = routes.mapIndexed { index, block ->
            val episodes = block.split("#").mapNotNull { ep ->
                val parts = ep.split("\$")
                if (parts.size < 2) return@mapNotNull null
                val playPageUrl = parts[1].trim()
                PlaybackEpisode(
                    episodeKey = playPageUrl,
                    episodeLabel = parts[0].trim(),
                    playPageUrl = playPageUrl,
                )
            }
            PlaybackRoute(
                routeKey = fromNames.getOrElse(index) { "route-${index + 1}" },
                routeName = fromNames.getOrElse(index) { "线路${index + 1}" },
                episodes = episodes,
            )
        }.filter { it.episodes.isNotEmpty() }

        return SourceDetail(
            sourceKey = siteKey,
            sourceName = siteName,
            contentKey = buildContentKey(id),
            sourceContentId = id,
            title = vod["vod_name"]?.jsonPrimitive?.content ?: "",
            coverUrl = vod["vod_pic"]?.jsonPrimitive?.content,
            summary = vod["vod_content"]?.jsonPrimitive?.content,
            metadata = emptyList(),
            routes = playbackRoutes,
        )
    }

    // 详情接口已直接返回 m3u8，playInfo 从 detail 结果取，此接口不需要再请求
    override suspend fun playInfo(playPageUrl: String): PlaybackCandidate {
        // playPageUrl 在此 parser 就是 m3u8 直链
        return PlaybackCandidate(
            sourceKey = siteKey,
            routeKey = "",
            episodeKey = playPageUrl,
            title = "",
            streamUrl = playPageUrl,
            headers = emptyMap(),
        )
    }

    private fun buildContentKey(id: String): String = "$siteKey:$id"

    private fun emptyDetail(id: String): SourceDetail = SourceDetail(
        sourceKey = siteKey,
        sourceName = siteName,
        contentKey = buildContentKey(id),
        sourceContentId = id,
        title = "",
        coverUrl = null,
        summary = null,
        metadata = emptyList(),
        routes = emptyList(),
    )

    private suspend fun fetch(url: String): String {
        return client.get(url) {
            headers {
                append("User-Agent", "okhttp/4.9.0")
            }
        }.bodyAsText()
    }
}
