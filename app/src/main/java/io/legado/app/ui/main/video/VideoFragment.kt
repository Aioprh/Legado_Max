package io.legado.app.ui.main.video

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.flexbox.FlexboxLayout
import io.legado.app.R
import io.legado.app.constant.BookSourceType
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.help.config.AppConfig
import io.legado.app.help.source.exploreKinds
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.ui.book.explore.ExploreShowActivity
import io.legado.app.ui.main.MainFragmentInterface
import io.legado.app.ui.main.MainActivity
import io.legado.app.ui.widget.SourceSelectDialog
import io.legado.app.utils.dpToPx
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 独立影视首页。
 *
 * 影视内容不内置任何“动漫/电影/电视剧”等分类。
 * 只读取用户启用的 BookSourceType.video 书源，并从该书源的 exploreUrl
 * 动态解析分类。这样不同影视书源可以提供完全不同的分类结构。
 */
class VideoFragment() : Fragment(), MainFragmentInterface {

    constructor(position: Int) : this() {
        arguments = Bundle().apply { putInt("position", position) }
    }

    override val position: Int? get() = arguments?.getInt("position")

    private lateinit var root: LinearLayout
    private lateinit var sourceButton: TextView
    private lateinit var categoryContainer: FlexboxLayout
    private lateinit var sourceHint: TextView

