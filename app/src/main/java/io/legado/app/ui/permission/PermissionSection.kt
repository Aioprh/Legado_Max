package io.legado.app.ui.permission

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import io.legado.app.R

@Immutable
data class PermissionItem(
    val titleRes: Int,
    val descRes: Int,
    val icon: ImageVector
)

@Immutable
data class PermissionSection(
    val titleRes: Int,
    val items: List<PermissionItem>
)

/**
 * 权限管理页的静态占位数据，接入真实权限控制后改为 ViewModel 提供的 UiState。
 */
internal val permissionSections: List<PermissionSection> = listOf(
    PermissionSection(
        titleRes = R.string.perm_section_cookie,
        items = listOf(
            PermissionItem(
                titleRes = R.string.perm_cookie_read,
                descRes = R.string.perm_cookie_read_desc,
                icon = Icons.Default.Visibility
            ),
            PermissionItem(
                titleRes = R.string.perm_cookie_write,
                descRes = R.string.perm_cookie_write_desc,
                icon = Icons.Default.Edit
            ),
            PermissionItem(
                titleRes = R.string.perm_cookie_delete,
                descRes = R.string.perm_cookie_delete_desc,
                icon = Icons.Default.Delete
            )
        )
    ),
    PermissionSection(
        titleRes = R.string.perm_section_book,
        items = listOf(
            PermissionItem(
                titleRes = R.string.perm_book_read,
                descRes = R.string.perm_book_read_desc,
                icon = Icons.Default.Visibility
            ),
            PermissionItem(
                titleRes = R.string.perm_book_modify,
                descRes = R.string.perm_book_modify_desc,
                icon = Icons.Default.Edit
            ),
            PermissionItem(
                titleRes = R.string.perm_book_delete,
                descRes = R.string.perm_book_delete_desc,
                icon = Icons.Default.Delete
            )
        )
    ),
    PermissionSection(
        titleRes = R.string.perm_section_java,
        items = listOf(
            PermissionItem(
                titleRes = R.string.perm_java_lang,
                descRes = R.string.perm_java_lang_desc,
                icon = Icons.Default.Code
            ),
            PermissionItem(
                titleRes = R.string.perm_java_io,
                descRes = R.string.perm_java_io_desc,
                icon = Icons.Default.Description
            ),
            PermissionItem(
                titleRes = R.string.perm_java_net,
                descRes = R.string.perm_java_net_desc,
                icon = Icons.Default.Public
            )
        )
    ),
    PermissionSection(
        titleRes = R.string.perm_section_android,
        items = listOf(
            PermissionItem(
                titleRes = R.string.perm_android_content,
                descRes = R.string.perm_android_content_desc,
                icon = Icons.Default.Folder
            ),
            PermissionItem(
                titleRes = R.string.perm_android_net,
                descRes = R.string.perm_android_net_desc,
                icon = Icons.Default.Wifi
            ),
            PermissionItem(
                titleRes = R.string.perm_android_system,
                descRes = R.string.perm_android_system_desc,
                icon = Icons.Default.Info
            )
        )
    ),
    PermissionSection(
        titleRes = R.string.perm_section_toast,
        items = listOf(
            PermissionItem(
                titleRes = R.string.perm_toast_enabled,
                descRes = R.string.perm_toast_enabled_desc,
                icon = Icons.Default.ToggleOn
            ),
            PermissionItem(
                titleRes = R.string.perm_toast_duration,
                descRes = R.string.perm_toast_duration_desc,
                icon = Icons.Default.Schedule
            )
        )
    )
)
