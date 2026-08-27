package io.legado.app.ui.widget.dialog

import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import android.widget.PopupMenu
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
    private var footerState = FooterState.NONE
    // 接口原始顺序（跨页累积），供切换排序时恢复
    private val rawItems = mutableListOf<ParagraphCommentItem>()
    // 排序模式：实时=接口原始顺序；最新=神评论置顶+按时间由新到旧；回复最多=按回复数降序
    private enum class SortMode { REALTIME, NEWEST, HOT }
    private var sortMode = SortMode.NEWEST

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
            // 排序切换：齿轮图标弹出菜单选择 实时评论 / 最新评论 / 回复最多
            tvSort.setOnClickListener { view -> showSortMenu(view) }
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
            // 加载失败点击重试 / 加载更多点击翻页
            llFooter.setOnClickListener {
                if (loading) return@setOnClickListener
                // 仅在有更多（点击继续加载）或加载失败（点击重试）时可点；
                // 真正的"没有更多了"点击不再触发加载
                if (hasMore || footerState == FooterState.FAILED) {
                    loadPage(page + 1)
                }
            }
        }
        adapter.replyListener = object : ParagraphCommentAdapter.ReplyListener {
            override fun onToggleReplies(item: ParagraphCommentItem) {
                if (item.repliesLoaded) {
                    item.repliesLoaded = false
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
        adapter.imageListener = object : ParagraphCommentAdapter.ImageListener {
            override fun onImageClick(url: String) {
                showDialogFragment(PhotoDialog(url))
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
            val url = buildCommentsUrl(nextPage)
            val body = fetchBody(url)
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
                    rawItems.clear()
                    rawItems.addAll(items)
                    adapter.setItems(
                        if (sortMode != SortMode.REALTIME) rawItems.sortedWith(sortComparator())
                        else rawItems
                    )
                } else {
                    rawItems.addAll(items)
                    appendPageItems(items)
                }
                if (newTotal >= 0) total = newTotal
                updateTitle()
                // 无更多判断：优先用接口返回的 hasNext 标志（pagination.hasNextPage/hasNext）。
                // 部分站点（如神魔番茄）的 totalCount 不可靠、或每页返回条数不足 pageSize，
                // 用"条数>=页大小 && 已加载<总数"推断会提前显示"没有更多了"，
                // 而接口的 hasNext 标志是权威的（站点自己的前端也用它）。
                val hasNextFlag = body?.let { parseHasNext(it) }
                hasMore = hasNextFlag ?: (items.isNotEmpty() &&
                    items.size >= config.pageSize &&
                    (total < 0 || adapter.getActualItemCount() < total))
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

    /** 最新模式排序：神评论置顶，其余按时间由新到旧（time=0 的未知时间排最后） */
    private fun sortComparator(): Comparator<ParagraphCommentItem> = when (sortMode) {
        SortMode.NEWEST -> compareByDescending<ParagraphCommentItem> { it.isGod }
            .thenByDescending { it.time }
        SortMode.HOT -> compareByDescending<ParagraphCommentItem> { it.replyCount }
            .thenByDescending { it.time }
        SortMode.REALTIME -> Comparator { _, _ -> 0 }
    }

    /** 顶栏齿轮图标点击弹排序菜单：实时评论 / 最新评论 / 回复最多 */
    private fun showSortMenu(anchor: View) {
        val menu = PopupMenu(requireContext(), anchor)
        menu.menu.add(0, 1, 0, R.string.paragraph_comment_sort_newest).apply {
            isCheckable = true
            isChecked = sortMode == SortMode.NEWEST
        }
        menu.menu.add(0, 2, 1, R.string.paragraph_comment_sort_realtime).apply {
            isCheckable = true
            isChecked = sortMode == SortMode.REALTIME
        }
        menu.menu.add(0, 3, 2, R.string.paragraph_comment_sort_hot).apply {
            isCheckable = true
            isChecked = sortMode == SortMode.HOT
        }
        menu.setOnMenuItemClickListener { item ->
            sortMode = when (item.itemId) {
                1 -> SortMode.NEWEST
                2 -> SortMode.REALTIME
                else -> SortMode.HOT
            }
            applySort()
            true
        }
        menu.show()
    }

    /** 顶栏居中标题实时显示评论总数（总数未知时退化为已加载条数） */
    private fun updateTitle() {
        val count = if (total >= 0) total else adapter.getActualItemCount().toLong()
        binding.toolBar.title = if (count > 0) {
            getString(R.string.paragraph_comment_total, count)
        } else {
            getString(R.string.paragraph_comment_title)
        }
    }

    /** 切换排序模式后整表重排（以接口原始顺序 rawItems 为基准） */
    private fun applySort() {
        if (sortMode == SortMode.REALTIME) {
            adapter.setItems(rawItems)
        } else {
            adapter.setItems(rawItems.sortedWith(sortComparator()))
        }
    }

    /** 追加下一页：实时模式直接末尾追加；最新模式按序插入，保持整体有序且不重置滚动位置 */
    private fun appendPageItems(newItems: List<ParagraphCommentItem>) {
        if (newItems.isEmpty()) return
        if (sortMode == SortMode.REALTIME) {
            adapter.addItems(newItems)
            return
        }
        val comparator = sortComparator()
        newItems.forEach { item ->
            val items = adapter.getItems()
            val idx = items.indexOfFirst { comparator.compare(it, item) < 0 }
            if (idx < 0) {
                adapter.addItem(item)
            } else {
                adapter.addItems(idx, listOf(item))
            }
        }
    }

    private fun parseComments(body: String): List<ParagraphCommentItem> {
        return runCatching {
            val rc = jsonPath.parse(body)
            val listPath = config.listPath.ifBlank { "$.Data.DataList" }
            val list = rc.read<List<Any?>>(listPath) ?: return@runCatching emptyList()
            val maps = list.mapNotNull { it as? Map<*, *> }
            val items = maps.map { map ->
                val content = readStr(map, config.fields.content, DEFAULT_CONTENTS)
                val images = readImages(map)
                val audio = readAudio(map)
                val inlineReplies = readInlineReplies(map)
                ParagraphCommentItem(
                    id = readStr(map, config.fields.id, DEFAULT_IDS),
                    rootId = readStr(map, config.fields.rootId, DEFAULT_ROOT_IDS),
                    nickname = readStr(map, config.fields.nickname, DEFAULT_NICKNAMES),
                    avatar = readStr(map, config.fields.avatar, DEFAULT_AVATARS),
                    level = readStr(map, config.fields.level, DEFAULT_LEVELS),
                    ip = readStr(map, config.fields.ip, DEFAULT_IPS),
                    content = content,
                    images = images,
                    audio = audio,
                    agree = readLong(map, config.fields.agree, DEFAULT_AGREES),
                    oppose = readLong(map, config.fields.oppose, DEFAULT_OPPOSES),
                    time = readTime(map, config.fields.time, DEFAULT_TIMES),
                    floor = readInt(map, config.fields.floor, DEFAULT_FLOORS),
                    replyCount = maxOf(
                        readInt(map, config.fields.replyCount, DEFAULT_REPLY_COUNTS),
                        inlineReplies.size
                    ),
                    isGod = isGodComment(map),
                    replies = inlineReplies.toMutableList()
                )
            }
            // 过滤完全无内容的空评论（无文字/图片/语音，如起点 ReviewType=4 特殊类型），避免显示“匿名用户+空白”；
            // 排序交由 [applySort] 按当前模式处理，这里保持接口原始顺序
            items.filter { item ->
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

    /**
     * 读取接口返回的"是否还有下一页"标志（pagination.hasNextPage / hasNext）。
     * 部分站点（如神魔番茄）的 totalCount 不可靠，分页必须以此为准；取不到返回 null，
     * 由调用方退回条数推断。
     */
    private fun parseHasNext(body: String): Boolean? {
        return runCatching {
            val rc = jsonPath.parse(body)
            val pagination = runCatching { rc.read<Any>("$.data.pagination") as? Map<*, *> }
                .getOrNull()
                ?: runCatching { rc.read<Any>("$.Data.Pagination") as? Map<*, *> }.getOrNull()
            val v = pagination?.let { findKey(it, "hasNextPage") ?: findKey(it, "hasNext") }
                ?: return@runCatching null
            when (v) {
                is Boolean -> v
                is Number -> v.toInt() > 0
                else -> v.toString().toBooleanStrictOrNull()
            }
        }.getOrNull()
    }

    // ---------- 回复 ----------

    private fun loadReplies(item: ParagraphCommentItem) {
        if (item.repliesLoading) return
        // 评论无 ID（番茄主楼评论没有 Id/CommentId 字段）但自带内嵌回复：
        // 直接展开内嵌预览，避免用空 commentId 请求 comment-replies 接口后一片空白
        if (item.id.isBlank() && item.replies.isNotEmpty()) {
            item.repliesLoaded = true
            adapter.updateItem(item)
            return
        }
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
                if (replies.isNotEmpty()) {
                    item.replies.clear()
                    item.replies.addAll(replies)
                }
                // 接口失败/空响应时保留评论自带的内嵌回复预览，避免点击后一片空白
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
                    ?: map["comment_id"]?.toString()
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
            val listPath = config.replyListPath.ifBlank { config.listPath.ifBlank { "$.Data.DataList" } }
            val list = rc.read<List<Any?>>(listPath) ?: return@runCatching emptyList()
            list.mapNotNull { it as? Map<*, *> }.map { map ->
                ParagraphReplyItem(
                    nickname = readStr(map, config.replyFields.nickname, DEFAULT_NICKNAMES),
                    avatar = readStr(map, config.replyFields.avatar, DEFAULT_AVATARS),
                    replyTo = readStr(map, config.replyFields.replyTo, DEFAULT_REPLY_TOS),
                    content = readStr(map, config.replyFields.content, DEFAULT_CONTENTS),
                    images = readImages(map),
                    audio = readAudio(map),
                    agree = readLong(map, config.replyFields.agree, DEFAULT_AGREES),
                    time = readTime(map, config.replyFields.time, DEFAULT_TIMES)
                )
            }.filter { reply ->
                // 过滤完全无内容的空回复
                reply.content.isNotBlank() || reply.images.isNotEmpty() || reply.audio.isNotBlank()
            }.sortedByDescending { it.time } // 回复同样按时间由新到旧排序
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
            // 去除响应体开头的 UTF-8 BOM：部分接口（如 pl.aadcn.cn）返回带 BOM 的 JSON，
            // 直接交给 json-smart 解析会导致路径读取返回 null（静默变空，无异常抛出）。
            analyzeUrl.getStrResponse().body?.trimStart('\uFEFF')
        }.onFailure {
            AppLog.put("段评请求失败 $url\n${it.stackTraceStr}", it)
        }.getOrNull()
    }

    private fun readStr(map: Map<*, *>, primary: String, defaults: List<String>): String {
        val paths = if (primary.isBlank()) {
            defaults
        } else {
            listOf(primary) + defaults.filter { it != primary }
        }
        for (p in paths) {
            val v = resolvePath(map, p) ?: continue
            val s = v.toString().trim()
            if (s.isNotEmpty() && s != "null") return s
        }
        return ""
    }

    private fun readLong(map: Map<*, *>, primary: String, defaults: List<String>): Long {
        val paths = if (primary.isBlank()) {
            defaults
        } else {
            listOf(primary) + defaults.filter { it != primary }
        }
        for (p in paths) {
            val v = resolvePath(map, p) ?: continue
            when (v) {
                is Number -> return v.toLong()
                else -> v.toString().toLongOrNull()?.let { return it }
            }
        }
        return 0
    }

    /**
     * 时间字段解析：兼容三种格式
     * 1. 数字（秒/毫秒时间戳）
     * 2. 纯数字字符串（秒/毫秒时间戳）
     * 3. 日期字符串（如 "2024-01-01 12:00:00"），解析为毫秒时间戳
     * 神魔小说番茄段评的 CreateTime 即为日期字符串，数字解析会静默得 0。
     */
    private fun readTime(map: Map<*, *>, primary: String, defaults: List<String>): Long {
        val paths = if (primary.isBlank()) {
            defaults
        } else {
            listOf(primary) + defaults.filter { it != primary }
        }
        for (p in paths) {
            val v = resolvePath(map, p) ?: continue
            val ts = when (v) {
                is Number -> toEpochMillis(v.toLong())
                is Boolean -> null
                else -> {
                    val s = v.toString().trim()
                    when {
                        s.isEmpty() || s == "null" -> null
                        else -> s.toLongOrNull()?.let { toEpochMillis(it) }
                            ?: parseDateString(s)
                    }
                }
            }
            if (ts != null && ts > 0) return ts
        }
        return 0
    }

    /** 兼容秒/毫秒时间戳：小于 1e11 视为秒级，统一转毫秒 */
    private fun toEpochMillis(ts: Long): Long {
        return if (ts in 1 until 100_000_000_000L) ts * 1000 else ts
    }

    /** 解析 "yyyy-MM-dd HH:mm:ss" / "yyyy-MM-dd HH:mm" / "yyyy-MM-dd"（兼容 / 与 T 分隔符）为毫秒时间戳 */
    private fun parseDateString(s: String): Long? {
        val clean = s.trim().replace('T', ' ').replace('/', '-')
        val m = DATE_STR_REGEX.matchEntire(clean) ?: return null
        return runCatching {
            val (y, mo, d, h, mi, se) = m.destructured
            java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.YEAR, y.toInt())
                set(java.util.Calendar.MONTH, mo.toInt() - 1)
                set(java.util.Calendar.DAY_OF_MONTH, d.toInt())
                set(java.util.Calendar.HOUR_OF_DAY, h.toIntOrNull() ?: 0)
                set(java.util.Calendar.MINUTE, mi.toIntOrNull() ?: 0)
                set(java.util.Calendar.SECOND, se.toIntOrNull() ?: 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
        }.getOrNull()
    }

    private fun readInt(map: Map<*, *>, primary: String, defaults: List<String>): Int {
        return readLong(map, primary, defaults).toInt()
    }

    /**
     * 解析形如 `$.user_info.user_name` / `$.list[0].name` 的字段路径。
     * 直接基于 Map 逐级读取（大小写不敏感），不依赖 jsonpath 对 Map 的二次解析，
     * 避免“列表能取到但字段全空”的静默失败。
     */
    private fun resolvePath(map: Map<*, *>, path: String): Any? {
        var cur: Any? = map
        for (tok in tokenize(path)) {
            if (tok == "$") continue
            cur = when (cur) {
                is Map<*, *> -> findKey(cur, tok)
                is List<*> -> {
                    val idx = tok.removePrefix("[").removeSuffix("]").toIntOrNull() ?: return null
                    if (idx in cur.indices) cur[idx] else null
                }
                else -> return null
            } ?: return null
        }
        return cur
    }

    /** 按点号拆分路径，同时把 [n] 数组下标保留为一个整体 */
    private fun tokenize(path: String): List<String> {
        val tokens = mutableListOf<String>()
        val cur = StringBuilder()
        var inBracket = false
        for (c in path) {
            when {
                c == '[' -> {
                    inBracket = true
                    cur.append(c)
                }
                c == ']' -> {
                    inBracket = false
                    cur.append(c)
                }
                c == '.' && !inBracket -> if (cur.isNotEmpty()) {
                    tokens.add(cur.toString())
                    cur.clear()
                }
                else -> cur.append(c)
            }
        }
        if (cur.isNotEmpty()) tokens.add(cur.toString())
        return tokens
    }

    /**
     * 判断评论是否为"神评论"（起点段评）：EssenceType === 2 或 IsEssence === true。
     * 与书源前端 qd 页面 isGod 的判定一致。
     */
    private fun isGodComment(map: Map<*, *>): Boolean {
        val essence = findKey(map, "EssenceType")
        if (essence is Number) {
            if (essence.toInt() == 2) return true
        } else if (essence != null) {
            essence.toString().toIntOrNull()?.let { if (it == 2) return true }
        }
        val isEssence = findKey(map, "IsEssence")
        if (isEssence is Boolean) return isEssence
        if (isEssence != null) {
            isEssence.toString().toBooleanStrictOrNull()?.let { return it }
        }
        return false
    }

    /**
     * 读取评论自带的内嵌回复预览（$.Replies / $.replies）。
     * 起点/番茄的主评论都会带 Replies 数组，点击"展开回复"可直接展示；
     * 番茄主楼评论无 Id 字段，依赖 comment-replies 接口请求不到，必须用内嵌回复兜底。
     */
    private fun readInlineReplies(map: Map<*, *>): List<ParagraphReplyItem> {
        val v = findKey(map, "Replies") ?: findKey(map, "replies") ?: return emptyList()
        val list: List<*> = when (v) {
            is List<*> -> v
            is String -> runCatching {
                GSON.fromJson<List<Any?>>(
                    v,
                    object : com.google.gson.reflect.TypeToken<List<Any?>>() {}.type
                )
            }.getOrNull() ?: return emptyList()
            else -> return emptyList()
        }
        return list.mapNotNull { it as? Map<*, *> }.map { m ->
            ParagraphReplyItem(
                nickname = readStr(m, config.replyFields.nickname, DEFAULT_NICKNAMES),
                avatar = readStr(m, config.replyFields.avatar, DEFAULT_AVATARS),
                replyTo = readStr(m, config.replyFields.replyTo, DEFAULT_REPLY_TOS),
                content = readStr(m, config.replyFields.content, DEFAULT_CONTENTS),
                images = readImages(m),
                audio = readAudio(m),
                agree = readLong(m, config.replyFields.agree, DEFAULT_AGREES),
                time = readTime(m, config.replyFields.time, DEFAULT_TIMES)
            )
        }.filter { r ->
            // 过滤完全无内容的空回复
            r.content.isNotBlank() || r.images.isNotEmpty() || r.audio.isNotBlank()
        }
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
        footerState = state
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
        private val DEFAULT_IDS = listOf("$.Id", "$.CommentId", "$.ReviewId", "$.Cid", "$.comment_id", "$.review_id", "$.cid", "$.id")
        private val DEFAULT_ROOT_IDS = listOf("$.RootReviewId", "$.RootId", "$.CommentId", "$.Id", "$.root_review_id", "$.root_comment_id", "$.rootCommentId", "$.parent_comment_id")
        private val DEFAULT_NICKNAMES = listOf("$.NickName", "$.UserName", "$.Name", "$.Uname", "$.user_name", "$.nickname", "$.comment_user.user_name", "$.comment_user.nickname", "$.user.nickname", "$.user.name", "$.user_info.user_name")
        private val DEFAULT_AVATARS = listOf("$.UserHeadIcon", "$.UserPhoto", "$.Avatar", "$.HeadIcon", "$.Photo", "$.user_avatar", "$.avatar", "$.comment_user.user_avatar", "$.comment_user.avatar", "$.user.avatar", "$.user.avatar_url", "$.user_info.user_avatar")
        private val DEFAULT_LEVELS = listOf("$.ShowTag", "$.Level", "$.UserLevel", "$.Grade")
        private val DEFAULT_IPS = listOf("$.IpLocation", "$.Ip", "$.Location", "$.Region", "$.ip_location", "$.ip_address")
        // 内容兜底必须含小写 text/content：同人小说网（pl.aadcn.cn）评论正文是 text，若 fields 未携带路径也能读到
        private val DEFAULT_CONTENTS = listOf("$.Content", "$.Text", "$.Msg", "$.ImageMeaning", "$.text", "$.content", "$.body", "$.comment_content", "$.comment_text", "$.review_content")
        private val DEFAULT_AGREES = listOf("$.AgreeAmount", "$.AgreeCount", "$.LikeCount", "$.LikeAmount", "$.Up", "$.digg_count", "$.like_count", "$.comment_like_count", "$.agree_count")
        private val DEFAULT_OPPOSES = listOf("$.OpposeAmount", "$.OpposeCount", "$.Down", "$.oppose_count", "$.oppose_amount")
        private val DEFAULT_TIMES = listOf("$.CreateTime", "$.Time", "$.CreatedAt", "$.CreateDate", "$.create_timestamp", "$.timestamp", "$.created_at", "$.create_time", "$.comment_create_time", "$.review_create_time", "$.post_time", "$.createTime", "$.createTimestamp", "$.pub_time", "$.publish_time", "$.comment_time", "$.update_time")
        private val DATE_STR_REGEX = Regex(
            """(\d{4})-(\d{1,2})-(\d{1,2})(?:[ T](\d{1,2}):(\d{1,2})(?::(\d{1,2}))?(?:\.\d+)?(?:\s*(?:Z|[+-]\d{1,2}:?\d{0,2}))?)?"""
        )
        private val DEFAULT_FLOORS = listOf("$.Floor", "$.FloorNum", "$.FloorNumber", "$.floor")
        private val DEFAULT_REPLY_COUNTS = listOf("$.ReviewCount", "$.ReplyCount", "$.ReplyNum", "$.SubCount", "$.reply_count", "$.comment_count", "$.replyCount", "$.child_count")
        private val DEFAULT_REPLY_TOS = listOf("$.RelatedUser", "$.ReplyToUser", "$.ToUserName", "$.ReplyName", "$.related_user", "$.reply_to", "$.to_user_name", "$.reply_user_name")

        /** 评论图片字段候选（大小写不敏感匹配，ImgInfo 为起点系真实字段；不含 FrameUrl——那是用户头像框，不是配图） */
        private val IMAGE_FIELD_CANDIDATES = listOf(
            "ImgInfo", "ImageDetail", "PreImage", "ImageUrl", "Images", "ImageList",
            "ImgUrl", "Imgs", "Image", "CommentImg", "CommentImage", "Photo",
            "image_url", "img_url", "image_list", "comment_images", "comment_imgs", "pic_list"
        )
        /** 评论语音字段候选（大小写不敏感匹配；起点系无 AudioUrl，用 AudioRoleId/AudioTime 标记语音评论） */
        private val AUDIO_FIELD_CANDIDATES = listOf(
            "AudioUrl", "VoiceUrl", "Audio", "Voice", "SoundUrl",
            "AudioRoleId", "AudioTime", "HotAudioStatus",
            "audio_url", "voice_url", "comment_audio", "audio_urls"
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
