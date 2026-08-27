package io.legado.app.ui.file.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.SubdirectoryArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File

/**
 * 文件列表
 * 使用 LazyColumn 实现滚动列表
 */
@Composable
fun FileList(
    files: List<File>,
    lastDir: File?,
    onFileClick: (File) -> Unit,
    onFileLongClick: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize()
    ) {
        items(files, key = { it.absolutePath }) { file ->
            FileItem(
                file = file,
                isParentDir = file == lastDir,
                onClick = { onFileClick(file) },
                onLongClick = { onFileLongClick(file) }
            )
        }
    }
}

/**
 * 单个文件项
 * 显示图标 + 文件名
 * 图标类型：上级目录(..)、文件夹、普通文件（Material Icons，禁止手写 Bitmap 解码 theme-styles.md §7.3）
 * 触控目标：行高不低于 48dp（accessibility.md §15.3）
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileItem(
    file: File,
    isParentDir: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 根据类型选择图标与语义色
        val (icon, tint) = when {
            isParentDir -> Icons.Default.SubdirectoryArrowLeft to MaterialTheme.colorScheme.onSurfaceVariant  // 返回上级图标
            file.isDirectory -> Icons.Default.Folder to MaterialTheme.colorScheme.primary                     // 文件夹图标
            else -> Icons.Default.InsertDriveFile to MaterialTheme.colorScheme.onSurfaceVariant               // 普通文件图标
        }

        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = tint
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = if (isParentDir) ".." else file.name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
