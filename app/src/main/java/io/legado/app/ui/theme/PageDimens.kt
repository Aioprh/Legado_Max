package io.legado.app.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 精准管理批次页面共用的尺寸常量（theme-styles.md §7.2）
 * Dimensions.kt 目标态文件正式建立前的过渡方案：就近定义、禁止继续散落硬编码，
 * Dimensions.kt 建立后再统一迁移
 */
object PageDimens {
    /** 列表页四周内边距 */
    val screenPadding: Dp = 16.dp

    /** 列表卡片之间的间距 */
    val cardSpacing: Dp = 12.dp
}
