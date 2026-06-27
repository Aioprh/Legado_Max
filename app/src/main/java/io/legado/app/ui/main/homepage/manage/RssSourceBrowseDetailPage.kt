/**
 * 文件：RssSourceBrowseDetailPage.kt
 *
 * 作用：订阅源模块浏览详情页，用于管理指定订阅源下的首页模块。
 *
 * 主要功能：
 * 1. 通过两 Tab 结构（已加入 / 发现）分类展示和管理模块
 * 2. Tab 0（已加入）：展示当前订阅源已加入的模块，支持长按拖拽排序、编辑、删除、显隐切换
 * 3. Tab 1（发现）：从订阅源分类创建模块，支持选择分类类型后添加
 *
 * 参照书源 SourceBrowseDetailPage 实现，关键差异：
 * - 订阅源使用 rss_ 前缀集 ID 以避免与书源集（src_）冲突
 * - 发现分类通过 RssSource.sortUrls() 获取
 * - 模块添加通过 onAddRssCustomModule 操作
 */
package io.legado.app.ui.main.homepage.manage

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.legado.app.R
import io.legado.app.domain.model.HomepageModuleType
import io.legado.app.domain.model.ModuleDef
import io.legado.app.ui.main.homepage.HomepageManageActions
import io.legado.app.ui.main.homepage.HomepageModuleManageUi
import io.legado.app.ui.main.homepage.HomepageViewModel
import io.legado.app.ui.theme.pageSecondaryTextColor
import io.legado.app.ui.widget.components.VerticalScrollbar
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.card.TextCard
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * 订阅源模块浏览详情页，两 Tab 结构
 *
 * 参照书源的 SourceBrowseDetailPage 实现，通过两个 Tab 分类管理
 * 指定订阅源下的首页模块：
 * - Tab 0：已加入当前集的模块列表
 * - Tab 1：从订阅源分类创建新模块
 *
 * @param sourceUrl 订阅源 URL
 * @param sourceName 订阅源名称，用于界面展示
 * @param targetSetId 目标集 ID（rss_ 前缀），为 null 表示默认
 * @param allModules 所有模块的 UI 数据列表
 * @param actions 首页管理操作回调集合（含 onGetRssKinds / onAddRssCustomModule）
 * @param onEditModule 编辑模块的回调
 * @param onBack 返回上一页的回调
 */
@Composable
fun RssSourceBrowseDetailPage(
    sourceUrl: String,
    sourceName: String,
    targetSetId: String?,
    allModules: List<HomepageModuleManageUi>,
    actions: HomepageManageActions,
    onEditModule: (String, ModuleDef) -> Unit,
    onBack: () -> Unit,
) {
    var selectedTab by remember { mutableStateOf(0) }

    // 计算当前 RSS 源的集 ID（rss_<sourceUrl>），用于「已加入」Tab 筛选
    val rssSetId = remember(sourceUrl) { "rss_$sourceUrl" }
    val effectiveTargetSetId = targetSetId ?: rssSetId

    Column(modifier = Modifier.fillMaxWidth()) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text(stringResource(R.string.homepage_joined)) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text(stringResource(R.string.homepage_discover)) }
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        when (selectedTab) {
            0 -> JoinedModulesTab(
                sourceUrl = sourceUrl,
                targetSetId = effectiveTargetSetId,
                allModules = allModules,
                actions = actions,
                onEditModule = onEditModule,
            )
            // 「发现」Tab 传入原始 targetSetId（null 时由 addRssCustomModule 负责创建集）
            1 -> RssDiscoverTab(
                sourceUrl = sourceUrl,
                targetSetId = targetSetId,
                sourceName = sourceName,
                actions = actions,
            )
        }
    }
}

/**
 * Tab 0: 已加入的模块（复用书源的 JoinedModulesTab 逻辑）
 */
