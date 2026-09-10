package io.legado.app.ui.main.explore

/**
 * 兼容管理页使用的 trailing-lambda 调用形式。
 *
 * 原成员函数的 transform 参数位于 setCurrent 之前，Kotlin 的 trailing lambda
 * 会绑定到最后一个参数，从而把 lambda 错误地推断给 String?。这里提供一个
 * 参数顺序正确的重载，保持现有调用点不变。
 */
fun DiscoverySuiteManageViewModel.transformConfig(
    setCurrent: String? = null,
    transform: (DiscoverySuiteConfig) -> DiscoverySuiteConfig
) {
    val current = DiscoverySuiteStore.load()
    DiscoverySuiteStore.save(transform(current))
    setCurrent?.let { DiscoverySuiteStore.setSelectedSuiteId(it) }
    reload()
}
