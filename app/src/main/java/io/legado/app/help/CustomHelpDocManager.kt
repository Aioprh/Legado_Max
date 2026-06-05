package io.legado.app.help

import android.content.Context
import android.os.Environment
import java.io.File

/**
 * 自定义帮助文档管理器
 *
 * 负责扫描、加载、保存、删除自定义文档
 */
object CustomHelpDocManager {

    private const val CUSTOM_DOC_DIR_NAME = "LegadoPlus"
    private const val HELP_DOC_SUB_DIR = "help_docs"

    // 支持的文件扩展名
    private val SUPPORTED_EXTENSIONS = listOf("md", "txt")

    // 缓存
    private var cachedGroups: List<CustomHelpDocGroup>? = null
    private var lastScanTime: Long = 0
    private const val CACHE_DURATION = 5 * 60 * 1000L // 5分钟

    /**
     * 获取自定义文档根目录
     */
    fun getCustomDocDir(context: Context): File {
        val externalDir = Environment.getExternalStorageDirectory()
        return File(externalDir, "$CUSTOM_DOC_DIR_NAME/$HELP_DOC_SUB_DIR")
    }

    /**
     * 检查外部存储是否可用
     */
    fun isExternalStorageAvailable(): Boolean {
        val state = Environment.getExternalStorageState()
        return state == Environment.MEDIA_MOUNTED
    }

    /**
     * 扫描自定义文档目录
     *
     * @param context 上下文
     * @param forceRefresh 是否强制刷新缓存
     * @return 自定义文档分组列表
     */
    fun scanCustomDocs(context: Context, forceRefresh: Boolean = false): List<CustomHelpDocGroup> {
        // 检查缓存
        if (!forceRefresh && cachedGroups != null &&
            System.currentTimeMillis() - lastScanTime < CACHE_DURATION) {
            return cachedGroups!!
        }

        // 检查外部存储
        if (!isExternalStorageAvailable()) {
            return emptyList()
        }

        val customDocDir = getCustomDocDir(context)

        // 如果目录不存在,返回空列表
        if (!customDocDir.exists()) {
            cachedGroups = emptyList()
            lastScanTime = System.currentTimeMillis()
            return emptyList()
        }

        // 扫描子文件夹
        val groups = mutableListOf<CustomHelpDocGroup>()
        customDocDir.listFiles()?.filter { it.isDirectory }?.forEach { groupFolder ->
            val docs = scanDocsInFolder(groupFolder)
            if (docs.isNotEmpty()) {
                groups.add(
                    CustomHelpDocGroup(
                        displayName = groupFolder.name,
                        docs = docs,
                        folderPath = groupFolder.absolutePath
                    )
                )
            }
        }

        // 按分组名排序
        groups.sortBy { it.displayName }

        // 更新缓存
        cachedGroups = groups
        lastScanTime = System.currentTimeMillis()

        return groups
    }

    /**
     * 扫描文件夹中的文档
     */
    private fun scanDocsInFolder(folder: File): List<CustomHelpDoc> {
        val docs = mutableListOf<CustomHelpDoc>()

        folder.listFiles()?.filter { it.isFile }?.forEach { file ->
            val extension = file.extension.lowercase()
            if (extension in SUPPORTED_EXTENSIONS) {
                val fileName = file.nameWithoutExtension
                docs.add(
                    CustomHelpDoc(
                        fileName = fileName,
                        displayName = fileName,
                        filePath = file.absolutePath,
                        extension = extension
                    )
                )
            }
        }

        // 按文件名排序
        docs.sortBy { it.fileName }

        return docs
    }

    /**
     * 加载文档内容
     *
     * @param filePath 文件路径
     * @return 文档内容,失败返回空字符串
     */
    fun loadDoc(filePath: String): String {
        return try {
            File(filePath).readText(Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    /**
     * 保存文档
     *
     * @param filePath 文件路径
     * @param content 文档内容
     * @return 是否成功
     */
    fun saveDoc(filePath: String, content: String): Boolean {
        return try {
            val file = File(filePath)
            // 确保父目录存在
            file.parentFile?.mkdirs()
            file.writeText(content, Charsets.UTF_8)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 删除文档
     *
     * @param filePath 文件路径
     * @return 是否成功
     */
    fun deleteDoc(filePath: String): Boolean {
        return try {
            val file = File(filePath)
            if (file.exists()) {
                file.delete()
            } else {
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 创建分组(创建文件夹)
     *
     * @param context 上下文
     * @param groupName 分组名称
     * @return 是否成功
     */
    fun createGroup(context: Context, groupName: String): Boolean {
        return try {
            // 检查分组名合法性
            if (!isValidFileName(groupName)) {
                return false
            }

            val customDocDir = getCustomDocDir(context)
            val groupDir = File(customDocDir, groupName)

            // 检查是否已存在
            if (groupDir.exists()) {
                return false
            }

            // 创建目录
            groupDir.mkdirs()

            // 清除缓存
            clearCache()

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 删除分组(删除文件夹及内部所有文件)
     *
     * @param folderPath 文件夹路径
     * @return 是否成功
     */
    fun deleteGroup(folderPath: String): Boolean {
        return try {
            val folder = File(folderPath)
            if (folder.exists() && folder.isDirectory) {
                folder.deleteRecursively()
            } else {
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 检查文件名是否合法
     *
     * @param name 文件名
     * @return 是否合法
     */
    fun isValidFileName(name: String): Boolean {
        if (name.isBlank()) return false
        // 不能包含这些字符: \ / : * ? " < > |
        val invalidChars = listOf('\\', '/', ':', '*', '?', '"', '<', '>', '|')
        return !name.any { it in invalidChars }
    }

    /**
     * 清除缓存
     */
    fun clearCache() {
        cachedGroups = null
        lastScanTime = 0
    }
}
