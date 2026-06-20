/**
 * 文件：SourceBrowseDetailPage.kt
 *
 * 作用：书源模块浏览详情页，用于管理指定书源下的首页模块。
 *
 * 主要功能：
 * 1. 通过三 Tab 结构（已加入 / 书源模块 / 发现）分类展示和管理模块
 * 2. Tab 0（已加入）：展示当前集已加入的模块，支持长按拖拽排序、编辑、删除、显隐切换
 * 3. Tab 1（书源模块）：展示书源 JSON 中定义的模块，支持一键加入或移除
 * 4. Tab 2（发现）：从书源发现分类创建模块，支持单选添加、多选创建按钮组、手动添加自定义模块
 *
 * 该页面是首页模块管理功能的核心交互界面之一。
 */
package io.legado.app.ui.main.homepage.manage

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import io.legado.app.ui.main.homepage.HomepageManageActions
import io.legado.app.ui.main.homepage.HomepageModuleManageUi
import io.legado.app.ui.main.homepage.HomepageViewModel
import io.legado.app.ui.theme.pageSecondaryTextColor
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.card.TextCard
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * 书源模块浏览详情页，三 Tab 结构
 *
 * 该页面通过三个 Tab 分类管理指定书源下的首页模块：
 * - Tab 0：已加入当前集的模块列表
 * - Tab 1：书源 JSON 中定义的模块列表
 * - Tab 2：从书源发现分类创建新模块
 *
 * @param sourceUrl 书源 URL，用于定位具体书源
 * @param sourceName 书源名称，用于界面展示
 * @param targetSetId 目标集 ID，为 null 表示默认集
 * @param allModules 所有模块的 UI 数据列表
 * @param actions 首页管理操作回调集合
 * @param onBack 返回上一页的回调
 */
@Composable
fun SourceBrowseDetailPage(
    sourceUrl: String,
    sourceName: String,
    targetSetId: String?,
    allModules: List<HomepageModuleManageUi>,
    actions: HomepageManageActions,
    onBack: () -> Unit,
) {
    // 当前选中的 Tab 索引，默认显示"已加入"Tab
    var selectedTab by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // 顶部 Tab 栏，提供三个分类入口
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("已加入") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("书源模块") }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("发现") }
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        // 根据选中的 Tab 显示对应内容
        when (selectedTab) {
            0 -> JoinedModulesTab(
                sourceUrl = sourceUrl,
                targetSetId = targetSetId,
                allModules = allModules,
                actions = actions,
            )
            1 -> SourceModulesTab(
                sourceUrl = sourceUrl,
                targetSetId = targetSetId,
                allModules = allModules,
                actions = actions,
            )
            2 -> DiscoverTab(
                sourceUrl = sourceUrl,
                targetSetId = targetSetId,
                actions = actions,
            )
        }
    }
}

/**
 * Tab 0: 已加入的模块
 *
 * 展示当前集已加入的模块列表，每个模块支持以下操作：
 * - 长按拖拽排序：调整模块显示顺序
 * - 编辑：打开编辑对话框修改模块配置
 * - 删除：从当前集移除模块
 * - 显隐切换：控制模块在首页是否可见
 *
 * @param sourceUrl 书源 URL
 * @param targetSetId 目标集 ID，为 null 表示默认集
 * @param allModules 所有模块的 UI 数据列表
 * @param actions 首页管理操作回调集合
 */
