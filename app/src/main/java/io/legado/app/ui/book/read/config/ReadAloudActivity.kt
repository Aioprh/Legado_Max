package io.legado.app.ui.book.read.config

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.core.graphics.ColorUtils as AndroidXColorUtils
import androidx.lifecycle.lifecycleScope
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.appcompat.widget.AppCompatTextView
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.constant.EventBus
import io.legado.app.constant.Status
import io.legado.app.data.appDb
import io.legado.app.databinding.ActivityReadAloudBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.help.glide.ImageLoader
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.lib.dialogs.selector
import io.legado.app.model.BookCover
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.book.toc.TocActivityResult
import io.legado.app.ui.widget.seekbar.SeekBarChangeListener
import io.legado.app.utils.GSON
import io.legado.app.utils.StringUtils
import io.legado.app.utils.applyNavigationBarPadding
import io.legado.app.utils.applyStatusBarPadding
import io.legado.app.utils.dpToPx
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.observeEvent
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
// 新朗读界面
class ReadAloudActivity : BaseActivity<ActivityReadAloudBinding>(imageBg = false),
    SpeakEngineDialog.CallBack {

    override val binding by viewBinding(ActivityReadAloudBinding::inflate)
    private val tocActivity = registerForActivityResult(TocActivityResult()) {
        it?.let {
            ReadBook.openChapter(it[0] as Int, it[1] as Int)
            updateBookInfo()
            updatePreviewText()
            updateChapterActionState()
        }
    }
    private var downY = 0f
    private var downX = 0f
    private var collapseHandled = false
    private var lastCover: String? = null
    private val speakEngineSummary: String
        get() {
            val ttsEngine = ReadAloud.ttsEngine
                ?: return getString(R.string.system_tts)
            if (StringUtils.isNumeric(ttsEngine)) {
                return appDb.httpTTSDao.getName(ttsEngine.toLong())
                    ?: getString(R.string.system_tts)
            }
            return GSON.fromJsonObject<SelectItem<String>>(ttsEngine).getOrNull()?.title
                ?: getString(R.string.system_tts)
        }

    override fun showReadAloudMiniBar(): Boolean = false

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        applyFallbackTheme()
        initData()
        initEvent()
        updateThemeFromCover()
    }

    override fun onResume() {
        super.onResume()
        updateBookInfo()
        upSpeakEngineSummary()
        updatePreviewText()
        updatePlayState()
        updateSkipActionState()
        updateChapterActionState()
    }

    private fun initData() = binding.run {
        readAloudContent.applyStatusBarPadding(withInitialPadding = true)
        readAloudContent.applyNavigationBarPadding(withInitialPadding = true)
        updateBookInfo()
        upSpeakEngineSummary()
        updatePreviewText()
        seekTimer.max = 180
        seekTimer.progress = if (BaseReadAloudService.timeMinute > 0) BaseReadAloudService.timeMinute else AppConfig.ttsTimer
        updateSpeedText(AppConfig.ttsSpeechRate)
        updateTimerText(BaseReadAloudService.timeMinute)
        // 如果设置了章节定时，禁用时间滑块
        if (BaseReadAloudService.chapterCount > 0) {
            seekTimer.isEnabled = false
        }
        updatePlayState()
        updateSkipActionState()
        updateChapterActionState()
    }

    private fun initEvent() = binding.run {
        ivBack.setOnClickListener { finish() }
        llChapterQuick.setOnClickListener { openChapterList() }
        ivChapterQuick.setOnClickListener { openChapterList() }
        ivMore.setOnClickListener { showDialogFragment<ReadAloudConfigDialog>() }
        llMoreSetting.setOnClickListener { showDialogFragment<ReadAloudConfigDialog>() }
        ivMoreSetting.setOnClickListener { showDialogFragment<ReadAloudConfigDialog>() }
        llSpeakEngine.setOnClickListener { showDialogFragment(SpeakEngineDialog()) }
        ivPlayPause.setOnClickListener {
            if (BaseReadAloudService.pause) ReadAloud.resume(this@ReadAloudActivity)
            else ReadAloud.pause(this@ReadAloudActivity)
        }
        ivPlayPrev.setOnClickListener {
            if (getPrefBoolean("mediaButtonPerNext", false)) {
                ReadAloud.prevChapter(this@ReadAloudActivity)
            } else {
                ReadAloud.prevParagraph(this@ReadAloudActivity)
            }
        }
        ivPlayNext.setOnClickListener {
            if (getPrefBoolean("mediaButtonPerNext", false)) {
                ReadAloud.nextChapter(this@ReadAloudActivity)
            } else {
                ReadAloud.nextParagraph(this@ReadAloudActivity)
            }
        }
        tvPrevChapter.setOnClickListener {
            if (hasPreviousChapter()) {
                ReadAloud.prevChapter(this@ReadAloudActivity)
            }
        }
        tvNextChapter.setOnClickListener {
            if (hasNextChapter()) {
                ReadAloud.nextChapter(this@ReadAloudActivity)
            }
        }
        llStop.setOnClickListener { finish() }
        ivStop.setOnClickListener { finish() }
        llSpeed.setOnClickListener { showSpeedDialog() }
        ivSpeedControl.setOnClickListener { showSpeedDialog() }
        timerBadge.setOnClickListener { showTimerDialog() }
        seekTimer.setOnSeekBarChangeListener(object : SeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                updateTimerText(progress)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                ReadAloud.setTimer(this@ReadAloudActivity, seekBar.progress)
            }
        })
        readAloudScroll.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downY = event.rawY
                    downX = event.rawX
                    collapseHandled = false
                }

                MotionEvent.ACTION_MOVE -> {
                    val dy = event.rawY - downY
                    val dx = event.rawX - downX
                    if (!collapseHandled && readAloudScroll.scrollY == 0 && dy > 110f.dpToPx() && dy > abs(dx) * 1.3f) {
                        collapseHandled = true
                        finish()
                    }
                }
            }
            false
        }
    }

    private fun openChapterList() {
        ReadBook.book?.let { tocActivity.launch(it.bookUrl) }
    }

    private fun updateBookInfo() = binding.run {
        val activeBookName = BaseReadAloudService.activeBookName
        val activeChapterTitle = BaseReadAloudService.activeChapterTitle
        val activeCover = BaseReadAloudService.activeBookCover
        if (lastCover != activeCover) {
            lastCover = activeCover
            if (activeCover != null) {
                ivCover.load(activeCover, activeBookName, BaseReadAloudService.activeBookAuthor, false)
            } else {
                ReadBook.book?.let { ivCover.load(it, false) }
            }
        }
        tvBookName.text = activeBookName ?: ReadBook.book?.name ?: ""
        tvChapterName.text = activeChapterTitle ?: ReadBook.book?.durChapterTitle ?: ""
    }

    private fun updatePreviewText() = binding.run {
        val paragraph = BaseReadAloudService.activePreviewText
            ?: ReadBook.curTextChapter?.paragraphs?.firstOrNull {
                ReadBook.durChapterPos in it.chapterIndices
            }?.text?.replace("\n", "")?.trim()
        tvPreview.text = paragraph?.takeIf { it.isNotEmpty() }
            ?: (BaseReadAloudService.activeChapterTitle ?: ReadBook.book?.durChapterTitle ?: "")
    }

    override fun upSpeakEngineSummary() {
        binding.tvSpeakEngine.text = speakEngineSummary
    }

    private fun adjustSpeed(delta: Int) {
        AppConfig.ttsSpeechRate = (AppConfig.ttsSpeechRate + delta).coerceIn(0, 45)
        updateSpeedText(AppConfig.ttsSpeechRate)
        val shouldResume = BaseReadAloudService.isRun && !BaseReadAloudService.pause
        if (shouldResume) ReadAloud.pause(this)
        ReadAloud.upTtsSpeechRate(this)
        if (shouldResume) ReadAloud.resume(this)
    }

    private fun applySpeed(value: Int) {
        AppConfig.ttsSpeechRate = value.coerceIn(0, 45)
        updateSpeedText(AppConfig.ttsSpeechRate)
        val shouldResume = BaseReadAloudService.isRun && !BaseReadAloudService.pause
        if (shouldResume) ReadAloud.pause(this)
        ReadAloud.upTtsSpeechRate(this)
        if (shouldResume) ReadAloud.resume(this)
    }

    @SuppressLint("SetTextI18n")
    private fun showSpeedDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22f.dpToPx().toInt(), 18f.dpToPx().toInt(), 22f.dpToPx().toInt(), 10f.dpToPx().toInt())
        }
        val followSys = AppCompatCheckBox(this).apply {
            text = getString(R.string.flow_sys)
            isChecked = AppConfig.ttsFlowSys
        }
        val valueText = AppCompatTextView(this).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            textSize = 18f
            text = "${((if (AppConfig.ttsFlowSys) AppConfig.defaultSpeechRate else AppConfig.ttsSpeechRate) + 5) / 10f}x"
            setPadding(0, 0, 0, 12f.dpToPx().toInt())
        }
        val seekBar = AppCompatSeekBar(this@ReadAloudActivity).apply {
            max = 45
            progress = if (AppConfig.ttsFlowSys) AppConfig.defaultSpeechRate else AppConfig.ttsSpeechRate
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setOnSeekBarChangeListener(object : SeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    valueText.text = "${(progress + 5) / 10f}x"
                }
            })
        }
        followSys.setOnCheckedChangeListener { _, isChecked ->
            AppConfig.ttsFlowSys = isChecked
            seekBar.isEnabled = !isChecked
            if (isChecked) {
                seekBar.progress = AppConfig.defaultSpeechRate
                valueText.text = "${(AppConfig.defaultSpeechRate + 5) / 10f}x"
            }
            val shouldResume = BaseReadAloudService.isRun && !BaseReadAloudService.pause
            if (shouldResume) ReadAloud.pause(this)
            ReadAloud.upTtsSpeechRate(this)
            if (shouldResume) ReadAloud.resume(this)
        }
        seekBar.isEnabled = !AppConfig.ttsFlowSys
        container.addView(followSys)
        container.addView(valueText)
        container.addView(seekBar)
        AlertDialog.Builder(this)
            .setTitle(R.string.read_aloud_speed)
            .setView(container)
            .setPositiveButton(R.string.ok) { _, _ ->
                AppConfig.ttsFlowSys = followSys.isChecked
                applySpeed(seekBar.progress)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showTimerDialog() {
        // 首先让用户选择定时模式
        val timerModes = arrayOf(
            getString(R.string.set_timer_by_time),
            getString(R.string.set_timer_by_chapter)
        )
        selector(getString(R.string.set_timer), timerModes.toList()) { _, modeIndex ->
            when (modeIndex) {
                0 -> showTimeTimerDialog() // 按时间定时
                1 -> showChapterTimerDialog() // 按章节定时
            }
        }
    }

    private fun showTimeTimerDialog() {
        val times = intArrayOf(0, 5, 10, 15, 30, 60, 90, 180)
        selector(getString(R.string.set_timer_by_time), times.map { 
            if (it == 0) getString(R.string.cancel) else getString(R.string.timer_m, it)
        }) { _, index ->
            ReadAloud.setTimer(this@ReadAloudActivity, times[index])
        }
    }

    private fun showChapterTimerDialog() {
        // 获取当前书籍的章节信息
        val book = ReadBook.book
        if (book == null) {
            toastOnUi("无法获取书籍信息")
            return
        }
        
        // 计算剩余章节数：总章节数 - 当前章节索引
        val currentChapterIndex = book.durChapterIndex
        val totalChapters = appDb.bookChapterDao.getChapterCount(book.bookUrl)
        val remainingChapters = totalChapters - currentChapterIndex
        
        // 显示输入对话框
        val inputEdit = android.widget.EditText(this).apply {
            hint = "剩余 $remainingChapters 章"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setSingleLine()
            setPadding(40, 20, 40, 20)
        }
        
        AlertDialog.Builder(this)
            .setTitle(R.string.set_timer_by_chapter)
            .setMessage("当前章节: ${currentChapterIndex + 1}/$totalChapters\n剩余: $remainingChapters 章")
            .setView(inputEdit)
            .setPositiveButton(R.string.ok) { _, _ ->
                validateAndSetChapterTimer(inputEdit.text.toString(), remainingChapters)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun validateAndSetChapterTimer(inputText: String, remainingChapters: Int) {
        if (inputText.isEmpty()) {
            toastOnUi("请输入章节数")
            return
        }
        
        val chapterCount = inputText.toIntOrNull()
        if (chapterCount == null || chapterCount < 0) {
            toastOnUi("请输入有效的章节数")
            return
        }
        
        if (chapterCount == 0) {
            // 取消定时
            ReadAloud.setTimerByChapter(this, 0)
            toastOnUi("已取消章节定时")
            return
        }
        
        if (chapterCount > remainingChapters) {
            toastOnUi("剩余章节不足（剩余 $remainingChapters 章）")
            return
        }
        
        ReadAloud.setTimerByChapter(this, chapterCount)
        toastOnUi("将在朗读 $chapterCount 章后停止")
    }

    @SuppressLint("SetTextI18n")
    private fun updateSpeedText(value: Int) {
        binding.tvSpeedValue.text = "${(value + 5) / 10f}x"
    }

    private fun updateTimerText(value: Int) {
        val timerText = if (BaseReadAloudService.chapterCount > 0) {
            getString(R.string.timer_chapter, BaseReadAloudService.chapterCount)
        } else {
            getString(R.string.timer_m, if (value < 0) 0 else value)
        }
        binding.tvTimer.text = timerText
        binding.tvTimerLabelRight.text = timerText
    }

    private fun updatePlayState() {
        if (BaseReadAloudService.pause) {
            binding.ivPlayPause.setImageResource(R.drawable.ic_play_24dp)
            binding.ivPlayPause.background = null
            binding.ivPlayPause.setColorFilter(0xFFFFFFFF.toInt())
        } else {
            binding.ivPlayPause.setImageResource(R.drawable.ic_pause_24dp)
            binding.ivPlayPause.background = null
            binding.ivPlayPause.setColorFilter(0xFFFFFFFF.toInt())
        }
    }

    private fun updateSkipActionState() {
        val useChapter = getPrefBoolean("mediaButtonPerNext", false)
        binding.ivPlayPrev.contentDescription = getString(
            if (useChapter) R.string.previous_chapter else R.string.read_aloud_prev_paragraph
        )
        binding.ivPlayNext.contentDescription = getString(
            if (useChapter) R.string.next_chapter else R.string.read_aloud_next_paragraph
        )
    }

    // 更新章节操作按钮状态
    private fun updateChapterActionState() {
        val hasPrevious = hasPreviousChapter()
        val hasNext = hasNextChapter()
        setChapterActionEnabled(hasPrevious, binding.tvPrevChapter)
        setChapterActionEnabled(hasNext, binding.tvNextChapter)
    }

    private fun setChapterActionEnabled(enabled: Boolean, vararg views: View) {
        views.forEach {
            it.isEnabled = enabled
        }
        views.firstOrNull()?.alpha = if (enabled) 1f else 0.38f
        views.drop(1).forEach { it.alpha = 1f }
    }

    private fun hasPreviousChapter(): Boolean {
        return currentReadAloudChapterIndex() > 0
    }

    private fun hasNextChapter(): Boolean {
        val chapterIndex = currentReadAloudChapterIndex()
        val chapterSize = currentReadAloudChapterSize()
        return chapterIndex >= 0 && chapterSize > 0 && chapterIndex < chapterSize - 1
    }

    private fun currentReadAloudChapterIndex(): Int {
        BaseReadAloudService.activeChapterIndex.takeIf { it >= 0 }?.let {
            return it
        }
        val book = ReadBook.book ?: return -1
        val activeBookUrl = BaseReadAloudService.activeBookUrl
        return if (activeBookUrl == null || activeBookUrl == book.bookUrl) {
            book.durChapterIndex
        } else {
            -1
        }
    }

    private fun currentReadAloudChapterSize(): Int {
        BaseReadAloudService.activeChapterSize.takeIf { it > 0 }?.let {
            return it
        }
        val book = ReadBook.book ?: return 0
        val activeBookUrl = BaseReadAloudService.activeBookUrl
        return if (activeBookUrl == null || activeBookUrl == book.bookUrl) {
            ReadBook.simulatedChapterSize.takeIf { it > 0 }
                ?: appDb.bookChapterDao.getChapterCount(book.bookUrl)
        } else {
            0
        }
    }

    override fun observeLiveBus() {
        observeEvent<Int>(EventBus.ALOUD_STATE) {
            updatePlayState()
            updateBookInfo()
            updatePreviewText()
            updateChapterActionState()
            if (it == Status.STOP) finish()
        }
        observeEvent<Int>(EventBus.READ_ALOUD_DS) {
            binding.seekTimer.progress = it
            updateTimerText(it)
            // 根据定时模式启用/禁用时间滑块
            binding.seekTimer.isEnabled = BaseReadAloudService.chapterCount == 0
        }
        observeEvent<Int>(EventBus.TTS_PROGRESS) {
            if (BaseReadAloudService.shouldIgnoreProgressSync(
                    ReadBook.durChapterIndex,
                    BaseReadAloudService.lastTtsChapterIndex
                )
            ) {
                return@observeEvent
            }
            updateBookInfo()
            updatePreviewText()
            updateChapterActionState()
        }
    }

    private fun applyFallbackTheme() {
        applyTheme(
            baseColor = 0xFF665185.toInt(),
            textColor = 0xFFF7F1FF.toInt()
        )
    }

    private fun updateThemeFromCover() {
        val cover = BaseReadAloudService.activeBookCover
            ?: ReadBook.book?.let { BookCover.getDisplayCover(it) }
            ?: return
        updateBlurBackground()
        lifecycleScope.launch(Dispatchers.IO) {
            val bitmap = runCatching {
                ImageLoader.loadBitmap(this@ReadAloudActivity, cover).submit().get()
            }.getOrNull()
            bitmap?.let {
                val dominant = extractDominantColor(it)
                withContext(Dispatchers.Main) {
                    applyTheme(dominant, 0xFFF7F1FF.toInt())
                    updatePlayState()
                }
            }
        }
    }

    private fun updateBlurBackground() {
        val activeCover = BaseReadAloudService.activeBookCover
        if (activeCover != null) {
            BookCover.loadBlur(this, activeCover, false, null)
                .into(binding.ivBlurBackground)
            return
        }
        ReadBook.book?.let { book ->
            BookCover.loadBlur(this, BookCover.getDisplayCover(book), sourceOrigin = book.origin)
                .into(binding.ivBlurBackground)
        }
    }

    private fun applyTheme(baseColor: Int, textColor: Int) = binding.run {
        val pageColor = AndroidXColorUtils.blendARGB(baseColor, 0xFF22172D.toInt(), 0.32f)
        val secondary = AndroidXColorUtils.setAlphaComponent(textColor, 190)
        root.setBackgroundColor(0xFF000000.toInt())
        root.tag = pageColor
        vBackgroundMask.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                AndroidXColorUtils.setAlphaComponent(pageColor, 182),
                AndroidXColorUtils.setAlphaComponent(0xFF140E1C.toInt(), 220)
            )
        )
        heroCard.radius = 34f.dpToPx()
        heroCard.setCardBackgroundColor(AndroidXColorUtils.setAlphaComponent(0xFFFFFFFF.toInt(), 12))
        coverMask.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                AndroidXColorUtils.setAlphaComponent(0xFF1A1224.toInt(), 0),
                AndroidXColorUtils.setAlphaComponent(0xFF1A1224.toInt(), 88),
                AndroidXColorUtils.setAlphaComponent(0xFF1A1224.toInt(), 228)
            )
        ).apply {
            cornerRadius = 34f.dpToPx()
        }
        timerBadge.background = createRoundDrawable(AndroidXColorUtils.setAlphaComponent(0xFFFFFFFF.toInt(), 22), 18f)
        tvSpeedValue.background = createRoundDrawable(AndroidXColorUtils.setAlphaComponent(0xFFFFFFFF.toInt(), 18), 18f)
        llSpeakEngine.background = createRoundDrawable(AndroidXColorUtils.setAlphaComponent(0xFFFFFFFF.toInt(), 18), 24f)
        ivPlayPrev.background = null
        ivPlayNext.background = null
        ivStop.background = null
        ivSpeedControl.background = null
        ivMoreSetting.background = null
        ivChapterQuick.background = null

        listOf(tvPageTitle, tvChapterName, tvPreview, tvTimer, tvSpeedValue, tvTimerLabelLeft, tvTimerLabelRight, tvSpeakEngine, tvPrevChapter, tvNextChapter).forEach { it.setTextColor(textColor) }
        listOf(tvBookName, tvSpeedLabel, tvStop, tvMoreSetting, tvChapterQuick).forEach {
            it.setTextColor(secondary)
        }
        ivBack.setColorFilter(textColor)
        ivMore.setColorFilter(textColor)
        ivTimer.setColorFilter(secondary)
        ivSpeakEngine.setColorFilter(textColor)
        ivSpeakEngineArrow.setColorFilter(textColor)
        ivStop.setColorFilter(secondary)
        ivSpeedControl.setColorFilter(secondary)
        ivChapterQuick.setColorFilter(secondary)
        ivMoreSetting.setColorFilter(secondary)
        ivPlayPrev.setColorFilter(textColor)
        ivPlayNext.setColorFilter(textColor)
    }

    private fun extractDominantColor(bitmap: Bitmap): Int {
        val stepX = (bitmap.width / 18).coerceAtLeast(1)
        val stepY = (bitmap.height / 18).coerceAtLeast(1)
        var red = 0L
        var green = 0L
        var blue = 0L
        var count = 0L
        for (x in 0 until bitmap.width step stepX) {
            for (y in 0 until bitmap.height step stepY) {
                val pixel = bitmap.getPixel(x, y)
                red += android.graphics.Color.red(pixel)
                green += android.graphics.Color.green(pixel)
                blue += android.graphics.Color.blue(pixel)
                count++
            }
        }
        if (count == 0L) return 0xFF665185.toInt()
        return android.graphics.Color.rgb((red / count).toInt(), (green / count).toInt(), (blue / count).toInt())
    }

    private fun createRoundDrawable(fillColor: Int, radiusDp: Float, strokeColor: Int? = null): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusDp.dpToPx()
            setColor(fillColor)
            strokeColor?.let { setStroke(1.dpToPx(), it) }
        }
    }
}
