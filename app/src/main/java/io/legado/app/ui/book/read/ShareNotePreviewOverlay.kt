package io.legado.app.ui.book.read

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.bumptech.glide.Glide
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.help.config.ShareNoteTemplateManager
import io.legado.app.help.glide.ImageLoader
import io.legado.app.lib.theme.UiCorner
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.lib.theme.uiTypeface
import io.legado.app.ui.config.ShareNoteTemplateManageActivity
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.utils.dpToPx
import io.legado.app.utils.share
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil

/**
 * 摘录分享预览弹层。
 *
 * 正文长按选择文本后，通过 [showShareNotePreviewOverlay] 展示：
 * 上半部分为基于 WebView + html2canvas 渲染的分享图片预览，
 * 底部为操作面板（模板/配色/字体切换、保存、分享、取消）。
 */
class ShareNotePreviewOverlay private constructor(
    private val activity: ReadBookActivity,
    initialEntry: ShareNoteTemplateManager.Entry,
    private val payload: ShareNoteImageRenderer.Payload
) : FrameLayout(activity) {

    private val cardMargin = 22.dpToPx()
    private val topPadding = 72.dpToPx()
    private val mainBottomBarHeight = 96.dpToPx()
    private val templateBottomBarHeight = 286.dpToPx()
    private val scrollView = ScrollView(activity)
    private val cardContainer = FrameLayout(activity)
    private var webView = WebView(activity)
    private val previewImage = ImageView(activity)
    private val statusView = TextView(activity)
    private val bottomBar = ComposeView(activity)
    private var currentEntry = initialEntry
    private var selectedDirName by mutableStateOf(initialEntry.dirName)
    private var selectedTemplateName by mutableStateOf(initialEntry.meta.name)
    private var templateEntries by mutableStateOf(listOf(initialEntry))
    private var shareStyle by mutableStateOf(ShareNoteTemplateManager.currentStyle())
    private var busyState by mutableStateOf(false)
    private var renderFailed by mutableStateOf(false)
    private var bottomPanelMode by mutableStateOf(BottomPanelMode.Actions)
    private var renderedResult: ShareNoteImageRenderer.RenderResult? = null
    private var renderJob: Job? = null
    private var renderToken = 0
    private var closed = false

    init {
        isClickable = true
        setBackgroundColor(0x8A000000.toInt())
        clipChildren = false
        clipToPadding = false
        setupScrollLayer()
        setupStatusView()
        setupBottomBar()
        loadTemplateEntries()
        doOnLayout { render(currentEntry) }
    }

    private fun setupScrollLayer() {
        scrollView.clipToPadding = false
        scrollView.isFillViewport = false
        updateScrollPadding()
        scrollView.overScrollMode = View.OVER_SCROLL_NEVER
        addView(
            scrollView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )

        val holder = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            clipToPadding = false
        }
        scrollView.addView(
            holder,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )
        holder.addView(
            cardContainer,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = cardMargin
                rightMargin = cardMargin
            }
        )

        configurePreviewWebView(webView)
        cardContainer.addView(
            webView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                1
            )
        )
        previewImage.isVisible = false
        previewImage.scaleType = ImageView.ScaleType.FIT_CENTER
        previewImage.adjustViewBounds = false
        cardContainer.addView(
            previewImage,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun configurePreviewWebView(view: WebView) {
        ShareNoteImageRenderer.configureWebView(view)
        view.isClickable = false
        view.isLongClickable = false
        view.setOnTouchListener { _, _ -> true }
    }

    private fun recreatePreviewWebView() {
        if (closed) return
        val old = webView
        val params = old.layoutParams ?: FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            1
        )
        val replacement = WebView(activity)
        configurePreviewWebView(replacement)
        cardContainer.removeView(old)
        webView = replacement
        cardContainer.addView(replacement, 0, params)
        runCatching {
            old.stopLoading()
            old.loadUrl("about:blank")
        }.onFailure {
            AppLog.put("Share note WebView stop failed\n${it.localizedMessage}", it)
        }
        runCatching {
            old.destroy()
        }.onFailure {
            AppLog.put("Share note WebView destroy failed\n${it.localizedMessage}", it)
        }
    }

    private fun setupStatusView() {
        statusView.text = activity.getString(R.string.share_note_loading_preview)
        statusView.setTextColor(activity.primaryTextColor)
        statusView.textSize = 14f
        statusView.typeface = activity.uiTypeface()
        statusView.gravity = Gravity.CENTER
        statusView.setPadding(14.dpToPx(), 8.dpToPx(), 14.dpToPx(), 8.dpToPx())
        statusView.background = GradientDrawable().apply {
            cornerRadius = UiCorner.actionRadius(activity)
            setColor(activity.backgroundColor)
        }
        addView(
            statusView,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        )
    }

    private fun setupBottomBar() {
        bottomBar.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        bottomBar.installViewTreeOwnersFrom(this, activity)
        bottomBar.setContent {
            LegadoTheme {
                ShareNoteQuickPanel()
            }
        }
        addView(
            bottomBar,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
        )
    }

    private fun updateScrollPadding() {
        val bottomPadding = when (bottomPanelMode) {
            BottomPanelMode.Actions -> mainBottomBarHeight +
                if (renderFailed) 50.dpToPx() else 0
            BottomPanelMode.Templates -> templateBottomBarHeight
        } + 24.dpToPx()
        scrollView.setPadding(0, topPadding, 0, bottomPadding)
    }

    private fun setBusy(message: String?, busy: Boolean) {
        statusView.text = message.orEmpty()
        statusView.isVisible = busy
        busyState = busy
        bottomBar.isEnabled = !busy
        bottomBar.alpha = if (busy) 0.72f else 1f
    }

    private fun loadTemplateEntries() {
        activity.lifecycleScope.launch {
            val entries = withContext(IO) { ShareNoteTemplateManager.loadEntries() }
            if (!closed && entries.isNotEmpty()) {
                templateEntries = entries
            }
        }
    }

    @Composable
    private fun ShareNoteQuickPanel() {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                when (bottomPanelMode) {
                    BottomPanelMode.Actions -> ShareNoteActionBar()
                    BottomPanelMode.Templates -> ShareNoteTemplatePanel()
                }
                if (bottomPanelMode == BottomPanelMode.Actions && renderFailed && !busyState) {
                    ShareNotePanelButton(
                        text = activity.getString(R.string.retry),
                        enabled = true,
                        onClick = ::retryRender,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    @Composable
    private fun ShareNoteActionBar() {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ShareNotePanelButton(
                text = "模板",
                enabled = !busyState,
                onClick = ::showTemplatePanel,
                modifier = Modifier.weight(1f)
            )
            ShareNotePanelButton(
                text = activity.getString(R.string.action_save),
                enabled = !busyState,
                onClick = ::exportAndSave,
                modifier = Modifier.weight(1f)
            )
            ShareNotePanelButton(
                text = activity.getString(R.string.share),
                enabled = !busyState,
                onClick = ::exportAndShare,
                modifier = Modifier.weight(1f)
            )
            ShareNotePanelButton(
                text = activity.getString(R.string.cancel),
                enabled = !busyState,
                onClick = ::dismiss,
                modifier = Modifier.weight(1f)
            )
        }
    }

    @Composable
    private fun ShareNoteTemplatePanel() {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "更换模板",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = selectedTemplateName,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShareNotePanelButton(
                    text = "管理",
                    modifier = Modifier.width(70.dp),
                    enabled = !busyState,
                    onClick = ::openTemplateManage
                )
                ShareNotePanelButton(
                    text = "完成",
                    modifier = Modifier.width(70.dp),
                    enabled = !busyState,
                    onClick = ::showActionPanel
                )
            }
        }
        ShareNoteSectionCard(title = "模板") {
            ShareNoteSegmentedOptions(
                options = templateEntries.map { it.dirName to it.meta.name },
                selectedValue = selectedDirName,
                onSelected = { dirName ->
                    if (!busyState) templateEntries.firstOrNull { it.dirName == dirName }?.let(::selectTemplate)
                }
            )
        }
        ShareNoteSectionCard(title = "配色") {
            ShareNoteSegmentedOptions(
                options = ShareNoteTemplateManager.stylePalettes.map { it.id to it.name },
                selectedValue = shareStyle.paletteId,
                onSelected = { paletteId ->
                    if (!busyState) updateShareStyle(shareStyle.copy(paletteId = paletteId))
                }
            )
        }
        ShareNoteSectionCard(title = "字体") {
            ShareNoteSegmentedOptions(
                options = ShareNoteTemplateManager.fontFamilies.map {
                    it to ShareNoteTemplateManager.fontLabel(it)
                },
                selectedValue = shareStyle.fontFamily,
                onSelected = { font ->
                    if (!busyState) updateShareStyle(shareStyle.copy(fontFamily = font))
                }
            )
        }
    }

    @Composable
    private fun ShareNoteSectionCard(
        title: String,
        content: @Composable () -> Unit
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 9.dp, vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ) {
            Column(modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp)) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                content()
            }
        }
    }

    @Composable
    private fun ShareNoteSegmentedOptions(
        options: List<Pair<String, String>>,
        selectedValue: String,
        onSelected: (String) -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            options.forEach { (value, label) ->
                val selected = value == selectedValue
                Box(
                    modifier = Modifier
                        .background(
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color.Transparent
                            },
                            shape = RoundedCornerShape(20.dp)
                        )
                        .clickable { onSelected(value) }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = label,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    @Composable
    private fun ShareNotePanelButton(
        text: String,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        onClick: () -> Unit
    ) {
        val cornerRadius = with(LocalDensity.current) {
            UiCorner.actionRadius(activity).toDp()
        }
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(cornerRadius),
            color = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (enabled) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled, onClick = onClick)
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = text,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    private fun showTemplatePanel() {
        bottomPanelMode = BottomPanelMode.Templates
        updateScrollPadding()
    }

    private fun showActionPanel() {
        bottomPanelMode = BottomPanelMode.Actions
        updateScrollPadding()
    }

    private fun selectTemplate(entry: ShareNoteTemplateManager.Entry) {
        if (entry.dirName == selectedDirName && renderedResult != null) return
        ShareNoteTemplateManager.rememberLast(entry)
        render(entry)
    }

    private fun updateShareStyle(next: ShareNoteTemplateManager.ShareStyle) {
        if (next == shareStyle) return
        ShareNoteTemplateManager.saveStyle(next)
        shareStyle = ShareNoteTemplateManager.currentStyle()
        render(currentEntry)
    }

    private fun openTemplateManage() {
        activity.startActivity(Intent(activity, ShareNoteTemplateManageActivity::class.java))
    }

    private fun render(entry: ShareNoteTemplateManager.Entry) {
        if (closed) return
        val token = nextRenderToken()
        currentEntry = entry
        selectedDirName = entry.dirName
        selectedTemplateName = entry.meta.name
        renderedResult = null
        renderFailed = false
        updateScrollPadding()
        renderJob?.cancel()
        renderJob = activity.lifecycleScope.launch {
            setBusy(activity.getString(R.string.share_note_loading_preview), true)
            runCatching {
                val renderStyle = shareStyle
                val availableWidth = previewAvailableWidth()
                previewImage.isVisible = false
                webView.isVisible = true
                val result = renderWithRetry(entry, renderStyle, availableWidth, token)
                showRenderedResult(result, availableWidth, token)
            }.onFailure {
                if (isActiveRender(token) && it !is CancellationException) {
                    previewImage.isVisible = false
                    webView.isVisible = false
                    renderFailed = true
                    updateScrollPadding()
                    AppLog.put("Share note preview render failed\n${it.localizedMessage}", it, true)
                    activity.toastOnUi(
                        activity.getString(R.string.share_note_render_failed, it.localizedMessage ?: "unknown")
                    )
                }
            }
            if (isActiveRender(token)) setBusy(null, false)
        }
    }

    private fun previewAvailableWidth(): Int {
        val hostWidth = width.takeIf { it > cardMargin * 2 }
            ?: resources.displayMetrics.widthPixels
        return (hostWidth - cardMargin * 2).coerceAtLeast(240.dpToPx())
    }

    private fun showRenderedResult(
        result: ShareNoteImageRenderer.RenderResult,
        availableWidth: Int,
        token: Int
    ) {
        ensureActiveRender(token)
        if (!ShareNoteImageRenderer.isUsableRenderResult(result)) {
            result.file.delete()
            renderedResult = null
            renderFailed = true
            throw IllegalStateException("Share note render result is invalid")
        }
        renderedResult = result
        renderFailed = false
        updateScrollPadding()
        val displayWidth = result.width.coerceAtMost(availableWidth).coerceAtLeast(1)
        val displayHeight = ceil(result.height * (displayWidth.toFloat() / result.width.toFloat()))
            .toInt()
            .coerceAtLeast(1)
        cardContainer.layoutParams = cardContainer.layoutParams.apply {
            width = displayWidth
            height = displayHeight
        }
        webView.layoutParams = webView.layoutParams.apply {
            width = displayWidth
            height = displayHeight
        }
        previewImage.layoutParams = previewImage.layoutParams.apply {
            width = displayWidth
            height = displayHeight
        }
        ImageLoader.load(activity, result.file)
            .fitCenter()
            .into(previewImage)
        webView.isVisible = false
        previewImage.isVisible = true
        cardContainer.requestLayout()
        scrollView.scrollTo(0, 0)
    }

    private suspend fun ensureRenderedResult(token: Int): ShareNoteImageRenderer.RenderResult {
        ensureActiveRender(token)
        renderedResult?.let { cached ->
            if (ShareNoteImageRenderer.isUsableRenderResult(cached)) {
                return cached
            }
            cached.file.delete()
            renderedResult = null
        }
        val renderStyle = shareStyle
        val availableWidth = previewAvailableWidth()
        previewImage.isVisible = false
        webView.isVisible = true
        val result = renderWithRetry(currentEntry, renderStyle, availableWidth, token)
        ensureActiveRender(token)
        showRenderedResult(result, availableWidth, token)
        return result
    }

    private suspend fun renderWithRetry(
        entry: ShareNoteTemplateManager.Entry,
        style: ShareNoteTemplateManager.ShareStyle,
        availableWidth: Int,
        token: Int
    ): ShareNoteImageRenderer.RenderResult {
        var lastError: Throwable? = null
        repeat(RENDER_MAX_ATTEMPTS) { index ->
            ensureActiveRender(token)
            if (index > 0) {
                recreatePreviewWebView()
                delay(RENDER_RETRY_DELAY_MS * index.toLong())
                ensureActiveRender(token)
            }
            try {
                val result = ShareNoteImageRenderer.renderMountedWebView(
                    context = activity,
                    webView = webView,
                    entry = entry,
                    payload = payload,
                    targetWidth = availableWidth,
                    style = style
                )
                ensureActiveRender(token)
                return result
            } catch (e: CancellationException) {
                if (e is TimeoutCancellationException) {
                    lastError = e
                    AppLog.put(
                        "Share note render attempt ${index + 1}/$RENDER_MAX_ATTEMPTS timed out\n${e.localizedMessage}",
                        e
                    )
                    return@repeat
                }
                throw e
            } catch (e: Exception) {
                lastError = e
                AppLog.put(
                    "Share note render attempt ${index + 1}/$RENDER_MAX_ATTEMPTS failed\n${e.localizedMessage}",
                    e
                )
            }
        }
        val error = lastError ?: throw IllegalStateException("Share note render failed")
        if (error is TimeoutCancellationException) {
            throw IllegalStateException("Share note render timed out", error)
        }
        throw error
    }

    private fun nextRenderToken(): Int {
        renderToken += 1
        return renderToken
    }

    private fun isActiveRender(token: Int): Boolean {
        return !closed && token == renderToken
    }

    private fun ensureActiveRender(token: Int) {
        if (!isActiveRender(token)) {
            throw CancellationException("Share note preview closed")
        }
    }

    private fun retryRender() {
        if (busyState || closed) return
        recreatePreviewWebView()
        render(currentEntry)
    }

    private fun exportAndSave() {
        if (busyState) return
        renderFailed = false
        updateScrollPadding()
        val token = nextRenderToken()
        renderJob?.cancel()
        renderJob = activity.lifecycleScope.launch {
            setBusy(activity.getString(R.string.share_note_exporting), true)
            runCatching {
                ensureActiveRender(token)
                val file = ensureRenderedResult(token).file
                ensureActiveRender(token)
                ShareNoteImageRenderer.savePngToGallery(activity, file)
            }.onSuccess {
                if (isActiveRender(token)) activity.toastOnUi(R.string.share_note_saved)
            }.onFailure {
                if (isActiveRender(token) && it !is CancellationException) {
                    renderFailed = renderedResult?.let(ShareNoteImageRenderer::isUsableRenderResult) != true
                    updateScrollPadding()
                    AppLog.put("Share note save failed\n${it.localizedMessage}", it, true)
                    activity.toastOnUi(
                        activity.getString(R.string.share_note_save_failed, it.localizedMessage ?: "unknown")
                    )
                }
            }
            if (isActiveRender(token)) setBusy(null, false)
        }
    }

    private fun exportAndShare() {
        if (busyState) return
        renderFailed = false
        updateScrollPadding()
        val token = nextRenderToken()
        renderJob?.cancel()
        renderJob = activity.lifecycleScope.launch {
            setBusy(activity.getString(R.string.share_note_exporting), true)
            runCatching {
                ensureActiveRender(token)
                ensureRenderedResult(token).file
            }.onSuccess { file ->
                if (isActiveRender(token)) {
                    runCatching {
                        activity.share(file, "image/png")
                    }.onFailure {
                        activity.toastOnUi(it.localizedMessage ?: activity.getString(R.string.can_not_share))
                    }
                }
            }.onFailure {
                if (isActiveRender(token) && it !is CancellationException) {
                    renderFailed = renderedResult?.let(ShareNoteImageRenderer::isUsableRenderResult) != true
                    updateScrollPadding()
                    AppLog.put("Share note image export failed\n${it.localizedMessage}", it, true)
                    activity.toastOnUi(
                        activity.getString(R.string.share_note_render_failed, it.localizedMessage ?: "unknown")
                    )
                }
            }
            if (isActiveRender(token)) setBusy(null, false)
        }
    }

    fun dismiss() {
        if (closed) return
        closed = true
        renderJob?.cancel()
        renderedResult = null
        runCatching { Glide.with(activity).clear(previewImage) }
        previewImage.setImageDrawable(null)
        runCatching {
            webView.stopLoading()
            cardContainer.removeView(webView)
        }.onFailure {
            AppLog.put("Share note WebView stop failed\n${it.localizedMessage}", it)
        }
        runCatching {
            webView.destroy()
        }.onFailure {
            AppLog.put("Share note WebView destroy failed\n${it.localizedMessage}", it)
        }
        (parent as? ViewGroup)?.removeView(this)
    }

    private enum class BottomPanelMode {
        Actions,
        Templates
    }

    companion object {
        private const val RENDER_MAX_ATTEMPTS = 3
        private const val RENDER_RETRY_DELAY_MS = 180L

        fun show(
            activity: ReadBookActivity,
            parent: ViewGroup,
            entry: ShareNoteTemplateManager.Entry,
            payload: ShareNoteImageRenderer.Payload
        ): ShareNotePreviewOverlay {
            val overlay = ShareNotePreviewOverlay(activity, entry, payload)
            parent.addView(
                overlay,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            return overlay
        }
    }
}

/**
 * 为 ComposeView 设置 ViewTreeOwner，使其能在非 Fragment/Activity 的容器中使用
 * lifecycleScope / viewModel 等能力。
 */
private fun ComposeView.installViewTreeOwnersFrom(
    anchor: View,
    fallbackContext: android.content.Context = context
) {
    val ownerContext = fallbackContext.findOwnerContext()
    val lifecycleOwner = anchor.findViewTreeLifecycleOwner()
        ?: ownerContext as? LifecycleOwner
    val viewModelStoreOwner = anchor.findViewTreeViewModelStoreOwner()
        ?: ownerContext as? ViewModelStoreOwner
    val savedStateRegistryOwner = anchor.findViewTreeSavedStateRegistryOwner()
        ?: ownerContext as? SavedStateRegistryOwner
    lifecycleOwner?.let { setViewTreeLifecycleOwner(it) }
    viewModelStoreOwner?.let { setViewTreeViewModelStoreOwner(it) }
    savedStateRegistryOwner?.let { setViewTreeSavedStateRegistryOwner(it) }
}

private tailrec fun android.content.Context.findOwnerContext(): android.content.Context {
    return when (this) {
        is LifecycleOwner, is ViewModelStoreOwner, is SavedStateRegistryOwner -> this
        is android.content.ContextWrapper -> baseContext.findOwnerContext()
        else -> this
    }
}
