package io.legado.app.ui.widget.dialog

import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jayway.jsonpath.ReadContext
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.BaseSource
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.databinding.DialogParagraphCommentBinding
import io.legado.app.exception.NoStackTraceException
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.lib.theme.primaryColor
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.utils.GSON
import io.legado.app.utils.dpToPx
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.gone
import io.legado.app.utils.jsonPath
import io.legado.app.utils.setLayout
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.stackTraceStr
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * 段评弹窗：原生列表展示段评（头像/昵称/等级/地区/时间/内容/赞踩/楼层），
 * 支持分页加载与回复展开/收起。
 *
 * 由书源 JS 通过 `java.showParagraphComments(JSON.stringify(config))` 打开，
 * 配置见 [ParagraphCommentConfig]。
 */
class ParagraphCommentDialog() : BaseDialogFragment(R.layout.dialog_paragraph_comment) {

    constructor(sourceKey: String, config: ParagraphCommentConfig) : this() {
        arguments = Bundle().apply {
            putString("sourceKey", sourceKey)
            putString("config", GSON.toJson(config))
        }
    }

    private val binding by viewBinding(DialogParagraphCommentBinding::bind)
    private val adapter by lazy { ParagraphCommentAdapter(requireContext()) }
    private var source: BaseSource? = null
    private var config: ParagraphCommentConfig = ParagraphCommentConfig()
    private var page = 0
    private var total = -1L // -1 表示未知总数，用空页判断是否还有更多
    private var hasMore = true
    private var loading = false

