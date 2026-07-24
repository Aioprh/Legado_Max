package io.legado.app.utils

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

/**
 * 图片处理工具类。
 *
 * 提供位图保存和采样大小计算功能，用于图片裁剪和缩放场景。
 * 保存时自动按宽高比缩放，输出 JPEG 格式，质量 92%。
 */
object ImageProcessUtils {

    /**
     * 将位图保存为 JPEG 文件。
     * 按指定宽高比缩放位图，输出到指定目录或路径。
     *
     * @param outputPath 指定输出路径，为空时自动生成文件名
     * @return 保存后的文件绝对路径，失败返回 null
     */
    fun saveBitmapToFile(
        context: Context,
        bitmap: Bitmap,
        aspectWidth: Int,
        aspectHeight: Int,
        dirName: String,
        prefix: String,
        targetWidth: Int = 1600,
        outputPath: String? = null
    ): String? {
        val safeAspectWidth = aspectWidth.coerceAtLeast(1)
        val safeAspectHeight = aspectHeight.coerceAtLeast(1)
        val safeTargetWidth = targetWidth.coerceAtLeast(128)
        val targetHeight = (safeTargetWidth * safeAspectHeight.toFloat() / safeAspectWidth)
            .roundToInt()
            .coerceAtLeast(128)
        val scaled = if (bitmap.width != safeTargetWidth || bitmap.height != targetHeight) {
            Bitmap.createScaledBitmap(bitmap, safeTargetWidth, targetHeight, true)
        } else {
            bitmap
        }
        val file = if (outputPath.isNullOrBlank()) {
            val dir = context.externalFiles.getFile(dirName).apply { mkdirs() }
            File(dir, "${prefix}_${System.currentTimeMillis()}.jpg")
        } else {
            File(outputPath).withJpgExtension().apply {
                parentFile?.mkdirs()
            }
        }
        try {
            FileOutputStream(file).use {
                scaled.compress(Bitmap.CompressFormat.JPEG, 92, it)
            }
        } finally {
            if (scaled !== bitmap) scaled.recycle()
        }
        return file.absolutePath
    }

    /** 计算合适的采样大小，避免加载过大图片导致 OOM */
    fun calculateSampleSize(width: Int, height: Int, targetWidth: Int, targetHeight: Int): Int {
        var sampleSize = 1
        while (width / (sampleSize * 2) >= targetWidth &&
            height / (sampleSize * 2) >= targetHeight
        ) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun File.withJpgExtension(): File {
        if (extension.equals("jpg", ignoreCase = true) ||
            extension.equals("jpeg", ignoreCase = true)
        ) {
            return this
        }
        return File(parentFile ?: File("."), "$nameWithoutExtension.jpg")
    }
}
