package com.moonreader.app

import com.moonreader.app.data.source.OpdsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpdsParserTest {

    @Test
    fun `parseFeed extracts series entries, covers, and next page link`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>All Series</title>
              <link rel="next" href="/opds/v1.2/series?page=1" type="application/atom+xml"/>
              <entry>
                <id>series-1</id>
                <title>Komik Nusantara</title>
                <author><name>Raka</name></author>
                <summary>Sinopsis komik OPDS</summary>
                <category term="Action"/>
                <updated>2026-05-01T10:00:00Z</updated>
                <link rel="subsection" href="/opds/v1.2/series/1" type="application/atom+xml;profile=opds-catalog"/>
                <link rel="http://opds-spec.org/image" href="/covers/1.jpg" type="image/jpeg"/>
              </entry>
            </feed>
        """.trimIndent()

        val feed = OpdsParser.parseFeed(xml, "http://192.168.1.5:4567/opds/v1.2/series")
        assertEquals("http://192.168.1.5:4567/opds/v1.2/series?page=1", feed.nextPageUrl)
        val result = OpdsParser.toMangaPageResult(feed, "opds_1")
        assertTrue(result.hasNextPage)
        assertEquals(1, result.mangas.size)

        val manga = result.mangas.first()
        assertEquals("Komik Nusantara", manga.title)
        assertEquals("Raka", manga.author)
        assertEquals("http://192.168.1.5:4567/opds/v1.2/series/1", manga.url)
        assertEquals("http://192.168.1.5:4567/covers/1.jpg", manga.coverUrl)
        assertEquals(listOf("Action"), manga.genres)
    }

    @Test
    fun `toChapterList parses OPDS-PSE page stream and expands page numbers`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom" xmlns:pse="http://vaemoi.net/opds-pse">
              <entry>
                <id>ch-12</id>
                <title>Chapter 12</title>
                <link rel="http://vaemoi.net/opds-pse/stream"
                      href="/api/v1/books/12/pages/{pageNumber}"
                      pse:count="3"/>
              </entry>
            </feed>
        """.trimIndent()

        val feed = OpdsParser.parseFeed(xml, "http://192.168.1.5:4567/opds/v1.2/series/1")
        assertNull(feed.nextPageUrl)

        val chapters = OpdsParser.toChapterList(feed, "opds_1:abc")
        assertEquals(1, chapters.size)
        val ch = chapters.first()
        assertEquals(12f, ch.chapterNumber, 0.01f)
        assertEquals(3, ch.totalPages)
        assertTrue(ch.url.startsWith("pse|3|"))

        val pages = OpdsParser.expandPsePages(3, ch.url.removePrefix("pse|3|"))
        assertEquals(3, pages.size)
        assertEquals("http://192.168.1.5:4567/api/v1/books/12/pages/0", pages[0].imageUrl)
        assertEquals("http://192.168.1.5:4567/api/v1/books/12/pages/2", pages[2].imageUrl)
    }
}