    private enum class FooterState { NONE, LOADING, NO_MORE, FAILED }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setGravity(Gravity.BOTTOM)
        val dm = resources.displayMetrics
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, (dm.heightPixels * 0.9).toInt())
    }

    override fun onDestroy() {
        super.onDestroy()
        // 释放语音播放器，避免页面关闭后继续占用
        runCatching { mediaPlayer?.stop() }
        mediaPlayer?.release()
        mediaPlayer = null
        playingItem = null
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        // 顶部圆角
        view.background = GradientDrawable().apply {
            val radius = 16.dpToPx().toFloat()
            cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
            setColor(ThemeStore.backgroundColor())
        }
        binding.run {
            toolBar.setBackgroundColor(primaryColor)
            toolBar.title = getString(R.string.paragraph_comment_title)
            toolBar.setNavigationOnClickListener { dismiss() }
            recyclerView.layoutManager = LinearLayoutManager(requireContext())
            recyclerView.adapter = adapter
            recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                    val lm = rv.layoutManager as? LinearLayoutManager ?: return
                    if (hasMore && !loading &&
                        lm.findLastVisibleItemPosition() >= adapter.getActualItemCount() - 4
                    ) {
                        loadPage(page + 1)
                    }
                }
            })
            // 加载失败点击重试
            llFooter.setOnClickListener {
                if (loading) return@setOnClickListener
                loadPage(page + 1)
            }
        }
        adapter.replyListener = object : ParagraphCommentAdapter.ReplyListener {
            override fun onToggleReplies(item: ParagraphCommentItem) {
                if (item.repliesLoaded) {
                    item.repliesLoaded = false
                    item.replies.clear()
                    adapter.updateItem(item)
                } else {
                    loadReplies(item)
                }
            }
        }
        adapter.audioListener = object : ParagraphCommentAdapter.AudioListener {
            override fun onToggleAudio(item: ParagraphCommentItem) {
                toggleAudio(item)
            }
        }
        arguments?.let {
            val sourceKey = it.getString("sourceKey")
            source = sourceKey?.let { key ->
                appDb.bookSourceDao.getBookSource(key) ?: appDb.rssSourceDao.getByKey(key)
            }
            it.getString("config")?.let { json ->
                config = GSON.fromJsonObject<ParagraphCommentConfig>(json).getOrNull()
                    ?: ParagraphCommentConfig()
            }
        }
        if (config.commentsUrl.isNullOrBlank()) {
            AppLog.put("段评弹窗 commentsUrl 为空，无法加载", NoStackTraceException("commentsUrl 为空"))
            showMsg(getString(R.string.paragraph_comment_load_failed))
            return
        }
        loadPage(1)
    }

    // ---------- 段评列表 ----------

    private fun loadPage(nextPage: Int) {
        if (loading) return
        loading = true
        if (nextPage == 1) {
            binding.rotateLoading.visible()
            binding.tvMsg.gone()
            updateFooter(FooterState.NONE)
        } else {
            updateFooter(FooterState.LOADING)
        }
        execute {
            val body = fetchBody(buildCommentsUrl(nextPage))
            val items = body?.let { parseComments(it) }.orEmpty()
            val newTotal = body?.let { parseTotal(it) } ?: -1L
            withContext(Dispatchers.Main) {
                loading = false
                binding.rotateLoading.gone()
                if (body == null) {
                    if (nextPage == 1 && adapter.isEmpty()) {
                        showMsg(getString(R.string.paragraph_comment_load_failed))
                        updateFooter(FooterState.NONE)
                    } else {
                        hideMsg()
                        updateFooter(FooterState.FAILED)
                    }
                    return@withContext
                }
                page = nextPage
                if (nextPage == 1) {
                    adapter.setItems(items)
                } else {
                    adapter.addItems(items)
                }
                if (newTotal >= 0) total = newTotal
                // 无更多：返回空、或本页不足 pageSize（末页）、或已到总数
                hasMore = items.isNotEmpty() &&
                    items.size >= config.pageSize &&
                    (total < 0 || adapter.getActualItemCount() < total)
                if (adapter.isEmpty()) {
                    showMsg(getString(R.string.paragraph_comment_empty))
                    updateFooter(FooterState.NONE)
                } else {
                    hideMsg()
                    updateFooter(if (hasMore) FooterState.NONE else FooterState.NO_MORE)
                }
            }
        }
    }

    private fun buildCommentsUrl(nextPage: Int): String {
        return config.commentsUrl
            .replace("[page]", nextPage.toString())
            .replace("[pageSize]", config.pageSize.toString())
    }

    private fun parseComments(body: String): List<ParagraphCommentItem> {
        return runCatching {
            val rc = jsonPath.parse(body)
            val listPath = config.listPath.ifBlank { "$.Data.DataList" }
            val list = rc.read<List<Any?>>(listPath) ?: return@runCatching emptyList()
            list.mapNotNull { it as? Map<*, *> }.map { map ->
                val itemRc = jsonPath.parse(map)
                val content = readStr(itemRc, config.fields.content, DEFAULT_CONTENTS)
                val images = readImages(map)
                val audio = readAudio(map)
                ParagraphCommentItem(
                    id = readStr(itemRc, config.fields.id, DEFAULT_IDS),
                    rootId = readStr(itemRc, config.fields.rootId, DEFAULT_ROOT_IDS),
                    nickname = readStr(itemRc, config.fields.nickname, DEFAULT_NICKNAMES),
                    avatar = readStr(itemRc, config.fields.avatar, DEFAULT_AVATARS),
                    level = readStr(itemRc, config.fields.level, DEFAULT_LEVELS),
                    ip = readStr(itemRc, config.fields.ip, DEFAULT_IPS),
                    content = content,
                    images = images,
                    audio = audio,
                    agree = readLong(itemRc, config.fields.agree, DEFAULT_AGREES),
                    oppose = readLong(itemRc, config.fields.oppose, DEFAULT_OPPOSES),
                    time = readLong(itemRc, config.fields.time, DEFAULT_TIMES),
                    floor = readInt(itemRc, config.fields.floor, DEFAULT_FLOORS),
                    replyCount = readInt(itemRc, config.fields.replyCount, DEFAULT_REPLY_COUNTS)
                )
            }.filter { item ->
                // 过滤完全无内容的空评论（无文字/图片/语音，如起点 ReviewType=4 特殊类型），避免显示“匿名用户+空白”
                item.content.isNotBlank() || item.images.isNotEmpty() || item.audio.isNotBlank()
            }
        }.getOrElse {
            AppLog.put("段评解析失败", it)
            emptyList()
        }
    }

    private fun parseTotal(body: String): Long {
        return runCatching {
            val totalPath = config.totalPath.ifBlank { "$.Data.TotalCount" }
            val rc = jsonPath.parse(body)
            (rc.read<Any>(totalPath) as? Number)?.toLong() ?: -1L
        }.getOrDefault(-1L)
    }

    // ---------- 回复 ----------

    private fun loadReplies(item: ParagraphCommentItem) {
        if (item.repliesLoading) return
        if (config.repliesUrl.isBlank()) {
            // 无回复接口：直接标记已加载（空回复），收起按钮不显示
            item.repliesLoaded = true
            adapter.updateItem(item)
            return
        }
        item.repliesLoading = true
        adapter.updateItem(item)
        execute {
            val body = fetchBody(buildRepliesUrl(item))
            val replies = body?.let { parseReplies(it) }.orEmpty()
            withContext(Dispatchers.Main) {
                item.repliesLoading = false
                item.replies.clear()
                item.replies.addAll(replies)
                item.repliesLoaded = true
                adapter.updateItem(item)
            }
        }
    }

    private fun buildRepliesUrl(item: ParagraphCommentItem): String {
        return config.repliesUrl
            .replace("[reviewId]", item.id)
            .replace("[rootId]", item.rootId.ifBlank { item.id })
            .replace("[pageSize]", config.pageSize.toString())
    }

    // ---------- 语音播放 ----------

    private var mediaPlayer: MediaPlayer? = null
    private var playingItem: ParagraphCommentItem? = null

    /** 点击语音条：播放/停止；无地址时从 audio 接口补全 */
    private fun toggleAudio(item: ParagraphCommentItem) {
        if (item.audioLoading) return
        if (item.audioPlaying) {
            stopAudio(item)
            return
        }
        stopOtherAudio(item)
        if (item.audioUrl.startsWith("http")) {
            playAudio(item, item.audioUrl)
        } else {
            loadAudio(item)
        }
    }

    private fun stopOtherAudio(except: ParagraphCommentItem? = null) {
        val current = playingItem ?: return
        if (current === except) return
        stopAudio(current)
    }

    /** 从 audio 接口拉取该段语音列表，按评论 Id 匹配音频地址后播放 */
    private fun loadAudio(item: ParagraphCommentItem) {
        item.audioLoading = true
        adapter.updateItem(item)
        execute {
            val body = fetchBody(buildAudioUrl())
            val audioUrl = body?.let { findAudioUrl(it, item.id) }.orEmpty()
            withContext(Dispatchers.Main) {
                item.audioLoading = false
                if (audioUrl.startsWith("http")) {
                    item.audioUrl = audioUrl
                    adapter.updateItem(item)
                    playAudio(item, audioUrl)
                } else {
                    adapter.updateItem(item)
                    toastOnUi(getString(R.string.paragraph_comment_audio_failed))
                }
            }
        }
    }

    private fun buildAudioUrl(): String {
        val template = config.audioUrl.ifBlank {
            config.commentsUrl
                .replace("type=text", "type=audio")
                .replace("type=all", "type=audio")
        }
        // 拉取该段全部语音评论，用较大页大小
        return template
            .replace("[page]", "1")
            .replace("[pageSize]", "50")
    }

    /** 在 audio 接口返回列表中找到同 Id 评论的音频地址 */
    private fun findAudioUrl(body: String, commentId: String): String {
        if (commentId.isBlank()) return ""
        return runCatching {
            val rc = jsonPath.parse(body)
            val listPath = config.listPath.ifBlank { "$.Data.DataList" }
            val list = rc.read<List<Any?>>(listPath) ?: return@runCatching ""
            for (map in list.mapNotNull { it as? Map<*, *> }) {
                val id = map["Id"]?.toString()
                    ?: map["CommentId"]?.toString()
                    ?: map["ReviewId"]?.toString()
                    ?: ""
                if (id == commentId) {
                    readAudio(map).takeIf { it.startsWith("http") }?.let { return it }
                }
            }
            ""
        }.getOrDefault("")
    }

    private fun playAudio(item: ParagraphCommentItem, url: String) {
        try {
            val player = MediaPlayer().apply {
                setDataSource(url)
                setOnPreparedListener { start() }
                setOnCompletionListener { stopAudio(item) }
                setOnErrorListener { _, _, _ ->
                    stopAudio(item)
                    true
                }
                prepareAsync()
            }
            mediaPlayer = player
            playingItem = item
            item.audioPlaying = true
            adapter.updateItem(item)
        } catch (e: Exception) {
            AppLog.put("段评语音播放失败", e)
            toastOnUi(getString(R.string.paragraph_comment_audio_failed))
        }
    }

    private fun stopAudio(item: ParagraphCommentItem) {
        runCatching { mediaPlayer?.stop() }
        mediaPlayer?.release()
        mediaPlayer = null
        if (playingItem === item) playingItem = null
        item.audioPlaying = false
        adapter.updateItem(item)
    }

    private fun parseReplies(body: String): List<ParagraphReplyItem> {
        return runCatching {
            val rc = jsonPath.parse(body)
            val listPath = config.listPath.ifBlank { "$.Data.DataList" }
            val list = rc.read<List<Any?>>(listPath) ?: return@runCatching emptyList()
            list.mapNotNull { it as? Map<*, *> }.map { map ->
                val itemRc = jsonPath.parse(map)
                ParagraphReplyItem(
                    nickname = readStr(itemRc, config.replyFields.nickname, DEFAULT_NICKNAMES),
                    avatar = readStr(itemRc, config.replyFields.avatar, DEFAULT_AVATARS),
                    replyTo = readStr(itemRc, config.replyFields.replyTo, DEFAULT_REPLY_TOS),
                    content = readStr(itemRc, config.replyFields.content, DEFAULT_CONTENTS),
                    images = readImages(map),
                    audio = readAudio(map),
                    agree = readLong(itemRc, config.replyFields.agree, DEFAULT_AGREES),
                    time = readLong(itemRc, config.replyFields.time, DEFAULT_TIMES)
                )
            }.filter { reply ->
                // 过滤完全无内容的空回复
                reply.content.isNotBlank() || reply.images.isNotEmpty() || reply.audio.isNotBlank()
            }
        }.getOrElse {
            AppLog.put("段评回复解析失败", it)
            emptyList()
        }
    }

    // ---------- 工具 ----------

    private suspend fun fetchBody(url: String): String? {
        if (url.isBlank()) return null
        return runCatching {
            val analyzeUrl = AnalyzeUrl(url, source = source, coroutineContext = EmptyCoroutineContext)
            analyzeUrl.getStrResponse().body
        }.onFailure {
            AppLog.put("段评请求失败 $url\n${it.stackTraceStr}", it)
        }.getOrNull()
    }

    private fun readStr(rc: ReadContext, primary: String, defaults: List<String>): String {
        val paths = if (primary.isBlank()) {
            defaults
        } else {
            listOf(primary) + defaults.filter { it != primary }
        }
        for (p in paths) {
            val v = runCatching { rc.read<Any>(p) }.getOrNull() ?: continue
            val s = v.toString().trim()
            if (s.isNotEmpty() && s != "null") return s
        }
        return ""
    }

    private fun readLong(rc: ReadContext, primary: String, defaults: List<String>): Long {
        val paths = if (primary.isBlank()) {
            defaults
        } else {
            listOf(primary) + defaults.filter { it != primary }
        }
        for (p in paths) {
            val v = runCatching { rc.read<Any>(p) }.getOrNull() ?: continue
            when (v) {
                is Number -> return v.toLong()
                else -> v.toString().toLongOrNull()?.let { return it }
            }
        }
        return 0
    }

    private fun readInt(rc: ReadContext, primary: String, defaults: List<String>): Int {
        return readLong(rc, primary, defaults).toInt()
    }

    /**
     * 提取评论图片：直接基于 Map 读取，字段名大小写不敏感，
     * 覆盖起点系常见命名（ImageDetail/PreImage/ImageUrl/Images/ImageList/ImgUrl…）。
     * 兼容图片地址被序列化成 JSON 数组/对象字符串的情况。
     */
    private fun readImages(map: Map<*, *>): List<String> {
        val result = LinkedHashSet<String>()
        for (key in IMAGE_FIELD_CANDIDATES) {
            val v = findKey(map, key) ?: continue
            collectImageUrls(v, result)
            if (result.size >= 9) break
        }
        return result.take(9)
    }

    private fun collectImageUrls(v: Any?, out: MutableSet<String>) {
        when (v) {
            null -> Unit
            is Map<*, *> -> v.values.forEach { collectImageUrls(it, out) }
            is List<*> -> v.forEach { collectImageUrls(it, out) }
            else -> {
                val s = v.toString().trim()
                if (s.isEmpty()) return
                // 兼容“图片地址被序列化成字符串数组/JSON”的情况
                if (s.startsWith("[") || s.startsWith("{")) {
                    runCatching { GSON.fromJson<Any?>(s, Any::class.java) }
                        .getOrNull()
                        ?.let { collectImageUrls(it, out); return }
                }
                if (s.startsWith("http") && out.size < 9) out.add(s)
            }
        }
    }

    /** 提取语音评论：有直接播放地址返回地址，否则检测到语音相关字段（AudioRoleId/AudioTime 等）返回标记值 */
    private fun readAudio(map: Map<*, *>): String {
        for (key in AUDIO_FIELD_CANDIDATES) {
            val v = findKey(map, key) ?: continue
            val s = v.toString().trim()
            if (s.isBlank() || s == "null" || s == "0") continue
            return if (s.startsWith("http")) s else "voice"
        }
        return ""
    }

    /** 忽略大小写查找 Map 键 */
    private fun findKey(map: Map<*, *>, key: String): Any? {
        map[key]?.let { return it }
        val lower = key.lowercase()
        map.entries.forEach { (k, v) ->
            if (k != null && k.toString().lowercase() == lower) return v
        }
        return null
    }

    private fun updateFooter(state: FooterState) {
        when (state) {
            FooterState.NONE -> binding.llFooter.gone()
            FooterState.LOADING -> {
                binding.footerRotateLoading.visible()
                binding.footerTvMsg.text = getString(R.string.paragraph_comment_loading)
                binding.llFooter.visible()
            }
            FooterState.NO_MORE -> {
                binding.footerRotateLoading.gone()
                binding.footerTvMsg.text = getString(R.string.paragraph_comment_no_more)
                binding.llFooter.visible()
            }
            FooterState.FAILED -> {
                binding.footerRotateLoading.gone()
                binding.footerTvMsg.text = getString(R.string.paragraph_comment_load_failed)
                binding.llFooter.visible()
            }
        }
    }

    private fun showMsg(msg: String) {
        binding.tvMsg.text = msg
        binding.tvMsg.visible()
    }

    private fun hideMsg() {
        binding.tvMsg.gone()
    }

    companion object {
        private val DEFAULT_IDS = listOf("$.Id", "$.CommentId", "$.ReviewId", "$.Cid")
        private val DEFAULT_ROOT_IDS = listOf("$.RootReviewId", "$.RootId", "$.CommentId", "$.Id")
        private val DEFAULT_NICKNAMES = listOf("$.NickName", "$.UserName", "$.Name", "$.Uname")
        private val DEFAULT_AVATARS = listOf("$.UserHeadIcon", "$.UserPhoto", "$.Avatar", "$.HeadIcon", "$.Photo")
        private val DEFAULT_LEVELS = listOf("$.ShowTag", "$.Level", "$.UserLevel", "$.Grade")
        private val DEFAULT_IPS = listOf("$.IpLocation", "$.Ip", "$.Location", "$.Region")
        private val DEFAULT_CONTENTS = listOf("$.Content", "$.Text", "$.Msg", "$.ImageMeaning")
        private val DEFAULT_AGREES = listOf("$.AgreeAmount", "$.AgreeCount", "$.LikeCount", "$.LikeAmount", "$.Up")
        private val DEFAULT_OPPOSES = listOf("$.OpposeAmount", "$.OpposeCount", "$.Down")
        private val DEFAULT_TIMES = listOf("$.CreateTime", "$.Time", "$.CreatedAt", "$.CreateDate")
        private val DEFAULT_FLOORS = listOf("$.Floor", "$.FloorNum", "$.FloorNumber")
        private val DEFAULT_REPLY_COUNTS = listOf("$.ReviewCount", "$.ReplyCount", "$.ReplyNum", "$.SubCount")
        private val DEFAULT_REPLY_TOS = listOf("$.RelatedUser", "$.ReplyToUser", "$.ToUserName", "$.ReplyName")

        /** 评论图片字段候选（大小写不敏感匹配，ImgInfo 为起点系真实字段；不含 FrameUrl——那是用户头像框，不是配图） */
        private val IMAGE_FIELD_CANDIDATES = listOf(
            "ImgInfo", "ImageDetail", "PreImage", "ImageUrl", "Images", "ImageList",
            "ImgUrl", "Imgs", "Image", "CommentImg", "CommentImage", "Photo"
        )
        /** 评论语音字段候选（大小写不敏感匹配；起点系无 AudioUrl，用 AudioRoleId/AudioTime 标记语音评论） */
        private val AUDIO_FIELD_CANDIDATES = listOf(
            "AudioUrl", "VoiceUrl", "Audio", "Voice", "SoundUrl",
            "AudioRoleId", "AudioTime", "HotAudioStatus"
        )

        /** 旧版段评 pclick（java.showBrowser('',d)）里的段号 */
        private val OLD_PARAGRAPH_ID_REGEX = Regex("""paragraph_id[=:]\s*(\d+)""")
        /** 段评接口端点（如 https://host/qd/comments.php） */
        private val OLD_COMMENTS_ENDPOINT_REGEX =
            Regex("""https?://[^'"\s]*?(?:comments?|reviews?)\.[a-z]+""", RegexOption.IGNORE_CASE)

        /**
         * 兼容早期 AI 生成书源：pclick 用 `java.showBrowser('', d)` 展示纯文本段评。
         * 这里从 pclick 中提取段评接口地址与段号，重新以结构化接口请求并打开原生段评弹窗
         * （头像/昵称/内容/点赞/时间/分页/回复展开），避免弹出旧的纯文本拼接弹窗。
         * 返回 true 表示已接管（原 pclick 不再执行）；false 表示不适用，仍走原逻辑。
         */
        fun tryUpgradeOldPclick(
            activity: AppCompatActivity,
            source: BaseSource,
            book: Book,
            chapter: BookChapter,
            click: String
        ): Boolean {
            if (!click.contains("showBrowser", ignoreCase = true)) return false
            if (!click.contains("action=paragraph", ignoreCase = true)) return false
            val pid = OLD_PARAGRAPH_ID_REGEX.find(click)?.groupValues?.get(1) ?: return false
            val endpoint = OLD_COMMENTS_ENDPOINT_REGEX.find(click)?.value?.trimEnd('?', '&') ?: return false
            // 与书源内 pclick 相同的取参逻辑：从 book/chapter 的 URL 中抠出 id
            val bookId = book.bookUrl.split("book_id=").getOrNull(1)?.substringBefore("&").orEmpty()
            val chapterId = chapter.url.split("chapter_id=").getOrNull(1)?.substringBefore("&").orEmpty()
            if (bookId.isBlank() || chapterId.isBlank()) return false
            val commentsUrl = "$endpoint?action=paragraph&book_id=$bookId&chapter_id=$chapterId" +
                "&paragraph_id=$pid&type=text&page=[page]&page_size=[pageSize]"
            val repliesUrl = "$endpoint?action=replies&book_id=$bookId&chapter_id=$chapterId" +
                "&review_id=[reviewId]&root_review_id=[rootId]&page=1&page_size=[pageSize]"
            val config = ParagraphCommentConfig(
                listPath = "$.Data.DataList",
                totalPath = "$.Data.TotalCount",
                commentsUrl = commentsUrl,
                repliesUrl = repliesUrl
            )
            activity.runOnUiThread {
                activity.showDialogFragment(ParagraphCommentDialog(source.getKey(), config))
            }
            return true
        }
    }
}
