package io.legado.app.ui.debuglog

import android.app.Activity
import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.core.view.postDelayed
import io.legado.app.constant.AppLog
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.debuglog.components.DebugFloatingBall

object DebugFloatingBallManager {
    private var isShowing = false
    private var isAttaching = false
    private var currentActivity: Activity? = null
    private var floatingBallView: ComposeView? = null
    private var showToken: Int = 0
    
    fun updateFloatingBallState(enabled: Boolean) {
        if (enabled) {
            currentActivity?.let { activity ->
                if (!activity.isFinishing && !activity.isDestroyed) {
                    show(activity)
                }
            }
        } else {
            hide()
        }
    }
    
    fun show(activity: Activity) {
        if (!AppConfig.debugLogFloatingBall) {
            return
        }
        
        if (isShowing || isAttaching) {
            AppLog.put("DebugFloatingBall: show() called but already showing or attaching")
            return
        }
        
        if (activity.isFinishing || activity.isDestroyed) {
            AppLog.put("DebugFloatingBall: show() called but activity is finishing or destroyed")
            return
        }
        
        currentActivity = activity
        isAttaching = true
        val currentToken = ++showToken
        
        val rootView = activity.window.decorView as? ViewGroup
        if (rootView == null) {
            AppLog.put("DebugFloatingBall: show() failed - rootView is null")
            isAttaching = false
            return
        }
        
        try {
            val composeView = createComposeView(activity)
            floatingBallView = composeView
            
            rootView.post {
                if (!validateShowToken(currentToken, activity)) {
                    AppLog.put("DebugFloatingBall: show() cancelled - token invalid or activity state changed")
                    isAttaching = false
                    floatingBallView = null
                    return@post
                }
                
                if (composeView.parent != null) {
                    AppLog.put("DebugFloatingBall: show() cancelled - view already has parent")
                    isAttaching = false
                    floatingBallView = null
                    return@post
                }
                
                val layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.BOTTOM or Gravity.END
                    bottomMargin = 100.dpToPx(activity)
                    marginEnd = 16.dpToPx(activity)
                }
                
                try {
                    rootView.addView(composeView, layoutParams)
                    isShowing = true
                    isAttaching = false
                    AppLog.put("DebugFloatingBall: show() success")
                } catch (e: Exception) {
                    AppLog.put("DebugFloatingBall: show() failed to add view - ${e.message}", e)
                    isAttaching = false
                    floatingBallView = null
                }
            }
            
        } catch (e: Exception) {
            AppLog.put("DebugFloatingBall: show() exception - ${e.message}", e)
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
        
        floatingBallView?.let { view ->
            view.postDelayed(50) {
                try {
                    val parent = view.parent as? ViewGroup
                    parent?.removeView(view)
                    AppLog.put("DebugFloatingBall: hide() success")
                } catch (e: Exception) {
                    AppLog.put("DebugFloatingBall: hide() exception - ${e.message}", e)
                }
            }
        }
        
        floatingBallView = null
        isShowing = false
    }
    
    fun onActivityResumed(activity: Activity) {
        if (AppConfig.debugLogFloatingBall && !isShowing && !isAttaching) {
            show(activity)
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
            hide()
            currentActivity = null
        }
    }
    
    fun onPanelDismissed(activity: Activity) {
        if (AppConfig.debugLogFloatingBall && currentActivity == activity) {
            if (!activity.isFinishing && !activity.isDestroyed) {
                show(activity)
            }
        }
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
                MaterialTheme {
                    DebugFloatingBallContent()
                }
            }
        }
    }
    
    @Composable
    private fun DebugFloatingBallContent() {
        Box(
            modifier = Modifier.padding(8.dp)
        ) {
            DebugFloatingBall(
                onClick = {
                    currentActivity?.let { activity ->
                        if (!activity.isFinishing && !activity.isDestroyed) {
                            hide()
                            activity.window.decorView.postDelayed(200) {
                                if (!activity.isFinishing && !activity.isDestroyed) {
                                    DebugLogPanelDialog.show(activity)
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
