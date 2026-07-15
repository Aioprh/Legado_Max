package io.legado.app.utils

import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import splitties.init.appCtx

fun <T> ActivityResultLauncher<T?>.launch() {
    launch(null)
}

/**
 * 单选图片 Contract
 *
 * 优先使用系统图片选择器（PickVisualMedia），
 * 若不可用则回退到 ACTION_GET_CONTENT。
 */
class SelectImageContract : ActivityResultContract<Int?, SelectImageContract.Result>() {

    private val delegate = ActivityResultContracts.PickVisualMedia()
    private var requestCode: Int? = null
    private var useFallback = false

    override fun createIntent(context: Context, input: Int?): Intent {
        requestCode = input
        val intent = Intent(Intent.ACTION_GET_CONTENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("image/*")
        if (intent.resolveActivity(appCtx.packageManager) == null) {
            useFallback = true
            val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            return delegate.createIntent(context, request)
        }
        return intent
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Result {
        val uri = if (useFallback) {
            delegate.parseResult(resultCode, intent)
        } else if (resultCode == RESULT_OK) {
            intent?.data
        } else {
            null
        }
        return Result(requestCode, uri)
    }

    data class Result(
        val requestCode: Int?,
        val uri: Uri? = null
    )

}

/**
 * 多选图片 Contract
 *
 * 优先使用系统图片选择器多选模式（PickMultipleVisualMedia，显示相册界面），
 * 若系统不支持则回退到 ACTION_GET_CONTENT + EXTRA_ALLOW_MULTIPLE（显示文件选择器界面）。
 *
 * @return 选中的图片 URI 列表，未选择时返回空列表
 */
class SelectMultipleImagesContract :
    ActivityResultContract<Int?, List<Uri>>() {

    private val delegate = ActivityResultContracts.PickMultipleVisualMedia()
    private var useFallback = false

    override fun createIntent(context: Context, input: Int?): Intent {
        // 优先使用系统图片选择器（PickMultipleVisualMedia），显示相册界面
        if (ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(context)) {
            val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            return delegate.createIntent(context, request)
        }
        // 系统不支持图片选择器时，回退到 ACTION_GET_CONTENT + EXTRA_ALLOW_MULTIPLE
        useFallback = true
        return Intent(Intent.ACTION_GET_CONTENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("image/*")
            .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> {
        if (resultCode != RESULT_OK) return emptyList()
        if (!useFallback) {
            // PickMultipleVisualMedia 的返回值直接是 List<Uri>
            return delegate.parseResult(resultCode, intent)
        }
        // ACTION_GET_CONTENT 多选时通过 ClipData 返回，单选时通过 data 返回
        val result = mutableListOf<Uri>()
        intent?.clipData?.let { clipData ->
            for (i in 0 until clipData.itemCount) {
                result.add(clipData.getItemAt(i).uri)
            }
        }
        if (result.isEmpty()) {
            intent?.data?.let { result.add(it) }
        }
        return result
    }

}

/**
 * 启动指定 Activity 的 Contract
 *
 * @param cls 目标 Activity 的 Class
 * @param input 可选的 Intent 配置 lambda
 * @return ActivityResult
 */
class StartActivityContract(private val cls: Class<*>) :
    ActivityResultContract<(Intent.() -> Unit)?, ActivityResult>() {

    override fun createIntent(context: Context, input: (Intent.() -> Unit)?): Intent {
        val intent = Intent(context, cls)
        input?.let {
            intent.apply(input)
        }
        return intent
    }

    override fun parseResult(
        resultCode: Int, intent: Intent?
    ): ActivityResult {
        return ActivityResult(resultCode, intent)
    }

}
