package io.legado.app.model

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import io.legado.app.R
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.constant.IntentAction
import io.legado.app.constant.ReadConstants
import io.legado.app.constant.Status
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.readRecord.ReadRecord
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.book.getBookSource
import io.legado.app.help.book.readSimulating
import io.legado.app.help.book.simulatedTotalChapterNum
import io.legado.app.help.book.update
import io.legado.app.help.config.AppConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.globalExecutor
import io.legado.app.model.webBook.WebBook
import io.legado.app.service.AudioPlayService
import io.legado.app.model.SourceCallBack
import io.legado.app.utils.postEvent
import io.legado.app.utils.startService
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancelChildren
import splitties.init.appCtx
import kotlin.text.trim

@SuppressLint("StaticFieldLeak")
@Suppress("unused")
object AudioPlay : CoroutineScope by MainScope() {
    private const val PROGRESS_SAVE_INTERVAL = 15_000L

    enum class PlayMode(val iconRes: Int) {
        LIST_END_STOP(R.drawable.ic_play_mode_list_end_stop),
        SINGLE_LOOP(R.drawable.ic_play_mode_single_loop),
        RANDOM(R.drawable.ic_play_mode_random),
        LIST_LOOP(R.drawable.ic_play_mode_list_loop);

        fun next(): PlayMode = when (this) {
            LIST_END_STOP -> SINGLE_LOOP
            SINGLE_LOOP -> RANDOM
            RANDOM -> LIST_LOOP
            LIST_LOOP -> LIST_END_STOP
        }
    }

    var playMode = PlayMode.LIST_END_STOP
    var status = Status.STOP
    private var activityContext: Context? = null
    private var serviceContext: Context? = null
    private val context: Context get() = activityContext ?: serviceContext ?: appCtx
    var callback: CallBack? = null
    var book: Book? = null
    var chapterSize = 0
    var simulatedChapterSize = 0
    var durChapterIndex = 0
    var durChapterPos = 0
    var durChapter: BookChapter? = null
    var durPlayUrl = ""
    var durLyric: String? = null
    var durAudioSize = 0
    var inBookshelf = false
    var bookSource: BookSource? = null
    val loadingChapters = arrayListOf<Int>()
    private val preloadedUrls = HashMap<String, String>()
    private val readRecord = ReadRecord()
    private var sessionStartTime = 0L
    var readStartTime: Long = System.currentTimeMillis()
    private var lastProgressSaveTime = 0L
    val executor = globalExecutor

