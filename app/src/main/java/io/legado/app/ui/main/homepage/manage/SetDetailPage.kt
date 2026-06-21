package io.legado.app.ui.main.homepage.manage

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.legado.app.domain.model.HomepageModuleType
import io.legado.app.domain.model.ModuleDef
import io.legado.app.ui.main.homepage.HomepageModuleManageUi
import io.legado.app.ui.main.homepage.HomepageViewModel
import io.legado.app.ui.theme.pageSecondaryTextColor
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.card.TextCard
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * 集详情页面。
 *
 * 该文件实现首页模块管理中的集详情页面，展示某个集（书源集或自定义集）内包含的所有模块。
 * 模块分为标准模块（可排序）和无限流模块（固定底部，不可排序）两类。
 * 支持模块的启用/禁用、编辑、删除、长按拖拽排序等操作，并提供添加模块的入口。
 *
 * 主要功能：
 * - 展示集内所有模块，区分标准模块和无限流模块
 * - 支持标准模块的长按拖拽排序
 * - 支持模块的编辑、删除和可见性切换
 * - 提供添加模块的入口按钮
 *
 * @param setTitle 集标题，用于页面展示
 * @param modules 集内所有模块的 UI 数据列表
 * @param isCustomSet 当前集是否为自定义集
 * @param onToggleModule 切换模块可见性的回调，参数为模块 ID 和目标状态
 * @param onEditModule 编辑模块的回调，参数为模块 ID 和模块定义
 * @param onDeleteModule 删除模块的回调，参数为模块 ID
 * @param onReorderModules 重新排序模块的回调，参数为新的模块 ID 顺序列表
 * @param onAddModules 点击"添加模块"按钮的回调
 * @param onBack 返回上一页的回调
 */
@Composable
fun SetDetailPage(
    setTitle: String,
    modules: List<HomepageModuleManageUi>,
    isCustomSet: Boolean,
    onToggleModule: (String, Boolean) -> Unit,
    onEditModule: (String, ModuleDef) -> Unit,
    onDeleteModule: (String) -> Unit,
    onReorderModules: (List<String>) -> Unit,
    onAddModules: () -> Unit,
    onBack: () -> Unit,
) {
    // 本地排序列表，拖拽时即时更新
    var localModules by remember(modules) { mutableStateOf(modules) }
    val listState = rememberLazyListState()
    val hapticFeedback = LocalHapticFeedback.current

    // 将模块分为标准模块和无限流模块
    val standardModules = remember(localModules) {
        localModules.filter { !HomepageViewModel.isInfinite(it.type, it.layoutConfig) }
    }
    val infiniteModules = remember(localModules) {
        localModules.filter { HomepageViewModel.isInfinite(it.type, it.layoutConfig) }
    }

    // 拖拽排序状态
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        localModules = localModules.toMutableList().apply {
            val fromIndex = indexOfFirst { it.id == from.key }
            val toIndex = indexOfFirst { it.id == to.key }
            if (fromIndex >= 0 && toIndex >= 0 && fromIndex != toIndex) {
                add(toIndex, removeAt(fromIndex))
            }
        }
    }

    // 拖拽结束后持久化排序
    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging) {
            val orderedIds = localModules.map { it.id }
            if (orderedIds != modules.map { it.id }) {
                onReorderModules(orderedIds)
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 标准模块（可排序）
            items(standardModules, key = { it.id }) { module ->
                ReorderableItem(reorderableState, key = module.id) { isDragging ->
                    ModuleItem(
                        module = module,
                        isDragging = isDragging,
                        onToggle = { onToggleModule(module.id, it) },
                        onEdit = {
                            // 构造模块定义对象，传递给编辑回调
                            onEditModule(
                                module.id,
                                ModuleDef(
                                    key = module.moduleKey,
                                    type = module.type,
                                    title = module.title,
                                    args = module.args,
                                    layoutConfig = module.layoutConfig,
                                    url = module.url,
                                    sourceUrl = module.sourceUrl
                                )
                            )
                        },
                        onDelete = { onDeleteModule(module.id) },
                        dragModifier = Modifier
                            .zIndex(if (isDragging) 1f else 0f)
                            .longPressDraggableHandle(
                                onDragStarted = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                },
                                onDragStopped = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                }
                            )
                    )
                }
            }
            // 无限流模块（固定底部，不可排序）
            if (infiniteModules.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "无限流模块",
                        style = MaterialTheme.typography.labelMedium,
                        color = pageSecondaryTextColor(),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
                items(infiniteModules, key = { it.id }) { module ->
                    ModuleItem(
                        module = module,
                        isDragging = false,
                        onToggle = { onToggleModule(module.id, it) },
                        onEdit = {
                            // 构造模块定义对象，传递给编辑回调
                            onEditModule(
                                module.id,
                                ModuleDef(
                                    key = module.moduleKey,
                                    type = module.type,
                                    title = module.title,
                                    args = module.args,
                                    layoutConfig = module.layoutConfig,
                                    url = module.url,
                                    sourceUrl = module.sourceUrl
                                )
                            )
                        },
                        onDelete = { onDeleteModule(module.id) },
                        dragModifier = Modifier
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        // 添加模块按钮
        OutlinedButton(
            onClick = onAddModules,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Text("添加模块")
        }
    }
}

/**
 * 单个模块项的 UI 组件。
 *
 * 以卡片形式展示模块的标题和类型，并提供拖拽排序、编辑、删除和可见性切换等操作按钮。
 *
 * @param module 模块的 UI 数据
 * @param isDragging 当前项是否正在被拖拽
 * @param onToggle 切换可见性的回调
 * @param onEdit 编辑模块的回调
 * @param onDelete 删除模块的回调
 * @param dragModifier 拖拽手柄的 Modifier，用于长按拖拽排序
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ModuleItem(
    module: HomepageModuleManageUi,
    isDragging: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    dragModifier: Modifier,
) {
    // 根据模块类型 key 获取对应的枚举类型
    val moduleType = HomepageModuleType.fromKey(module.type)
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .then(dragModifier)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 拖拽手柄图标
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "拖拽排序",
                tint = pageSecondaryTextColor(),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
            // 模块标题和类型标签
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    // 优先显示自定义标题，其次原始标题，最后显示默认名称
                    text = module.title.ifBlank { module.originalTitle.ifBlank { "未命名模块" } },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                TextCard(
                    text = moduleType.title,
                    textStyle = MaterialTheme.typography.labelSmall
                )
            }
            // 编辑按钮
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "编辑"
                )
            }
            // 删除按钮
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "删除"
                )
            }
            // 可见性开关
            Switch(
                checked = module.isVisible,
                onCheckedChange = onToggle
            )
        }
    }
}
