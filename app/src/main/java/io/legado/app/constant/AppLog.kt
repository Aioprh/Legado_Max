package io.legado.app.constant

import android.util.Log
import io.legado.app.BuildConfig
import io.legado.app.data.repository.debug.DebugEventCenter
import io.legado.app.help.config.AppConfig
import io.legado.app.model.debug.DebugCategory
import io.legado.app.model.debug.DebugEvent
import io.legado.app.model.debug.DebugLevel
import io.legado.app.utils.LogUtils
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import splitties.init.appCtx

object AppLog {

    private val mLogs = arrayListOf<Triple<Long, String, Throwable?>>()

    val logs get() = mLogs.toList()

    @Synchronized
    fun put(message: String?, throwable: Throwable? = null, toast: Boolean = false) {
        message ?: return
        if (toast) {
            appCtx.toastOnUi(message)
        }
        if (mLogs.size > 100) {
            mLogs.removeLastOrNull()
        }
        if (throwable == null) {
            LogUtils.d("AppLog", message)
        } else {
            LogUtils.d("AppLog", "$message\n${throwable.stackTraceToString()}")
        }
        mLogs.add(0, Triple(System.currentTimeMillis(), message, throwable))
        if (BuildConfig.DEBUG) {
            val stackTrace = Thread.currentThread().stackTrace
            Log.e(stackTrace[3].className, message, throwable)
        }

        // 新增：上报到调试事件中心（异步，不阻塞主流程）
        GlobalScope.launch(Dispatchers.IO) {
            DebugEventCenter.emit(
                DebugEvent(
                    level = if (throwable != null) DebugLevel.ERROR else DebugLevel.INFO,
                    category = DebugCategory.APP,
                    message = message,
                    detail = throwable?.stackTraceToString(),
                    throwable = throwable
                )
            )
        }
    }

    @Synchronized
    fun putNotSave(message: String?, throwable: Throwable? = null, toast: Boolean = false) {
        message ?: return
        if (toast) {
            appCtx.toastOnUi(message)
        }
        if (mLogs.size > 100) {
            mLogs.removeLastOrNull()
        }
        mLogs.add(0, Triple(System.currentTimeMillis(), message, throwable))
        if (BuildConfig.DEBUG) {
            val stackTrace = Thread.currentThread().stackTrace
            Log.e(stackTrace[3].className, message, throwable)
        }

        // 新增：上报到调试事件中心（异步）
        GlobalScope.launch(Dispatchers.IO) {
            DebugEventCenter.emit(
                DebugEvent(
                    level = if (throwable != null) DebugLevel.ERROR else DebugLevel.INFO,
                    category = DebugCategory.APP,
                    message = message,
                    detail = throwable?.stackTraceToString(),
                    throwable = throwable
                )
            )
        }
    }

    @Synchronized
    fun clear() {
        mLogs.clear()
    }

    fun putDebug(message: String?, throwable: Throwable? = null) {
        if (AppConfig.recordLog) {
            put(message, throwable)
        }

        // 新增：即使recordLog为false也上报调试级别日志到事件中心
        GlobalScope.launch(Dispatchers.IO) {
            DebugEventCenter.emit(
                DebugEvent(
                    level = DebugLevel.DEBUG,
                    category = DebugCategory.APP,
                    message = message ?: "",
                    detail = throwable?.stackTraceToString(),
                    throwable = throwable
                )
            )
        }
    }

}