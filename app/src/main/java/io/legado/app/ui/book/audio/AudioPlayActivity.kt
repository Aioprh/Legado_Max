package io.legado.app.ui.book.audio

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.widget.SeekBar
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.BookType
import io.legado.app.constant.EventBus
import io.legado.app.constant.Status
import io.legado.app.constant.Theme
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.databinding.ActivityAudioPlayBinding
import io.legado.app.help.audio.AudioDownloadManager
import io.legado.app.help.book.isAudio
import io.legado.app.help.book.removeType
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.model.AudioPlay
import io.legado.app.model.BookCover
import io.legado.app.model.MaxAudioSystem
import io.legado.app.service.AudioPlayService
import io.legado.app.ui.about.AppLogDialog
import io.legado.app.ui.book.audio.config.AudioSkipCredits
import io.legado.app.ui.book.changesource.ChangeBookSourceDialog
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.book.toc.TocActivityResult
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.widget.seekbar.SeekBarChangeListener
import io.legado.app.utils.StartActivityContract
import io.legado.app.utils.applyNavigationBarPadding
import io.legado.app.utils.dpToPx
import io.legado.app.utils.gone
import io.legado.app.utils.invisible
import io.legado.app.utils.observeEvent
import io.legado.app.utils.observeEventSticky
import io.legado.app.utils.sendToClip
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.startActivityForBook
import io.legado.app.utils.toDurationTime
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.views.onLongClick
import java.util.Locale
import com.dirror.lyricviewx.OnPlayClickListener
import io.legado.app.lib.theme.ThemeStore.Companion.accentColor
import io.legado.app.model.SourceCallBack
import io.legado.app.ui.book.audio.SliderPopup.Companion.SPEED
import io.legado.app.ui.book.audio.SliderPopup.Companion.TIMER

