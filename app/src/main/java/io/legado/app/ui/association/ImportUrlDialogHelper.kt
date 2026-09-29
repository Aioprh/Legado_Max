package io.legado.app.ui.association

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.databinding.DialogImportUrlBinding
import io.legado.app.help.http.okHttpClient
import io.legado.app.utils.dpToPx
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * 网络导入 URL 对话框辅助类。
 *
 * 历史记录使用 AutoCompleteTextView 展示。这里刻意限制下拉高度，
 * 避免历史记录过多时把整个 AlertDialog 覆盖掉，尤其是在软键盘弹出时。
 */
object ImportUrlDialogHelper {

    /** 下拉历史最多展示的条数，完整历史仍由调用方负责持久化。 */
    private const val MAX_VISIBLE_HISTORY = 6

    fun createBinding(
        layoutInflater: LayoutInflater,
        context: Context,
        lifecycleOwner: LifecycleOwner,
        cacheUrls: MutableList<String>,
        onUrlsChanged: (List<String>) -> Unit,
        openBrowser: (String) -> Unit
    ): DialogImportUrlBinding {
        var checkJob: Job? = null

        return DialogImportUrlBinding.inflate(layoutInflater).apply {
            editView.hint = "URL"
            editView.setFilterValues(
                cacheUrls
                    .asSequence()
                    .filter { it.isAbsUrl() }
                    .distinct()
                    .take(MAX_VISIBLE_HISTORY)
                    .toList()
            )

            // 关键：限制历史下拉框高度，避免长历史把对话框和键盘区域完全盖住。
            editView.dropDownHeight = 6 * 48.dpToPx()
            editView.dropDownVerticalOffset = 4.dpToPx()
            editView.dropDownHorizontalOffset = 0

            editView.delCallBack = { value ->
                cacheUrls.removeAll { it == value }
                onUrlsChanged(cacheUrls)
            }

            ivSignal.setImageResource(R.drawable.ic_signal)

            ibOpenBrowser.setOnClickListener {
                val text = editView.text?.toString()?.trim()
                when {
                    text.isNullOrEmpty() -> {
                        context.toastOnUi(R.string.please_input_url)
                    }

                    !text.isAbsUrl() -> {
                        context.toastOnUi(R.string.url_format_error)
                    }

                    else -> {
                        openBrowser(text)
                    }
                }
            }

            editView.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) = Unit

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) = Unit

                override fun afterTextChanged(s: Editable?) {
                    val text = s?.toString()?.trim()
                    if (text.isNullOrEmpty() || !text.isAbsUrl()) {
                        checkJob?.cancel()
                        ivSignal.visibility = View.GONE
                        return
                    }

                    ivSignal.visibility = View.VISIBLE
                    ivSignal.setImageResource(R.drawable.ic_signal)
                    checkJob?.cancel()
                    checkJob = lifecycleOwner.lifecycleScope.launch {
                        // 避免输入 URL 时每次按键都触发网络请求。
                        delay(650)
                        val isConnected = checkUrlConnection(text)
                        ivSignal.setImageResource(
                            if (isConnected) {
                                R.drawable.ic_signal_green
                            } else {
                                R.drawable.ic_signal_red
                            }
                        )
                    }
                }
            })
        }
    }

    /**
     * 检测 URL 是否能建立 HTTP 连接。
     *
     * HEAD 对部分站点并不被支持，因此 405 也视为“服务器可达”；
     * 这里的图标只表示连接状态，不把它当作“书源 JSON 一定有效”的判断。
     */
    private suspend fun checkUrlConnection(url: String): Boolean {
        return withContext(IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .head()
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    response.isSuccessful || response.code == 405
                }
            } catch (e: Exception) {
                AppLog.put("URL连接检测失败: $url", e, dialogName = "URL导入")
                false
            }
        }
    }
}
