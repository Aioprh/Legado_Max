package io.legado.app.ui.config.theme.manage

import android.os.Bundle
import androidx.core.view.postOnAnimation
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import io.legado.app.R
import io.legado.app.base.BaseComposeActivity
import io.legado.app.constant.PreferKey
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.widget.number.NumberPickerDialog
import io.legado.app.utils.externalFiles
import io.legado.app.utils.getFile
import io.legado.app.utils.share
import io.legado.app.utils.toastOnUi
import io.legado.app.constant.EventBus
import io.legado.app.utils.observeEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * 主题管理 Activity（Compose 版，组合模式重构）。
 *
 * 入口页面，展示日间/夜间主题列表，支持应用、编辑、删除、置顶、多选导出等操作。
 * UI 层使用 Jetpack Compose（ThemeManageScreen），ViewModel 仅负责数据操作，
 * 通用 UI 状态（Tab/多选/编辑弹窗）由 ConfigManageState 在 Composable 层持有。
 *
 * 平台侧逻辑（ColorPickerDialog、NumberPickerDialog、文件选择）由 Activity 回调处理，
 * 结果通过 lambda 回传给 Screen 层更新 draft。
 */
class ThemeManageActivity : BaseComposeActivity(), ColorPickerDialogListener {

    private lateinit var viewModel: ThemeManageViewModel

    // 当前颜色选择器对应的属性key，用于 onColorSelected 回调
    private var pendingColorKey: String? = null

    // 由 Screen 层注册的回调：颜色选择 / 虚化值 / 背景图选择结果回传
    private var onColorResult: ((String, Int) -> Unit)? = null
    private var onBlurResult: ((Int) -> Unit)? = null
    private var onBackgroundImageResult: ((String) -> Unit)? = null

    // 由 Screen 层注册：获取当前 draft 中的背景图路径（用于清理旧文件）
    private var getCurrentDraftBgPath: (() -> String?)? = null

    // 由 Screen 层注册：删除确认时获取当前选中索引
    private var getSelectedIndicesForDelete: (() -> Set<Int>)? = null

    /**
     * 图片选择回调。
     *
     * 将选中的图片复制到应用外部文件目录（externalFiles/bgImage），然后传递路径给 Screen。
     */
    private val selectImage = registerForActivityResult(HandleFileContract()) { result ->
        result.uri?.let { uri ->
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val backgroundsDir = externalFiles
                        .getFile(PreferKey.bgImage)
                        .apply { mkdirs() }
                    val extension = when (uri.scheme) {
                        "content" -> {
                            val mimeType = contentResolver.getType(uri)
                            when (mimeType) {
                                "image/jpeg" -> "jpg"
                                "image/png" -> "png"
                                "image/webp" -> "webp"
                                else -> "jpg"
                            }
                        }
                        else -> uri.path?.substringAfterLast('.', "jpg") ?: "jpg"
                    }
                    val destFile = File(backgroundsDir, "theme_bg_${System.currentTimeMillis()}.$extension")
                    contentResolver.openInputStream(uri)?.use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    // 删除当前草稿里旧背景图文件
                    val oldPath = getCurrentDraftBgPath?.invoke()
                    if (!oldPath.isNullOrBlank() && oldFileInside(backgroundsDir, oldPath)) {
                        File(oldPath).delete()
                    }
                    onBackgroundImageResult?.invoke(destFile.absolutePath)
                    toastOnUi(R.string.success)
                } catch (e: Exception) {
                    toastOnUi(R.string.select_image_failed)
                }
            }
        }
    }

    /** 判断旧背景文件是否位于统一背景图目录内，避免误删用户外部文件 */
    private fun oldFileInside(dir: File, path: String): Boolean {
        runCatching {
            val canonicalDir = dir.canonicalPath
            val canonicalFile = File(path).canonicalPath
            return if (canonicalFile == canonicalDir) {
                false
            } else {
                canonicalFile.startsWith(canonicalDir + File.separator)
            }
        }.onFailure {
            return false
        }
        return false
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        viewModel = ViewModelProvider(this)[ThemeManageViewModel::class.java]
    }

    private var recreatePending = false

    override fun observeLiveBus() {
        super.observeLiveBus()
        observeEvent<String>(EventBus.RECREATE) {
            if (recreatePending || isFinishing || isDestroyed) return@observeEvent
            recreatePending = true
            // 不要在 Compose onClick 的 UP 事件调用栈里同步销毁窗口。
            // 下一帧再重建，让当前 PointerEvent 完整收尾，避免新窗口触摸状态异常。
            window.decorView.postOnAnimation {
                if (!isFinishing && !isDestroyed) recreate()
            }
        }
    }

    @androidx.compose.runtime.Composable
    override fun ComposeContent() {
        ThemeManageScreen(
            viewModel = viewModel,
            onBackClick = { finish() },
            onImportFromClipboard = { toastOnUi(R.string.import_success) },
            onImportEmpty = { toastOnUi(R.string.clipboard_empty) },
            onImportFailed = { toastOnUi(R.string.import_failed) },
            onSelectImage = { selectImage.launch { mode = HandleFileContract.IMAGE } },
            onShareJson = { json -> share(json) },
            onDeleteConfirm = {
                AlertDialog.Builder(this)
                    .setTitle(R.string.delete)
                    .setMessage(R.string.sure_del)
                    .setPositiveButton(R.string.yes) { _, _ ->
                        val indices = getSelectedIndicesForDelete?.invoke() ?: emptySet()
                        viewModel.executeDeleteSelected(indices)
                    }
                    .setNegativeButton(R.string.no, null)
                    .show()
            },
            onToast = { toastOnUi(it) },
            onToastMsg = { toastOnUi(it) },
            onColorClick = { colorKey, currentColor ->
                pendingColorKey = colorKey
                val color = runCatching { currentColor.toColorInt() }
                    .getOrDefault(ContextCompat.getColor(this, R.color.default_primary))
                val dialog = ColorPickerDialog.newBuilder()
                    .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
                    .setColor(color)
                    .setShowAlphaSlider(false)
                    .setAllowPresets(true)
                    .setAllowCustom(true)
                    .setDialogId(DIALOG_ID_THEME_COLOR)
                    .create()
                dialog.setColorPickerDialogListener(this@ThemeManageActivity)
                supportFragmentManager
                    .beginTransaction()
                    .add(dialog, "theme_color_$colorKey")
                    .commitAllowingStateLoss()
            },
            onBlurClick = { currentBlur ->
                NumberPickerDialog(this)
                    .setTitle(getString(R.string.background_image_blurring))
                    .setMinValue(0)
                    .setMaxValue(25)
                    .setValue(currentBlur)
                    .show { blur -> onBlurResult?.invoke(blur) }
            },
            // ── 注册回调：供 Activity 回传结果给 Screen 层 ──
            registerOnColorResult = { callback -> onColorResult = callback },
            registerOnBlurResult = { callback -> onBlurResult = callback },
            registerOnBackgroundImageResult = { callback -> onBackgroundImageResult = callback },
            registerGetDraftBgPath = { callback -> getCurrentDraftBgPath = callback },
            registerGetSelectedIndices = { callback -> getSelectedIndicesForDelete = callback }
        )
    }

    override fun onColorSelected(dialogId: Int, color: Int) {
        if (dialogId == DIALOG_ID_THEME_COLOR) {
            val key = pendingColorKey ?: return
            onColorResult?.invoke(key, color)
        }
    }

    override fun onDialogDismissed(dialogId: Int) {
        // no-op
    }

    companion object {
        private const val DIALOG_ID_THEME_COLOR = 401
    }
}
