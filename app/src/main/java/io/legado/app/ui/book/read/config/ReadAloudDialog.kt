package io.legado.app.ui.book.read.config

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.SeekBar
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.EventBus
import io.legado.app.data.appDb
import io.legado.app.databinding.DialogReadAloudBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.dialogs.selector
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.widget.seekbar.SeekBarChangeListener
import io.legado.app.utils.*
import io.legado.app.utils.viewbindingdelegate.viewBinding


class ReadAloudDialog : BaseDialogFragment(R.layout.dialog_read_aloud) {
    private val callBack: CallBack? get() = activity as? CallBack
    private val binding by viewBinding(DialogReadAloudBinding::bind)

    override fun onStart() {
        super.onStart()
        dialog?.window?.run {
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setBackgroundDrawableResource(R.color.background)
            decorView.setPadding(0, 0, 0, 0)
            val attr = attributes
            attr.dimAmount = 0.0f
            attr.gravity = Gravity.BOTTOM
            attributes = attr
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        (activity as ReadBookActivity).bottomDialog--
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        val bottomDialog = (activity as ReadBookActivity).bottomDialog++
        if (bottomDialog > 0) {
            dismiss()
            return
        }
        val bg = requireContext().bottomBackground
        val isLight = ColorUtils.isColorLight(bg)
        val textColor = requireContext().getPrimaryTextColor(isLight)
        binding.run {
            rootView.setBackgroundColor(bg)
            tvPre.setTextColor(textColor)
            tvNext.setTextColor(textColor)
            ivPlayPrev.setColorFilter(textColor)
            ivPlayPause.setColorFilter(textColor)
            ivPlayNext.setColorFilter(textColor)
            ivStop.setColorFilter(textColor)
            ivTimer.setColorFilter(textColor)
            tvTimer.setTextColor(textColor)
            ivTtsSpeechReduce.setColorFilter(textColor)
            tvTtsSpeed.setTextColor(textColor)
            tvTtsSpeedValue.setTextColor(textColor)
            ivTtsSpeechAdd.setColorFilter(textColor)
            ivCatalog.setColorFilter(textColor)
            tvCatalog.setTextColor(textColor)
            ivMainMenu.setColorFilter(textColor)
            tvMainMenu.setTextColor(textColor)
            ivToBackstage.setColorFilter(textColor)
            tvToBackstage.setTextColor(textColor)
            ivSetting.setColorFilter(textColor)
            tvSetting.setTextColor(textColor)
            cbTtsFollowSys.setTextColor(textColor)
        }
        initData()
        initEvent()
    }

    private fun initData() = binding.run {
        upPlayState()
        upTimerText(BaseReadAloudService.timeMinute)
        cbTtsFollowSys.isChecked = requireContext().getPrefBoolean("ttsFollowSys", true)
        upTtsSpeechRateEnabled(!cbTtsFollowSys.isChecked)
        upSeekTimer()
        upSkipActionState()
    }

    private fun initEvent() = binding.run {
        llMainMenu.setOnClickListener {
            callBack?.showMenuBar()
            dismissAllowingStateLoss()
        }
        llSetting.setOnClickListener {
            ReadAloudConfigDialog().show(childFragmentManager, "readAloudConfigDialog")
        }
        tvPre.setOnClickListener { ReadBook.moveToPrevChapter(upContent = true, toLast = false) }
        tvNext.setOnClickListener { ReadBook.moveToNextChapter(true) }
        ivStop.setOnClickListener {
            ReadAloud.stop(requireContext())
            dismissAllowingStateLoss()
        }
        ivPlayPause.setOnClickListener {
            when {
                !BaseReadAloudService.isRun -> callBack?.onClickReadAloud()
                BaseReadAloudService.pause -> ReadAloud.resume(requireContext())
                else -> ReadAloud.pause(requireContext())
            }
        }
        ivPlayPrev.setOnClickListener {
            if (requireContext().getPrefBoolean("mediaButtonPerNext", false)) {
                ReadAloud.prevChapter(requireContext())
            } else {
                ReadAloud.prevParagraph(requireContext())
            }
        }
        ivPlayNext.setOnClickListener {
            if (requireContext().getPrefBoolean("mediaButtonPerNext", false)) {
                ReadAloud.nextChapter(requireContext())
            } else {
                ReadAloud.nextParagraph(requireContext())
            }
        }
        llCatalog.setOnClickListener { callBack?.openChapterList() }
        llToBackstage.setOnClickListener { callBack?.finish() }
        cbTtsFollowSys.setOnCheckedChangeListener { _, isChecked ->
            AppConfig.ttsFlowSys = isChecked
            upTtsSpeechRateEnabled(!isChecked)
            upTtsSpeechRate()
        }
        ivTtsSpeechReduce.setOnClickListener {
            seekTtsSpeechRate.progress = AppConfig.ttsSpeechRate - 1
            AppConfig.ttsSpeechRate -= 1
            upTtsSpeechRate()
        }
        ivTtsSpeechAdd.setOnClickListener {
            seekTtsSpeechRate.progress = AppConfig.ttsSpeechRate + 1
            AppConfig.ttsSpeechRate += 1
            upTtsSpeechRate()
        }
        ivTimer.setOnClickListener {
            AppConfig.ttsTimer = seekTimer.progress
            toastOnUi("保存设定时间成功！")
        }
        tvTimer.setOnClickListener {
            // 首先让用户选择定时模式
            val timerModes = arrayOf(
                getString(R.string.set_timer_by_time),
                getString(R.string.set_timer_by_chapter)
            )
            context?.selector(getString(R.string.set_timer), timerModes.toList()) { _, modeIndex ->
                when (modeIndex) {
                    0 -> showTimeTimerDialog() // 按时间定时
                    1 -> showChapterTimerDialog() // 按章节定时
                }
            }
        }
        //设置保存的默认值
        seekTtsSpeechRate.progress = AppConfig.ttsSpeechRate
        seekTtsSpeechRate.setOnSeekBarChangeListener(object : SeekBarChangeListener {

            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                super.onProgressChanged(seekBar, progress, fromUser)
                upTtsSpeechRateText(progress)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                AppConfig.ttsSpeechRate = seekBar.progress
                upTtsSpeechRate()
            }
        })
        seekTimer.setOnSeekBarChangeListener(object : SeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                upTimerText(progress)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                ReadAloud.setTimer(requireContext(), seekTimer.progress)
            }
        })
    }

    private fun upTtsSpeechRateEnabled(enabled: Boolean) {
        binding.run {
            upTtsSpeechRateText(AppConfig.ttsSpeechRate)
            tvTtsSpeedValue.visible(enabled)
            seekTtsSpeechRate.isEnabled = enabled
            ivTtsSpeechReduce.isEnabled = enabled
            ivTtsSpeechAdd.isEnabled = enabled
        }
    }

    private fun upPlayState() {
        if (!BaseReadAloudService.pause) {
            binding.ivPlayPause.setImageResource(R.drawable.ic_pause_24dp)
            binding.ivPlayPause.contentDescription = getString(R.string.pause)
        } else {
            binding.ivPlayPause.setImageResource(R.drawable.ic_play_24dp)
            binding.ivPlayPause.contentDescription = getString(R.string.audio_play)
        }
        val bg = requireContext().bottomBackground
        val isLight = ColorUtils.isColorLight(bg)
        val textColor = requireContext().getPrimaryTextColor(isLight)
        binding.ivPlayPause.setColorFilter(textColor)
    }

    private fun upSkipActionState() {
        val useChapter = requireContext().getPrefBoolean("mediaButtonPerNext", false)
        binding.ivPlayPrev.contentDescription = getString(
            if (useChapter) R.string.previous_chapter else R.string.read_aloud_prev_paragraph
        )
        binding.ivPlayNext.contentDescription = getString(
            if (useChapter) R.string.next_chapter else R.string.read_aloud_next_paragraph
        )
    }

    private fun upSeekTimer() {
        binding.seekTimer.post {
            if (BaseReadAloudService.timeMinute > 0) {
                binding.seekTimer.progress = BaseReadAloudService.timeMinute
            } else {
                binding.seekTimer.progress = AppConfig.ttsTimer
            }
        }
    }

    private fun upTimerText(timeMinute: Int) {
        binding.tvTimer.text = if (BaseReadAloudService.chapterCount > 0) {
            requireContext().getString(R.string.timer_chapter, BaseReadAloudService.chapterCount)
        } else {
            if (timeMinute < 0) {
                requireContext().getString(R.string.timer_m, 0)
            } else {
                requireContext().getString(R.string.timer_m, timeMinute)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun upTtsSpeechRateText(value: Int) {
        binding.tvTtsSpeedValue.text = ((value + 5) / 10f).toString()
    }

    private fun upTtsSpeechRate() {
        ReadAloud.upTtsSpeechRate(requireContext())
        if (!BaseReadAloudService.pause) {
            ReadAloud.pause(requireContext())
            ReadAloud.resume(requireContext())
        }
    }

    private fun showTimeTimerDialog() {
        val times = intArrayOf(0, 5, 10, 15, 30, 60, 90, 180)
        val timeKeys = times.map { 
            if (it == 0) getString(R.string.cancel) else getString(R.string.timer_m, it)
        }
        context?.selector(getString(R.string.set_timer_by_time), timeKeys) { _, index ->
            ReadAloud.setTimer(requireContext(), times[index])
        }
    }

    @SuppressLint("SetTextI18n")
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
        val inputEdit = android.widget.EditText(requireContext()).apply {
            hint = "剩余 $remainingChapters 章"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setSingleLine()
            setPadding(40, 20, 40, 20)
        }
        
        android.app.AlertDialog.Builder(requireContext())
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
            ReadAloud.setTimerByChapter(requireContext(), 0)
            toastOnUi("已取消章节定时")
            return
        }
        
        if (chapterCount > remainingChapters) {
            toastOnUi("剩余章节不足（剩余 $remainingChapters 章）")
            return
        }
        
        ReadAloud.setTimerByChapter(requireContext(), chapterCount)
        toastOnUi("将在朗读 $chapterCount 章后停止")
    }

    override fun observeLiveBus() {
        observeEvent<Int>(EventBus.ALOUD_STATE) { upPlayState() }
        observeEvent<Int>(EventBus.READ_ALOUD_DS) { binding.seekTimer.progress = it }
    }

    interface CallBack {
        fun showMenuBar()
        fun openChapterList()
        fun onClickReadAloud()
        fun finish()
    }
}
