package io.legado.app.ui.video.player

/**
 * 视频自动下一集控制器。
 *
 * 只负责决定是否应该切换到下一集，不直接持有 Android Player，
 * 这样播放完成回调、选集 UI 和播放器生命周期可以分别处理。
 */
class VideoAutoNextController(
    private val enabled: () -> Boolean = { true }
) {
    private var switching = false

    /** 当前播放完成时是否允许触发自动下一集。 */
    fun shouldAutoNext(hasNext: Boolean): Boolean {
        if (!enabled() || !hasNext || switching) return false
        switching = true
        return true
    }

    /** 下一集切换完成或失败后调用，允许后续再次自动切换。 */
    fun finishSwitch() {
        switching = false
    }

    fun reset() {
        switching = false
    }

    val isSwitching: Boolean
        get() = switching
}
