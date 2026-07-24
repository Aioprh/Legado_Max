package io.legado.app.utils

import android.content.Context
import android.net.Uri
import io.legado.app.ui.image.ImageCropContract
import java.io.File
import kotlin.math.abs

/**
 * 图片裁剪辅助工具。
 *
 * 封装裁剪请求的构建逻辑，包括宽高比约简、输出路径生成。
 * 宽高比会通过 GCD 约简为最简整数比，确保裁剪框比例精确。
 */
object ImageCropHelper {

    /** 裁剪请求：包含请求码、输出路径和裁剪参数 */
    data class Request(
        val requestCode: Int,
        val outputPath: String,
        val params: ImageCropContract.Params
    )

    /**
     * 构建裁剪请求。
     * 自动约简宽高比、生成带时间戳的唯一输出文件名。
     */
    fun buildRequest(
        context: Context,
        sourceUri: Uri,
        requestCode: Int,
        aspectWidth: Int,
        aspectHeight: Int,
        dirName: String,
        prefix: String,
        targetWidth: Int
    ): Request {
        val aspect = normalizeAspect(aspectWidth, aspectHeight)
        val outputPath = createOutputPath(context, dirName, prefix)
        return Request(
            requestCode = requestCode,
            outputPath = outputPath,
            params = ImageCropContract.Params(
                uri = sourceUri,
                aspectWidth = aspect.first,
                aspectHeight = aspect.second,
                dirName = dirName,
                prefix = prefix,
                targetWidth = targetWidth,
                outputPath = outputPath
            )
        )
    }

    private fun createOutputPath(context: Context, dirName: String, prefix: String): String {
        val dir = context.externalFiles.getFile(dirName).apply { mkdirs() }
        return File(dir, "${prefix}_${System.currentTimeMillis()}.jpg").absolutePath
    }

    private fun normalizeAspect(width: Int, height: Int): Pair<Int, Int> {
        val safeWidth = abs(width).coerceAtLeast(1)
        val safeHeight = abs(height).coerceAtLeast(1)
        val divisor = gcd(safeWidth, safeHeight)
        return safeWidth / divisor to safeHeight / divisor
    }

    private tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
}
