package io.legado.app.help.config

import io.legado.app.utils.FileUtils
import io.legado.app.utils.getFile
import splitties.init.appCtx
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

/**
 * .red 主题包格式解析工具。
 *
 * .red 文件格式：前 4 字节为 `RED\0` 文件头（魔法字节 `R`, `E`, `D`, `0x00`），
 * 之后是标准的 ZIP 数据。该格式由 archive_primate_beta 分支引入，
 * 用于将主题包与普通 ZIP 文件区分开。
 *
 * 本工具负责：
 * - 检测文件是否为 .red 格式（或标准 ZIP）
 * - 剥离 .red 头部，提取出标准 ZIP 数据到临时文件
 * - 分类 ZIP 内容（主题 / 底栏 / 封面图集）
 */
internal object RedAssetPackage {

    /** .red 文件的魔法头：`R`, `E`, `D`, `0x00` */
    private val RED_MAGIC = byteArrayOf('R'.code.toByte(), 'E'.code.toByte(), 'D'.code.toByte(), 0x00)

    /** ZIP 文件的魔法头：`P`, `K` */
    private val ZIP_MAGIC = byteArrayOf('P'.code.toByte(), 'K'.code.toByte())

    /**
     * 从文件中提取标准 ZIP 数据。
     *
     * 如果文件本身就是标准 ZIP（以 `PK` 开头），直接返回原文件。
     * 如果文件是 .red 格式（以 `RED\0` 开头，偏移 4 后是 `PK`），
     * 则剥离头部后将 ZIP 数据写入临时文件并返回。
     * 否则返回 null。
     *
     * @param file 输入文件（.red 或 .zip）
     * @param tempDir 临时目录，用于存放提取后的 ZIP 文件
     * @return 标准 ZIP 文件，或 null（格式不匹配时）
     */
    fun zipPayload(file: File, tempDir: File): File? {
        val header = file.inputStream().use { input ->
            ByteArray(8).also { bytes ->
                val count = input.read(bytes)
                if (count < 4) return null
            }
        }

        // 检查是否为 .red 格式：RED\0 + PK
        val hasRedHeader = header[0] == RED_MAGIC[0] &&
            header[1] == RED_MAGIC[1] &&
            header[2] == RED_MAGIC[2] &&
            header[3] == RED_MAGIC[3]

        val zipOffset = if (hasRedHeader) {
            // 验证偏移 4 处是否为 PK 头
            if (header.size >= 6 && header[4] == ZIP_MAGIC[0] && header[5] == ZIP_MAGIC[1]) {
                4L
            } else {
                return null
            }
        } else if (header[0] == ZIP_MAGIC[0] && header[1] == ZIP_MAGIC[1]) {
            // 标准 ZIP 文件
            0L
        } else {
            return null
        }

        if (zipOffset == 0L) return file

        // 剥离 .red 头部，写入临时 ZIP 文件
        val target = tempDir.getFile("red_asset_${System.currentTimeMillis()}.zip")
        file.inputStream().use { input ->
            input.skip(zipOffset)
            FileOutputStream(target).use { output -> input.copyTo(output) }
        }
        return target.takeIf { it.isFile && it.length() > 0L }
    }

    /**
     * 判断文件是否为 .red 格式。
     *
     * 注意：标准 ZIP 文件不被视为 .red 格式。
     */
    fun isRedFormat(file: File): Boolean {
        return file.inputStream().use { input ->
            val magic = ByteArray(4)
            val count = input.read(magic)
            count >= 4 &&
                magic[0] == RED_MAGIC[0] &&
                magic[1] == RED_MAGIC[1] &&
                magic[2] == RED_MAGIC[2] &&
                magic[3] == RED_MAGIC[3]
        }
    }

    /**
     * 安全解压 ZIP 文件到目标目录。
     *
     * @param zipFile ZIP 文件
     * @param targetDir 解压目标目录
     */
    fun unzipSecure(zipFile: File, targetDir: File) {
        if (targetDir.exists()) {
            FileUtils.delete(targetDir, deleteRootDir = true)
        }
        targetDir.mkdirs()
        val canonicalTarget = targetDir.canonicalPath
        ZipFile(zipFile).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val target = File(targetDir, entry.name)
                // 路径穿越检查：确保解压目标在目标目录内
                val canonicalChild = target.canonicalPath
                if (!canonicalChild.startsWith(canonicalTarget)) {
                    throw IllegalArgumentException("Invalid RED package")
                }
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        FileOutputStream(target).use { output -> input.copyTo(output) }
                    }
                }
            }
        }
    }

    /**
     * 从 appCtx 获取临时目录用于 .red 文件处理。
     */
    fun tempDir(): File {
        return appCtx.cacheDir.resolve("redAssetTemp").apply { mkdirs() }
    }
}
