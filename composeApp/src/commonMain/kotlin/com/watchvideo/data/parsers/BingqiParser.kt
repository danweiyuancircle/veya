package com.watchvideo.data.parsers

import com.watchvideo.data.SiteParser
import com.watchvideo.data.model.Episode
import com.watchvideo.data.model.PlayInfo
import com.watchvideo.data.model.Route
import com.watchvideo.data.model.SearchResult
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter

/**
 * 秉奇影视（bingqichem.com）。
 *
 * 该站为 maccms 伪静态前端（FED 模板），无开放 JSON 采集 API（api.php/provide/vod 返回 404），
 * 走 HTML 抓取三跳：
 *   搜索  GET /search.php?searchword={kw}  → 结果项 /slob/{id}.html
 *   详情  GET /slob/{id}.html             → 全部线路+集数 /seed/{id}-{线路}-{集}.html
 *   播放  GET /seed/{id}-{线路}-{集}.html  → var now=base64decode("...") 解码即当前集 m3u8 直链
 *
 * 注意：搜索结果用 /slob/ 前缀、播放页用 /seed/ 前缀，同 id。
 */
class BingqiParser(private val client: HttpClient) : SiteParser {
    override val siteKey = "bingqi"
    override val siteName = "秉奇影视"
    override val baseUrl = "https://www.bingqichem.com"

    override suspend fun search(keyword: String): List<SearchResult> {
        val html = fetch("$baseUrl/search.php?searchword=${keyword.encodeURLParameter()}")
        return parseSearchResults(html)
    }

    override suspend fun detail(id: String): List<Route> {
        val html = fetch("$baseUrl/slob/$id.html")
        return parseRoutes(html)
    }

    override suspend fun playInfo(playPageUrl: String): PlayInfo {
        val html = fetch(playPageUrl)
        return try {
            parsePlayInfo(html)
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

    private fun parseSearchResults(html: String): List<SearchResult> {
        // 封面与标题是两个独立 <a>，均带 /slob/{id}.html，按 id 关联
        val coverPattern = Regex("""<a class="fed-list-pics[^"]*" href="/slob/(\d+)\.html" data-original="([^"]+)"""")
        val titlePattern = Regex("""<a class="fed-list-title[^"]*" href="/slob/(\d+)\.html"[^>]*>([^<]+)</a>""")

        val covers = coverPattern.findAll(html).associate { it.groupValues[1] to it.groupValues[2] }

        return titlePattern.findAll(html).map { m ->
            val id = m.groupValues[1]
            val title = m.groupValues[2].trim()
            val imgPath = covers[id] ?: ""
            val cover = if (imgPath.startsWith("http")) imgPath else "$baseUrl$imgPath"
            SearchResult(id = id, title = title, cover = cover, siteKey = siteKey)
        }.toList()
    }

    private fun parseRoutes(html: String): List<Route> {
        // 线路名（fed-tabs-btns）。该标签也用于非线路 tab（如"剧情介绍"），
        // 数量可能多于 playlist 块，按 ul 数量截断对齐即可，多余的丢弃。
        val tabPattern = Regex("""<li class="fed-tabs-btns[^"]*">([^<]+)</li>""")
        val tabNames = tabPattern.findAll(html).map { it.groupValues[1].trim() }.toList()

        // 每条线路一个 ul.stui-content__playlist，集数 <a title="第NN集" href="/seed/...html"...>第NN集</a>
        val ulPattern = Regex("""<ul class="stui-content__playlist[^"]*">(.*?)</ul>""", RegexOption.DOT_MATCHES_ALL)
        val episodePattern = Regex("""<a [^>]*href="(/seed/[^"]+\.html)"[^>]*>([^<]+)</a>""")

        return ulPattern.findAll(html).mapIndexed { index, ulMatch ->
            val blockHtml = ulMatch.groupValues[1]
            val episodes = episodePattern.findAll(blockHtml).map { ep ->
                Episode(
                    name = ep.groupValues[2].trim(),
                    playUrl = "$baseUrl${ep.groupValues[1]}"
                )
            }.toList()
            Route(
                name = tabNames.getOrElse(index) { "线路${index + 1}" },
                episodes = episodes
            )
        }.filter { it.episodes.isNotEmpty() }.toList()
    }

    private fun parsePlayInfo(html: String): PlayInfo {
        // 播放页 JS：var now=base64decode("<base64 当前集 m3u8>")（next 是下一集，勿用）
        val pattern = Regex("""var\s+now\s*=\s*base64decode\("([A-Za-z0-9+/=]+)"\)""")
        val encoded = pattern.find(html)?.groupValues?.get(1)
            ?: throw IllegalStateException("未找到 base64decode 播放地址")
        return PlayInfo(m3u8Url = decodeBase64(encoded), title = "")
    }

    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    private fun decodeBase64(encoded: String): String =
        String(kotlin.io.encoding.Base64.decode(encoded))
}