@SuppressLint("ObsoleteSdkInt")
class AudioPlayActivity :
    VMBaseActivity<ActivityAudioPlayBinding, AudioPlayViewModel>(toolBarTheme = Theme.Dark),
    ChangeBookSourceDialog.CallBack,
    AudioPlay.CallBack {

    companion object { const val EXTRA_OPEN_CHAPTER_LIST = "open_chapter_list" }
    override val binding by viewBinding(ActivityAudioPlayBinding::inflate)
    override val viewModel by viewModels<AudioPlayViewModel>()
    private val timerSliderPopup by lazy { SliderPopup(this, TIMER) }
    private val speedControlPopup by lazy { SliderPopup(this, SPEED) }
    private var adjustProgress = false
    private var playMode = AudioPlay.PlayMode.LIST_END_STOP
    private val lyricViewX by lazy { binding.lyricViewX }
    private var lyricOn = false
    private var oldLyric: String? = null
    private var menuCustomBtn: MenuItem? = null
    private var lyricInitRunnable: Runnable? = null
    private val tocActivityResult = registerForActivityResult(TocActivityResult()) { it?.let { result -> if (result[0] != AudioPlay.book?.durChapterIndex || result[1] == 0) AudioPlay.skipTo(result[0] as Int) } }
    private val sourceEditResult = registerForActivityResult(StartActivityContract(BookSourceEditActivity::class.java)) { if (it.resultCode == RESULT_OK) viewModel.upSource() }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.titleBar.setBackgroundResource(R.color.transparent)
        AudioPlay.register(this)
        viewModel.titleData.observe(this) { name -> binding.titleBar.title = name; val lyric = AudioPlay.durChapter?.getVariable("lyric")?.takeIf { it.isNotBlank() }; upLyric(lyric ?: AudioPlay.durLyric) }
        viewModel.coverData.observe(this) { upCover(it) }
        viewModel.customBtnListData.observe(this) { menuCustomBtn?.isVisible = it }
        viewModel.initData(intent) { initListener(); if (intent.getBooleanExtra(EXTRA_OPEN_CHAPTER_LIST, false)) binding.root.postDelayed({ AudioPlay.book?.bookUrl?.let(tocActivityResult::launch) }, 120L) }
        initView(); animatePlayerEntrance()
    }

    private fun animatePlayerEntrance() {
        binding.coverContainer.alpha = 0f; binding.coverContainer.scaleX = 0.96f; binding.coverContainer.scaleY = 0.96f
        binding.llPlayerProgress.alpha = 0f; binding.llPlayMenu.alpha = 0f
        binding.llPlayerProgress.translationY = 14.dpToPx().toFloat(); binding.llPlayMenu.translationY = 22.dpToPx().toFloat()
        binding.coverContainer.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(420).start()
        binding.llPlayerProgress.animate().alpha(1f).translationY(0f).setStartDelay(100).setDuration(340).start()
        binding.llPlayMenu.animate().alpha(1f).translationY(0f).setStartDelay(150).setDuration(360).start()
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean { menuInflater.inflate(R.menu.audio_play, menu); menuCustomBtn = menu.findItem(R.id.menu_custom_btn)?.also { it.isVisible = viewModel.customBtnListData.value == true }; return super.onCompatCreateOptionsMenu(menu) }
    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean { menu.findItem(R.id.menu_login)?.isVisible = !AudioPlay.bookSource?.loginUrl.isNullOrBlank(); menu.findItem(R.id.menu_wake_lock)?.isChecked = AppConfig.audioPlayUseWakeLock; return super.onMenuOpened(featureId, menu) }

    private fun showDownloadedAudioList() {
        lifecycleScope.launch(IO) {
            val items = AudioDownloadManager.listDownloaded()
            val labels = items.map { "${it.title}\n${formatFileSize(it.size)}" }.toTypedArray()
            launch(kotlinx.coroutines.Dispatchers.Main) {
                if (labels.isEmpty()) { AlertDialog.Builder(this@AudioPlayActivity).setTitle(R.string.downloaded_audio).setMessage(R.string.downloaded_audio_empty).setPositiveButton(android.R.string.ok, null).show(); return@launch }
                AlertDialog.Builder(this@AudioPlayActivity).setTitle(getString(R.string.downloaded_audio_count, labels.size)).setItems(labels, null).setPositiveButton(android.R.string.ok, null).show()
            }
        }
    }
    private fun formatFileSize(size: Long): String = when { size >= 1024L * 1024L -> String.format(Locale.ROOT, "%.1f MB", size / 1024f / 1024f); size >= 1024L -> String.format(Locale.ROOT, "%.1f KB", size / 1024f); else -> "$size B" }

    private fun showBatchDownloadDialog() {
        val book = AudioPlay.book ?: return
        val source = AudioPlay.bookSource ?: return
        val start = AudioPlay.durChapterIndex + 1
        val total = AudioPlay.simulatedChapterSize
        if (start >= total) { toastOnUi(getString(R.string.audio_batch_no_chapters)); return }
        val options = arrayOf(getString(R.string.audio_batch_10), getString(R.string.audio_batch_50), getString(R.string.audio_batch_all))
        AlertDialog.Builder(this)
            .setTitle(R.string.audio_batch_download)
            .setItems(options) { _, which ->
                val end = when (which) { 0 -> (start + 10).coerceAtMost(total); 1 -> (start + 50).coerceAtMost(total); else -> total }
                val chapters = (start until end).mapNotNull { appDb.bookChapterDao.getChapter(book.bookUrl, it) }
                if (chapters.isEmpty()) toastOnUi(getString(R.string.audio_batch_no_chapters))
                else {
                    toastOnUi(getString(R.string.audio_batch_started, chapters.size))
                    AudioDownloadManager.downloadChapters(this, book, source, chapters,
                        onProgress = { current, count, title, success -> toastOnUi("$current/$count ${if (success) "✓" else "✗"} $title") },
                        onFinished = { toastOnUi(getString(R.string.audio_batch_finished)) }
                    )
                }
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_custom_btn -> AudioPlay.bookSource?.let { source -> AudioPlay.book?.let { book -> SourceCallBack.callBackBtn(this, SourceCallBack.CLICK_CUSTOM_BUTTON, source, book, AudioPlay.durChapter, BookType.audio) } }
            R.id.menu_change_source -> AudioPlay.book?.let { showDialogFragment(ChangeBookSourceDialog(it.name, it.author)) }
            R.id.menu_login -> AudioPlay.bookSource?.let { startActivity<SourceLoginActivity> { putExtra("bookType", BookType.audio) } }
            R.id.menu_download_audio -> { val url = AudioPlayService.url; val chapter = AudioPlay.durChapter; if (url.isBlank() || chapter == null) toastOnUi(getString(R.string.audio_download_no_url)) else { toastOnUi(getString(R.string.audio_download_started, chapter.title)); AudioDownloadManager.download(this, url, "${AudioPlay.book?.name ?: "audio"}_${chapter.title}") { ok, _ -> toastOnUi(if (ok) getString(R.string.audio_download_success, chapter.title) else getString(R.string.audio_download_failed)) } } }
            R.id.menu_batch_download_audio -> showBatchDownloadDialog()
            R.id.menu_downloaded_audio -> showDownloadedAudioList()
            R.id.menu_wake_lock -> AppConfig.audioPlayUseWakeLock = !AppConfig.audioPlayUseWakeLock
            R.id.menu_copy_audio_url -> AudioPlay.book?.let { book -> val url = AudioPlayService.url; SourceCallBack.callBackBtn(this, SourceCallBack.CLICK_COPY_PLAY_URL, AudioPlay.bookSource, book, AudioPlay.durChapter, BookType.audio, url) { sendToClip(url) } }
            R.id.menu_edit_source -> AudioPlay.bookSource?.let { sourceEditResult.launch { putExtra("sourceUrl", it.bookSourceUrl) } }
            R.id.menu_skip_credits -> AudioPlay.book?.let { showDialogFragment(AudioSkipCredits.newInstance(it)) }
            R.id.menu_log -> showDialogFragment<AppLogDialog>()
        }
        return super.onCompatOptionsItemSelected(item)
    }

    private fun initView() {
        observeEventSticky<AudioPlay.PlayMode>(EventBus.PLAY_MODE_CHANGED) { playMode = it; updatePlayModeIcon() }
        binding.playerProgress.setOnSeekBarChangeListener(object : SeekBarChangeListener { override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) { binding.tvDurTime.text = progress.toDurationTime() }; override fun onStartTrackingTouch(seekBar: SeekBar) { adjustProgress = true }; override fun onStopTrackingTouch(seekBar: SeekBar) { adjustProgress = false; AudioPlay.adjustProgress(seekBar.progress) } })
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) binding.ivSpeedControl.invisible()
        binding.ivSpeedControl.setOnClickListener { speedControlPopup.showAsDropDown(it, 0, (-100).dpToPx(), Gravity.TOP) }; binding.ivTimer.setOnClickListener { timerSliderPopup.showAsDropDown(it, 0, (-100).dpToPx(), Gravity.TOP) }; binding.llPlayMenu.applyNavigationBarPadding()
    }
    private fun initListener() { binding.ivPlayMode.setOnClickListener { AudioPlay.changePlayMode() }; binding.fabPlayStop.setOnClickListener { playButton() }; binding.fabPlayStop.onLongClick { AudioPlay.stop() }; binding.ivSkipNext.setOnClickListener { MaxAudioSystem.next() }; binding.ivSkipPrevious.setOnClickListener { MaxAudioSystem.previous() }; binding.ivChapter.setOnClickListener { AudioPlay.book?.let { tocActivityResult.launch(it.bookUrl) } } }
    private fun updatePlayModeIcon() { binding.ivPlayMode.setImageResource(playMode.iconRes) }
    private fun upCover(path: String?) { binding.coverContainer.animate().cancel(); binding.coverContainer.animate().alpha(0.72f).scaleX(0.985f).scaleY(0.985f).setDuration(110).withEndAction { BookCover.load(this, path, sourceOrigin = AudioPlay.bookSource?.bookSourceUrl) { BookCover.loadBlur(this, path, sourceOrigin = AudioPlay.bookSource?.bookSourceUrl).into(binding.ivBg) }.into(binding.ivCover); binding.coverContainer.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(280).start() }.start() }
    override fun upLyric(lyric: String?) { if (oldLyric == lyric) return; oldLyric = lyric; binding.lyricViewX.animate().cancel(); binding.lyricViewX.animate().alpha(0f).translationY(10.dpToPx().toFloat()).setDuration(140).withEndAction { if (lyric.isNullOrBlank()) { binding.lyricViewX.gone(); return@withEndAction }; lyricViewX.loadLyric(lyric); binding.lyricViewX.visible(); binding.lyricViewX.animate().alpha(1f).translationY(0f).setDuration(280).start(); if (lyricOn) upLyricP(AudioPlay.durChapterPos) else { lyricOn = true; lyricViewX.apply { setNormalTextSize(46F); setCurrentTextSize(56F); setTimelineTextColor(accentColor); setDraggable(true, object : OnPlayClickListener { override fun onPlayClick(time: Long): Boolean { AudioPlay.adjustProgress(time.toInt()); playButton(false); return true } }) }; lyricInitRunnable?.let { lyricViewX.removeCallbacks(it) }; lyricInitRunnable = Runnable { upLyricP(AudioPlay.durChapterPos) }; lyricViewX.postDelayed(lyricInitRunnable, 100) } }.start() }
    override fun upLyricP(position: Int) { lyricViewX.updateTime(position.toLong(), false) }
    private fun playButton(noLyr: Boolean = true) {
        when (AudioPlay.status) {
            Status.PLAY -> if (noLyr) AudioPlay.pause(this)
            Status.PAUSE -> AudioPlay.resume(this)
            else -> AudioPlay.loadOrUpPlayUrl()
        }
    }
    override val oldBook: Book? get() = AudioPlay.book
    override fun changeTo(source: BookSource, book: Book, toc: List<BookChapter>) { if (book.isAudio) viewModel.changeTo(source, book, toc) else { AudioPlay.stop(); lifecycleScope.launch { withContext(IO) { AudioPlay.book?.migrateTo(book, toc); book.removeType(BookType.updateError); AudioPlay.book?.delete(); appDb.bookDao.insert(book) }; startActivityForBook(book); finish() } } }
    override fun finish() { val book = AudioPlay.book ?: return super.finish(); if (AudioPlay.inBookshelf) { callBackBookEnd(); return super.finish() }; if (!AppConfig.showAddToShelfAlert) { callBackBookEnd(); viewModel.removeFromBookshelf { super.finish() } } else { alert(title = getString(R.string.add_to_bookshelf)) { setMessage(getString(R.string.check_add_bookshelf, book.name)); okButton { AudioPlay.book?.removeType(BookType.notShelf); AudioPlay.book?.save(); SourceCallBack.callBackBook(SourceCallBack.ADD_BOOK_SHELF, AudioPlay.bookSource, AudioPlay.book); AudioPlay.inBookshelf = true; setResult(RESULT_OK) }; noButton { callBackBookEnd(); viewModel.removeFromBookshelf { super.finish() } } } } }
    private fun callBackBookEnd() { SourceCallBack.callBackBook(SourceCallBack.END_READ, AudioPlay.bookSource, AudioPlay.book, AudioPlay.durChapter) }
    override fun onDestroy() {
        super.onDestroy()
        lyricInitRunnable?.let { lyricViewX.removeCallbacks(it) }
        if (AudioPlay.status != Status.PLAY) AudioPlay.stop()
        // 配置变更（旋转）时不取消加载任务，避免丢失正在解析的播放 URL
        AudioPlay.unregister(this, cancelLoading = !isChangingConfigurations)
    }
    @SuppressLint("SetTextI18n") override fun observeLiveBus() { observeEvent<Boolean>(EventBus.MEDIA_BUTTON) { if (it) playButton() }; observeEventSticky<Int>(EventBus.AUDIO_STATE) { AudioPlay.status = it; binding.fabPlayStop.setImageResource(if (it == Status.PLAY) R.drawable.ic_pause_24dp else R.drawable.ic_play_24dp); if (it == Status.PLAY) { val count = AudioDownloadManager.smartCount(this, AudioPlayService.playSpeed); AudioPlay.preloadNextChapters(count) } }; observeEventSticky<String>(EventBus.AUDIO_SUB_TITLE) { binding.tvSubTitle.text = it; binding.ivSkipPrevious.isEnabled = AudioPlay.durChapterIndex > 0 || MaxAudioSystem.queueSize() > 1; binding.ivSkipNext.isEnabled = AudioPlay.durChapterIndex < AudioPlay.simulatedChapterSize - 1 || MaxAudioSystem.queueSize() > 1 }; observeEventSticky<Int>(EventBus.AUDIO_SIZE) { binding.playerProgress.max = it; binding.tvAllTime.text = it.toDurationTime() }; observeEventSticky<Int>(EventBus.AUDIO_PROGRESS) { if (!adjustProgress) binding.playerProgress.progress = it; binding.tvDurTime.text = it.toDurationTime() }; observeEventSticky<Int>(EventBus.AUDIO_BUFFER_PROGRESS) { binding.playerProgress.secondaryProgress = it }; observeEventSticky<Float>(EventBus.AUDIO_SPEED) { if (it == 1f) binding.tvSpeed.invisible() else { binding.tvSpeed.text = String.format(Locale.ROOT, "%.1fX", it); binding.tvSpeed.visible() } }; observeEventSticky<Int>(EventBus.AUDIO_DS) { if (it > 0) { binding.tvTimer.text = "${it}m"; binding.tvTimer.visible() } else if (AudioPlay.chapterTimerCount == 0) binding.tvTimer.invisible() }; observeEventSticky<Int>(EventBus.AUDIO_CHAPTER_TIMER) { if (it > 0) { binding.tvTimer.text = getString(R.string.timer_chapter, it); binding.tvTimer.visible() } else if (AudioPlayService.timeMinute <= 0) binding.tvTimer.invisible() } }
    override fun upLoading(loading: Boolean) { }
}
