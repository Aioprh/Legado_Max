package io.legado.app.ui.config

import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceScreen
import androidx.recyclerview.widget.RecyclerView

/**
 * PreferenceScreen 搜索过滤工具。
 *
 * 根据查询字符串过滤 PreferenceScreen 中的 Preference 项，
 * 匹配 title 或 summary（不区分大小写），隐藏不匹配的项和空 Category。
 * 返回第一个匹配项的位置索引，供调用方滚动定位。
 */
object PreferenceSearchHelper {

    /**
     * 执行过滤，返回第一个匹配项在 [listView] 中的位置索引。
     *
     * @param preferenceScreen 目标 Preference 树
     * @param query 搜索关键词，空字符串表示显示全部
     * @return 第一个匹配项的索引，-1 表示无匹配
     */
    fun filter(preferenceScreen: PreferenceScreen, query: String): Int {
        val lowerQuery = query.lowercase()
        var firstMatchIndex = -1
        var currentIndex = 0

        for (i in 0 until preferenceScreen.preferenceCount) {
            val category = preferenceScreen.getPreference(i)
            if (category is PreferenceCategory) {
                var hasVisibleChild = false
                for (j in 0 until category.preferenceCount) {
                    val preference = category.getPreference(j)
                    val title = preference.title?.toString()?.lowercase() ?: ""
                    val summary = preference.summary?.toString()?.lowercase() ?: ""
                    val matches = query.isEmpty() || title.contains(lowerQuery) || summary.contains(lowerQuery)
                    preference.isVisible = matches
                    if (matches) {
                        hasVisibleChild = true
                        if (firstMatchIndex < 0) {
                            firstMatchIndex = currentIndex
                        }
                    }
                    currentIndex++
                }
                category.isVisible = hasVisibleChild || query.isEmpty()
                if (category.isVisible) {
                    currentIndex++
                }
            } else {
                val title = category.title?.toString()?.lowercase() ?: ""
                val summary = category.summary?.toString()?.lowercase() ?: ""
                val matches = query.isEmpty() || title.contains(lowerQuery) || summary.contains(lowerQuery)
                category.isVisible = matches
                if (matches && firstMatchIndex < 0) {
                    firstMatchIndex = currentIndex
                }
                currentIndex++
            }
        }

        return firstMatchIndex
    }

    /**
     * 执行过滤并自动滚动到第一个匹配项。
     *
     * @param preferenceScreen 目标 Preference 树
     * @param listView 用于滚动的 RecyclerView
     * @param query 搜索关键词
     */
    fun filterAndScroll(
        preferenceScreen: PreferenceScreen,
        listView: RecyclerView,
        query: String
    ) {
        val firstMatchIndex = filter(preferenceScreen, query)
        if (firstMatchIndex >= 0) {
            listView.post {
                listView.smoothScrollToPosition(firstMatchIndex)
            }
        }
    }
}