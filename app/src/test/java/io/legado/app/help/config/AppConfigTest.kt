package io.legado.app.help.config

import io.legado.app.model.debug.DebugCategory
import io.legado.app.utils.putPrefString
import io.legado.app.utils.removePref
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import splitties.init.appCtx

/**
 * AppConfig.debugLogOnlyCategories 持久化与边界条件测试
 */
class AppConfigTest {

    @After
    fun cleanup() {
        appCtx.removePref("debugLogOnlyCategories")
        appCtx.removePref("debugLogOnlyEnabled")
    }

    @Test
    fun `debugLogOnlyCategories 序列化与反序列化往返一致`() {
        val original = setOf(
            DebugCategory.SOURCE,
            DebugCategory.RSS,
            DebugCategory.APP
        )
        AppConfig.debugLogOnlyCategories = original
        val read = AppConfig.debugLogOnlyCategories
        assertEquals(original, read)
    }

    @Test
    fun `debugLogOnlyCategories 写入 ALL 时读取应过滤掉`() {
        AppConfig.debugLogOnlyCategories = setOf(DebugCategory.ALL, DebugCategory.SOURCE)
        val read = AppConfig.debugLogOnlyCategories
        assertTrue("ALL 应当被过滤", DebugCategory.ALL !in read)
        assertTrue("SOURCE 应当保留", DebugCategory.SOURCE in read)
    }

    @Test
    fun `debugLogOnlyCategories 空集合读写应为空`() {
        AppConfig.debugLogOnlyCategories = emptySet()
        assertEquals(emptySet<DebugCategory>(), AppConfig.debugLogOnlyCategories)
    }

    @Test
    fun `debugLogOnlyCategories 无效 enum name 应被忽略`() {
        appCtx.putPrefString("debugLogOnlyCategories", "SOURCE,FOO,RSS")
        val read = AppConfig.debugLogOnlyCategories
        assertEquals(setOf(DebugCategory.SOURCE, DebugCategory.RSS), read)
    }
}
