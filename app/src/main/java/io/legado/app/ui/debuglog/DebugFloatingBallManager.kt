package io.legado.app.ui.debuglog

import android.app.Activity
import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.core.view.postDelayed
import io.legado.app.constant.AppLog
import io.legado.app.constant.PreferKey
import io.legado.app.data.repository.debug.DebugEventCenter
import io.legado.app.data.repository.debug.FlowLogRecorder
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.debuglog.components.DebugFloatingBall
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.utils.removePref
import splitties.init.appCtx

object DebugFloatingBallManager {
    private var isShowing = false
    private var isAttaching = false
    private var currentActivity: Activity? = null
    private var floatingBallView: ComposeView? = null
    private var showToken: Int = 0
    private var hasLoggedShow = false
    private var hasLoggedHide = false
    
    fun updateFloatingBallState(enabled: Boolean) {
        if (enabled) {
            // 重置日志标记，每次开启功能时允许记录一次日志
            hasLoggedShow = false
            hasLoggedHide = false
            DebugLogPanelDialog.resetLogFlags()
            currentActivity?.let { activity ->
                if (!activity.isFinishing && !activity.isDestroyed) {
                    show(activity)
                }
            }
        } else {
            hide()
            FlowLogRecorder.clear()
            DebugEventCenter.clearSync()
        }
    }
    
