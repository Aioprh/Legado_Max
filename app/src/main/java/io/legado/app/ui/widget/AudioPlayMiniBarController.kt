package io.legado.app.ui.widget

import android.animation.ObjectAnimator
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils as AndroidXColorUtils
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.constant.Status
import io.legado.app.databinding.ViewAudioPlayMiniBarBinding
import io.legado.app.help.glide.ImageLoader
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.model.AudioPlay
import io.legado.app.model.BookCover
import io.legado.app.service.AudioPlayService
import io.legado.app.ui.book.audio.AudioPlayActivity
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.dpToPx
import io.legado.app.utils.invisible
import io.legado.app.utils.startActivity
import io.legado.app.utils.visible
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 音频书音乐风格迷你播放栏。
 *
 * 只在真正播放状态显示；暂停、停止、阅读界面和完整音频播放页均不显示，
 * 避免遮挡正文和造成重复播放器。
 */
class AudioPlayMiniBarController(
    private val activity: AppCompatActivity,
    parent: ViewGroup
) {
    private val binding = ViewAudioPlayMiniBarBinding.inflate(
        LayoutInflater.from(activity),
        parent,
        false
    )

    private var coverJob: Job? = null
    private var coverAnimator: ObjectAnimator? = null
    private var lastBookUrl: String? = null
    private var initialized = false

    init {
        parent.addView(binding.root)
        updateBottomMargin()
        bindEvents()
    }

    fun refresh() {
        binding.run {
            if (isExcludedScreen() || !AudioPlayService.isRun || AudioPlay.status != Status.PLAY) {
                hideInternal()
                return
            }

            updateBottomMargin()
            val book = AudioPlay.book
            val chapter = AudioPlay.durChapter
            val bookUrl = book?.bookUrl

            tvAudioMiniTitle.text = chapter?.title?.takeIf { it.isNotBlank() }
                ?: book?.durChapterTitle?.takeIf { it.isNotBlank() }
                ?: book?.name
                ?: "正在播放"
            tvAudioMiniSubtitle.text = listOfNotNull(
                book?.name?.takeIf { it.isNotBlank() },
                book?.author?.takeIf { it.isNotBlank() }
            ).joinToString(" · ")

            ivAudioMiniPlay.setImageResource(R.drawable.ic_pause_24dp)
            audioPlayMiniBar.visible()

            if (lastBookUrl != bookUrl) {
                lastBookUrl = bookUrl
                initialized = false
                coverJob?.cancel()
                coverAnimator?.cancel()
                coverAnimator = null
                ivAudioMiniCover.rotation = 0f
            }

            if (!initialized) {
                initialized = true
                applyTheme()
                val cover = book?.let { BookCover.getDisplayCover(it) }
                if (cover != null) {
                    ImageLoader.load(activity, cover)
                        .circleCrop()
                        .into(ivAudioMiniCover)
                    coverJob = activity.lifecycleScope.launch(IO) {
                        val bitmap = runCatching {
                            ImageLoader.loadBitmap(activity, cover).submit().get()
                        }.getOrNull()
                        bitmap?.let {
                            withContext(Main) { applyTheme(extractDominantColor(it)) }
                        }
                    }
                }
            }
            startCoverAnimation()
        }
    }

    fun hide() {
        hideInternal()
    }

    private fun bindEvents() {
        binding.run {
            audioPlayMiniBar.setOnClickListener { openAudioPlayer() }
            ivAudioMiniPlay.setOnClickListener {
                if (AudioPlayService.pause) {
                    AudioPlay.resume(activity)
                } else {
                    AudioPlay.pause(activity)
                }
            }
            ivAudioMiniPlaylist.setOnClickListener { openAudioPlayer() }
        }
    }

    private fun openAudioPlayer() {
        activity.startActivity<AudioPlayActivity>()
    }

    private fun hideInternal() {
        binding.audioPlayMiniBar.invisible()
        coverAnimator?.cancel()
        coverAnimator = null
    }

    private fun isExcludedScreen(): Boolean {
        return when (activity.javaClass.simpleName) {
            "ReadBookActivity", "AudioPlayActivity" -> true
            else -> false
        }
    }

    private fun updateBottomMargin() {
        binding.root.updateLayoutParams<android.widget.FrameLayout.LayoutParams> {
            bottomMargin = 76.dpToPx()
        }
    }

    private fun applyTheme(color: Int = ThemeStore.bottomBackground(activity)) {
        binding.run {
            val base = AndroidXColorUtils.blendARGB(color, 0xFFFFFFFF.toInt(), 0.12f)
            val surface = if (ColorUtils.isColorLight(base)) {
                AndroidXColorUtils.setAlphaComponent(0xFFFFFFFF.toInt(), 246)
            } else {
                AndroidXColorUtils.setAlphaComponent(0xFF18181B.toInt(), 246)
            }
            val textColor = if (ColorUtils.isColorLight(surface)) 0xFF22232A.toInt() else 0xFFF5F5F5.toInt()
            val secondaryColor = AndroidXColorUtils.setAlphaComponent(textColor, 150)

            audioPlayMiniBar.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 30.dpToPx().toFloat()
                setColor(surface)
                setStroke(1.dpToPx(), AndroidXColorUtils.setAlphaComponent(textColor, 22))
            }
            audioMiniCoverShell.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(AndroidXColorUtils.setAlphaComponent(textColor, 12))
            }
            tvAudioMiniTitle.setTextColor(textColor)
            tvAudioMiniSubtitle.setTextColor(secondaryColor)
            ivAudioMiniPlay.setColorFilter(textColor)
            ivAudioMiniPlaylist.setColorFilter(textColor)
        }
    }

    private fun startCoverAnimation() {
        val animator = coverAnimator ?: ObjectAnimator.ofFloat(
            binding.ivAudioMiniCover,
            View.ROTATION,
            0f,
            360f
        ).apply {
            duration = 12000L
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            coverAnimator = this
        }
        if (!animator.isStarted) animator.start()
        else if (animator.isPaused) animator.resume()
    }

    private fun extractDominantColor(bitmap: Bitmap): Int {
        val stepX = (bitmap.width / 16).coerceAtLeast(1)
        val stepY = (bitmap.height / 16).coerceAtLeast(1)
        var red = 0L
        var green = 0L
        var blue = 0L
        var count = 0L
        for (x in 0 until bitmap.width step stepX) {
            for (y in 0 until bitmap.height step stepY) {
                val pixel = bitmap.getPixel(x, y)
                red += Color.red(pixel)
                green += Color.green(pixel)
                blue += Color.blue(pixel)
                count++
            }
        }
        if (count == 0L) return ThemeStore.bottomBackground(activity)
        return Color.rgb(
            (red / count).toInt(),
            (green / count).toInt(),
            (blue / count).toInt()
        )
    }
}
