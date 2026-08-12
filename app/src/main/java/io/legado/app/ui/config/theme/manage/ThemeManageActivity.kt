package io.legado.app.ui.config.theme.manage

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
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
 * 主题管理容器 Activity
 * 
 * 为什么只留这点代码：
 * 作为纯粹的容器，仅充当系统级组件（如文件选择器 Intent、ColorPicker 碎片对话框）与界面的桥梁。
 * 所有的业务状态流转和逻辑校验已经下沉到了 [ThemeManageViewModel]，
 * 此类仅负责把外部系统回调转换成 ViewModel 的方法调用，绝对禁止在此类中硬编码任何 UI 状态。
 */
class ThemeManageActivity : BaseComposeActivity(), ColorPickerDialogListener {

    private val viewModel: ThemeManageViewModel by viewModels {
        ThemeManageViewModelFactory(application)
    }

    private var pendingColorKey: String? = null

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
                    val oldPath = viewModel.editDraft.value?.backgroundImgPath
                    if (!oldPath.isNullOrBlank() && oldFileInside(backgroundsDir, oldPath)) {
                        File(oldPath).delete()
                    }
                    viewModel.updateDraftBackgroundImage(destFile.absolutePath)
                    toastOnUi(R.string.success)
                } catch (e: Exception) {
                    toastOnUi(R.string.select_image_failed)
                }
            }
        }
    }

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

    private var recreatePending = false

    override fun observeLiveBus() {
        super.observeLiveBus()
        observeEvent<String>(EventBus.RECREATE) {
            if (recreatePending || isFinishing || isDestroyed) return@observeEvent
            recreatePending = true
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
                        viewModel.executeDeleteSelected()
                    }
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
                    .show { blur -> viewModel.updateDraftBlur(blur) }
            }
        )
    }

    override fun onColorSelected(dialogId: Int, color: Int) {
        if (dialogId == DIALOG_ID_THEME_COLOR) {
            val key = pendingColorKey ?: return
            viewModel.updateDraftColor(key, color)
        }
    }

    override fun onDialogDismissed(dialogId: Int) {}

    companion object {
        private const val DIALOG_ID_THEME_COLOR = 401
    }
}