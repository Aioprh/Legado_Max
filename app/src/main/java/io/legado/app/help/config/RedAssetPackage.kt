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
 * .red 文件来自 Reeden 阅读 App，其文件格式为：
 * - 前 3 字节：`R`, `E`, `D`（ASCII）
 * - 第 4 字节：版本号或子格式标识（如 `0x04` 表示 RED04_ZIP）
 * - 后续字节：ZIP 数据 或 GZIP 数据（视子格式而定）
 *
 * 已知的子格式：
 * - **RED04_ZIP**：`RED\x04` + `PK..`（标准 ZIP），最常见格式
 * - **RED_ASSET_ZIP**：`RED\x??` + `PK..`（第 4 字节非 0x04，也是 ZIP）
 * - **RED_GZIP_JSON**：`RED\x??` + `\x1f\x8b`（GZIP 压缩的 JSON）
 * - **RAW_GZIP_JSON**：直接以 `\x1f\x8b` 开头（无 RED 头的 GZIP JSON）
 * - **RED10_PRIVATE**：`RED\x10`（加密格式，不支持）
 *
 * 本工具负责：
 * - 检测 .red 文件的子格式
 * - 对于 ZIP 类格式，剥离头部后提取标准 ZIP 数据
 * - 安全解压 ZIP 内容
 */
internal object RedAssetPackage {

    /** .red 文件的前 3 字节魔法头：`R`, `E`, `D` */
    private val RED_PREFIX = byteArrayOf('R'.code.toByte(), 'E'.code.toByte(), 'D'.code.toByte())

    /** ZIP 文件的魔法头：`P`, `K` */
    private val ZIP_MAGIC = byteArrayOf('P'.code.toByte(), 'K'.code.toByte())

    /** GZIP 文件的魔法头：`\x1f`, `\x8b` */
    private val GZIP_MAGIC = byteArrayOf(0x1F.toByte(), 0x8B.toByte())

    /** RED10 加密格式的版本标识 */
    private const val RED_VERSION_PRIVATE = 0x10.toByte()

    /** .red ZIP 类格式的版本标识（RED04） */
    private const val RED_VERSION_ZIP = 0x04.toByte()

    /**
     * .red 文件的子格式枚举。
     */
    enum class RedFormat {
        /** `RED\x04` + ZIP，最常见的主题包格式 */
        RED04_ZIP,
        /** `RED\x??` + ZIP，第 4 字节非 0x04 的 ZIP 变体 */
        RED_ASSET_ZIP,
        /** `RED\x??` + GZIP，GZIP 压缩的 JSON */
        RED_GZIP_JSON,
        /** 直接以 GZIP 开头，无 RED 头 */
        RAW_GZIP_JSON,
        /** `RED\x10`，加密私有格式，不支持导入 */
        RED10_PRIVATE,
    }

    /**
     * 检测 .red 文件的子格式。
     *
     * @param file 输入文件
     * @return 格式枚举，或 null（格式不匹配时）
     */
    fun detectFormat(file: File): RedFormat? {
        if (!file.isFile || file.length() < 2) return null
        return file.inputStream().use { input ->
            val header = ByteArray(8)
            val size = input.read(header)
            when {
                // RED04_ZIP: RED\x04 + PK
                size >= 6 &&
                    header[0] == RED_PREFIX[0] &&
                    header[1] == RED_PREFIX[1] &&
                    header[2] == RED_PREFIX[2] &&
                    header[3] == RED_VERSION_ZIP &&
                    header[4] == ZIP_MAGIC[0] &&
                    header[5] == ZIP_MAGIC[1] -> RedFormat.RED04_ZIP

                // RED_ASSET_ZIP: RED\x?? + PK（第 4 字节非 0x04）
                size >= 6 &&
                    header[0] == RED_PREFIX[0] &&
                    header[1] == RED_PREFIX[1] &&
                    header[2] == RED_PREFIX[2] &&
                    header[4] == ZIP_MAGIC[0] &&
                    header[5] == ZIP_MAGIC[1] -> RedFormat.RED_ASSET_ZIP

                // RED10_PRIVATE: RED\x10（加密，不支持）
                size >= 4 &&
                    header[0] == RED_PREFIX[0] &&
                    header[1] == RED_PREFIX[1] &&
                    header[2] == RED_PREFIX[2] &&
                    header[3] == RED_VERSION_PRIVATE -> RedFormat.RED10_PRIVATE

                // RED_GZIP_JSON: RED\x?? + \x1f\x8b
                size >= 6 &&
                    header[0] == RED_PREFIX[0] &&
                    header[1] == RED_PREFIX[1] &&
                    header[2] == RED_PREFIX[2] &&
                    header[4] == GZIP_MAGIC[0] &&
                    header[5] == GZIP_MAGIC[1] -> RedFormat.RED_GZIP_JSON

                // RAW_GZIP_JSON: 直接以 \x1f\x8b 开头
                size >= 2 &&
                    header[0] == GZIP_MAGIC[0] &&
                    header[1] == GZIP_MAGIC[1] -> RedFormat.RAW_GZIP_JSON

                else -> null
            }
        }
    }

    /**
     * 判断文件是否为 .red 格式（任意子格式）。
     *
     * 注意：标准 ZIP 文件（以 PK 开头）和纯 GZIP 文件（以 \x1f\x8b 开头）
     * 不被视为 .red 格式，只有带 RED 头的文件才算。
     */
    fun isRedFormat(file: File): Boolean {
        if (!file.isFile || file.length() < 4) return false
        return file.inputStream().use { input ->
            val magic = ByteArray(3)
            input.read(magic) >= 3 &&
                magic[0] == RED_PREFIX[0] &&
                magic[1] == RED_PREFIX[1] &&
                magic[2] == RED_PREFIX[2]
        }
    }

    /**
     * 从 .red 文件中提取标准 ZIP 数据。
     *
     * 支持 RED04_ZIP 和 RED_ASSET_ZIP 两种子格式。
     * 如果文件本身就是标准 ZIP（以 PK 开头），直接返回原文件。
     * 如果文件是 GZIP 类格式或加密格式，返回 null（需用其他方式处理）。
     *
     * @param file 输入文件（.red 或 .zip）
     * @param tempDir 临时目录，用于存放提取后的 ZIP 文件
     * @return 标准 ZIP 文件，或 null（格式不匹配时）
     */
    fun zipPayload(file: File, tempDir: File): File? {
        val format = detectFormat(file) ?: run {
            // 不是 .red 格式，检查是否为标准 ZIP
            val isZip = file.inputStream().use { input ->
                val b1 = input.read()
                val b2 = input.read()
                b1 == ZIP_MAGIC[0].toInt() && b2 == ZIP_MAGIC[1].toInt()
            }
            return if (isZip) file else null
        }

        return when (format) {
            RedFormat.RED04_ZIP, RedFormat.RED_ASSET_ZIP -> {
                // 剥离 RED 头部，写入临时 ZIP 文件
                val target = tempDir.getFile("red_asset_${System.currentTimeMillis()}.zip")
                file.inputStream().use { input ->
                    input.skip(4) // 跳过 4 字节 RED 头
                    FileOutputStream(target).use { output -> input.copyTo(output) }
                }
                target.takeIf { it.isFile && it.length() > 0L }
            }
            // GZIP 和加密格式不通过此方法处理
            RedFormat.RED_GZIP_JSON,
            RedFormat.RAW_GZIP_JSON,
            RedFormat.RED10_PRIVATE -> null
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
