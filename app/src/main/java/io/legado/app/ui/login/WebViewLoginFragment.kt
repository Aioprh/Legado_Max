package io.legado.app.ui.login

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.activityViewModels
import io.legado.app.R
import io.legado.app.base.BaseFragment
import io.legado.app.constant.AppConst
import io.legado.app.data.entities.BaseSource
import io.legado.app.databinding.FragmentWebViewLoginBinding
import io.legado.app.help.http.CookieStore
import io.legado.app.help.webView.PooledWebView
import io.legado.app.lib.theme.accentColor
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.gone
import io.legado.app.utils.longSnackbar
import io.legado.app.utils.openUrl
import io.legado.app.utils.snackbar
import io.legado.app.utils.viewbindingdelegate.viewBinding
import androidx.core.net.toUri
import io.legado.app.help.webView.WebViewPool

class WebViewLoginFragment : BaseFragment(R.layout.fragment_web_view_login) {

    private val binding by viewBinding(FragmentWebViewLoginBinding::bind)
    private val viewModel by activityViewModels<SourceLoginViewModel>()
    private var pooledWebView: PooledWebView? = null
    private var currentWebView: WebView? = null

    private var checking = false

    /**
     * 周期性把 WebView 当前 Cookie 同步到 CookieStore。
     * 部分站点登录是通过页面内 fetch/XHR 提交的（SPA），登录成功后不会触发页面跳转，
     * 仅靠 onPageStarted/onPageFinished 可能漏存 Cookie，这里兜底定时同步。
     */
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cookieSyncRunnable = object : Runnable {
        override fun run() {
            if (viewModel.source == null || currentWebView == null) return
            val cookie = CookieManager.getInstance().getCookie(viewModel.source?.getKey())
            if (!cookie.isNullOrBlank()) {
                CookieStore.setCookie(viewModel.source!!.getKey(), cookie)
            }
            mainHandler.postDelayed(this, COOKIE_SYNC_INTERVAL_MS)
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        setSupportToolbar(binding.titleBar.toolbar)
        viewModel.source?.let {
            binding.titleBar.title = getString(R.string.login_source, it.getTag())
            initWebView(it)
            mainHandler.postDelayed(cookieSyncRunnable, COOKIE_SYNC_INTERVAL_MS)
        }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu) {
        menuInflater.inflate(R.menu.source_webview_login, menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem) {
        when (item.itemId) {
            R.id.menu_ok -> {
                if (!checking) {
                    checking = true
                    binding.titleBar.snackbar(R.string.check_host_cookie)
                    viewModel.source?.let {
                        loadUrl(it)
                    }
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView(source: BaseSource) {
        val webView = WebViewPool.acquire(requireContext()).let {
            pooledWebView = it
            it.realWebView
        }
        webView.onResume()
        binding.webViewContainer.addView(webView)
        currentWebView = webView
        binding.progressBar.fontColor = accentColor
        webView.settings.apply {
            useWideViewPort = true
            loadWithOverviewMode = true
            viewModel.headerMap[AppConst.UA_NAME]?.let {
                userAgentString = it
            }
        }
        val cookieManager = CookieManager.getInstance()
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                val cookie = cookieManager.getCookie(url)
                CookieStore.setCookie(source.getKey(), cookie)
                super.onPageStarted(view, url, favicon)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                val cookie = cookieManager.getCookie(url)
                CookieStore.setCookie(source.getKey(), cookie)
                if (checking) {
                    activity?.finish()
                }
                super.onPageFinished(view, url)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                return shouldOverrideUrlLoading(request.url)
            }

            @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION", "KotlinRedundantDiagnosticSuppress")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                return shouldOverrideUrlLoading(url.toUri())
            }

            private fun shouldOverrideUrlLoading(url: Uri): Boolean {
                when (url.scheme) {
                    "http", "https" -> {
                        return false
                    }

                    else -> {
                        binding.root.longSnackbar(R.string.jump_to_another_app, R.string.confirm) {
                            context?.openUrl(url)
                        }
                        return true
                    }
                }
            }

            @SuppressLint("WebViewClientOnReceivedSslError")
            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                handler?.proceed()
            }
        }
        webView.webChromeClient = object : WebChromeClient() {

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                binding.progressBar.setDurProgress(newProgress)
                binding.progressBar.gone(newProgress == 100)
            }

        }
        loadUrl(source)
    }

    private fun loadUrl(source: BaseSource) {
        val loginUrl = source.loginUrl ?: return
        val absoluteUrl = NetworkUtils.getAbsoluteURL(source.getKey(), loginUrl)
        currentWebView?.loadUrl(absoluteUrl, viewModel.headerMap)
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacks(cookieSyncRunnable)
        pooledWebView?.let { WebViewPool.release(it) }
        pooledWebView = null
        currentWebView = null
    }

    companion object {
        /** Cookie 周期同步间隔（毫秒） */
        private const val COOKIE_SYNC_INTERVAL_MS = 2000L
    }

}
