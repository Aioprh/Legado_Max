package io.legado.app.ui.main.homepage

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import io.legado.app.ui.book.info.BookInfoActivity
import io.legado.app.ui.book.explore.ExploreShowActivity
import io.legado.app.ui.main.MainFragmentInterface
import io.legado.app.ui.theme.LegadoTheme

/**
 * 首页 Fragment
 *
 * 作为首页在 MainActivity 中的容器，使用 ComposeView 承载 Compose 界面。
 * 通过 LegadoTheme 包裹内容，确保主题颜色统一适配。
 * 处理书籍点击（跳转 BookInfoActivity）和模块标题点击（跳转 ExploreShowActivity）的导航逻辑。
 */
class HomepageFragment() : Fragment(), MainFragmentInterface {

    constructor(position: Int) : this() {
        val bundle = Bundle()
        bundle.putInt("position", position)
        arguments = bundle
    }

    override val position: Int? get() = arguments?.getInt("position")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                LegadoTheme {
                    HomepageScreen(
                        onBookClick = { name, author, bookUrl, origin, coverPath ->
                            val intent = Intent(context, BookInfoActivity::class.java).apply {
                                putExtra("name", name)
                                putExtra("author", author)
                                putExtra("bookUrl", bookUrl)
                                origin?.let { putExtra("origin", it) }
                                coverPath?.let { putExtra("coverPath", it) }
                            }
                            startActivity(intent)
                        },
                        onModuleHeaderClick = { title, sourceUrl, exploreUrl ->
                            if (exploreUrl.isNullOrBlank()) return@HomepageScreen
                            val intent = Intent(context, ExploreShowActivity::class.java).apply {
                                putExtra("exploreName", title ?: "")
                                putExtra("sourceUrl", sourceUrl)
                                putExtra("exploreUrl", exploreUrl)
                            }
                            startActivity(intent)
                        },
                    )
                }
            }
        }
    }
}
