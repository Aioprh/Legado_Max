/**
 * 首页副作用定义
 *
 * 文件作用：定义首页模块中的一次性 UI 副作用（Effect）。
 * 主要功能：
 * - 定义页面导航相关副作用（跳转书籍详情、跳转探索列表）
 * - 定义用户提示副作用（显示 Snackbar）
 *
 * 采用密封接口实现，便于在 ViewModel 中统一管理和处理副作用。
 */
package io.legado.app.ui.main.homepage

/**
 * 首页副作用密封接口
 *
 * 用于表示首页中需要执行的一次性副作用，如页面导航和消息提示。
 */
sealed interface HomepageEffect {
    /**
     * 跳转到书籍详情页
     *
     * @property name 书名
     * @property author 作者
     * @property bookUrl 书籍 URL
     * @property origin 书源
     * @property coverPath 封面路径
     */
    data class NavigateToBookInfo(
        val name: String?,
        val author: String?,
        val bookUrl: String,
        val origin: String? = null,
        val coverPath: String? = null,
    ) : HomepageEffect

    /**
     * 跳转到探索列表页
     *
     * @property title 标题
     * @property sourceUrl 书源 URL
     * @property exploreUrl 探索 URL
     */
    data class NavigateToExploreShow(
        val title: String?,
        val sourceUrl: String,
        val exploreUrl: String?,
    ) : HomepageEffect

    /**
     * 显示 Snackbar 提示
     *
     * @property message 提示消息内容
     */
    data class ShowSnackbar(val message: String) : HomepageEffect
}
