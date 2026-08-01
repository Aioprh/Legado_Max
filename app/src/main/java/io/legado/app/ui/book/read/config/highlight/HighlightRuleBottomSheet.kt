package io.legado.app.ui.book.read.config.highlight

import android.view.View
import com.google.android.material.bottomsheet.BottomSheetBehavior

/**
 * 使用系统原生 [BottomSheetBehavior] 实现底部弹窗的拖动、回弹和下滑关闭。
 *
 * 替代原先手写的 translationY + onTouch 方案，解决以下问题：
 * - 拖动时背景与内容错位（dialog 窗口背景残留）
 * - 原位置残留白色区域
 * - 从内容区域（RecyclerView / ScrollView）无法继续下滑
 *
 * 要求 sheetContainer 的父布局为 CoordinatorLayout，
 * 且 XML 中已通过 `app:layout_behavior` 声明了 BottomSheetBehavior。
 *
 * @param dragHandle 保留参数以兼容调用方，实际拖动由 Behavior 全局接管
 * @param sheetContainer 底部 sheet 容器（需有 BottomSheetBehavior）
 * @param onDismiss sheet 完全隐藏后的回调（通常调用 dismissAllowingStateLoss）
 */
internal fun attachBottomSheetDismiss(
    @Suppress("UNUSED_PARAMETER") dragHandle: View,
    sheetContainer: View,
    onDismiss: () -> Unit,
) {
    val behavior = BottomSheetBehavior.from(sheetContainer)
    behavior.isHideable = true          // 允许下滑到完全隐藏
    behavior.skipCollapsed = true       // 跳过半折叠态，直接从展开 → 隐藏
    behavior.state = BottomSheetBehavior.STATE_EXPANDED
    behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
        override fun onStateChanged(bottomSheet: View, newState: Int) {
            if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                onDismiss()
            }
        }

        override fun onSlide(bottomSheet: View, slideOffset: Float) {
            // 由 Behavior 自动处理位移和回弹，无需手动操作
        }
    })
}
