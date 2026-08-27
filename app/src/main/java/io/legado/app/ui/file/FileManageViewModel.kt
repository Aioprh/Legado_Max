package io.legado.app.ui.file

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * 文件管理 Dialog 状态（state-events.md §4.5：Dialog 由 UiState 条件渲染）
 */
sealed interface FileDialogState {
    data class DeleteConfirm(val file: File) : FileDialogState
}

data class FileManageUiState(
    val dialog: FileDialogState? = null
)

/**
 * 文件管理一次性 UI 事件
 * 平台操作（FileProvider 打开文件、Toast）由 Activity 执行，ViewModel 只抛事件（state-events.md §4.1）
 */
sealed interface FileManageEvent {
    data class OpenFile(val file: File) : FileManageEvent
    data class OpenWithChooser(val dir: File?) : FileManageEvent
    data class Toast(val message: String?) : FileManageEvent
}

/**
 * 文件管理 ViewModel
 *
 * 职责：
 * - 管理当前目录路径和文件列表
 * - 处理目录导航（进入、返回、跳转）
 * - 处理文件搜索过滤
 * - 处理文件删除和打开
 *
 * 根目录经构造注入（生产环境为应用外部存储目录的父目录），JVM 单测可注入临时目录（testing.md §16）
 */
class FileManageViewModel(
    val rootDoc: File?
) : ViewModel() {

    /** UI 状态（承载 Dialog 显隐，§4.5） */
    private val _uiState = MutableStateFlow(FileManageUiState())
    val uiState: StateFlow<FileManageUiState> = _uiState.asStateFlow()

    // 关键事件（打开文件）：UNLIMITED 缓冲，事件不允许丢失（§4.1）
    private val _events = Channel<FileManageEvent>(Channel.UNLIMITED)
    val events: Flow<FileManageEvent> = _events.receiveAsFlow()

    // Toast 事件：允许丢事件（只提示最新一条），用 CONFLATED 通道（§4.1）
    private val _toasts = Channel<FileManageEvent.Toast>(Channel.CONFLATED)
    val toasts: Flow<FileManageEvent.Toast> = _toasts.receiveAsFlow()

    /** 子目录列表（用于路径导航条显示） */
    private val _subDocs = MutableStateFlow<MutableList<File>>(mutableListOf())
    val subDocsFlow: StateFlow<List<File>> = _subDocs.asStateFlow()

    /** 当前目录下的文件列表 */
    private val _files = MutableStateFlow<List<File>>(emptyList())
    val files: StateFlow<List<File>> = _files.asStateFlow()

    /** 搜索关键词 */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** 当前未过滤的文件列表（用于搜索过滤） */
    private var currentFiles = listOf<File>()

    /** 当前目录的上级目录（用于显示 ".." 项） */
    val lastDir: File? get() = _subDocs.value.lastOrNull() ?: rootDoc

    init {
        // 初始化时加载根目录
        upFiles(rootDoc)
    }

    /** 请求删除文件：弹出确认 Dialog */
    fun requestDelete(file: File) {
        _uiState.update { it.copy(dialog = FileDialogState.DeleteConfirm(file)) }
    }

    /** 关闭当前 Dialog */
    fun dismissDialog() {
        _uiState.update { it.copy(dialog = null) }
    }

    /** 确认删除：清 Dialog 状态后执行删除 */
    fun confirmDelete(file: File) {
        _uiState.update { it.copy(dialog = null) }
        delFile(file)
    }

    /**
     * 更新搜索关键词并过滤文件列表
     */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        filterFiles()
    }

    /**
     * 根据搜索关键词过滤文件列表
     * 过滤规则：保留 ".." 项和名称包含关键词的文件
     */
    private fun filterFiles() {
        val query = _searchQuery.value
        if (query.isNotEmpty()) {
            currentFiles.filter {
                it.name == ".." || it.name.contains(query)
            }.let {
                _files.value = it
            }
        } else {
            _files.value = currentFiles
        }
    }

    /**
     * 加载指定目录下的文件列表
     *
     * @param parentFile 目标目录，为 null 时不执行
     *
     * 文件排序规则：
     * - 文件夹优先于文件
     * - 同类型按名称排序
     * - 非根目录时，列表第一项为上级目录（用于返回）
     */
    fun upFiles(parentFile: File?) {
        viewModelScope.launch {
            try {
                parentFile ?: return@launch
                val result = if (parentFile == rootDoc) {
                    // 根目录：直接列出文件
                    parentFile.listFiles()?.sortedWith(
                        compareBy({ it.isFile }, { it.name })
                    ) ?: emptyList()
                } else {
                    // 非根目录：第一项为上级目录，后面是当前目录内容
                    val list = arrayListOf(parentFile)
                    parentFile.listFiles()?.sortedWith(
                        compareBy({ it.isFile }, { it.name })
                    )?.let {
                        list.addAll(it)
                    }
                    list
                }
                currentFiles = result
                _searchQuery.value = ""
                _files.value = result
            } catch (e: Exception) {
                _toasts.trySend(FileManageEvent.Toast(e.localizedMessage))
            }
        }
    }

    /**
     * 返回根目录
     */
    fun goToRoot() {
        _subDocs.value = mutableListOf()
        upFiles(rootDoc)
    }

    /**
     * 跳转到指定索引的路径
     * 用于路径导航条点击跳转
     *
     * @param index 子目录列表中的索引
     */
    fun goToPath(index: Int) {
        val newSubDocs = _subDocs.value.subList(0, index + 1).toMutableList()
        _subDocs.value = newSubDocs
        upFiles(newSubDocs.lastOrNull())
    }

    /**
     * 返回上级目录
     * 点击 ".." 项时调用
     */
    fun gotoLastDir() {
        val currentSubDocs = _subDocs.value.toMutableList()
        currentSubDocs.removeLastOrNull()
        _subDocs.value = currentSubDocs
        upFiles(lastDir)
    }

    /**
     * 进入子目录
     *
     * @param file 目标子目录
     */
    fun enterDir(file: File) {
        val currentSubDocs = _subDocs.value.toMutableList()
        currentSubDocs.add(file)
        _subDocs.value = currentSubDocs
        upFiles(file)
    }

    fun openPath(path: String) {
        val target = File(path).let { file ->
            when {
                file.isDirectory -> file
                file.isFile -> file.parentFile
                else -> file
            }
        } ?: return
        _subDocs.value = buildPathChain(target).toMutableList()
        upFiles(target)
    }

    private fun buildPathChain(target: File): List<File> {
        val root = rootDoc
        if (root != null && target.absolutePath.startsWith(root.absolutePath)) {
            val chain = mutableListOf<File>()
            var current: File? = target
            while (current != null && current != root) {
                chain.add(current)
                current = current.parentFile
            }
            return chain.asReversed()
        }
        return generateSequence(target) { it.parentFile }
            .toList()
            .asReversed()
            .filter { it.parentFile != null }
    }

    /**
     * 打开文件
     * 只做数据准备，FileProvider 授权与打开动作通过事件抛给 Activity 执行（§4.1）
     *
     * @param file 要打开的文件
     */
    fun openFile(file: File) {
        _events.trySend(FileManageEvent.OpenFile(file))
    }

    /**
     * 用系统选择器打开当前目录，让用户选择用哪个应用/文件管理器打开
     * 平台操作（FileProvider + Chooser）由 Activity 执行（§4.1）
     */
    fun openWithChooser() {
        _events.trySend(FileManageEvent.OpenWithChooser(lastDir))
    }

    /**
     * 删除文件
     * 删除后刷新当前目录
     *
     * @param file 要删除的文件
     */
    fun delFile(file: File) {
        viewModelScope.launch {
            try {
                file.delete()
                upFiles(lastDir)
            } catch (e: Exception) {
                _toasts.trySend(FileManageEvent.Toast(e.localizedMessage))
            }
        }
    }

}