@Composable
private fun JoinedModulesTab(
    sourceUrl: String,
    targetSetId: String?,
    allModules: List<HomepageModuleManageUi>,
    actions: HomepageManageActions,
    onEditModule: (String, ModuleDef) -> Unit,
) {
    val joinedModules = remember(sourceUrl, targetSetId, allModules) {
        allModules.filter { module ->
            module.sourceUrl == sourceUrl &&
                    (targetSetId == null || module.customSetId == targetSetId)
        }
    }

    var localModules by remember(joinedModules) { mutableStateOf(joinedModules) }
    val listState = rememberLazyListState()
    val hapticFeedback = LocalHapticFeedback.current

    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        localModules = localModules.toMutableList().apply {
            val fromIndex = indexOfFirst { it.id == from.key }
            val toIndex = indexOfFirst { it.id == to.key }
            if (fromIndex >= 0 && toIndex >= 0 && fromIndex != toIndex) {
                add(toIndex, removeAt(fromIndex))
            }
        }
    }

    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging) {
            val orderedIds = localModules.map { it.id }
            if (orderedIds != joinedModules.map { it.id }) {
                actions.onReorderModules(orderedIds)
            }
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(localModules, key = { it.id }) { module ->
                ReorderableItem(reorderableState, key = module.id) { isDragging ->
                    RssModuleItem(
                        module = module,
                        isDragging = isDragging,
                        onToggle = { actions.onToggleModule(module.id, it) },
                        onEdit = {
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
                        onDelete = { actions.onDeleteModule(module.id) },
                        dragModifier = Modifier
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
        VerticalScrollbar(
            state = listState,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}

/**
 * Tab 1: 从订阅源分类创建模块
 *
 * 从订阅源的 sortUrl 解析分类列表，支持选择分类后
 * 通过 AddCustomModuleDialog 添加为首页模块。
 * 当模块类型为按钮组时，支持多选分类，每个分类生成一个按钮。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RssDiscoverTab(
    sourceUrl: String,
    targetSetId: String?,
    sourceName: String,
    actions: HomepageManageActions,
) {
    // 异步获取订阅源的分类列表
    val rssKinds by produceState<List<Pair<String, String>>>(emptyList(), sourceUrl) {
        value = actions.onGetRssKinds(sourceUrl)
    }
    val isLoadingKinds = rssKinds.isEmpty()

    var selectedModuleType by remember { mutableStateOf(HomepageModuleType.Grid.key) }
    var typeMenuExpanded by remember { mutableStateOf(false) }
    // 多选分类索引集合（按钮组模式）
    var selectedKindIndices by remember(sourceUrl, selectedModuleType) { mutableStateOf(setOf<Int>()) }
    // 单选分类索引（非按钮组模式）
    var selectedKindIndex by remember(sourceUrl) { mutableStateOf<Int?>(null) }
    var showKindSheet by remember { mutableStateOf(false) }
    var showManualAddDialog by remember { mutableStateOf(false) }
    var manualAddPrefill by remember { mutableStateOf<ModuleDef?>(null) }

    // 是否处于按钮组多选模式
    val isButtonGroupMode = selectedModuleType == HomepageModuleType.ButtonGroup.key

    Column(modifier = Modifier.fillMaxWidth()) {
        // 模块类型选择
        ExposedDropdownMenuBox(
            expanded = typeMenuExpanded,
            onExpandedChange = { typeMenuExpanded = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        ) {
            OutlinedTextField(
                value = stringResource(HomepageModuleType.fromKey(selectedModuleType).titleRes),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.homepage_module_type)) },
                singleLine = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeMenuExpanded)
                },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = typeMenuExpanded,
                onDismissRequest = { typeMenuExpanded = false }
            ) {
                HomepageModuleType.entries.forEach { moduleType ->
                    if (moduleType == HomepageModuleType.Unknown) return@forEach
                    DropdownMenuItem(
                        text = { Text(stringResource(moduleType.titleRes)) },
                        onClick = {
                            selectedModuleType = moduleType.key
                            typeMenuExpanded = false
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        // 分类选择
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
                        text = stringResource(R.string.homepage_loading_categories),
                        style = MaterialTheme.typography.bodySmall,
                        color = pageSecondaryTextColor(),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        } else {
            ExposedDropdownMenuBox(
                expanded = false,
                onExpandedChange = { if (it) showKindSheet = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            ) {
                OutlinedTextField(
                    value = if (isButtonGroupMode) {
                        when {
                            selectedKindIndices.isEmpty() -> ""
                            selectedKindIndices.size <= 3 -> selectedKindIndices.mapNotNull { rssKinds.getOrNull(it)?.first?.ifBlank { sourceName } }
                                .joinToString("、")
                            else -> stringResource(R.string.homepage_selected_categories_count, selectedKindIndices.size)
                        }
                    } else {
                        selectedKindIndex?.let { rssKinds.getOrNull(it)?.first?.ifBlank { sourceName } } ?: ""
                    },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.homepage_select_category)) },
                    singleLine = true,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = false)
                    },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
            }
            // 按钮组模式下，显示多选提示
            if (isButtonGroupMode) {
                Text(
                    text = stringResource(R.string.homepage_multi_select_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = pageSecondaryTextColor(),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        // 手动添加按钮
        OutlinedButton(
            onClick = {
                manualAddPrefill = ModuleDef(
                    type = selectedModuleType,
                    title = sourceName,
                    sourceUrl = sourceUrl
                )
                showManualAddDialog = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.homepage_manual_add_module))
        }
    }

    // 分类选择底部弹窗
    if (showKindSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showKindSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = stringResource(R.string.homepage_select_category),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rssKinds.forEachIndexed { index, kind ->
                            val isSelected = if (isButtonGroupMode) {
                                selectedKindIndices.contains(index)
                            } else {
                                selectedKindIndex == index
                            }
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surface,
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface,
                                border = if (isSelected) null
                                else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                onClick = {
                                    if (isButtonGroupMode) {
                                        selectedKindIndices = if (isSelected) {
                                            selectedKindIndices - index
                                        } else {
                                            selectedKindIndices + index
                                        }
                                    } else {
                                        selectedKindIndex = index
                                        showKindSheet = false
                                        manualAddPrefill = ModuleDef(
                                            key = "rss_${kind.first}_${kind.second}",
                                            type = selectedModuleType,
                                            title = kind.first.ifBlank { sourceName },
                                            url = kind.second,
                                            sourceUrl = sourceUrl
                                        )
                                        showManualAddDialog = true
                                    }
                                }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(
                                        start = if (isButtonGroupMode) 4.dp else 12.dp,
                                        end = 12.dp,
                                        top = 4.dp,
                                        bottom = 4.dp
                                    )
                                ) {
                                    if (isButtonGroupMode) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = null,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Text(
                                        text = kind.first.ifBlank { sourceName },
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
                // 按钮组模式下，显示创建按钮
                if (isButtonGroupMode && selectedKindIndices.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            val selectedKinds = selectedKindIndices.mapNotNull { rssKinds.getOrNull(it) }
                            val title = selectedKinds.joinToString("、") { it.first.ifBlank { sourceName } }
                            val kindTitles = selectedKinds.map { it.first.ifBlank { sourceName } }
                            actions.onAddRssButtonGroupFromKinds(sourceUrl, targetSetId, title, kindTitles)
                            showKindSheet = false
                            selectedKindIndices = emptySet()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.homepage_create_button_group, selectedKindIndices.size))
                    }
                }
            }
        }
    }

    // 添加模块对话框（预填充分类信息）
    if (showManualAddDialog) {
        AddCustomModuleDialog(
            show = true,
            prefill = manualAddPrefill ?: ModuleDef(type = selectedModuleType, sourceUrl = sourceUrl),
            isEditMode = false,
            onConfirm = { moduleDef ->
                // 使用 RSS 专用添加操作（确保集 ID 使用 rss_ 前缀）
                actions.onAddRssCustomModule(sourceUrl, targetSetId, moduleDef)
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
 * 单个模块项的 UI 组件（订阅源版本）
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RssModuleItem(
    module: HomepageModuleManageUi,
    isDragging: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    dragModifier: Modifier,
) {
    val moduleType = HomepageModuleType.fromKey(module.type)
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (isDragging) 1f else 0f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = stringResource(R.string.homepage_drag_sort),
                tint = pageSecondaryTextColor(),
                modifier = Modifier
                    .size(24.dp)
                    .then(dragModifier)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = module.title.ifBlank {
                        module.originalTitle.ifBlank { stringResource(R.string.homepage_unnamed_module) }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextCard(
                        text = stringResource(moduleType.titleRes),
                        textStyle = MaterialTheme.typography.labelSmall
                    )
                    TextCard(
                        text = if (module.sourceType == "rss") "订阅源" else "书源",
                        textStyle = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.homepage_edit))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.homepage_delete))
            }
            Switch(checked = module.isVisible, onCheckedChange = onToggle)
        }
    }
}
