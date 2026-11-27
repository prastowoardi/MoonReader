package com.moonreader.app

import com.moonreader.app.data.local.TachibkParser
import com.moonreader.app.domain.model.MangaStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.GZIPOutputStream

class TachibkParserTest {

    @Test
    fun `parse gzipped tachibk protobuf restores categories, sources, manga and chapters`() {
        val chapterBytes = buildProto {
            writeString(1, "/ch/10")
            writeString(2, "Chapter 10")
            writeString(3, "IndoScan")
            writeVarint(4, 1L) // read = true
            writeVarint(5, 1L) // bookmark = true
            writeVarint(6, 7L) // lastPageRead = 7
            writeVarint(8, 1700000000000L)
            writeFloat32(9, 10.5f)
        }

        val mangaBytes = buildProto {
            writeVarint(1, 972717448578983812L)
            writeString(2, "/komik/sample-series")
            writeString(3, "Sample Tachiyomi Series")
            writeString(5, "Test Author")
            writeString(6, "Sinopsis contoh")
            writeString(7, "Action")
            writeString(7, "Fantasy")
            writeVarint(8, 1L) // ONGOING
            writeString(9, "https://example.com/cover.jpg")
            writeVarint(13, 1714699254337L)
            writeBytes(16, chapterBytes)
            writeVarint(17, 1L) // category order 1 -> "Favorit"
        }

        val cat0 = buildProto {
            writeString(1, "Fun")
            writeVarint(2, 0L)
        }
        val cat1 = buildProto {
            writeString(1, "Cult")
            writeVarint(2, 1L)
        }
        val src = buildProto {
            writeString(1, "Komik Cast")
            writeVarint(2, 972717448578983812L)
        }

        val rootProto = buildProto {
            writeBytes(1, mangaBytes)
            writeBytes(2, cat0)
            writeBytes(2, cat1)
            writeBytes(101, src)
        }

        val gzipped = ByteArrayOutputStream().use { baos ->
            GZIPOutputStream(baos).use { gz -> gz.write(rootProto) }
            baos.toByteArray()
        }

        val result = TachibkParser.parse(ByteArrayInputStream(gzipped))
        assertEquals(2, result.categories.size)
        assertEquals("Fun", result.categories[0].name)
        assertEquals("Cult", result.categories[1].name)

        assertEquals(1, result.mangas.size)
        val manga = result.mangas.single()
        assertEquals("Sample Tachiyomi Series", manga.title)
        assertEquals("Komik Cast", manga.sourceId)
        assertEquals(MangaStatus.ONGOING, manga.status)
        assertEquals(listOf("Action", "Fantasy"), manga.genres)
        assertEquals(result.categories[1].id, manga.categoryId)

        assertEquals(1, result.chapters.size)
        val ch = result.chapters.single()
        assertEquals("Chapter 10", ch.name)
        assertEquals(10.5f, ch.chapterNumber, 0.001f)
        assertTrue(ch.read)
        assertTrue(ch.bookmark)
        assertEquals(7, ch.lastPageRead)
    }

    private fun buildProto(block: ProtoWriter.() -> Unit): ByteArray {
        val w = ProtoWriter()
        w.block()
        return w.toByteArray()
    }

    private class ProtoWriter {
        private val out = ByteArrayOutputStream()

        fun writeVarint(field: Int, value: Long) {
            writeRawVarint(((field shl 3) or 0).toLong())
            writeRawVarint(value)
        }

        fun writeString(field: Int, value: String) {
            writeBytes(field, value.toByteArray(Charsets.UTF_8))
        }

        fun writeBytes(field: Int, value: ByteArray) {
            writeRawVarint(((field shl 3) or 2).toLong())
            writeRawVarint(value.size.toLong())
            out.write(value)
        }

        fun writeFloat32(field: Int, value: Float) {
            writeRawVarint(((field shl 3) or 5).toLong())
            val buf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(value)
            out.write(buf.array())
        }

        private fun writeRawVarint(v: Long) {
            var x = v
            while (true) {
                if ((x and 0x7FL.inv()) == 0L) {
                    out.write(x.toInt())
                    return
                }
                out.write(((x and 0x7F) or 0x80).toInt())
                x = x ushr 7
            }
        }

        fun toByteArray(): ByteArray = out.toByteArray()
    }
}