    fun show(activity: Activity) {
        if (!AppConfig.debugLogFloatingBall) {
            return
        }
        
        if (isShowing || isAttaching) {
            if (!hasLoggedShow) {
                AppLog.putReaderDebug("DebugFloatingBall: show() called but already showing or attaching")
                hasLoggedShow = true
            }
            return
        }
        
        if (activity.isFinishing || activity.isDestroyed) {
            if (!hasLoggedShow) {
                AppLog.putReaderDebug("DebugFloatingBall: show() called but activity is finishing or destroyed")
                hasLoggedShow = true
            }
            return
        }
        
        currentActivity = activity
        isAttaching = true
        val currentToken = ++showToken
        
        val rootView = activity.window.decorView as? ViewGroup
        if (rootView == null) {
            if (!hasLoggedShow) {
                AppLog.putReaderDebug("DebugFloatingBall: show() failed - rootView is null")
                hasLoggedShow = true
            }
            isAttaching = false
            return
        }
        
        try {
            val composeView = createComposeView(activity)
            floatingBallView = composeView
            
            rootView.post {
                if (!validateShowToken(currentToken, activity)) {
                    if (!hasLoggedShow) {
                        AppLog.putReaderDebug("DebugFloatingBall: show() cancelled - token invalid or activity state changed")
                        hasLoggedShow = true
                    }
                    isAttaching = false
                    floatingBallView = null
                    return@post
                }
                
                if (composeView.parent != null) {
                    if (!hasLoggedShow) {
                        AppLog.putReaderDebug("DebugFloatingBall: show() cancelled - view already has parent")
                        hasLoggedShow = true
                    }
                    isAttaching = false
                    floatingBallView = null
                    return@post
                }
                
                val layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                }
                
                try {
                    rootView.addView(composeView, layoutParams)
                    isShowing = true
                    isAttaching = false
                } catch (e: Exception) {
                    if (!hasLoggedShow) {
                        AppLog.putReaderDebug("DebugFloatingBall: show() failed to add view - ${e.message}")
                        hasLoggedShow = true
                    }
                    isAttaching = false
                    floatingBallView = null
                }
            }
            
        } catch (e: Exception) {
            if (!hasLoggedShow) {
                AppLog.putReaderDebug("DebugFloatingBall: show() exception - ${e.message}")
                hasLoggedShow = true
            }
            isAttaching = false
            floatingBallView = null
        }
    }
    
    fun hide() {
        if (!isShowing && !isAttaching) {
            return
        }

        showToken++
        isAttaching = false

        // Activity 已在销毁流程中时，立即同步移除视图，避免 postDelayed
        // 在 Activity 被 destroyed 后无法执行或延迟执行导致 ComposeView 泄漏。
        // AccessibilityInteractionController.mTempArrayList 会持有已分离的 ComposeView，
        // 仅靠 postDelayed 移除可能因 Activity destroyed 而不执行。
        val activity = currentActivity
        val isActivityDestroyed = activity?.isDestroyed == true || activity?.isFinishing == true
        floatingBallView?.let { view ->
            if (isActivityDestroyed) {
                // Activity 正在销毁，同步移除
                try {
                    val parent = view.parent as? ViewGroup
                    parent?.removeView(view)
                } catch (_: Exception) {
                }
            } else {
                // Activity 仍然存活，使用 postDelayed 延迟移除以避免动画闪烁
                view.postDelayed(50) {
                    try {
                        val parent = view.parent as? ViewGroup
                        parent?.removeView(view)
                    } catch (e: Exception) {
                        if (!hasLoggedHide) {
                            AppLog.putReaderDebug("DebugFloatingBall: hide() exception - ${e.message}")
                            hasLoggedHide = true
                        }
                    }
                }
            }
        }

        floatingBallView = null
        isShowing = false
    }
    
    fun onActivityResumed(activity: Activity) {
        if (AppConfig.debugLogFloatingBall) {
            if (!isShowing && !isAttaching) {
                show(activity)
            }
        }
    }
    
    fun onActivityPaused(activity: Activity) {
        if (currentActivity == activity) {
            hide()
            currentActivity = null
        }
    }
    
    fun onActivityDestroyed(activity: Activity) {
        if (currentActivity == activity) {
            showToken++
            // Activity 正在销毁，hide() 内部会检测到 destroyed 状态并同步移除视图
            hide()
            currentActivity = null
        } else {
            // 即使 currentActivity 不匹配，也要检查 floatingBallView 是否引用了该 Activity
            floatingBallView?.let { view ->
                if (view.context === activity) {
                    try {
                        val parent = view.parent as? ViewGroup
                        parent?.removeView(view)
                    } catch (_: Exception) {
                    }
                    floatingBallView = null
                    isShowing = false
                    isAttaching = false
                    showToken++
                }
            }
        }
    }
    
    /**
     * 应用退出时调用，重置悬浮球位置
     * 只有完全退出应用后再进入时才会重置位置
     */
    fun onAppFinished() {
        resetSavedPosition()
    }
    
    fun onPanelDismissed(activity: Activity) {
        if (AppConfig.debugLogFloatingBall && currentActivity == activity) {
            if (!activity.isFinishing && !activity.isDestroyed) {
                show(activity)
            }
        }
    }

    private fun resetSavedPosition() {
        appCtx.removePref(PreferKey.debugFloatingBallPosX)
        appCtx.removePref(PreferKey.debugFloatingBallPosY)
    }
    
    private fun validateShowToken(token: Int, activity: Activity): Boolean {
        return token == showToken &&
               currentActivity == activity &&
               AppConfig.debugLogFloatingBall &&
               !activity.isFinishing &&
               !activity.isDestroyed
    }
    
    private fun createComposeView(context: Context): ComposeView {
        return ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                LegadoTheme {
                    DebugFloatingBallContent()
                }
            }
        }
    }
    
    @Composable
    private fun DebugFloatingBallContent() {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            DebugFloatingBall(
                onClick = {
                    currentActivity?.let { activity ->
                        if (!activity.isFinishing && !activity.isDestroyed) {
                            hide()
                            activity.window.decorView.postDelayed(200) {
                                try {
                                    if (!activity.isFinishing && !activity.isDestroyed) {
                                        DebugLogPanelDialog.show(activity)
                                    }
                                } catch (e: Exception) {
                                    AppLog.putReaderDebug("DebugFloatingBall: 打开调试日志面板失败 - ${e.message}")
                                    // 打开失败时恢复悬浮球显示
                                    if (!activity.isFinishing && !activity.isDestroyed) {
                                        show(activity)
                                    }
                                }
                            }
                        }
                    }
                }
            )
        }
    }
    
    private fun Int.dpToPx(context: Context): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }
}
