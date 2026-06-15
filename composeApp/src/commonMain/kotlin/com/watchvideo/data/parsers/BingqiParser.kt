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

    override suspend fun search(keyword: String): List<SourceSearchItem> {
        val html = fetch("$baseUrl/search.php?searchword=${keyword.encodeURLParameter()}")
        return parseSearchResults(html)
    }

    override suspend fun detail(id: String): SourceDetail {
        val html = fetch("$baseUrl/slob/$id.html")
        return SourceDetail(
            sourceKey = siteKey,
            sourceName = siteName,
            contentKey = buildContentKey(id),
            sourceContentId = id,
            title = parseTitle(html),
            coverUrl = parseCover(html),
            summary = parseSummary(html),
            metadata = emptyList(),
            routes = parseRoutes(html),
        )
    }

    /** 标题：优先 og:title，回退 <h1>，再回退 <title>（去站点后缀）。 */
    private fun parseTitle(html: String): String {
        Regex("""<meta\s+property="og:title"\s+content="([^"]+)"""").find(html)
            ?.let { return it.groupValues[1].trim() }
        Regex("""<h1[^>]*>([^<]+)</h1>""").find(html)
            ?.let { return it.groupValues[1].trim() }
        return Regex("""<title>([^<|_-]+)""").find(html)?.groupValues?.get(1)?.trim().orEmpty()
    }

    /** 封面：优先 og:image，回退详情页内首个 data-original 图。补全相对路径。 */
    private fun parseCover(html: String): String? {
        val raw = Regex("""<meta\s+property="og:image"\s+content="([^"]+)"""").find(html)?.groupValues?.get(1)
            ?: Regex("""data-original="([^"]+\.(?:jpg|png|webp|jpeg))"""").find(html)?.groupValues?.get(1)
            ?: return null
        return if (raw.startsWith("http")) raw else "$baseUrl$raw"
    }

    /** 简介：og:description（可空）。 */
    private fun parseSummary(html: String): String? =
        Regex("""<meta\s+property="og:description"\s+content="([^"]+)"""").find(html)?.groupValues?.get(1)?.trim()

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

    internal fun parseSearchResults(html: String): List<SourceSearchItem> {
        // 不依赖具体 class 名 / 属性顺序，只锚定稳定特征：
        //   标题——含 href="/slob/{id}.html" 的 <a>，取其 title 属性或标签内文本
        //   封面——同一 <a>/<img> 上 data-original 或 src 指向的图，按 id 关联
        // maccms FED 模板搜索结果项中，标题链接通常带 title 属性。

        // 封面：data-original/src 与 /slob/{id}.html 出现在同一标签，两种属性顺序都覆盖
        val covers = mutableMapOf<String, String>()
        Regex("""(?:data-original|src)="([^"]+\.(?:jpg|jpeg|png|webp))"[^>]*href="/slob/(\d+)\.html"""")
            .findAll(html).forEach { covers.putIfAbsent(it.groupValues[2], it.groupValues[1]) }
        Regex("""href="/slob/(\d+)\.html"[^>]*(?:data-original|src)="([^"]+\.(?:jpg|jpeg|png|webp))"""")
            .findAll(html).forEach { covers.putIfAbsent(it.groupValues[1], it.groupValues[2]) }

        // 标题优先取 title 属性（顺序无关），回退取标签内文本
        val byTitleAttr = Regex("""<a\b[^>]*?href="/slob/(\d+)\.html"[^>]*?\btitle="([^"]+)"""")
            .findAll(html).associate { it.groupValues[1] to it.groupValues[2].trim() }
        val byTitleAttr2 = Regex("""<a\b[^>]*?\btitle="([^"]+)"[^>]*?href="/slob/(\d+)\.html"""")
            .findAll(html).associate { it.groupValues[2] to it.groupValues[1].trim() }
        val byText = Regex("""<a\b[^>]*?href="/slob/(\d+)\.html"[^>]*>([^<]{1,80})</a>""")
            .findAll(html).associate { it.groupValues[1] to it.groupValues[2].trim() }

        // 合并所有 id，标题三路回退，去重
        val ids = (byTitleAttr.keys + byTitleAttr2.keys + byText.keys).toSet()
        return ids.mapNotNull { id ->
            val title = (byTitleAttr[id] ?: byTitleAttr2[id] ?: byText[id])?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val imgPath = covers[id].orEmpty()
            val cover = when {
                imgPath.isBlank() -> null
                imgPath.startsWith("http") -> imgPath
                else -> "$baseUrl$imgPath"
            }
            SourceSearchItem(
                sourceKey = siteKey,
                sourceName = siteName,
                contentKey = buildContentKey(id),
                sourceContentId = id,
                title = title,
                coverUrl = cover,
                subtitle = null,
                tags = emptyList(),
                detailUrl = "$baseUrl/slob/$id.html",
            )
        }
    }

    private fun parseRoutes(html: String): List<PlaybackRoute> {
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
        }.filter { it.episodes.isNotEmpty() }.toList()
    }

    private fun parsePlayInfo(playPageUrl: String, html: String): PlaybackCandidate {
        // 播放页 JS：var now=base64decode("<base64 当前集 m3u8>")（next 是下一集，勿用）
        val pattern = Regex("""var\s+now\s*=\s*base64decode\("([A-Za-z0-9+/=]+)"\)""")
        val encoded = pattern.find(html)?.groupValues?.get(1)
            ?: throw IllegalStateException("未找到 base64decode 播放地址")
        return PlaybackCandidate(
            sourceKey = siteKey,
            routeKey = "",
            episodeKey = playPageUrl,
            title = "",
            streamUrl = decodeBase64(encoded),
            headers = emptyMap(),
        )
    }

    private fun buildContentKey(id: String): String = "$siteKey:$id"

    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    private fun decodeBase64(encoded: String): String =
        String(kotlin.io.encoding.Base64.decode(encoded))
}
