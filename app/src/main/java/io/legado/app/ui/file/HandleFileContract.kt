package io.legado.app.ui.file

import android.app.Activity.RESULT_OK
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import io.legado.app.help.IntentData
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.utils.RealPathUtil
import io.legado.app.utils.externalFiles
import io.legado.app.utils.putJson
import splitties.init.appCtx

@Suppress("unused")
class HandleFileContract :
    ActivityResultContract<(HandleFileContract.HandleFileParam.() -> Unit)?, HandleFileContract.Result>() {

    private var requestCode: Int = 0

    override fun createIntent(context: Context, input: (HandleFileParam.() -> Unit)?): Intent {
        val intent = Intent(context, HandleFileActivity::class.java)
        val handleFileParam = HandleFileParam()
        input?.let {
            handleFileParam.apply(input)
        }
        if (handleFileParam.mode == IMAGE) {
            handleFileParam.allowExtensions = arrayOf("jpg", "png", "bmp", "webp")
        }
        handleFileParam.let {
            requestCode = it.requestCode
            intent.putExtra("mode", it.mode)
            intent.putExtra("title", it.title)
            intent.putExtra("allowExtensions", it.allowExtensions)
            intent.putJson("otherActions", it.otherActions)
            intent.putExtra("onlyOtherActions", it.onlyOtherActions)
            it.fileData?.let { fileData ->
                intent.putExtra("fileName", fileData.name)
                intent.putExtra("fileKey", IntentData.put(fileData.data))
                intent.putExtra("contentType", fileData.type)
            }
            intent.putExtra("value", it.value)
        }
        return intent
    }

    /**
     * 解析 Activity 返回结果。
     *
     * - [uri]：单选时的 URI（来自 intent.data）
     * - [uris]：多选时的 URI 列表（来自 intent.clipData）
     *
     * 调用方应优先检查 [Result.uris]，为空时回退到 [Result.uri]。
     */
    override fun parseResult(resultCode: Int, intent: Intent?): Result {
        val uri = if (resultCode != RESULT_OK || intent?.data == null ||
            RealPathUtil.getTreePath(intent.data!!)
                ?.startsWith(appCtx.externalFiles.parent!!) == true
        ) {
            null
        } else {
            intent.data
        }
        val uris = if (resultCode == RESULT_OK && intent?.clipData != null) {
            val clipData = intent.clipData!!
            (0 until clipData.itemCount).mapNotNull { i ->
                val itemUri = clipData.getItemAt(i).uri
                if (RealPathUtil.getTreePath(itemUri)
                        ?.startsWith(appCtx.externalFiles.parent!!) == true
                ) {
                    null
                } else {
                    itemUri
                }
            }
        } else {
            emptyList()
        }
        // 向后兼容：多选结果只放在 uris 中，但旧调用方可能只检查 uri，
        // 因此当 uri 为空且 uris 不为空时，用第一个 URI 回填 uri。
        val effectiveUri = uri ?: uris.firstOrNull()
        return Result(
            effectiveUri,
            requestCode,
            intent?.getStringExtra("value"),
            intent?.getStringExtra("clipboard_json"),
            uris
        )
    }

    companion object {
        const val DIR = 0
        const val FILE = 1
        const val DIR_SYS = 2
        const val EXPORT = 3
        const val IMAGE = 4
    }

    @Suppress("ArrayInDataClass")
    data class HandleFileParam(
        var mode: Int = DIR,
        var title: String? = null,
        var allowExtensions: Array<String> = arrayOf(),
        var otherActions: ArrayList<SelectItem<Int>>? = null,
        var onlyOtherActions: Boolean = false,
        var fileData: FileData? = null,
        var requestCode: Int = 0,
        var value: String? = null
    )

    /**
     * 选择结果
     *
     * @param uri 单选 URI（传统模式）
     * @param uris 多选 URI 列表（IMAGE 模式下系统图片/文件选择器多选时填充）
     */
    data class Result(
        val uri: Uri?,
        val requestCode: Int,
        val value: String?,
        val clipboardJson: String? = null,
        val uris: List<Uri> = emptyList()
    )

    data class FileData(
        val name: String,
        val data: Any,
        val type: String
    )

}
