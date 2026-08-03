package io.legado.app.ui.config

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.lifecycle.ViewModelProvider
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import io.legado.app.R
import io.legado.app.ui.config.theme.ThemeManageScreen
import io.legado.app.ui.config.theme.ThemeManageViewModel
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.theme.initLegadoComposeTheme
import io.legado.app.ui.theme.setLegadoContent
import io.legado.app.ui.widget.number.NumberPickerDialog
import io.legado.app.utils.RealPathUtil
import io.legado.app.utils.share
import io.legado.app.utils.toastOnUi

class ThemeManageActivity : AppCompatActivity(), ColorPickerDialogListener {

    private lateinit var viewModel: ThemeManageViewModel

    // 当前颜色选择器对应的属性key，用于 onColorSelected 回调
    private var pendingColorKey: String? = null

    private val selectImage = registerForActivityResult(HandleFileContract()) { result ->
        result.uri?.let { uri ->
            RealPathUtil.getPath(this, uri)?.let { path ->
                viewModel.onBackgroundImageSelected(path)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        initLegadoComposeTheme()
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[ThemeManageViewModel::class.java]
        setLegadoContent {
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