    fun changePlayMode() { playMode=playMode.next(); book?.setPlayMode(playMode.ordinal); postEvent(EventBus.PLAY_MODE_CHANGED, playMode) }
    fun upData(book: Book) { AudioPlay.book=book; chapterSize=appDb.bookChapterDao.getChapterCount(book.bookUrl); simulatedChapterSize=if(book.readSimulating()) book.simulatedTotalChapterNum() else chapterSize; if(durChapterIndex!=book.durChapterIndex){ stopPlay(); durChapterIndex=book.durChapterIndex; durChapterPos=book.durChapterPos; durPlayUrl=""; durLyric=null; durAudioSize=0 }; upDurChapter(); MaxAudioSystem.syncCurrentBook(book) }
    fun resetData(book: Book) { stop(); AudioPlay.book=book; readRecord.bookName=book.name; readRecord.bookAuthor=book.author; readRecord.deviceId=AppConst.androidId; readRecord.lastRead=System.currentTimeMillis(); sessionStartTime=System.currentTimeMillis(); readStartTime=System.currentTimeMillis(); lastProgressSaveTime=0L; chapterSize=appDb.bookChapterDao.getChapterCount(book.bookUrl); simulatedChapterSize=if(book.readSimulating()) book.simulatedTotalChapterNum() else chapterSize; bookSource=book.getBookSource(); durChapterIndex=book.durChapterIndex; durChapterPos=book.durChapterPos; PlayMode.entries.getOrNull(book.getPlayMode())?.let{playMode=it; postEvent(EventBus.PLAY_MODE_CHANGED,it)}; val speed=book.getPlaySpeed(); AudioPlayService.playSpeed=speed; postEvent(EventBus.AUDIO_SPEED,speed); durPlayUrl=""; durLyric=null; durAudioSize=0; synchronized(preloadedUrls){preloadedUrls.clear()}; upDurChapter(); SourceCallBack.callBackBook(SourceCallBack.START_READ,bookSource,book,durChapter); postEvent(EventBus.AUDIO_BUFFER_PROGRESS,0); MaxAudioSystem.syncCurrentBook(book); preloadNextChapters(2) }
    fun upReadTime(){if(!AppConfig.enableReadRecord)return; executor.execute{val now=System.currentTimeMillis();readRecord.readTime+=now-readStartTime;readStartTime=now;readRecord.lastRead=now;readRecord.durChapterTitle=book?.durChapterTitle.orEmpty();kotlinx.coroutines.runBlocking{appDb.readRecordDao.insert(readRecord)};sessionStartTime=now}}
    fun markReadStart(){if(!AppConfig.enableReadRecord)return;val now=System.currentTimeMillis();sessionStartTime=now;readStartTime=now;readRecord.lastRead=now}
    private fun addLoading(index:Int):Boolean=synchronized(this){if(loadingChapters.contains(index))false else{loadingChapters.add(index);true}}
    private fun removeLoading(index:Int){synchronized(this){loadingChapters.remove(index)}}
    fun loadOrUpPlayUrl(){if(durPlayUrl.isEmpty())loadPlayUrl()else upPlayUrl()}
    private fun loadPlayUrl(){val index=durChapterIndex;val key="${book?.bookUrl}#$index";synchronized(preloadedUrls){preloadedUrls.remove(key)}?.let{durPlayUrl=it;durLyric=durChapter?.getVariable("lyric");upPlayUrl();preloadNextChapters(2);return};if(!addLoading(index))return;val currentBook=book;val source=bookSource;if(currentBook!=null&&source!=null){upDurChapter();val chapter=durChapter;if(chapter==null){removeLoading(index);return};if(chapter.isVolume){skipTo(index+1);removeLoading(index);return};upLoading(true);WebBook.getContent(this,source,currentBook,chapter).onSuccess{content->val value=content.trim();if(value.isEmpty())appCtx.toastOnUi("未获取到资源链接")else contentLoadFinish(chapter,value)}.onError{AppLog.put("获取资源链接出错\n$it",it,true);upLoading(false)}.onCancel{removeLoading(index)}.onFinally{callback?.upLyric(durLyric);removeLoading(index)}}else{removeLoading(index);appCtx.toastOnUi("book or source is null")}}
    fun preloadNextChapters(count:Int=2){val currentBook=book?:return;val source=bookSource?:return;val start=durChapterIndex+1;val end=(start+count).coerceAtMost(simulatedChapterSize);for(index in start until end)preloadChapter(currentBook,source,index)}
    private fun preloadChapter(currentBook:Book,source:BookSource,index:Int){val key="${currentBook.bookUrl}#$index";synchronized(preloadedUrls){if(preloadedUrls.containsKey(key))return};if(!addLoading(index))return;val chapter=appDb.bookChapterDao.getChapter(currentBook.bookUrl,index);if(chapter==null||chapter.isVolume){removeLoading(index);return};WebBook.getContent(this,source,currentBook,chapter).onSuccess{content->content.trim().takeIf{it.isNotEmpty()}?.let{value->synchronized(preloadedUrls){preloadedUrls[key]=value}}}.onFinally{removeLoading(index);postEvent(EventBus.AUDIO_QUEUE_CHANGED,MaxAudioSystem.queue())}}
    private fun contentLoadFinish(chapter:BookChapter,content:String){if(chapter.index==book?.durChapterIndex){durPlayUrl=content;durLyric=chapter.getVariable("lyric");upPlayUrl();preloadNextChapters(2)}}
    private fun upPlayUrl(){if(isPlayToEnd())playNew()else play()}
    fun play(){context.startService<AudioPlayService>{action=IntentAction.play}}
    private fun playNew(){context.startService<AudioPlayService>{action=IntentAction.playNew}}
    fun upDurChapter(){val currentBook=book?:return;durChapter=appDb.bookChapterDao.getChapter(currentBook.bookUrl,durChapterIndex);durAudioSize=durChapter?.end?.toInt()?:0;val title=durChapter?.title?:appCtx.getString(R.string.data_loading);postEvent(EventBus.AUDIO_SUB_TITLE,title);postEvent(EventBus.AUDIO_SIZE,durAudioSize);postEvent(EventBus.AUDIO_PROGRESS,durChapterPos)}
    fun pause(context:Context){if(AudioPlayService.isRun){readStartTime=System.currentTimeMillis();context.startService<AudioPlayService>{action=IntentAction.pause}}}
    fun resume(context:Context){if(AudioPlayService.isRun)context.startService<AudioPlayService>{action=IntentAction.resume}}
    fun stop(){if(AudioPlayService.isRun)context.startService<AudioPlayService>{action=IntentAction.stop}}
    fun setSpeed(speed:Float){if(AudioPlayService.isRun){book?.setPlaySpeed(speed);val clamped=speed.coerceIn(ReadConstants.MIN_PLAY_SPEED,ReadConstants.MAX_PLAY_SPEED);context.startService<AudioPlayService>{action=IntentAction.setSpeed;putExtra("speed",clamped)}}}
    fun adjustProgress(position:Int){durChapterPos=position;saveRead();if(AudioPlayService.isRun)context.startService<AudioPlayService>{action=IntentAction.adjustProgress;putExtra("position",position)}}
    fun skipTo(index:Int){Coroutine.async{stopPlay();if(index in 0..<simulatedChapterSize){durChapterIndex=index;durChapterPos=0;durPlayUrl="";durLyric=null;saveRead();loadPlayUrl();MaxAudioSystem.syncCurrentBook(book)}}}
    fun prev(){Coroutine.async{stopPlay();if(durChapterIndex>0){durChapterIndex--;durChapterPos=0;durPlayUrl="";durLyric=null;saveRead();loadPlayUrl();MaxAudioSystem.syncCurrentBook(book)}}}
    fun next(){stopPlay();upReadTime();when(playMode){PlayMode.LIST_END_STOP->if(durChapterIndex+1<simulatedChapterSize){durChapterIndex++;durChapterPos=0;durPlayUrl="";durLyric=null;saveRead();loadPlayUrl()};PlayMode.SINGLE_LOOP->{durChapterPos=0;durPlayUrl="";durLyric=null;saveRead();loadPlayUrl()};PlayMode.RANDOM->{if(simulatedChapterSize>0){durChapterIndex=(0 until simulatedChapterSize).random();durChapterPos=0;durPlayUrl="";durLyric=null;saveRead();loadPlayUrl()}};PlayMode.LIST_LOOP->{if(simulatedChapterSize>0){durChapterIndex=(durChapterIndex+1)%simulatedChapterSize;durChapterPos=0;durPlayUrl="";durLyric=null;saveRead();loadPlayUrl()}}};MaxAudioSystem.syncCurrentBook(book)}
    fun setTimer(minute:Int){if(AudioPlayService.isRun)context.startService<AudioPlayService>{action=IntentAction.setTimer;putExtra("minute",minute)}else{AudioPlayService.timeMinute=minute;postEvent(EventBus.AUDIO_DS,minute)}}
    fun addTimer(){context.startService<AudioPlayService>{action=IntentAction.addTimer}}
    fun stopPlay(){if(AudioPlayService.isRun)context.startService<AudioPlayService>{action=IntentAction.stopPlay}}
    fun saveRead(first:Boolean=false){val currentBook=book?:return;Coroutine.async{currentBook.lastCheckCount=0;val durTime=System.currentTimeMillis();currentBook.durChapterTime=durTime;val chapterChanged=currentBook.durChapterIndex!=durChapterIndex;currentBook.durChapterIndex=durChapterIndex;currentBook.durChapterPos=durChapterPos;if(first||chapterChanged)appDb.bookChapterDao.getChapter(currentBook.bookUrl,currentBook.durChapterIndex)?.let{currentBook.durChapterTitle=it.getDisplayTitle(ContentProcessor.get(currentBook.name,currentBook.origin).getTitleReplaceRules(),currentBook.getUseReplaceRule(),replaceBook=currentBook.toReplaceBook());SourceCallBack.callBackBook(SourceCallBack.SAVE_READ,bookSource,currentBook,it,durTime.toString())};currentBook.update()}}
    fun saveDurChapter(audioSize:Long){val chapter=durChapter?:return;Coroutine.async{durAudioSize=audioSize.toInt();chapter.end=audioSize;chapter.update()}}
    fun playPositionChanged(position:Int){durChapterPos=position;val now=System.currentTimeMillis();if(now-lastProgressSaveTime>=PROGRESS_SAVE_INTERVAL){lastProgressSaveTime=now;saveRead()}}
    fun upLoading(loading:Boolean){callback?.upLoading(loading)}
    private fun isPlayToEnd():Boolean=durChapterIndex+1==simulatedChapterSize&&durChapterPos==durAudioSize
    fun register(context:Context){activityContext=context;callback=context as CallBack}
    fun unregister(context:Context){if(activityContext===context){activityContext=null;callback=null};coroutineContext.cancelChildren()}
    fun registerService(context:Context){serviceContext=context}
    fun unregisterService(){serviceContext=null}
    interface CallBack{fun upLoading(loading:Boolean);fun upLyric(lyric:String?);fun upLyricP(position:Int)}
}
