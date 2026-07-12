package io.legado.app.utils

import java.util.concurrent.ConcurrentHashMap

/**
 * 正则表达式全局缓存
 *
 * 避免每次匹配都重新编译正则表达式，大幅减少内存和CPU消耗。
 * 使用 ConcurrentHashMap 保证线程安全。
 *
 * 供屏蔽规则（BlockRule）和高亮规则（HighlightRule）等共用。
 */
object RegexCache {
    private val cache = ConcurrentHashMap<String, Regex>()

    /**
     * 获取或编译正则表达式
     * 缓存命中直接返回，未命中则编译并缓存
     */
    fun getOrCompile(pattern: String): Regex {
        return cache.getOrPut(pattern) { Regex(pattern) }
    }

    /**
     * 获取或编译正则表达式（带选项）
     * 缓存命中直接返回，未命中则编译并缓存
     */
    fun getOrCompile(pattern: String, option: RegexOption): Regex {
        val key = "$option:$pattern"
        return cache.getOrPut(key) { Regex(pattern, option) }
    }

    /**
     * 获取或编译正则表达式（自定义编译逻辑）
     *
     * 当编译方式不是简单的 `Regex(pattern)` 时（如需要先转义），
     * 使用此重载，以 [key] 作为缓存键，[compiler] 作为编译函数。
     * 缓存命中直接返回，未命中则编译并缓存。
     */
    fun getOrCompile(key: String, compiler: () -> Regex): Regex {
        return cache.getOrPut(key, compiler)
    }

    /**
     * 清除缓存
     * 在规则变更时调用，避免旧规则残留
     */
    fun clear() {
        cache.clear()
    }
}
