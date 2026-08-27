package io.legado.app.help.config

import io.legado.app.model.debug.DebugCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
// 测试 AppConfig 类（DebugLogOnlyConfig 序列化/解析）
/**
 * 质量评估（2026-08-27）：小而美的教科书式单测。
 * - 纯函数测试（序列化/解析往返），无依赖、断言直给（testing.md §16.1「纯函数必须测」）。
 * - 边界覆盖到位：空白串、非法枚举名、ALL 特殊值过滤。
 * - 无需改进；同类纯函数工具新增时照此模式写。
 */
class AppConfigTest {

    @Test
    fun debugLogOnlyCategoriesRoundTrip() {
        val original = setOf(DebugCategory.SOURCE, DebugCategory.RSS, DebugCategory.APP)

        val serialized = DebugLogOnlyConfig.serializeCategories(original)
        val parsed = DebugLogOnlyConfig.parseCategories(serialized)

        assertEquals(original, parsed)
    }

    @Test
    fun debugLogOnlyCategoriesFilterAllWhenSerializingAndParsing() {
        val serialized = DebugLogOnlyConfig.serializeCategories(
            setOf(DebugCategory.ALL, DebugCategory.SOURCE)
        )
        val parsed = DebugLogOnlyConfig.parseCategories("ALL,$serialized")

        assertFalse(serialized.contains(DebugCategory.ALL.name))
        assertTrue(DebugCategory.ALL !in parsed)
        assertTrue(DebugCategory.SOURCE in parsed)
    }

    @Test
    fun debugLogOnlyCategoriesParseBlankAsEmptySet() {
        assertEquals(emptySet<DebugCategory>(), DebugLogOnlyConfig.parseCategories(""))
        assertEquals(emptySet<DebugCategory>(), DebugLogOnlyConfig.parseCategories("   "))
    }

    @Test
    fun debugLogOnlyCategoriesIgnoreInvalidNames() {
        val parsed = DebugLogOnlyConfig.parseCategories("SOURCE,FOO,RSS")

        assertEquals(setOf(DebugCategory.SOURCE, DebugCategory.RSS), parsed)
    }
}
