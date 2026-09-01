package io.legado.app.ui.widget

import android.animation.ObjectAnimator
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils as AndroidXColorUtils
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.databinding.ViewAudioPlayMiniBarBinding
import io.legado.app.help.glide.ImageLoader
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.model.AudioPlay
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

/** 音频书音乐风格迷你播放栏：轻薄、半透明、液态玻璃质感。播放或暂停时保留。 */
class AudioPlayMiniBarController(
    private val activity: AppCompatActivity,
    private val parent: ViewGroup
) {
    private val binding = ViewAudioPlayMiniBarBinding.inflate(LayoutInflater.from(activity), parent, false)
    private var coverJob: Job? = null
    private var coverAnimator: ObjectAnimator? = null
    private var lastBookUrl: String? = null
    private var initialized = false
    private var bottomNavigation: View? = null
    private var bottomNavigationLayoutListener: View.OnLayoutChangeListener? = null

    init {
        parent.addView(binding.root)
        bindBottomNavigationAnchor()
        updateBottomMargin()
        bindEvents()
    }

    fun refresh() {
        binding.run {
            if (isExcludedScreen() || !AudioPlayService.isRun || AudioPlay.book == null || AudioPlay.status == io.legado.app.constant.Status.STOP) {
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
            ivAudioMiniPlay.setImageResource(
                if (AudioPlay.status == io.legado.app.constant.Status.PLAY && !AudioPlayService.pause) {
                    R.drawable.ic_pause_24dp
                } else {
                    R.drawable.ic_play_24dp
                }
            )
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
                val cover = book?.let { io.legado.app.model.BookCover.getDisplayCover(it) }
                if (cover != null) {
                    ImageLoader.load(activity, cover).circleCrop().into(ivAudioMiniCover)
                    coverJob = activity.lifecycleScope.launch(IO) {
                        val bitmap = runCatching { ImageLoader.loadBitmap(activity, cover).submit().get() }.getOrNull()
                        bitmap?.let { withContext(Main) { applyTheme(extractDominantColor(it)) } }
                    }
                }
            }
            if (AudioPlay.status == io.legado.app.constant.Status.PLAY && !AudioPlayService.pause) {
                startCoverAnimation()
            } else {
                coverAnimator?.pause()
            }
        }
    }

    fun hide() = hideInternal()

    private fun bindEvents() {
        binding.run {
            audioPlayMiniBar.setOnClickListener { openAudioPlayer() }
            ivAudioMiniPlay.setOnClickListener {
                if (AudioPlayService.pause) {
                    AudioPlay.resume(activity)
                } else {
                    AudioPlay.pause(activity)
                }
                audioPlayMiniBar.post { refresh() }
            }
            ivAudioMiniPlaylist.setOnClickListener { openChapterList() }
        }
    }

    private fun openAudioPlayer() = activity.startActivity<AudioPlayActivity>()

    private fun openChapterList() {
        activity.startActivity<AudioPlayActivity> {
            putExtra(AudioPlayActivity.EXTRA_OPEN_CHAPTER_LIST, true)
        }
    }

    private fun hideInternal() {
        coverJob?.cancel()
        coverAnimator?.cancel()
        coverAnimator = null
        binding.audioPlayMiniBar.animate()
            .alpha(0f)
            .translationY(12.dpToPx().toFloat())
            .setDuration(140L)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                binding.audioPlayMiniBar.invisible()
                binding.audioPlayMiniBar.alpha = 1f
                binding.audioPlayMiniBar.translationY = 0f
            }
            .start()
    }

    private fun isExcludedScreen(): Boolean = when (activity.javaClass.simpleName) {
        "ReadBookActivity", "AudioPlayActivity" -> true
        else -> false
    }

    private fun bindBottomNavigationAnchor() {
        val navigation = activity.findViewById<View>(R.id.bottom_navigation_glass) ?: return
        bottomNavigation = navigation
        bottomNavigationLayoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateBottomMargin()
        }
        navigation.addOnLayoutChangeListener(bottomNavigationLayoutListener)
        parent.addOnLayoutChangeListener { _, _, _, _, _, _, _, _ -> updateBottomMargin() }
    }

    private fun updateBottomMargin() {
        val navigation = bottomNavigation?.takeIf { it.isShown && it.height > 0 }
        val margin = if (navigation != null) {
            val parentLocation = IntArray(2)
            val navigationLocation = IntArray(2)
            parent.getLocationOnScreen(parentLocation)
            navigation.getLocationOnScreen(navigationLocation)
            (parent.height - (navigationLocation[1] - parentLocation[1])).coerceAtLeast(0)
        } else {
            2.dpToPx()
        }
        binding.root.updateLayoutParams<android.widget.FrameLayout.LayoutParams> {
            bottomMargin = margin
        }
    }

    private fun applyTheme(color: Int = activity.bottomBackground) {
        binding.run {
            val lightMode = ColorUtils.isColorLight(color)
            val tint = if (lightMode) 0xFFFFFFFF.toInt() else 0xFFEEF4FF.toInt()
            val glassBase = if (lightMode) {
                AndroidXColorUtils.setAlphaComponent(0xFFFFFFFF.toInt(), 190)
            } else {
                AndroidXColorUtils.setAlphaComponent(0xFF15171D.toInt(), 178)
            }
            val highlight = AndroidXColorUtils.setAlphaComponent(
                AndroidXColorUtils.blendARGB(color, tint, 0.45f),
                if (lightMode) 72 else 58
            )
            val glassStart = AndroidXColorUtils.blendARGB(glassBase, highlight, 0.38f)
            val glassEnd = AndroidXColorUtils.blendARGB(glassBase, color, 0.16f)
            val textColor = if (lightMode) 0xFF181A20.toInt() else 0xFFF7F9FF.toInt()
            val secondaryColor = AndroidXColorUtils.setAlphaComponent(textColor, 145)
            val borderColor = AndroidXColorUtils.setAlphaComponent(Color.WHITE, if (lightMode) 150 else 105)
            val glowColor = AndroidXColorUtils.setAlphaComponent(
                AndroidXColorUtils.blendARGB(color, tint, 0.65f),
                75
            )
            audioPlayMiniBar.background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(glassStart, glassBase, glassEnd)
            ).apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 27.dpToPx().toFloat()
                setStroke(1.dpToPx(), borderColor)
            }
            audioMiniCoverShell.background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(glowColor, AndroidXColorUtils.setAlphaComponent(textColor, 18))
            ).apply { shape = GradientDrawable.OVAL }
            tvAudioMiniTitle.setTextColor(textColor)
            tvAudioMiniSubtitle.setTextColor(secondaryColor)
            ivAudioMiniPlay.setColorFilter(textColor)
            ivAudioMiniPlaylist.setColorFilter(textColor)
            audioPlayMiniBar.elevation = 20.dpToPx().toFloat()
        }
    }

    private fun startCoverAnimation() {
        val animator = coverAnimator ?: ObjectAnimator.ofFloat(binding.ivAudioMiniCover, View.ROTATION, 0f, 360f).apply {
            duration = 12000L
            repeatCount = ObjectAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
            coverAnimator = this
        }
        if (!animator.isStarted) animator.start() else if (animator.isPaused) animator.resume()
    }

    private fun extractDominantColor(bitmap: Bitmap): Int {
        val stepX = (bitmap.width / 16).coerceAtLeast(1)
        val stepY = (bitmap.height / 16).coerceAtLeast(1)
        var red = 0L
        var green = 0L
        var blue = 0L
        var count = 0L
        for (x in 0 until bitmap.width step stepX) for (y in 0 until bitmap.height step stepY) {
            val pixel = bitmap.getPixel(x, y)
            red += Color.red(pixel)
            green += Color.green(pixel)
            blue += Color.blue(pixel)
            count++
        }
        if (count == 0L) return activity.bottomBackground
        return Color.rgb((red / count).toInt(), (green / count).toInt(), (blue / count).toInt())
    }
}
