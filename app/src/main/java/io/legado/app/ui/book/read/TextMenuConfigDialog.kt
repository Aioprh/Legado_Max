package io.legado.app.ui.book.read

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.DialogFragment
import io.legado.app.R
import io.legado.app.utils.toastOnUi

/**
 * 文本菜单项配置对话框 - Compose实现
 * 
 * 功能说明：
 * 提供一个界面让用户选择要显示/隐藏的文本菜单项
 */
class TextMenuConfigDialog : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                TextMenuConfigDialogContent(
                    onDismiss = { dismiss() }
                )
            }
        }
    }
}

/**
 * 文本菜单配置对话框内容
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextMenuConfigDialogContent(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val menuItems = remember { TextMenuConfig.getAllMenuItems() }
    var hiddenIds by remember { 
        mutableStateOf(TextMenuConfig.getHiddenMenuItemIds(context))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // 标题栏
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.text_menu_config),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "关闭"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )

                // 说明文字
                Text(
                    text = stringResource(R.string.text_menu_config_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                // 菜单项列表
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                ) {
                    items(menuItems) { item ->
                        MenuItemRow(
                            item = item,
                            isChecked = item.id !in hiddenIds,
                            onCheckedChange = { checked ->
                                // 更新隐藏ID集合
                                val newHiddenIds = hiddenIds.toMutableSet()
                                if (checked) {
                                    newHiddenIds.remove(item.id)
                                } else {
                                    newHiddenIds.add(item.id)
                                }
                                // 保存配置
                                TextMenuConfig.setHiddenMenuItemIds(context, newHiddenIds)
                                hiddenIds = newHiddenIds
                            }
                        )
                    }
                }

                // 底部按钮
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = {
                            TextMenuConfig.resetToDefault(context)
                            hiddenIds = emptySet()
                            context.toastOnUi("已重置为默认配置")
                        }
                    ) {
                        Text(text = stringResource(R.string.reset_to_default))
                    }

                    TextButton(onClick = onDismiss) {
                        Text(text = stringResource(R.string.close))
                    }
                }
            }
        }
    }
}

/**
 * 菜单项行
 */
@Composable
fun MenuItemRow(
    item: TextMenuConfig.MenuItemInfo,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!isChecked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp)
        ) {
            Text(
                text = stringResource(item.nameResId),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "ID: ${item.id}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Checkbox(
            checked = isChecked,
            onCheckedChange = onCheckedChange
        )
    }
}