    private var currentSource: BookSourcePart? = null
    private var currentKinds: List<ExploreKind> = emptyList()

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
        }

        val header = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(18.dpToPx(), 12.dpToPx(), 18.dpToPx(), 8.dpToPx())
        }

        sourceButton = TextView(requireContext()).apply {
            textSize = 18f
            setTextColor(primaryTextColor)
            setPadding(16.dpToPx(), 10.dpToPx(), 16.dpToPx(), 10.dpToPx())
            background = requireContext().getDrawable(R.drawable.bg_popup_menu)
            isClickable = true
            setOnClickListener { chooseSource() }
        }
        header.addView(sourceButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val search = TextView(requireContext()).apply {
            text = "⌕"
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(primaryTextColor)
            setPadding(10.dpToPx())
            setOnClickListener {
                toastOnUi("影视搜索将使用当前影视书源")
            }
        }
        header.addView(search, LinearLayout.LayoutParams(52.dpToPx(), 52.dpToPx()))

        root.addView(header)

        sourceHint = TextView(requireContext()).apply {
            textSize = 14f
            setTextColor(primaryTextColor)
            setPadding(18.dpToPx(), 4.dpToPx(), 18.dpToPx(), 6.dpToPx())
            visibility = View.GONE
        }
        root.addView(sourceHint)

        val categoryScroll = HorizontalScrollView(requireContext()).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            setPadding(14.dpToPx(), 4.dpToPx(), 14.dpToPx(), 10.dpToPx())
        }
        categoryContainer = FlexboxLayout(requireContext()).apply {
            flexWrap = com.google.android.flexbox.FlexWrap.NOWRAP
        }
        categoryScroll.addView(categoryContainer)
        root.addView(categoryScroll)

        val title = TextView(requireContext()).apply {
            text = "影视"
            textSize = 24f
            setTextColor(primaryTextColor)
            setPadding(20.dpToPx(), 12.dpToPx(), 20.dpToPx(), 8.dpToPx())
        }
        root.addView(title)

        val desc = TextView(requireContext()).apply {
            text = "内容、分类和目录均来自当前影视书源"
            textSize = 14f
            setTextColor(primaryTextColor)
            alpha = 0.68f
            setPadding(20.dpToPx(), 0, 20.dpToPx(), 20.dpToPx())
        }
        root.addView(desc)

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updateMainBottomPadding((activity as? MainActivity)?.mainContentBottomPadding() ?: 0)
        loadSources()
    }

    override fun onResume() {
        super.onResume()
        if (view != null) loadSources()
    }

    private fun loadSources() {
        lifecycleScope.launch {
            val sources = withContext(IO) {
                appDb.bookSourceDao.allEnabledPart
                    .filter {
                        it.enabled &&
                            it.bookSourceType == BookSourceType.video &&
                            it.enabledExplore &&
                            it.hasExploreUrl
                    }
                    .sortedBy { it.customOrder }
            }
            if (!isAdded) return@launch
            if (sources.isEmpty()) {
                currentSource = null
                sourceButton.text = "影视书源"
                sourceHint.text = "暂无启用的影视书源，请先导入并启用视频类型书源"
                sourceHint.visibility = View.VISIBLE
                categoryContainer.removeAllViews()
                return@launch
            }
            val saved = AppConfig.videoSourceUrl
            currentSource = sources.firstOrNull { it.bookSourceUrl == saved } ?: sources.first()
            sourceHint.visibility = View.GONE
            sourceButton.text = currentSource?.getDisPlayNameGroup() ?: "影视书源"
            loadCategories(currentSource)
        }
    }

    private fun chooseSource() {
        lifecycleScope.launch {
            val sources = withContext(IO) {
                appDb.bookSourceDao.allEnabledPart
                    .filter {
                        it.enabled &&
                            it.bookSourceType == BookSourceType.video &&
                            it.enabledExplore &&
                            it.hasExploreUrl
                    }
                    .sortedBy { it.customOrder }
            }
            if (sources.isEmpty()) {
                toastOnUi("暂无可用的影视书源")
                return@launch
            }
            SourceSelectDialog.show(
                requireContext(),
                "选择影视书源",
                sources,
                currentSource?.bookSourceUrl,
                { it.getDisPlayNameGroup() },
                { listOf(it.bookSourceName, it.bookSourceGroup.orEmpty()) },
                "搜索影视书源",
                { it.bookSourceUrl },
                { selected ->
                    currentSource = selected
                    AppConfig.videoSourceUrl = selected.bookSourceUrl
                    sourceButton.text = selected.getDisPlayNameGroup()
                    loadCategories(selected)
                }
            )
        }
    }

    private fun loadCategories(part: BookSourcePart?) {
        if (part == null) return
        lifecycleScope.launch {
            val kinds = withContext(IO) {
                runCatching { part.exploreKinds() }
                    .getOrElse { emptyList() }
                    .filter { !it.url.isNullOrBlank() }
            }
            if (!isAdded || currentSource?.bookSourceUrl != part.bookSourceUrl) return@launch
            currentKinds = kinds
            renderCategories(kinds)
        }
    }

    private fun renderCategories(kinds: List<ExploreKind>) {
        categoryContainer.removeAllViews()
        if (kinds.isEmpty()) {
            sourceHint.text = "当前书源没有提供可解析的影视分类"
            sourceHint.visibility = View.VISIBLE
            return
        }
        sourceHint.visibility = View.GONE
        kinds.forEachIndexed { index, kind ->
            val chip = TextView(requireContext()).apply {
                text = kind.title
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(primaryTextColor)
                setPadding(18.dpToPx(), 9.dpToPx(), 18.dpToPx(), 9.dpToPx())
                background = requireContext().getDrawable(
                    if (index == 0) R.drawable.bg_video_chapter_item else R.drawable.bg_popup_menu
                )
                setOnClickListener { openCategory(kind) }
            }
            categoryContainer.addView(
                chip,
                FlexboxLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    rightMargin = 8.dpToPx()
                }
            )
        }
    }

    private fun openCategory(kind: ExploreKind) {
        val source = currentSource ?: return
        val url = kind.url ?: return
        startActivity<ExploreShowActivity> {
            putExtra("sourceUrl", source.bookSourceUrl)
            putExtra("exploreUrl", url)
            putExtra("exploreName", kind.title)
        }
    }

    override fun updateMainBottomPadding(bottomPadding: Int) {
        if (::root.isInitialized) root.setPadding(
            root.paddingLeft,
            root.paddingTop,
            root.paddingRight,
            bottomPadding
        )
    }
}
