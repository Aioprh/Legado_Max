package io.legado.app.help.http

import io.legado.app.utils.toastOnUi
import okhttp3.Interceptor
import okhttp3.Response
import splitties.init.appCtx
import java.io.IOException
import javax.net.ssl.SSLPeerUnverifiedException

object OkHttpExceptionInterceptor : Interceptor {

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        try {
            return chain.proceed(chain.request())
        } catch (e: IOException) {
            throw e.withSslPeerHint()
        } catch (e: Throwable) {
            throw IOException(e)
        }
    }

    private fun IOException.withSslPeerHint(): IOException {
        if (this !is SSLPeerUnverifiedException) {
            return this
        }
        if (message?.startsWith(SSL_PEER_HINT) == true) {
            return this
        }
        appCtx.toastOnUi("请仔细看报错并开启相应功能")
        return SSLPeerUnverifiedException("$SSL_PEER_HINT\n$message").also {
            it.initCause(this)
            it.stackTrace = stackTrace
        }
    }

    private const val SSL_PEER_HINT =
        "SSL证书主机名校验失败: HTTPS会用URL里的主机名/IP校验证书, 当前主机与证书签发域名不一致,只有Max版添加了这个验证; 解决方案: 到其他设置里打开\"跳过SSL验证\"开关"

}
