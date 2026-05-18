package io.legado.app.ui.widget

import android.animation.ObjectAnimator
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import androidx.core.graphics.ColorUtils as AndroidXColorUtils
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.databinding.ViewReadAloudMiniBarBinding
import io.legado.app.help.glide.ImageLoader
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.service.BaseReadAloudService
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.dpToPx
import io.legado.app.utils.invisible
import io.legado.app.utils.visible
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReadAloudMiniBarController(
    private val activity: BaseActivity<*>,
    parent: ViewGroup
) {
    companion object {
        private var sharedTranslationX = 0f
        private var sharedTranslationY = 0f
    }

    private val binding = ViewReadAloudMiniBarBinding.inflate(
        LayoutInflater.from(activity),
        parent,
        false
    )
    private var miniCoverAnimator: ObjectAnimator? = null
    private var miniBarVisible = false
    private var miniBarThemeInitialized = false
    private var downX = 0f
    private var downY = 0f
    private var dragging = false

    init {
        parent.addView(binding.root)
        bindEvents()
        updateBottomMargin()
    }

    fun refresh() {
        binding.run {
        if (!activity.showReadAloudMiniBar() || !BaseReadAloudService.isRun) {
            readAloudMiniBar.invisible()
            stopAnimation(reset = true)
            miniBarVisible = false
            miniBarThemeInitialized = false
            return
        }
        updateBottomMargin()
        ivReadAloudMiniPlay.setImageResource(
            if (BaseReadAloudService.pause) R.drawable.ic_play_24dp else R.drawable.ic_pause_24dp
        )
        readAloudMiniBar.visible()
        if (!miniBarVisible) {
            miniBarVisible = true
            readAloudMiniBar.alpha = 0f
            readAloudMiniBar.translationX = sharedTranslationX
            readAloudMiniBar.translationY = sharedTranslationY + 12.dpToPx().toFloat()
            readAloudMiniBar.animate()
                .alpha(1f)
                .translationX(sharedTranslationX)
                .translationY(sharedTranslationY)
                .setDuration(180L)
                .start()
        } else {
            readAloudMiniBar.translationX = sharedTranslationX
            readAloudMiniBar.translationY = sharedTranslationY
        }
        if (BaseReadAloudService.pause) {
            pauseAnimation()
        } else {
            startAnimation()
        }
        if (miniBarThemeInitialized) return
        miniBarThemeInitialized = true
        applyTheme(activity.defaultReadAloudMiniBarColor())
        ReadBook.book?.let { book ->
            ImageLoader.load(activity, book.getDisplayCover())
                .circleCrop()
                .into(ivReadAloudMiniCover)
            activity.lifecycleScope.launch(IO) {
                val bitmap = runCatching {
                    ImageLoader.loadBitmap(activity, book.getDisplayCover()).submit().get()
                }.getOrNull()
                bitmap?.let {
                    val dominant = extractDominantColor(it)
                    withContext(Main) {
                        applyTheme(dominant)
                    }
                }
            }
        }
    }
    }

    fun onPause() {
        pauseAnimation()
    }

    fun hide() {
        binding.readAloudMiniBar.invisible()
    }

    private fun bindEvents() {
        binding.run {
        readAloudMiniBar.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    dragging = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (kotlin.math.abs(dx) > 10 || kotlin.math.abs(dy) > 10) {
                        dragging = true
                    }
                    if (dragging) {
                        v.translationX += dx
                        v.translationY += dy
                        sharedTranslationX = v.translationX
                        sharedTranslationY = v.translationY
                        downX = event.rawX
                        downY = event.rawY
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!dragging) {
                        activity.onReadAloudMiniBarClick()
                    }
                    true
                }

                else -> false
            }
        }
        readAloudMiniBar.setOnLongClickListener {
            activity.onReadAloudMiniBarLongClick()
        }
        ivReadAloudMiniPlay.setOnClickListener {
            if (BaseReadAloudService.pause) ReadAloud.resume(activity)
            else ReadAloud.pause(activity)
        }
        ivReadAloudMiniClose.setOnClickListener {
            ReadAloud.stop(activity)
            readAloudMiniBar.invisible()
        }
    }
    }

    private fun updateBottomMargin() {
        binding.root.updateLayoutParams<FrameLayout.LayoutParams> {
            bottomMargin = activity.readAloudMiniBarBottomMarginDp().dpToPx()
        }
    }

    private fun applyTheme(color: Int) = binding.run {
        val bubble = AndroidXColorUtils.blendARGB(color, 0xFF79658C.toInt(), 0.42f)
        val textColor =
            if (ColorUtils.isColorLight(bubble)) 0xFF1A1422.toInt() else 0xFFFFFFFF.toInt()
        val softColor = AndroidXColorUtils.setAlphaComponent(textColor, 72)
        readAloudMiniBar.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 30.dpToPx().toFloat()
            setColor(AndroidXColorUtils.setAlphaComponent(bubble, 236))
        }
        readAloudMiniCoverShell.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(AndroidXColorUtils.setAlphaComponent(0xFFFFFFFF.toInt(), 18))
            setStroke(
                1.dpToPx(),
                AndroidXColorUtils.setAlphaComponent(0xFFFFFFFF.toInt(), 58)
            )
        }
        ivReadAloudMiniPlay.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(AndroidXColorUtils.setAlphaComponent(0xFFFFFFFF.toInt(), 8))
            setStroke(
                2.dpToPx(),
                AndroidXColorUtils.setAlphaComponent(0xFFFFFFFF.toInt(), 84)
            )
        }
        ivReadAloudMiniClose.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(AndroidXColorUtils.setAlphaComponent(0xFFFFFFFF.toInt(), 6))
        }
        ivReadAloudMiniPlay.setColorFilter(textColor)
        ivReadAloudMiniClose.setColorFilter(softColor)
    }

    private fun startAnimation() {
        val animator = miniCoverAnimator ?: ObjectAnimator.ofFloat(
            binding.ivReadAloudMiniCover,
            View.ROTATION,
            0f,
            360f
        ).apply {
            duration = 9000L
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            miniCoverAnimator = this
        }
        if (animator.isPaused) animator.resume()
        if (!animator.isStarted) animator.start()
    }

    private fun pauseAnimation() {
        miniCoverAnimator?.takeIf { it.isRunning }?.pause()
    }

    private fun stopAnimation(reset: Boolean) {
        miniCoverAnimator?.cancel()
        miniCoverAnimator = null
        if (reset) binding.ivReadAloudMiniCover.rotation = 0f
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
        if (count == 0L) return activity.defaultReadAloudMiniBarColor()
        return Color.rgb((red / count).toInt(), (green / count).toInt(), (blue / count).toInt())
    }
}
