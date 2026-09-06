package io.legado.app.ui.book.audio

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.SeekBar
import io.legado.app.R
import io.legado.app.databinding.PopupSeekBarBinding
import io.legado.app.model.AudioPlay
import io.legado.app.service.AudioPlayService
import io.legado.app.ui.widget.seekbar.SeekBarChangeListener
import kotlin.math.roundToInt

class SliderPopup(private val context: Context, private val name: Int) :
    PopupWindow(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) {
    companion object {
        const val TIMER = 1
        const val SPEED = 2
        private const val TIMER_BY_CHAPTER = 3
        private const val MAX_TIMER_CHAPTERS = 50
    }

    private val binding = PopupSeekBarBinding.inflate(LayoutInflater.from(context))
    private var timerMode = TIMER

    init {
        contentView = binding.root
        isTouchable = true
        isOutsideTouchable = false
        isFocusable = true
        if (name == TIMER) {
            binding.timerMode.visibility = View.VISIBLE
            binding.tvTimerByTime.setOnClickListener { switchTimerMode(TIMER) }
            binding.tvTimerByChapter.setOnClickListener { switchTimerMode(TIMER_BY_CHAPTER) }
        }
        setProcess()
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (name == TIMER) {
                    if (timerMode == TIMER_BY_CHAPTER) {
                        setProcessChapterText(progress)
                        if (fromUser) AudioPlay.setTimerByChapter(progress)
                    } else {
                        setProcessTimerText(progress)
                        if (fromUser) AudioPlay.setTimer(progress)
                    }
                    return
                }
                val speed = (progress / 10f).roundToInt() / 10f
                setProcessSpeedText(speed)
                if (fromUser) AudioPlay.setSpeed(speed)
            }
        })
    }

    override fun showAsDropDown(anchor: View?, xoff: Int, yoff: Int, gravity: Int) {
        super.showAsDropDown(anchor, xoff, yoff, gravity)
        syncProgress()
    }

    override fun showAtLocation(parent: View?, gravity: Int, x: Int, y: Int) {
        super.showAtLocation(parent, gravity, x, y)
        syncProgress()
    }

    private fun syncProgress() {
        if (name != TIMER) {
            binding.seekBar.progress = (AudioPlayService.playSpeed * 100).toInt()
            return
        }
        timerMode = if (AudioPlay.chapterTimerCount > 0) TIMER_BY_CHAPTER else TIMER
        updateModeAppearance()
        setProcess()
        binding.seekBar.progress = if (timerMode == TIMER_BY_CHAPTER) {
            AudioPlay.chapterTimerCount
        } else {
            AudioPlayService.timeMinute
        }
    }

    private fun switchTimerMode(mode: Int) {
        timerMode = mode
        updateModeAppearance()
        setProcess()
        binding.seekBar.progress = if (mode == TIMER_BY_CHAPTER) AudioPlay.chapterTimerCount else AudioPlayService.timeMinute
    }

    private fun updateModeAppearance() {
        val selected = context.getColor(R.color.primaryText)
        val normal = context.getColor(R.color.secondaryText)
        binding.tvTimerByTime.setTextColor(if (timerMode == TIMER) selected else normal)
        binding.tvTimerByChapter.setTextColor(if (timerMode == TIMER_BY_CHAPTER) selected else normal)
    }

    private fun setProcessTimerText(process: Int) {
        binding.tvSeekValue.text = context.getString(R.string.timer_m, process)
    }

    private fun setProcessChapterText(process: Int) {
        binding.tvSeekValue.text = context.getString(R.string.timer_chapter, process)
    }

    @SuppressLint("SetTextI18n")
    private fun setProcessSpeedText(speed: Float) {
        binding.tvSeekValue.text = "%.1fX".format(speed)
    }

    private fun setProcess() {
        if (name == TIMER) {
            if (timerMode == TIMER_BY_CHAPTER) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) binding.seekBar.min = 0
                binding.seekBar.max = MAX_TIMER_CHAPTERS
                setProcessChapterText(AudioPlay.chapterTimerCount)
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) binding.seekBar.min = 0
                binding.seekBar.max = 180
                setProcessTimerText(AudioPlayService.timeMinute)
            }
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) binding.seekBar.min = 50
        binding.seekBar.max = 300
        binding.seekBar.progress = (AudioPlayService.playSpeed * 100).toInt()
        setProcessSpeedText(AudioPlayService.playSpeed)
    }
}