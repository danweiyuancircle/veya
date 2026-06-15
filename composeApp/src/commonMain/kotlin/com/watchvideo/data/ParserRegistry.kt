package com.watchvideo.data

import com.watchvideo.data.parsers.BingqiParser
import com.watchvideo.data.parsers.ModuParser
import io.ktor.client.HttpClient

object ParserRegistry {
    private val client = createHttpClient()
    private val parsers: List<SiteParser> = listOf(
        ModuParser(client),
        BingqiParser(client)
    )

    fun all(): List<SiteParser> = parsers
    fun get(siteKey: String): SiteParser = parsers.first { it.siteKey == siteKey }
}
