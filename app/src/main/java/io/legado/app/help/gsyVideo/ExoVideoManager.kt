package io.legado.app.help.gsyVideo

import android.os.Message
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.shuyu.gsyvideoplayer.GSYVideoBaseManager
import io.legado.app.R

/**
基类管理器
 */
class ExoVideoManager: GSYVideoBaseManager() {
    companion object {
        val SMALL_ID: Int = R.id.small_id
        val FULLSCREEN_ID: Int = R.id.full_id
        var TAG: String = "GSYExoVideoManager"
    }

    init {
        super.init()
    }

    @OptIn(UnstableApi::class)
    override fun getPlayManager(): ExoPlayerManager {
        return ExoPlayerManager()
    }

    /**
     * 重写 setNeedMute 方法，使其立即应用静音状态
     * 解决静音播放开关关闭后，视频播放不能立刻达到静音/有声的效果的问题
     */
    @OptIn(UnstableApi::class)
    override fun setNeedMute(needMute: Boolean) {
        // 先调用父类方法设置状态
        super.setNeedMute(needMute)
        // 然后立即应用到播放器，确保实时生效
        playerManager?.setNeedMute(needMute)
    }

//    fun prepare(
//        url: String,
//        mapHeadData: MutableMap<String?, String?>?,
//        index: Int,
//        loop: Boolean,
//        speed: Float,
//        cache: Boolean,
//        cachePath: File?,
//        overrideExtension: String?
//    ) {
//        val msg = Message()
//        msg.what = HANDLER_PREPARE
//        msg.obj =GSYModel(url, mapHeadData, loop, speed, cache, cachePath, overrideExtension)
//        sendMessage(msg)
//    }
    /**
     * 上一集
     */
    @OptIn(UnstableApi::class)
    fun previous() {
        if (playerManager == null) {
            return
        }
        (playerManager as ExoPlayerManager).previous()
    }


    fun setDisplayNew(holder: Any?) {
        val msg = Message()
        msg.what = HANDLER_SETDISPLAY
        msg.obj = holder
        if (playerManager != null) {
            playerManager.showDisplay(msg)
        }
    }

    /**
     * 下一集
     */
    @OptIn(UnstableApi::class)
    fun next() {
        if (playerManager == null) {
            return
        }
        (playerManager as ExoPlayerManager).next()
    }

}