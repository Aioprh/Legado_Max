package io.legado.app.ui.config.theme.legacy.appTheme

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.TextView
import androidx.core.content.ContextCompat
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.data.repository.CoverGalleryRepository
import io.legado.app.databinding.ActivityApplicationThemeEditBinding
import io.legado.app.help.config.ApplicationThemeManager
import io.legado.app.help.config.NavigationBarConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.help.config.TopBarConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.selector
import io.legado.app.lib.theme.accentColor
import io.legado.app.utils.applyNavigationBarPadding
import io.legado.app.utils.dpToPx
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding

/**
 * 应用主题编辑 Activity。
 *
 * 编辑单个应用主题方案的各组件配置：日间/夜间主题、顶栏、底栏、封面图集。
 * 每个组件通过选择器对话框从已有配置中挑选，可设为「不设置」跳过该维度。
 */
class ApplicationThemeEditActivity : BaseActivity<ActivityApplicationThemeEditBinding>() {

    override val binding by viewBinding(ActivityApplicationThemeEditBinding::inflate)
    private var config: ApplicationThemeManager.Config? = null

    /**
     * Activity 创建时的初始化。
     * 加载指定 ID 的主题配置，设置标题、绑定操作按钮并渲染界面。
     */
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        val id = intent.getStringExtra(EXTRA_ID).orEmpty()
        config = runCatching { ApplicationThemeManager.find(id) }.getOrElse {
            toastOnUi(it.localizedMessage ?: getString(R.string.error))
            finish()
            return
        }
        if (config == null) {
            toastOnUi(R.string.error)
            finish()
            return
        }
        binding.titleBar.title = getString(R.string.application_theme_edit)
        // 底部操作区避让系统导航栏
        binding.root.getChildAt(binding.root.childCount - 1)?.applyNavigationBarPadding(withInitialPadding = true)
        bindActions()
        styleBottomButtons()
        render()
    }

    /**
     * 设置底部保存按钮的样式。
     * 使用圆角矩形背景并设置强调色边框和文字颜色。
     */
    private fun styleBottomButtons() = binding.run {
        val accent = accentColor
        val saveBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 8f.dpToPx()
            setColor(ContextCompat.getColor(this@ApplicationThemeEditActivity, R.color.background_add_button))
            setStroke(1.dpToPx(), accent)
        }
        btnSave.background = saveBg
        btnSave.setTextColor(accent)
    }

    /**
     * 绑定各组件行和底部按钮的点击事件。
     * 每行点击后弹出对应组件的选择器对话框。
     */
    private fun bindActions() = binding.run {
        rowDayTheme.setOnClickListener { selectTheme(false) }
        rowNightTheme.setOnClickListener { selectTheme(true) }
        rowDayTopBar.setOnClickListener { selectTopBar(false) }
        rowNightTopBar.setOnClickListener { selectTopBar(true) }
        rowDayBottomBar.setOnClickListener { selectBottomBar(false) }
        rowNightBottomBar.setOnClickListener { selectBottomBar(true) }
        rowDayCover.setOnClickListener { selectCover(false) }
        rowNightCover.setOnClickListener { selectCover(true) }
        btnSave.setOnClickListener { save() }
        btnDelete.setOnClickListener { confirmDelete() }
    }

    /**
     * 渲染界面，将当前配置的各组件名称显示到对应的行。
     *
     * @param updateName 是否更新名称输入框，默认为 true
     */
    private fun render(updateName: Boolean = true) {
        val item = config ?: return
        if (updateName) binding.etName.setText(item.name)
        setRow(binding.rowDayTheme, R.string.application_theme_component_theme, item.dayTheme?.themeName)
        setRow(binding.rowNightTheme, R.string.application_theme_component_theme, item.nightTheme?.themeName)
        setRow(binding.rowDayTopBar, R.string.application_theme_component_top_bar, topBarName(false, item.dayTopBarDir))
        setRow(binding.rowNightTopBar, R.string.application_theme_component_top_bar, topBarName(true, item.nightTopBarDir))
        setRow(binding.rowDayBottomBar, R.string.application_theme_component_bottom_bar, bottomBarName(false, item.dayBottomBarId))
        setRow(binding.rowNightBottomBar, R.string.application_theme_component_bottom_bar, bottomBarName(true, item.nightBottomBarId))
        val covers = CoverGalleryRepository()
        setRow(binding.rowDayCover, R.string.application_theme_component_cover, covers.getGroupName(item.dayCoverGroupId))
        setRow(binding.rowNightCover, R.string.application_theme_component_cover, covers.getGroupName(item.nightCoverGroupId))
    }

    /**
     * 设置组件行的显示文本。
     * 格式为「组件名: 值」，未设置时显示「不设置」。
     *
     * @param view     要设置文本的 TextView
     * @param titleRes 组件名称的字符串资源 ID
     * @param value    当前选中的值，为 null 表示未设置
     */
    private fun setRow(view: TextView, titleRes: Int, value: String?) {
        view.text = getString(
            R.string.application_theme_component_value,
            getString(titleRes),
            value ?: getString(R.string.application_theme_not_set)
        )
    }

    /**
     * 显示主题选择器对话框。
     * 从已有主题配置中挑选，可设为「不设置」。
     *
     * @param isNight 是否选择夜间主题
     */
    private fun selectTheme(isNight: Boolean) {
        val options = ThemeConfig.configList.filter { it.isNightTheme == isNight }
        val labels = listOf(getString(R.string.application_theme_not_set)) + options.map { it.themeName }
        selector(getString(R.string.application_theme_component_theme), labels) { _, index ->
            val item = config ?: return@selector
            val selected = options.getOrNull(index - 1)?.copy()
            config = if (isNight) item.copy(nightTheme = selected) else item.copy(dayTheme = selected)
            render(updateName = false)
        }
    }

    /**
     * 显示顶栏配置选择器对话框。
     * 从已有顶栏配置中挑选，可设为「不设置」。
     *
     * @param isNight 是否选择夜间顶栏
     */
    private fun selectTopBar(isNight: Boolean) {
        val options = TopBarConfig.loadEntries(this, isNight)
        val labels = listOf(getString(R.string.application_theme_not_set)) + options.map { it.config.name }
        selector(getString(R.string.application_theme_component_top_bar), labels) { _, index ->
            val item = config ?: return@selector
            val selected = options.getOrNull(index - 1)?.dirName.orEmpty()
            config = if (isNight) item.copy(nightTopBarDir = selected) else item.copy(dayTopBarDir = selected)
            render(updateName = false)
        }
    }

    /**
     * 显示底栏配置选择器对话框。
     * 从已有底栏配置中挑选，可设为「不设置」。
     *
     * @param isNight 是否选择夜间底栏
     */
    private fun selectBottomBar(isNight: Boolean) {
        val options = NavigationBarConfig.loadConfigs(this).filter { it.isNight == isNight }
        val labels = listOf(getString(R.string.application_theme_not_set)) + options.map { it.name }
        selector(getString(R.string.application_theme_component_bottom_bar), labels) { _, index ->
            val item = config ?: return@selector
            val selected = options.getOrNull(index - 1)?.id
            config = if (isNight) item.copy(nightBottomBarId = selected) else item.copy(dayBottomBarId = selected)
            render(updateName = false)
        }
    }

    /**
     * 显示封面图集选择器对话框。
     * 从已有封面图集中挑选，可设为「不设置」。
     *
     * @param isNight 是否选择夜间封面图集
     */
    private fun selectCover(isNight: Boolean) {
        val options = CoverGalleryRepository().allGroupsWithImages().map { it.group }
        val labels = listOf(getString(R.string.application_theme_not_set)) + options.map { it.name }
        selector(getString(R.string.application_theme_component_cover), labels) { _, index ->
            val item = config ?: return@selector
            val selected = options.getOrNull(index - 1)?.id
            config = if (isNight) item.copy(nightCoverGroupId = selected) else item.copy(dayCoverGroupId = selected)
            render(updateName = false)
        }
    }

    /**
     * 保存当前编辑的主题配置。
     * 校验名称非空后替换原有配置并关闭页面。
     */
    private fun save() {
        val item = config ?: return
        val name = binding.etName.text.toString().trim()
        if (name.isBlank()) {
            toastOnUi(R.string.input_is_empty)
            return
        }
        runCatching {
            ApplicationThemeManager.replace(item.copy(name = name, updatedAt = System.currentTimeMillis()))
        }.onSuccess {
            toastOnUi(R.string.success)
            finish()
        }.onFailure {
            toastOnUi(R.string.application_theme_name_exists)
        }
    }

    /**
     * 显示删除确认对话框。
     * 用户确认后删除当前配置并关闭页面。
     */
    private fun confirmDelete() {
        alert(R.string.delete, R.string.sure_del) {
            okButton {
                config?.let { ApplicationThemeManager.delete(this@ApplicationThemeEditActivity, it.id) }
                finish()
            }
            cancelButton()
        }
    }

    /**
     * 根据目录名获取顶栏配置的显示名称。
     *
     * @param isNight 是否为夜间模式
     * @param dirName 顶栏配置目录名
     * @return 顶栏配置名称，未找到时返回 null
     */
    private fun topBarName(isNight: Boolean, dirName: String): String? {
        if (dirName.isBlank()) return null
        return TopBarConfig.loadEntries(this, isNight).firstOrNull { it.dirName == dirName }?.config?.name
    }

    /**
     * 根据 ID 获取底栏配置的显示名称。
     *
     * @param isNight 是否为夜间模式
     * @param id      底栏配置 ID
     * @return 底栏配置名称，未找到时返回 null
     */
    private fun bottomBarName(isNight: Boolean, id: String?): String? {
        return NavigationBarConfig.loadConfigs(this).firstOrNull { it.isNight == isNight && it.id == id }?.name
    }

    companion object {
        const val EXTRA_ID = "applicationThemeId"
    }
}
