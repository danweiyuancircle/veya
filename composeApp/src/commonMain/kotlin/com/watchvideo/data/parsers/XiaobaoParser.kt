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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SiteParseException(siteKey: String, message: String, cause: Throwable? = null) :
    Exception("[$siteKey] $message", cause)

class XiaobaoParser(private val client: HttpClient) : SiteParser {
    override val siteKey = "xiaobao"
    override val siteName = "小宝影院"
    override val baseUrl = "https://www.xiaobaotv.com"

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun search(keyword: String): List<SourceSearchItem> {
        val html = fetch("$baseUrl/search.html?wd=${keyword.encodeURLParameter()}&submit=")
        return parseSearchResults(html)
    }

    override suspend fun detail(id: String): SourceDetail {
        val html = fetch("$baseUrl/vod/detail/$id.html")
        return SourceDetail(
            sourceKey = siteKey,
            sourceName = siteName,
            contentKey = buildContentKey(id),
            sourceContentId = id,
            title = "",
            coverUrl = null,
            summary = null,
            metadata = emptyList(),
            routes = parseRoutes(html),
        )
    }

    override suspend fun playInfo(playPageUrl: String): PlaybackCandidate {
        val html = fetch(playPageUrl)
        return try {
            parsePlayInfo(playPageUrl, html)
        } catch (e: Exception) {
            throw SiteParseException(siteKey, "playInfo 解析失败: $playPageUrl", e)
        }
    }

    private suspend fun fetch(url: String): String {
        return client.get(url) {
            headers {
                append("User-Agent", "Mozilla/5.0 (Linux; Android 12; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                append("Referer", baseUrl)
                append("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                append("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            }
        }.bodyAsText()
    }

    private fun parseSearchResults(html: String): List<SourceSearchItem> {
        // 封面图 <a> 标签属性顺序固定：class → href → title → data-original
        // class 以 myui-vodlist__thumb 开头，精确锁定搜索结果卡片，避免误匹配侧边栏
        val pattern = Regex("""<a class="myui-vodlist__thumb[^"]*" href="/vod/detail/(\d+)\.html" title="([^"]+)" data-original="([^"]+)"""")
        return pattern.findAll(html).map { m ->
            val id = m.groupValues[1]
            val title = m.groupValues[2]
            val imgPath = m.groupValues[3]
            val cover = if (imgPath.startsWith("http")) imgPath else "$baseUrl$imgPath"
            SourceSearchItem(
                sourceKey = siteKey,
                sourceName = siteName,
                contentKey = buildContentKey(id),
                sourceContentId = id,
                title = title,
                coverUrl = cover,
                subtitle = null,
                tags = emptyList(),
                detailUrl = "$baseUrl/vod/detail/$id.html",
            )
        }.toList()
    }

    private fun parseRoutes(html: String): List<PlaybackRoute> {
        val tabPattern = Regex("""data-tab="([^"]+)"""")
        val tabNames = tabPattern.findAll(html).map { it.groupValues[1] }.toList()

        val ulPattern = Regex("""<ul[^>]*myui-content__list[^>]*>(.*?)</ul>""", RegexOption.DOT_MATCHES_ALL)
        val episodePattern = Regex("""href="(/vod/play/[^"]+)"[^>]*>([^<]+)</a>""")

        return ulPattern.findAll(html).mapIndexed { index, ulMatch ->
            val blockHtml = ulMatch.groupValues[1]
            val episodes = episodePattern.findAll(blockHtml).map { ep ->
                val playPageUrl = "$baseUrl${ep.groupValues[1]}"
                PlaybackEpisode(
                    episodeKey = playPageUrl,
                    episodeLabel = ep.groupValues[2].trim(),
                    playPageUrl = playPageUrl,
                )
            }.toList()
            PlaybackRoute(
                routeKey = tabNames.getOrElse(index) { "route-${index + 1}" },
                routeName = tabNames.getOrElse(index) { "线路${index + 1}" },
                episodes = episodes
            )
        }.toList()
    }

    private fun parsePlayInfo(playPageUrl: String, html: String): PlaybackCandidate {
        val playerPattern = Regex("""var player_aaaa\s*=\s*(\{.*?\})\s*</script>""", RegexOption.DOT_MATCHES_ALL)
        val match = playerPattern.find(html)
            ?: throw IllegalStateException("未找到 player_aaaa 配置")

        val playerJson = json.parseToJsonElement(match.groupValues[1]).jsonObject
        val encrypt = playerJson["encrypt"]?.jsonPrimitive?.int ?: 0
        val rawUrl = playerJson["url"]?.jsonPrimitive?.content
            ?: throw IllegalStateException("未找到播放地址")

        val m3u8Url = when (encrypt) {
            1 -> decodeBase64(rawUrl)
            2 -> decodeUrlPercent(rawUrl)
            else -> rawUrl
        }

        val vodData = playerJson["vod_data"]?.jsonObject
        val title = vodData?.get("vod_name")?.jsonPrimitive?.content ?: ""

        return PlaybackCandidate(
            sourceKey = siteKey,
            routeKey = "",
            episodeKey = playPageUrl,
            title = title,
            streamUrl = m3u8Url,
            headers = emptyMap(),
        )
    }

    private fun buildContentKey(id: String): String = "$siteKey:$id"

    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    private fun decodeBase64(encoded: String): String =
        String(kotlin.io.encoding.Base64.decode(encoded))

    private fun decodeUrlPercent(encoded: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < encoded.length) {
            when {
                encoded[i] == '%' && i + 2 < encoded.length -> {
                    val hex = encoded.substring(i + 1, i + 3)
                    sb.append(hex.toInt(16).toChar())
                    i += 3
                }
                encoded[i] == '+' -> { sb.append(' '); i++ }
                else -> { sb.append(encoded[i]); i++ }
            }
        }
        return sb.toString()
    }
}