@Composable
private fun JoinedModulesTab(
    sourceUrl: String,
    targetSetId: String?,
    allModules: List<HomepageModuleManageUi>,
    actions: HomepageManageActions,
) {
    // 获取当前集已加入的模块：匹配书源 URL 和目标集 ID
    val joinedModules = remember(sourceUrl, targetSetId, allModules) {
        allModules.filter { module ->
            module.sourceUrl == sourceUrl &&
                    (targetSetId == null || module.customSetId == targetSetId)
        }
    }

    // 本地排序列表，拖拽时即时更新
    var localModules by remember(joinedModules) { mutableStateOf(joinedModules) }
    val listState = rememberLazyListState()
    val hapticFeedback = LocalHapticFeedback.current

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
            if (orderedIds != joinedModules.map { it.id }) {
                actions.onReorderModules(orderedIds)
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(localModules, key = { it.id }) { module ->
            ReorderableItem(reorderableState, key = module.id) { isDragging ->
                ModuleItem(
                    module = module,
                    isDragging = isDragging,
                    onToggle = { actions.onToggleModule(module.id, it) },
                    onEdit = {
                        // 构造模块定义对象，传递给编辑回调
                        actions.onUpdateModule(
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
                    onDelete = { actions.onDeleteModule(module.id) },
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
    }
}

/**
 * Tab 1: 书源 JSON 中定义的模块
 *
 * 展示书源 JSON 中定义的所有模块，用户可通过开关将其加入或移出当前集。
 * - 开启开关：将该模块加入当前集
 * - 关闭开关：从当前集移除该模块
 *
 * @param sourceUrl 书源 URL
 * @param targetSetId 目标集 ID，为 null 表示默认集
 * @param allModules 所有模块的 UI 数据列表
 * @param actions 首页管理操作回调集合
 */
@Composable
private fun SourceModulesTab(
    sourceUrl: String,
    targetSetId: String?,
    allModules: List<HomepageModuleManageUi>,
    actions: HomepageManageActions,
) {
    // 获取书源定义的模块列表
    val sourceModules = remember(sourceUrl) {
        actions.onGetSourceModules(sourceUrl, null)
    }

    // 本地排序列表，拖拽时即时更新
    var localModules by remember(sourceModules) { mutableStateOf(sourceModules) }
    val listState = rememberLazyListState()
    val hapticFeedback = LocalHapticFeedback.current

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
            if (orderedIds != sourceModules.map { it.id }) {
                actions.onReorderModules(orderedIds)
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(localModules, key = { it.id }) { module ->
            ReorderableItem(reorderableState, key = module.id) { isDragging ->
                // 检查该模块是否已加入当前集
                val isJoined = allModules.any {
                    it.sourceUrl == sourceUrl &&
                            it.moduleKey == module.moduleKey &&
                            (targetSetId == null || it.customSetId == targetSetId)
                }
                // 根据模块类型 key 获取对应的枚举值，用于显示类型标题
                val moduleType = HomepageModuleType.fromKey(module.type)
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(if (isDragging) 1f else 0f)
                        .longPressDraggableHandle(
                            onDragStarted = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                            },
                            onDragStopped = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
                            }
                        )
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
                        // 左侧：模块标题和类型标签
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = module.title.ifBlank { module.originalTitle.ifBlank { "未命名模块" } },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            TextCard(
                                text = moduleType.title,
                                textStyle = MaterialTheme.typography.labelSmall
                            )
                        }
                        // 加入/移除开关：根据开关状态将模块加入或移出当前集
                        Switch(
                            checked = isJoined,
                            onCheckedChange = { checked ->
                                // 构造模块定义对象
                                val moduleDef = ModuleDef(
                                    key = module.moduleKey,
                                    type = module.type,
                                    title = module.title,
                                    args = module.args,
                                    layoutConfig = module.layoutConfig,
                                    url = module.url,
                                    sourceUrl = module.sourceUrl
                                )
                                if (checked) {
                                    // 加入模块到当前集
                                    actions.onJoinModule(sourceUrl, targetSetId, moduleDef)
                                } else {
                                    // 移除：找到对应模块并删除
                                    val targetModule = allModules.find {
                                        it.sourceUrl == sourceUrl &&
                                                it.moduleKey == module.moduleKey &&
                                                (targetSetId == null || it.customSetId == targetSetId)
                                    }
                                    targetModule?.let { actions.onDeleteModule(it.id) }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Tab 2: 从书源发现分类创建模块
 *
 * 从书源的发现分类创建新的首页模块，支持以下三种方式：
 * 1. 选择单个分类：直接创建对应模块
 * 2. 选择多个分类：创建按钮组，将多个分类聚合为一个按钮组模块
 * 3. 手动添加：打开自定义模块对话框，手动填写模块配置
 *
 * @param sourceUrl 书源 URL
 * @param targetSetId 目标集 ID，为 null 表示默认集
 * @param actions 首页管理操作回调集合
 */
@Composable
private fun DiscoverTab(
    sourceUrl: String,
    targetSetId: String?,
    actions: HomepageManageActions,
) {
    // 异步获取书源的发现分类列表（支持 JS 动态生成的分类）
    val exploreKinds by produceState<List<Pair<String, String>>>(emptyList(), sourceUrl) {
        value = actions.onGetExploreKinds(sourceUrl)
    }
    // 是否正在加载分类
    val isLoadingKinds = exploreKinds.isEmpty()

    // 选中的模块类型，默认为网格类型
    var selectedModuleType by remember { mutableStateOf(HomepageModuleType.Grid.key) }
    // 模块类型下拉菜单的展开状态
    var typeMenuExpanded by remember { mutableStateOf(false) }
    // 选中的发现分类索引（单选，null 表示未选择）
    var selectedKindIndex by remember { mutableStateOf<Int?>(null) }
    // 分类下拉菜单的展开状态
    var kindMenuExpanded by remember { mutableStateOf(false) }
    // 手动添加模块对话框的显示状态
    var showManualAddDialog by remember { mutableStateOf(false) }
    // 手动添加模块对话框的预填充数据
    var manualAddPrefill by remember { mutableStateOf<ModuleDef?>(null) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // 模块类型选择：通过下拉菜单选择要创建的模块类型
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = HomepageModuleType.fromKey(selectedModuleType).title,
                onValueChange = {},
                readOnly = true,
                label = { Text("模块类型") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { typeMenuExpanded = true }) {
                Icon(Icons.Default.ArrowDropDown, contentDescription = "选择类型")
            }
            DropdownMenu(
                expanded = typeMenuExpanded,
                onDismissRequest = { typeMenuExpanded = false }
            ) {
                HomepageModuleType.entries.forEach { moduleType ->
                    // 跳过未知类型
                    if (moduleType == HomepageModuleType.Unknown) return@forEach
                    DropdownMenuItem(
                        text = { Text(moduleType.title) },
                        onClick = {
                            selectedModuleType = moduleType.key
                            typeMenuExpanded = false
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        // 发现分类选择：通过下拉菜单选择分类（与模块类型选择器样式一致）
        if (isLoadingKinds) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    Text(
                        text = "正在加载发现分类...",
                        style = MaterialTheme.typography.bodySmall,
                        color = pageSecondaryTextColor(),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = selectedKindIndex?.let { exploreKinds[it].first } ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("选择分类") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { kindMenuExpanded = true }) {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "选择分类")
                }
                DropdownMenu(
                    expanded = kindMenuExpanded,
                    onDismissRequest = { kindMenuExpanded = false }
                ) {
                    exploreKinds.forEachIndexed { index, kind ->
                        DropdownMenuItem(
                            text = { Text(kind.first) },
                            onClick = {
                                selectedKindIndex = index
                                kindMenuExpanded = false
                                // 选择分类后直接打开预填充的添加模块对话框
                                manualAddPrefill = ModuleDef(
                                    key = "explore_${kind.first}",
                                    type = selectedModuleType,
                                    title = kind.first,
                                    url = kind.second,
                                    sourceUrl = sourceUrl
                                )
                                showManualAddDialog = true
                            }
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        // 手动添加按钮（不选择分类，直接添加自定义模块）
        OutlinedButton(
            onClick = {
                manualAddPrefill = ModuleDef(
                    type = selectedModuleType,
                    sourceUrl = sourceUrl
                )
                showManualAddDialog = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("手动添加模块")
        }
    }

    // 手动添加模块对话框：用于填写自定义模块的完整配置，预填充所选分类信息
    if (showManualAddDialog) {
        AddCustomModuleDialog(
            show = true,
            prefill = manualAddPrefill ?: ModuleDef(type = selectedModuleType, sourceUrl = sourceUrl),
            isEditMode = false,
            onConfirm = { moduleDef ->
                actions.onAddCustomModule(sourceUrl, targetSetId, moduleDef)
                showManualAddDialog = false
                manualAddPrefill = null
                selectedKindIndex = null
            },
            onDismiss = {
                showManualAddDialog = false
                manualAddPrefill = null
            }
        )
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
    // 根据模块类型 key 获取对应的枚举值
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
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                TextCard(
                    text = moduleType.title,
                    textStyle = MaterialTheme.typography.labelSmall
                )
            }
            // 编辑按钮：打开编辑对话框，传入当前模块的完整配置
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.Edit, contentDescription = "编辑")
            }
            // 删除按钮：从当前集移除该模块
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = "删除")
            }
            // 显隐开关：控制模块在首页是否可见
            Switch(
                checked = module.isVisible,
                onCheckedChange = onToggle
            )
        }
    }
}
