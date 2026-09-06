package io.legado.app.ui.widget

import android.animation.ObjectAnimator
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils as AndroidXColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.databinding.ViewAudioPlayMiniBarBinding
import io.legado.app.help.glide.ImageLoader
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.model.AudioPlay
import io.legado.app.service.AudioPlayService
import io.legado.app.ui.book.audio.AudioPlayActivity
import io.legado.app.utils.dpToPx
import io.legado.app.utils.invisible
import io.legado.app.utils.startActivity
import io.legado.app.utils.visible
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private var imeVisible = false
    private var globalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null
    private var contentContainer: ViewGroup? = null
    private var contentPaddingLeft = 0
    private var contentPaddingTop = 0
    private var contentPaddingRight = 0
    private var contentPaddingBottom = 0
    private val originalRecyclerPaddingBottom = java.util.WeakHashMap<RecyclerView, Int>()

    init {
        parent.addView(binding.root)
        bindContentSafeArea()
        bindBottomNavigationAnchor()
        bindImeVisibility()
        updateBottomMargin()
        bindEvents()
    }

    fun refresh() {
        binding.run {
            val hasAudio = AudioPlay.book != null && AudioPlay.status != io.legado.app.constant.Status.STOP
            if (isExcludedScreen() || !hasAudio || isImeVisible()) {
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
            val playing = AudioPlay.status == io.legado.app.constant.Status.PLAY && !AudioPlayService.pause
            ivAudioMiniPlay.setImageResource(if (playing) R.drawable.ic_pause_24dp else R.drawable.ic_play_24dp)
            audioPlayMiniBar.visible()
            updateContentSafeArea()
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
            if (playing) startCoverAnimation() else coverAnimator?.pause()
        }
    }

    fun hide() = hideInternal()

    private fun bindEvents() {
        binding.run {
            audioPlayMiniBar.setOnClickListener { openAudioPlayer() }
            ivAudioMiniPlay.setOnClickListener {
                if (AudioPlayService.pause) AudioPlay.resume(activity) else AudioPlay.pause(activity)
                audioPlayMiniBar.postDelayed({ refresh() }, 100L)
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
        if (!binding.audioPlayMiniBar.isShown) {
            updateContentSafeArea()
            return
        }
        binding.audioPlayMiniBar.animate().cancel()
        binding.audioPlayMiniBar.animate()
            .alpha(0f)
            .translationY(12.dpToPx().toFloat())
            .setDuration(140L)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                binding.audioPlayMiniBar.invisible()
                binding.audioPlayMiniBar.alpha = 1f
                binding.audioPlayMiniBar.translationY = 0f
                updateContentSafeArea()
            }
            .start()
    }

    private fun isExcludedScreen(): Boolean = when (activity.javaClass.simpleName) {
        "ReadBookActivity", "AudioPlayActivity", "SearchActivity", "BookInfoActivity" -> true
        else -> false
    }

    private fun bindContentSafeArea() {
        contentContainer = activity.findViewById<ViewGroup>(R.id.content_container)?.also { container ->
            contentPaddingLeft = container.paddingLeft
            contentPaddingTop = container.paddingTop
            contentPaddingRight = container.paddingRight
            contentPaddingBottom = container.paddingBottom
        }
    }

    /** 迷你播放栏覆盖在内容之上时，同时给内容容器和内部 RecyclerView 预留空间。 */
    private fun updateContentSafeArea() {
        val container = contentContainer ?: return
        if (!binding.audioPlayMiniBar.isShown || binding.audioPlayMiniBar.height <= 0) {
            container.setPadding(contentPaddingLeft, contentPaddingTop, contentPaddingRight, contentPaddingBottom)
            restoreRecyclerViewPadding(container)
            return
        }

        val containerLocation = IntArray(2)
        val miniLocation = IntArray(2)
        container.getLocationOnScreen(containerLocation)
        binding.audioPlayMiniBar.getLocationOnScreen(miniLocation)
        val miniTop = (miniLocation[1] - containerLocation[1]).coerceAtLeast(0)
        val safeBottom = (container.height - miniTop + 10.dpToPx()).coerceAtLeast(0)

        container.setPadding(contentPaddingLeft, contentPaddingTop, contentPaddingRight, contentPaddingBottom + safeBottom)
        applyRecyclerViewSafeArea(container, safeBottom)
    }

    private fun applyRecyclerViewSafeArea(view: View, safeBottom: Int) {
        if (view is RecyclerView) {
            val original = originalRecyclerPaddingBottom.getOrPut(view) { view.paddingBottom }
            view.clipToPadding = false
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, original + safeBottom)
            return
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                applyRecyclerViewSafeArea(view.getChildAt(index), safeBottom)
            }
        }
    }

    private fun restoreRecyclerViewPadding(view: View) {
        if (view is RecyclerView) {
            originalRecyclerPaddingBottom.remove(view)?.let { original ->
                view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, original)
            }
            return
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                restoreRecyclerViewPadding(view.getChildAt(index))
            }
        }
    }

    private fun bindImeVisibility() {
        globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            val visible = isImeVisible()
            if (visible != imeVisible) {
                imeVisible = visible
                if (visible) hideInternal() else refresh()
            }
        }
        parent.viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)
    }

    private fun isImeVisible(): Boolean {
        return ViewCompat.getRootWindowInsets(activity.window.decorView)
            ?.isVisible(WindowInsetsCompat.Type.ime()) == true
    }

    private fun updateBottomMargin() {
        val navigation = bottomNavigation?.takeIf { it.isShown && it.height > 0 }
        val margin = when {
            navigation != null -> {
                val parentLocation = IntArray(2)
                val navigationLocation = IntArray(2)
                parent.getLocationOnScreen(parentLocation)
                navigation.getLocationOnScreen(navigationLocation)
                val navigationTop = navigationLocation[1] - parentLocation[1]
                (parent.height - navigationTop + 8.dpToPx()).coerceAtLeast(8.dpToPx())
            }
            activity.javaClass.simpleName == "TocActivity" -> 62.dpToPx()
            else -> 18.dpToPx()
        }
        binding.root.updateLayoutParams<android.widget.FrameLayout.LayoutParams> { bottomMargin = margin }
        binding.root.post { updateContentSafeArea() }
    }

    private fun bindBottomNavigationAnchor() {
        val navigation = activity.findViewById<View>(R.id.bottom_navigation_glass) ?: return
        bottomNavigation = navigation
        bottomNavigationLayoutListener = object : View.OnLayoutChangeListener {
            override fun onLayoutChange(v: View, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
                updateBottomMargin()
            }
        }
        navigation.addOnLayoutChangeListener(bottomNavigationLayoutListener)
        parent.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
            override fun onLayoutChange(v: View, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
                updateBottomMargin()
            }
        })
    }

    private fun applyTheme(color: Int = activity.bottomBackground) {
        binding.run {
            val nightMode = (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val tint = if (nightMode) 0xFFEAF1FF.toInt() else 0xFF1B2633.toInt()
            val glassBase = if (nightMode) {
                AndroidXColorUtils.setAlphaComponent(0xFF16191F.toInt(), 214)
            } else {
                AndroidXColorUtils.setAlphaComponent(Color.WHITE, 218)
            }
            val accent = AndroidXColorUtils.blendARGB(color, tint, if (nightMode) 0.62f else 0.35f)
            val highlight = AndroidXColorUtils.setAlphaComponent(accent, if (nightMode) 62 else 46)
            val glassStart = AndroidXColorUtils.blendARGB(glassBase, highlight, 0.55f)
            val glassEnd = AndroidXColorUtils.blendARGB(glassBase, accent, 0.10f)
            val textColor = if (nightMode) 0xFFF7F9FF.toInt() else 0xFF1D232B.toInt()
            val secondaryColor = AndroidXColorUtils.setAlphaComponent(textColor, 145)
            val borderColor = AndroidXColorUtils.setAlphaComponent(Color.WHITE, if (nightMode) 105 else 180)
            val glowColor = AndroidXColorUtils.setAlphaComponent(accent, if (nightMode) 80 else 60)
            audioPlayMiniBar.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(glassStart, glassBase, glassEnd)).apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 31.dpToPx().toFloat()
                setStroke(1.dpToPx(), borderColor)
            }
            audioMiniCoverShell.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(glowColor, AndroidXColorUtils.setAlphaComponent(textColor, 18))).apply { shape = GradientDrawable.OVAL }
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
