package io.legado.app.lib.dialogs

import android.content.Context
import android.content.DialogInterface
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.KeyEvent
import android.view.View
import androidx.appcompat.app.AlertDialog
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.applyTint

internal class AndroidAlertBuilder(override val ctx: Context) : AlertBuilder<AlertDialog> {
    private val builder = AlertDialog.Builder(ctx)

    private class ButtonConfig(
        val text: CharSequence,
        val handler: ((DialogInterface) -> Unit)?
    )

    private var positiveConfig: ButtonConfig? = null
    private var negativeConfig: ButtonConfig? = null
    private var neutralConfig: ButtonConfig? = null

    override fun setTitle(title: CharSequence) {
        builder.setTitle(title)
    }

    override fun setTitle(titleResource: Int) {
        builder.setTitle(titleResource)
    }

    override fun setMessage(message: CharSequence) {
        builder.setMessage(message)
    }

    override fun setMessage(messageResource: Int) {
        builder.setMessage(messageResource)
    }

    override fun setIcon(icon: Drawable) {
        builder.setIcon(icon)
    }

    override fun setIcon(iconResource: Int) {
        builder.setIcon(iconResource)
    }

    override fun setCustomTitle(customTitle: View) {
        builder.setCustomTitle(customTitle)
    }

    override fun setCustomView(customView: View) {
        builder.setView(customView)
    }

    override fun setCancelable(isCancelable: Boolean) {
        builder.setCancelable(isCancelable)
    }

    override fun onCancelled(handler: (DialogInterface) -> Unit) {
        builder.setOnCancelListener(handler)
    }

    override fun onKeyPressed(handler: (dialog: DialogInterface, keyCode: Int, e: KeyEvent) -> Boolean) {
        builder.setOnKeyListener(handler)
    }

    override fun positiveButton(
        buttonText: String,
        onClicked: ((dialog: DialogInterface) -> Unit)?
    ) {
        positiveConfig = ButtonConfig(buttonText, onClicked)
    }

    override fun positiveButton(
        buttonTextResource: Int,
        onClicked: ((dialog: DialogInterface) -> Unit)?
    ) {
        positiveConfig = ButtonConfig(ctx.getString(buttonTextResource), onClicked)
    }

    override fun negativeButton(
        buttonText: String,
        onClicked: ((dialog: DialogInterface) -> Unit)?
    ) {
        negativeConfig = ButtonConfig(buttonText, onClicked)
    }

    override fun negativeButton(
        buttonTextResource: Int,
        onClicked: ((dialog: DialogInterface) -> Unit)?
    ) {
        negativeConfig = ButtonConfig(ctx.getString(buttonTextResource), onClicked)
    }

    override fun neutralButton(
        buttonText: String,
        onClicked: ((dialog: DialogInterface) -> Unit)?
    ) {
        neutralConfig = ButtonConfig(buttonText, onClicked)
    }

    override fun neutralButton(
        buttonTextResource: Int,
        onClicked: ((dialog: DialogInterface) -> Unit)?
    ) {
        neutralConfig = ButtonConfig(ctx.getString(buttonTextResource), onClicked)
    }

    /**
     * 将按钮配置应用到 AlertDialog.Builder。
     *
     * 当没有显式设置 neutral 按钮时，将 negative 按钮路由到 neutral 槽位（左侧），
     * positive 按钮保持在 positive 槽位（右侧），使两个按钮分居对话框两端，避免误触。
     * 当有显式 neutral 按钮时，保持标准布局不变。
     */
    private fun applyButtons() {
        positiveConfig?.let { config ->
            builder.setPositiveButton(config.text) { dialog, _ -> config.handler?.invoke(dialog) }
        }
        if (neutralConfig != null) {
            neutralConfig?.let { config ->
                builder.setNeutralButton(config.text) { dialog, _ -> config.handler?.invoke(dialog) }
            }
            negativeConfig?.let { config ->
                builder.setNegativeButton(config.text) { dialog, _ -> config.handler?.invoke(dialog) }
            }
        } else if (negativeConfig != null) {
            // 无 neutral 按钮时，将 negative 路由到 neutral 槽位实现左对齐
            negativeConfig?.let { config ->
                builder.setNeutralButton(config.text) { dialog, _ -> config.handler?.invoke(dialog) }
            }
        }
    }

    override fun onDismiss(handler: (dialog: DialogInterface) -> Unit) {
        builder.setOnDismissListener(handler)
    }

    override fun items(
        items: List<CharSequence>,
        onItemSelected: (dialog: DialogInterface, index: Int) -> Unit
    ) {
        builder.setItems(Array(items.size) { i -> items[i].toString() }) { dialog, which ->
            onItemSelected(dialog, which)
        }
    }

    override fun <T> items(
        items: List<T>,
        onItemSelected: (dialog: DialogInterface, item: T, index: Int) -> Unit
    ) {
        builder.setItems(Array(items.size) { i -> items[i].toString() }) { dialog, which ->
            onItemSelected(dialog, items[which], which)
        }
    }

    override fun multiChoiceItems(
        items: Array<String>,
        checkedItems: BooleanArray,
        onClick: (dialog: DialogInterface, which: Int, isChecked: Boolean) -> Unit
    ) {
        builder.setMultiChoiceItems(items, checkedItems) { dialog, which, isChecked ->
            onClick(dialog, which, isChecked)
        }
    }

    override fun singleChoiceItems(
        items: Array<String>,
        checkedItem: Int,
        onClick: ((dialog: DialogInterface, which: Int) -> Unit)?
    ) {
        builder.setSingleChoiceItems(items, checkedItem) { dialog, which ->
            onClick?.invoke(dialog, which)
        }
    }

    override fun build(): AlertDialog {
        applyButtons()
        val dialog = builder.create()
        fixDialogWindowCompat(dialog)
        if (AppConfig.isEInkMode) {
            dialog.window?.run {
                val attr = attributes
                attr.dimAmount = 0f
                attr.windowAnimations = 0
                attributes = attr
                setBackgroundDrawableResource(R.drawable.bg_eink_border_dialog)
            }
        }
        return dialog
    }

    override fun show(): AlertDialog {
        applyButtons()
        val dialog = builder.show().applyTint()
        fixDialogWindowCompat(dialog)
        if (AppConfig.isEInkMode) {
            dialog.window?.run {
                val attr = attributes
                attr.dimAmount = 0f
                attr.windowAnimations = 0
                attributes = attr
                setBackgroundDrawableResource(R.drawable.bg_eink_border_dialog)
            }
        }
        return dialog
    }

    /**
     * 修复部分 OEM 设备（如 OPPO ColorOS）上 AlertDialog 不显示的问题。
     *
     * 当 Activity 使用沉浸式导航栏（setDecorFitsSystemWindows(false) +
     * SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION）时，部分 OEM 的 WindowManager
     * 实现会让 Dialog 窗口继承 Activity 的窗口属性，导致 Dialog 内容延伸到
     * 导航栏后方或被 Activity 窗口遮挡，出现「菜单不可见但截图可见」的现象。
     *
     * 解决方案：强制将 Dialog 窗口的 DecorFitsSystemWindows 设为 true（默认值），
     * 并清除 decorView 上可能继承的 systemUiVisibility 标志，确保 Dialog 内容
     * 不延伸到系统栏后方。
     */
    private fun fixDialogWindowCompat(dialog: AlertDialog) {
        dialog.window?.let { window ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(true)
            }
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = 0
        }
    }
}