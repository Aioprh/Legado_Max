package io.legado.app.ui.book.audio

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.SeekBar
import android.widget.TextView
import io.legado.app.R
import io.legado.app.databinding.PopupSeekBarBinding
import io.legado.app.model.AudioPlay
import io.legado.app.service.AudioPlayService
import io.legado.app.ui.widget.seekbar.SeekBarChangeListener
import io.legado.app.utils.dpToPx
import io.legado.app.lib.theme.ThemeStore.Companion.accentColor
import kotlin.math.roundToInt

/**
 * Audio speed / sleep-timer popup.
 *
 * Timer UI is intentionally kept in one component so the time/chapter modes
 * share the same state synchronisation and slider behaviour.
 */
class SliderPopup(private val context: Context, private val name: Int) :
    PopupWindow(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) {

    companion object {
        const val TIMER = 1
        const val SPEED = 2
        private const val TIMER_BY_CHAPTER = 3
        private const val MAX_TIMER_CHAPTERS = 50
        private val TIME_PRESETS = intArrayOf(0, 15, 30, 45, 60, 90, 120)
        private val CHAPTER_PRESETS = intArrayOf(0, 1, 2, 3, 5, 10, 20, 30)
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
            binding.timerPresetsScroll.visibility = View.VISIBLE
            binding.tvTimerByTime.setOnClickListener { switchTimerMode(TIMER) }
            binding.tvTimerByChapter.setOnClickListener { switchTimerMode(TIMER_BY_CHAPTER) }
            binding.seekBar.layoutParams = binding.seekBar.layoutParams.apply {
                height = 48.dpToPx()
            }
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
                    updatePresetSelection(progress)
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
        updatePresetSelection(binding.seekBar.progress)
    }

    private fun switchTimerMode(mode: Int) {
        timerMode = mode
        updateModeAppearance()
        setProcess()
        binding.seekBar.progress = if (mode == TIMER_BY_CHAPTER) {
            AudioPlay.chapterTimerCount
        } else {
            AudioPlayService.timeMinute
        }
        updatePresetSelection(binding.seekBar.progress)
    }

    private fun updateModeAppearance() {
        binding.tvTimerByTime.setTextColor(if (timerMode == TIMER) accentColor else context.getColor(R.color.secondaryText))
        binding.tvTimerByChapter.setTextColor(if (timerMode == TIMER_BY_CHAPTER) accentColor else context.getColor(R.color.secondaryText))
    }

    private fun updatePresetSelection(value: Int) {
        if (name != TIMER) return
        val presets = if (timerMode == TIMER_BY_CHAPTER) CHAPTER_PRESETS else TIME_PRESETS
        for (index in 0 until binding.timerPresets.childCount) {
            val child = binding.timerPresets.getChildAt(index) as? TextView ?: continue
            val preset = presets.getOrNull(index) ?: continue
            child.setTextColor(if (preset == value) accentColor else context.getColor(R.color.secondaryText))
        }
    }

    private fun rebuildPresets() {
        binding.timerPresets.removeAllViews()
        val presets = if (timerMode == TIMER_BY_CHAPTER) CHAPTER_PRESETS else TIME_PRESETS
        presets.forEach { value ->
            val textView = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    40.dpToPx()
                )
                minWidth = 48.dpToPx()
                gravity = android.view.Gravity.CENTER
                setPadding(
                    12.dpToPx(),
                    0,
                    12.dpToPx(),
                    0
                )
                textSize = 13f
                text = if (timerMode == TIMER_BY_CHAPTER) {
                    context.getString(R.string.timer_chapter, value)
                } else {
                    context.getString(R.string.timer_m, value)
                }
                setOnClickListener {
                    binding.seekBar.progress = value
                    if (timerMode == TIMER_BY_CHAPTER) AudioPlay.setTimerByChapter(value) else AudioPlay.setTimer(value)
                    updatePresetSelection(value)
                }
            }
            binding.timerPresets.addView(textView)
        }
        updatePresetSelection(binding.seekBar.progress)
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
            rebuildPresets()
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
