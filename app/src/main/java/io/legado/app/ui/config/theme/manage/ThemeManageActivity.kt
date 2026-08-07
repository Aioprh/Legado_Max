package io.legado.app.ui.config.theme.manage

import android.os.Bundle
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * 主题管理 Activity（Compose 版）。
 *
 * 入口页面，展示日间/夜间主题列表，支持应用、编辑、删除、置顶、多选导出等操作。
 * UI 层使用 Jetpack Compose（ThemeManageScreen），ViewModel 通过 StateFlow 驱动渲染，
 * 平台侧逻辑（ColorPickerDialog、NumberPickerDialog、文件选择）由 Activity 回调处理。
 */
class ThemeManageActivity : BaseComposeActivity(), ColorPickerDialogListener {

    private lateinit var viewModel: ThemeManageViewModel

    // 当前颜色选择器对应的属性key，用于 onColorSelected 回调
    private var pendingColorKey: String? = null

    /**
     * 图片选择回调。
     *
     * 将选中的图片复制到应用外部文件目录（externalFiles/bgImage，与旧版
     * ThemeConfigFragment.setBgFromUri、主题导入导出所使用的同一背景图约定目录），
     * 然后将稳定绝对的私有路径传递给 ViewModel。
     *
     * 注意：在 Android 10+ (Scoped Storage) 上，RealPathUtil.getPath()
     * 无法将 content:// URI 转换为真实文件路径，因此需要手动复制文件而非保存原始路径。
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
                    // 删除当前草稿里旧背景图文件，仅当它仍然位于统一背景图目录内，避免垃圾文件累积
                    val oldPath = viewModel.uiState.value.editDialog.draft?.backgroundImgPath
                    if (!oldPath.isNullOrBlank() && oldFileInside(backgroundsDir, oldPath)) {
                        File(oldPath).delete()
                    }
                    viewModel.onBackgroundImageSelected(destFile.absolutePath)
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
            onRecreate = { recreate() },
            onDeleteConfirm = {
                AlertDialog.Builder(this)
                    .setTitle(R.string.delete)
                    .setMessage(R.string.sure_del)
                    .setPositiveButton(R.string.yes) { _, _ -> viewModel.executeDeleteSelected() }
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
                    .show { blur -> viewModel.onBlurSelected(blur) }
            }
        )
    }

    override fun onColorSelected(dialogId: Int, color: Int) {
        if (dialogId == DIALOG_ID_THEME_COLOR) {
            val key = pendingColorKey ?: return
            viewModel.onColorSelected(key, color)
        }
    }

    override fun onDialogDismissed(dialogId: Int) {
        // no-op
    }

    companion object {
        private const val DIALOG_ID_THEME_COLOR = 401
    }
}
