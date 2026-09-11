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
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.ListView
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.core.graphics.ColorUtils as AndroidXColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.widget.NestedScrollView
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
import java.util.WeakHashMap
import kotlin.math.max

class AudioPlayMiniBarController(
    private val activity: AppCompatActivity,
    private val parent: ViewGroup
) {
    private val binding = ViewAudioPlayMiniBarBinding.inflate(LayoutInflater.from(activity), parent, false)
    private var coverJob: Job? = null
    private var coverAnimator: ObjectAnimator? = null
    private var lastBookUrl: String? = null
    private var initialized = false
    private var bottomAnchor: View? = null
    private var bottomAnchorLayoutListener: View.OnLayoutChangeListener? = null
    private var imeVisible = false
    private var globalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null
    private var contentContainer: ViewGroup? = null
    private var originalRecyclerPaddingBottom = WeakHashMap<RecyclerView, Int>()
    private var originalComposePaddingBottom = WeakHashMap<ComposeView, Int>()
    private var originalScrollPaddingBottom = WeakHashMap<View, Int>()
    private var webView: WebView? = null
    private var webViewBottomObstructionPx = 0
    private var webViewLayoutListener: View.OnLayoutChangeListener? = null
    private var parentLayoutListener: View.OnLayoutChangeListener? = null
    private var destroyed = false

    private val webViewBridge = object {
        @JavascriptInterface
        fun reportBottomObstruction(px: Float) {
            if (destroyed) return
            activity.runOnUiThread {
                if (destroyed) return@runOnUiThread
                webViewBottomObstructionPx = px.takeIf { it.isFinite() }?.toInt()?.coerceAtLeast(0) ?: 0
                updateBottomMargin()
            }
        }
    }

    init {
        parent.addView(binding.root)
        bindContentSafeArea()
        bindBottomNavigationAnchor()
        bindImeVisibility()
        bindParentLayout()
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
            updateWebViewSafeArea()
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

    fun destroy() {
        destroyed = true
        webView?.let { view ->
            runCatching { view.removeJavascriptInterface("LegadoAudioSafeArea") }
            webViewLayoutListener?.let { listener -> view.removeOnLayoutChangeListener(listener) }
        }
        webView = null
        parentLayoutListener?.let { parent.removeOnLayoutChangeListener(it) }
        globalLayoutListener?.let { parent.viewTreeObserver.removeOnGlobalLayoutListener(it) }
        bottomAnchor?.let { view ->
            bottomAnchorLayoutListener?.let { listener -> view.removeOnLayoutChangeListener(listener) }
        }
        parent.removeView(binding.root)
    }

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
        contentContainer = activity.findViewById<ViewGroup>(R.id.content_container)
    }

    private fun bindParentLayout() {
        parentLayoutListener = object : View.OnLayoutChangeListener {
            override fun onLayoutChange(
                v: View,
                left: Int,
                top: Int,
                right: Int,
                bottom: Int,
                oldLeft: Int,
                oldTop: Int,
                oldRight: Int,
                oldBottom: Int
            ) {
                if (!destroyed) {
                    updateWebViewSafeArea()
                    updateBottomMargin()
                }
            }
        }
        parent.addOnLayoutChangeListener(parentLayoutListener)
    }

    /**
     * 迷你播放栏保持真正的悬浮层，不改变页面布局高度。
     * 只给滚动容器（RecyclerView/ComposeView/ScrollView 等）增加“播放栏本身”所占的
     * 额外滚动空间，底部导航已有的安全区不重复计算，避免出现多余大空白。
     * 主界面以 content_container 为根，其他页面一律从内容根视图递归，
     * 保证各页面底部的按钮/选项都能滚动到播放悬浮栏上方。
     */
    private fun updateContentSafeArea() {
        val container = contentContainer ?: parent
        val miniBar = binding.audioPlayMiniBar
        if (!miniBar.isShown || miniBar.height <= 0) {
            restoreContentSafeArea(container)
            return
        }

        val containerLocation = IntArray(2)
        val miniLocation = IntArray(2)
        container.getLocationOnScreen(containerLocation)
        miniBar.getLocationOnScreen(miniLocation)
        val miniTop = miniLocation[1] - containerLocation[1]

        val navigation = bottomAnchor?.takeIf { it.isShown && it.height > 0 }
        val contentBottom = if (navigation != null) {
            val navigationLocation = IntArray(2)
            navigation.getLocationOnScreen(navigationLocation)
            (navigationLocation[1] - containerLocation[1]).coerceAtMost(container.height)
        } else {
            container.height
        }
        val safeBottom = (contentBottom - miniTop + 10.dpToPx()).coerceAtLeast(0)
        applyContentSafeArea(container, safeBottom)
    }

    private fun applyContentSafeArea(view: View, safeBottom: Int) {
        when (view) {
            is RecyclerView -> {
                val original = originalRecyclerPaddingBottom.getOrPut(view) { view.paddingBottom }
                view.clipToPadding = false
                view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, original + safeBottom)
                return
            }
            is ComposeView -> {
                val original = originalComposePaddingBottom.getOrPut(view) { view.paddingBottom }
                view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, original + safeBottom)
                return
            }
            is NestedScrollView, is ScrollView, is ListView -> {
                val original = originalScrollPaddingBottom.getOrPut(view) { view.paddingBottom }
                view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, original + safeBottom)
                return
            }
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                applyContentSafeArea(view.getChildAt(index), safeBottom)
            }
        }
    }

    private fun restoreContentSafeArea(view: View) {
        when (view) {
            is RecyclerView -> {
                originalRecyclerPaddingBottom.remove(view)?.let { original ->
                    view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, original)
                }
                return
            }
            is ComposeView -> {
                originalComposePaddingBottom.remove(view)?.let { original ->
                    view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, original)
                }
                return
            }
            is NestedScrollView, is ScrollView, is ListView -> {
                originalScrollPaddingBottom.remove(view)?.let { original ->
                    view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, original)
                }
                return
            }
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                restoreContentSafeArea(view.getChildAt(index))
            }
        }
    }

    private fun bindImeVisibility() {
        globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            val visible = isImeVisible()
            if (visible != imeVisible) {
                imeVisible = visible
                if (visible) hideInternal() else refresh()
            } else if (binding.audioPlayMiniBar.isShown) {
                updateWebViewSafeArea()
                updateBottomMargin()
                updateContentSafeArea()
            }
        }
        parent.viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)
    }

    private fun isImeVisible(): Boolean {
        return ViewCompat.getRootWindowInsets(activity.window.decorView)
            ?.isVisible(WindowInsetsCompat.Type.ime()) == true
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findWebView(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    /**
     * WebView 页面自身可能存在 position:fixed/sticky 的底部导航。
     * 这里直接从网页 DOM 获取真实遮挡高度，并与 Android 系统安全区、原生底栏一起取最大值。
     */
    private fun updateWebViewSafeArea() {
        val found = findWebView(parent)
        if (found == null) {
            if (webViewBottomObstructionPx != 0) {
                webViewBottomObstructionPx = 0
                updateBottomMargin()
            }
            webView = null
            return
        }

        if (webView !== found) {
            webView?.let { old ->
                runCatching { old.removeJavascriptInterface("LegadoAudioSafeArea") }
                webViewLayoutListener?.let { listener -> old.removeOnLayoutChangeListener(listener) }
            }
            webView = found
            found.addJavascriptInterface(webViewBridge, "LegadoAudioSafeArea")
            webViewLayoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                if (!destroyed) updateWebViewSafeArea()
            }
            found.addOnLayoutChangeListener(webViewLayoutListener)
        }

        if (!found.isAttachedToWindow) return
        found.evaluateJavascript(
            """
            (function(){
              try {
                var d=window.devicePixelRatio||1;
                var vh=window.innerHeight||document.documentElement.clientHeight||0;
                var vw=window.innerWidth||document.documentElement.clientWidth||0;
                var m=0;
                document.querySelectorAll('*').forEach(function(e){
                  var s=getComputedStyle(e);
                  if(s.display==='none'||s.visibility==='hidden'||parseFloat(s.opacity||'1')<0.05)return;
                  if(s.position!=='fixed'&&s.position!=='sticky')return;
                  var r=e.getBoundingClientRect();
                  if(r.width<vw*0.5||r.height<40||r.height>vh*0.35)return;
                  if(r.bottom<vh-16||r.top>vh||r.bottom<=r.top)return;
                  var z=parseInt(s.zIndex||'0',10);
                  if(!isNaN(z)&&z<0)return;
                  m=Math.max(m,Math.min(vh,vh-Math.max(0,r.top)));
                });
                if(window.LegadoAudioSafeArea)window.LegadoAudioSafeArea.reportBottomObstruction(Math.round(m*d));
              }catch(e){}
            })();
            """.trimIndent(),
            null
        )

        found.evaluateJavascript(
            """
            (function(){
              if(window.__legadoAudioSafeAreaInstalled)return;
              window.__legadoAudioSafeAreaInstalled=true;
              function report(){
                try{
                  var d=window.devicePixelRatio||1;
                  var vh=window.innerHeight||document.documentElement.clientHeight||0;
                  var vw=window.innerWidth||document.documentElement.clientWidth||0;
                  var m=0;
                  document.querySelectorAll('*').forEach(function(e){
                    var s=getComputedStyle(e);
                    if(s.display==='none'||s.visibility==='hidden'||parseFloat(s.opacity||'1')<0.05)return;
                    if(s.position!=='fixed'&&s.position!=='sticky')return;
                    var r=e.getBoundingClientRect();
                    if(r.width<vw*0.5||r.height<40||r.height>vh*0.35)return;
                    if(r.bottom<vh-16||r.top>vh||r.bottom<=r.top)return;
                    var z=parseInt(s.zIndex||'0',10);
                    if(!isNaN(z)&&z<0)return;
                    m=Math.max(m,Math.min(vh,vh-Math.max(0,r.top)));
                  });
                  if(window.LegadoAudioSafeArea)window.LegadoAudioSafeArea.reportBottomObstruction(Math.round(m*d));
                }catch(e){}
              }
              window.LegadoAudioSafeAreaReport=report;
              new MutationObserver(function(){
                clearTimeout(window.__legadoAudioSafeAreaTimer);
                window.__legadoAudioSafeAreaTimer=setTimeout(report,80);
              }).observe(document.documentElement,{subtree:true,childList:true,attributes:true,attributeFilter:['style','class']});
              window.addEventListener('resize',report,{passive:true});
              window.addEventListener('orientationchange',report,{passive:true});
              report();
            })();
            """.trimIndent(),
            null
        )
    }

    private fun updateBottomMargin() {
        if (destroyed) return
        val navigation = bottomAnchor?.takeIf { it.isShown && it.height > 0 }
        val baseMargin = when {
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
        val systemBottom = ViewCompat.getRootWindowInsets(activity.window.decorView)
            ?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
        val systemSafeMargin = systemBottom + 8.dpToPx()
        val webSafeMargin = if (webViewBottomObstructionPx > 0) {
            webViewBottomObstructionPx + 10.dpToPx()
        } else {
            0
        }
        val margin = max(baseMargin, max(systemSafeMargin, webSafeMargin))
        binding.root.updateLayoutParams<android.widget.FrameLayout.LayoutParams> { bottomMargin = margin }
        binding.root.post { updateContentSafeArea() }
    }

    /**
     * 底部锚点：优先主界面底部导航，其次带 SelectActionBar 的管理页底部操作栏。
     * 悬浮栏会浮在锚点上方，避免覆盖底部选项。
     */
    private fun bindBottomNavigationAnchor() {
        val navigation = activity.findViewById<View>(R.id.bottom_navigation_glass)
            ?: activity.findViewById<View>(R.id.select_action_bar)
            ?: return
        bottomAnchor = navigation
        bottomAnchorLayoutListener = object : View.OnLayoutChangeListener {
            override fun onLayoutChange(v: View, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
                updateBottomMargin()
            }
        }
        navigation.addOnLayoutChangeListener(bottomAnchorLayoutListener)
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
