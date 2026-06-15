package com.watchvideo.data.parsers

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BingqiParserTest {

    private fun parser(): BingqiParser =
        BingqiParser(HttpClient(MockEngine { respond("") }))

    @Test
    fun parses_title_and_cover_regardless_of_attribute_order() {
        // class 在前、href 后、title 属性形式
        val htmlA = """
            <a class="fed-list-pics" href="/slob/12345.html" data-original="/upload/a.jpg"></a>
            <a class="fed-list-title" href="/slob/12345.html" title="庆余年 第二季">庆余年 第二季</a>
        """.trimIndent()

        val a = parser().parseSearchResults(htmlA)
        assertEquals(1, a.size)
        assertEquals("12345", a[0].sourceContentId)
        assertEquals("庆余年 第二季", a[0].title)
        assertTrue(a[0].coverUrl!!.endsWith("/upload/a.jpg"))
    }

    @Test
    fun parses_when_title_attr_precedes_href() {
        // title 在 href 之前的属性顺序，旧正则会失配
        val htmlB =
            """<a title="繁花" class="x" href="/slob/678.html">繁花</a>"""
        val b = parser().parseSearchResults(htmlB)
        assertEquals(1, b.size)
        assertEquals("678", b[0].sourceContentId)
        assertEquals("繁花", b[0].title)
    }

    @Test
    fun falls_back_to_inner_text_when_no_title_attr() {
        val htmlC = """<a class="t" href="/slob/9.html">与凤行</a>"""
        val c = parser().parseSearchResults(htmlC)
        assertEquals(1, c.size)
        assertEquals("与凤行", c[0].title)
    }

    @Test
    fun returns_empty_for_unrelated_html() {
        assertTrue(parser().parseSearchResults("<html><body>no results</body></html>").isEmpty())
    }
}
