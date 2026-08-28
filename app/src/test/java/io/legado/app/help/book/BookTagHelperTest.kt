package io.legado.app.help.book

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [BookTagHelper] 单元测试。
 */
class BookTagHelperTest {

    @Test
    fun `parse null returns empty`() {
        assertEquals(emptyList<String>(), BookTagHelper.parse(null))
    }

    @Test
    fun `parse blank returns empty`() {
        assertEquals(emptyList<String>(), BookTagHelper.parse(""))
        assertEquals(emptyList<String>(), BookTagHelper.parse("   "))
    }

    @Test
    fun `parse single tag`() {
        assertEquals(listOf("玄幻"), BookTagHelper.parse("玄幻"))
    }

    @Test
    fun `parse comma separated`() {
        assertEquals(listOf("玄幻", "修仙"), BookTagHelper.parse("玄幻,修仙"))
    }

    @Test
    fun `parse chinese comma separated`() {
        assertEquals(listOf("玄幻", "修仙"), BookTagHelper.parse("玄幻，修仙"))
    }

    @Test
    fun `parse semicolon separated`() {
        assertEquals(listOf("玄幻", "修仙"), BookTagHelper.parse("玄幻;修仙"))
    }

    @Test
    fun `parse mixed separators`() {
        assertEquals(listOf("玄幻", "修仙", "都市"), BookTagHelper.parse("玄幻,修仙;都市"))
    }

    @Test
    fun `parse deduplicates case insensitive`() {
        assertEquals(listOf("Tag"), BookTagHelper.parse("Tag,tag,TAG"))
    }

    @Test
    fun `parse trims whitespace`() {
        assertEquals(listOf("玄幻", "修仙"), BookTagHelper.parse("  玄幻 , 修仙 "))
    }

    @Test
    fun `join empty returns null`() {
        assertNull(BookTagHelper.join(emptyList()))
    }

    @Test
    fun `join single tag`() {
        assertEquals("玄幻", BookTagHelper.join(listOf("玄幻")))
    }

    @Test
    fun `join multiple tags`() {
        assertEquals("玄幻,修仙", BookTagHelper.join(listOf("玄幻", "修仙")))
    }

    @Test
    fun `join deduplicates`() {
        assertEquals("Tag", BookTagHelper.join(listOf("Tag", "tag")))
    }

    @Test
    fun `has returns true when present`() {
        assertTrue(BookTagHelper.has("玄幻,修仙", "修仙"))
    }

    @Test
    fun `has returns false when absent`() {
        assertFalse(BookTagHelper.has("玄幻,修仙", "都市"))
    }

    @Test
    fun `has case insensitive`() {
        assertTrue(BookTagHelper.has("Tag,Other", "tag"))
    }

    @Test
    fun `has null returns false`() {
        assertFalse(BookTagHelper.has(null, "anything"))
    }
}
