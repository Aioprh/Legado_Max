package io.legado.app.ui.debuglog

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.postDelayed
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import io.legado.app.constant.AppLog

object DebugLogPanelDialog {
    
    private var dialogView: View? = null
    private var isShowing = false
    private var currentActivity: Activity? = null
    
    fun show(activity: Activity) {
        if (isShowing) {
            AppLog.put("DebugLogPanelDialog: show() called but already showing")
            return
        }
        
        if (activity.isFinishing || activity.isDestroyed) {
            AppLog.put("DebugLogPanelDialog: show() called but activity is finishing or destroyed")
            return
        }
        
        currentActivity = activity
        val rootView = activity.window.decorView as? ViewGroup
        if (rootView == null) {
            AppLog.put("DebugLogPanelDialog: show() failed - rootView is null")
            return
        }
        
        try {
            val composeView = createComposeView(activity)
            composeView.id = View.generateViewId()
            
            val layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            
            rootView.addView(composeView, layoutParams)
            dialogView = composeView
            isShowing = true
            AppLog.put("DebugLogPanelDialog: show() success")
            
        } catch (e: Exception) {
            AppLog.put("DebugLogPanelDialog: show() exception - ${e.message}", e)
            dialogView = null
            isShowing = false
            currentActivity = null
        }
    }
    
    fun dismiss() {
        if (!isShowing) {
            return
        }
        
        val activity = currentActivity
        dialogView?.let { view ->
            view.postDelayed(50) {
                try {
                    val parent = view.parent as? ViewGroup
                    parent?.removeView(view)
                    AppLog.put("DebugLogPanelDialog: dismiss() success")
                } catch (e: Exception) {
                    AppLog.put("DebugLogPanelDialog: dismiss() exception - ${e.message}", e)
                }
            }
        }
        
        dialogView = null
        isShowing = false
        currentActivity = null
        
        activity?.let {
            if (!it.isFinishing && !it.isDestroyed) {
                DebugFloatingBallManager.onPanelDismissed(it)
            }
        }
    }
    
    fun onActivityDestroyed(activity: Activity) {
        if (currentActivity == activity) {
            // 强制清理，不依赖 isShowing 状态
            dialogView?.let { view ->
                try {
                    val parent = view.parent as? ViewGroup
                    parent?.removeView(view)
                } catch (_: Exception) {
                }
            }
            dialogView = null
            isShowing = false
            currentActivity = null
        }
    }
    
    private fun createComposeView(activity: Activity): ComposeView {
        return ComposeView(activity).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                MaterialTheme {
                    CompositionLocalProvider(
                        LocalViewModelStoreOwner provides activity as androidx.lifecycle.ViewModelStoreOwner
                    ) {
                        DebugLogPanelContent(
                            onDismiss = { dismiss() }
                        )
                    }
                }
            }
        }
    }
    
    @Composable
    private fun DebugLogPanelContent(onDismiss: () -> Unit) {
        val focusManager = LocalFocusManager.current
        
        // 拦截系统返回键，关闭调试日志面板
        // 因为 ComposeView 直接添加到 decorView，不会自动处理返回事件
        BackHandler(enabled = true) {
            onDismiss()
        }
        
        // 进入面板时清除焦点，避免 EditText 等组件持有焦点
        DisposableEffect(Unit) {
            focusManager.clearFocus()
            onDispose {
                focusManager.clearFocus()
            }
        }
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            DebugLogScreen(
                onDismiss = onDismiss,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
