package io.legado.app.ui.book.explore

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.DialogFragment
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.data.entities.SearchBook
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.pageCardContainerColor
import io.legado.app.ui.theme.pageSecondaryTextColor
import io.legado.app.ui.theme.pageAccentColor
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.getClipText
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.sendToClip
import io.legado.app.utils.toastOnUi

/**
 * 屏蔽规则配置弹窗
 *
 * 使用 Compose 构建界面，通过 DialogFragment + ComposeView 模式桥接到传统 View 系统。
 * 包含规则列表、编辑弹窗、分组管理、起效规则查看等功能。
 */
class ExploreBlockRuleConfigDialog : DialogFragment() {

    var sourceUrl: String = ""
    var allBooks: List<SearchBook> = emptyList()
    var onRulesChanged: (() -> Unit)? = null
    var onShowProgressChanged: ((Boolean) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                LegadoTheme {
                    ExploreBlockRuleConfigContent(
                        sourceUrl = sourceUrl,
                        allBooks = allBooks,
                        onDismiss = { dismissAllowingStateLoss() },
                        onRulesChanged = {
                            onRulesChanged?.invoke()
                        },
                        onShowProgressChanged = { show ->
                            onShowProgressChanged?.invoke(show)
                        }
                    )
                }
            }
        }
    }
}

/**
 * 屏蔽规则配置主界面
 * 包含规则列表、分组筛选、屏蔽进度开关、起效规则查看等功能
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExploreBlockRuleConfigContent(
    sourceUrl: String,
    allBooks: List<SearchBook>,
    onDismiss: () -> Unit,
    onRulesChanged: () -> Unit,
    onShowProgressChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var rules by remember { mutableStateOf<List<ExploreBlockRule>>(ExploreBlockRuleStore.load(context)) }
    var currentGroup by remember { mutableStateOf<String?>(null) }
    var editingRule by remember { mutableStateOf<ExploreBlockRule?>(null) }
    var deletingRule by remember { mutableStateOf<ExploreBlockRule?>(null) }
    var showGroupManage by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showProgress by remember { mutableStateOf(context.getPrefBoolean(PreferKey.exploreBlockRuleShowProgress, false)) }
    var showActiveRules by remember { mutableStateOf(false) }

    fun refresh() {
        ExploreBlockRuleStore.invalidateCache()
        rules = ExploreBlockRuleStore.load(context)
        onRulesChanged()
    }

    fun saveRules(newRules: List<ExploreBlockRule>) {
        ExploreBlockRuleStore.save(context, newRules)
        rules = newRules
        onRulesChanged()
    }

    val filteredRules = if (currentGroup == null) rules else rules.filter { it.group == currentGroup }
    val groups = ExploreBlockRuleGroupStore.load(context)

    // Delete confirm dialog
    deletingRule?.let { rule ->
        AlertDialog(
            onDismissRequest = { deletingRule = null },
            title = { Text(stringResource(R.string.explore_block_rule_delete_confirm, rule.name.ifBlank { rule.pattern })) },
            confirmButton = {
                TextButton(onClick = {
                    saveRules(rules.filterNot { it.id == rule.id })
                    deletingRule = null
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { deletingRule = null }) { Text(stringResource(android.R.string.cancel)) }
            }
        )
    }

    // Edit dialog
    editingRule?.let { rule ->
        ExploreBlockRuleEditContent(
            sourceRule = rule,
            groups = groups,
            onSave = { newRule ->
                val index = rules.indexOfFirst { it.id == newRule.id }
                val newRules = if (index >= 0) {
                    rules.toMutableList().also { it[index] = newRule }
                } else {
                    rules + newRule
                }
                saveRules(newRules)
                editingRule = null
            },
            onDismiss = { editingRule = null }
        )
    }

    // Group manage dialog
    if (showGroupManage) {
        ExploreBlockRuleGroupManageContent(
            groups = groups,
            onAddGroup = { name ->
                val newGroups = (groups + name).distinct()
                ExploreBlockRuleGroupStore.save(context, newGroups)
                refresh()
            },
            onRenameGroup = { oldName, newName ->
                val newRules = rules.map {
                    if (it.group == oldName) it.copy(group = newName) else it
                }
                val newGroups = groups.map { if (it == oldName) newName else it }
                ExploreBlockRuleGroupStore.save(context, newGroups)
                saveRules(newRules)
                if (currentGroup == oldName) currentGroup = newName
            },
            onDeleteGroup = { name ->
                val newRules = rules.map {
                    if (it.group == name) it.copy(group = ExploreBlockRuleGroupStore.DEFAULT_GROUP) else it
                }
                val newGroups = groups.filterNot { it == name }
                ExploreBlockRuleGroupStore.save(context, newGroups)
                saveRules(newRules)
                if (currentGroup == name) currentGroup = null
            },
            onDismiss = { showGroupManage = false }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = pageCardContainerColor()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "关闭")
                }
                Text(
                    text = stringResource(R.string.explore_block_rule_config),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    editingRule = ExploreBlockRule(
                        group = currentGroup ?: ExploreBlockRuleGroupStore.DEFAULT_GROUP
                    )
                }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.explore_block_rule_add))
                }
                IconButton(onClick = { showMoreMenu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                }
                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { showMoreMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.explore_block_rule_group_manage)) },
                        onClick = {
                            showMoreMenu = false
                            showGroupManage = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.explore_block_rule_import_success).replace("成功", "")) },
                        onClick = {
                            showMoreMenu = false
                            importFromClipboard(context) { refresh() }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.explore_block_rule_export_success).replace("已复制到剪贴板", "导出")) },
                        onClick = {
                            showMoreMenu = false
                            exportToClipboard(context, filteredRules)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Group filter chips
            if (groups.size > 1) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = currentGroup == null,
                        onClick = { currentGroup = null },
                        label = { Text(stringResource(R.string.explore_block_rule_scope_all)) }
                    )
                    groups.forEach { group ->
                        FilterChip(
                            selected = currentGroup == group,
                            onClick = { currentGroup = group },
                            label = { Text(group) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 显示屏蔽进度开关 + 起效的规则按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.explore_block_rule_show_progress),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = showProgress,
                    onCheckedChange = {
                        showProgress = it
                        context.putPrefBoolean(PreferKey.exploreBlockRuleShowProgress, it)
                        onShowProgressChanged(it)
                    }
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showActiveRules = true }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.explore_block_rule_active_rules),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = pageAccentColor()
                )
                val currentMatchedRules = ExploreBlockRuleStore.getMatchedRules(context, allBooks, sourceUrl)
                Text(
                    text = "${currentMatchedRules.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = pageSecondaryTextColor()
                )
            }

            HorizontalDivider()

            // Rule list
            if (filteredRules.isEmpty()) {
                Text(
                    text = stringResource(R.string.explore_block_rule_empty),
                    modifier = Modifier.padding(18.dp),
                    color = pageSecondaryTextColor()
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 480.dp)
                ) {
                    items(filteredRules, key = { it.id }) { rule ->
                        BlockRuleItem(
                            rule = rule,
                            onToggleEnabled = {
                                val newRules = rules.map {
                                    if (it.id == rule.id) it.copy(enabled = !it.enabled) else it
                                }
                                saveRules(newRules)
                            },
                            onEdit = { editingRule = rule },
                            onDelete = { deletingRule = rule }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    // 起效的规则弹窗：显示实际匹配到书籍的规则，可展开查看匹配的书
    if (showActiveRules) {
        val activeMatchedRules = ExploreBlockRuleStore.getMatchedRules(context, allBooks, sourceUrl)
        AlertDialog(
            onDismissRequest = { showActiveRules = false },
            title = { Text(stringResource(R.string.explore_block_rule_active_rules)) },
            text = {
                if (activeMatchedRules.isEmpty()) {
                    Text(
                        text = stringResource(R.string.explore_block_rule_active_rules_empty),
                        color = pageSecondaryTextColor()
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(activeMatchedRules, key = { it.id }) { rule ->
                            val matchedBooks = allBooks.filter { rule.matches(it) }
                            ActiveRuleItem(rule = rule, matchedBooks = matchedBooks)
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showActiveRules = false }) { Text(stringResource(android.R.string.ok)) }
            }
        )
    }
}

/** 起效规则项，可展开/收起查看匹配到的书籍，默认收起 */
@Composable
private fun ActiveRuleItem(
    rule: ExploreBlockRule,
    matchedBooks: List<SearchBook>
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // 规则标题行，点击展开/收起
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rule.name.ifBlank { rule.pattern },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${rule.modeLabel()} / ${rule.scopeSummary()} / 匹配${matchedBooks.size}本",
                    style = MaterialTheme.typography.bodySmall,
                    color = pageSecondaryTextColor()
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "收起" else "展开",
                modifier = Modifier.size(20.dp),
                tint = pageSecondaryTextColor()
            )
        }

        // 展开后显示匹配的书籍列表
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)) {
                matchedBooks.forEach { book ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "《${book.name}》",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        if (book.author.isNotBlank()) {
                            Text(
                                text = " ${book.author}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = pageSecondaryTextColor()
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 规则列表项，显示名称、模式、范围、分组，支持启用/编辑/删除 */
@Composable
private fun BlockRuleItem(
    rule: ExploreBlockRule,
    onToggleEnabled: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = rule.name.ifBlank { rule.pattern },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium
            )
        },
        supportingContent = {
            Column {
                Text(
                    text = "${rule.modeLabel()} / ${rule.scopeSummary()} / ${rule.group}",
                    style = MaterialTheme.typography.bodySmall,
                    color = pageSecondaryTextColor()
                )
                if (rule.pattern.isNotBlank()) {
                    Text(
                        text = rule.pattern,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = pageSecondaryTextColor()
                    )
                }
                if (!rule.scope.isNullOrBlank()) {
                    Text(
                        text = "仅: ${rule.scope!!.replace(";", "; ").trim()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = pageSecondaryTextColor()
                    )
                }
                if (!rule.excludeScope.isNullOrBlank()) {
                    Text(
                        text = "排除: ${rule.excludeScope!!.replace(";", "; ").trim()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = pageSecondaryTextColor()
                    )
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = { onToggleEnabled() }
                )
                IconButton(onClick = onEdit, modifier = Modifier.width(32.dp)) {
                    Icon(Icons.Filled.Edit, contentDescription = "编辑", modifier = Modifier.width(18.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.width(32.dp)) {
                    Icon(Icons.Filled.Delete, contentDescription = "删除", modifier = Modifier.width(18.dp))
                }
            }
        },
        modifier = Modifier.clickable { onEdit() }
    )
}

/** 规则编辑弹窗，支持新增和编辑屏蔽规则 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ExploreBlockRuleEditContent(
    sourceRule: ExploreBlockRule,
    groups: List<String>,
    onSave: (ExploreBlockRule) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(sourceRule.name) }
    var pattern by remember { mutableStateOf(sourceRule.pattern) }
    var isRegex by remember { mutableStateOf(sourceRule.isRegex) }
    var selectedGroup by remember { mutableStateOf(sourceRule.group.ifBlank { ExploreBlockRuleGroupStore.DEFAULT_GROUP }) }
    var targetScope by remember { mutableIntStateOf(sourceRule.targetScope) }
    var scope by remember { mutableStateOf(sourceRule.scope.orEmpty()) }
    var excludeScope by remember { mutableStateOf(sourceRule.excludeScope.orEmpty()) }
    var enabled by remember { mutableStateOf(sourceRule.enabled) }
    var patternError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (sourceRule.id.isBlank() || sourceRule.name.isBlank() && sourceRule.pattern.isBlank())
                    stringResource(R.string.explore_block_rule_add)
                else
                    stringResource(R.string.explore_block_rule_edit)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Rule name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.explore_block_rule_name)) },
                    placeholder = { Text(stringResource(R.string.explore_block_rule_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Pattern + regex toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = pattern,
                        onValueChange = {
                            pattern = it
                            patternError = if (it.isNotBlank() && isRegex) {
                                runCatching { Regex(it) }.exceptionOrNull()?.localizedMessage
                            } else null
                        },
                        label = { Text(stringResource(R.string.explore_block_rule_pattern)) },
                        placeholder = { Text(stringResource(R.string.explore_block_rule_pattern_hint)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        isError = patternError != null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Switch(checked = isRegex, onCheckedChange = {
                            isRegex = it
                            patternError = if (pattern.isNotBlank() && it) {
                                runCatching { Regex(pattern) }.exceptionOrNull()?.localizedMessage
                            } else null
                        })
                        Text(
                            text = if (isRegex) stringResource(R.string.explore_block_rule_regex_mode)
                            else stringResource(R.string.explore_block_rule_keyword_mode),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                patternError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                // Group selection
                var groupExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = groupExpanded,
                    onExpandedChange = { groupExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedGroup,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.explore_block_rule_group_default)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = groupExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = groupExpanded,
                        onDismissRequest = { groupExpanded = false }
                    ) {
                        groups.forEach { group ->
                            DropdownMenuItem(
                                text = { Text(group) },
                                onClick = {
                                    selectedGroup = group
                                    groupExpanded = false
                                }
                            )
                        }
                    }
                }

                // Target scope multi-select
                Text(
                    text = stringResource(R.string.explore_block_rule_target_scope),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val scopeOptions = listOf(
                        ExploreBlockRule.SCOPE_TITLE to stringResource(R.string.explore_block_rule_scope_title),
                        ExploreBlockRule.SCOPE_AUTHOR to stringResource(R.string.explore_block_rule_scope_author),
                        ExploreBlockRule.SCOPE_TAG to stringResource(R.string.explore_block_rule_scope_tag),
                        ExploreBlockRule.SCOPE_INTRO to stringResource(R.string.explore_block_rule_scope_intro),
                    )
                    scopeOptions.forEach { (flag, label) ->
                        FilterChip(
                            selected = (targetScope and flag) != 0,
                            onClick = {
                                targetScope = targetScope xor flag
                            },
                            label = { Text(label) }
                        )
                    }
                }

                // Source scope
                OutlinedTextField(
                    value = scope,
                    onValueChange = { scope = it },
                    label = { Text(stringResource(R.string.explore_block_rule_source_scope)) },
                    placeholder = { Text(stringResource(R.string.explore_block_rule_source_scope_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Exclude scope
                OutlinedTextField(
                    value = excludeScope,
                    onValueChange = { excludeScope = it },
                    label = { Text(stringResource(R.string.explore_block_rule_exclude_scope)) },
                    placeholder = { Text(stringResource(R.string.explore_block_rule_exclude_scope_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Enabled
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.explore_block_rule_enabled),
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = pattern.isNotBlank() && patternError == null && targetScope != 0,
                onClick = {
                    onSave(
                        sourceRule.copy(
                            id = sourceRule.id.ifBlank { System.currentTimeMillis().toString() },
                            name = name.ifBlank { pattern },
                            pattern = pattern,
                            isRegex = isRegex,
                            group = selectedGroup.ifBlank { ExploreBlockRuleGroupStore.DEFAULT_GROUP },
                            targetScope = targetScope,
                            enabled = enabled,
                            scope = scope.takeIf { it.isNotBlank() },
                            excludeScope = excludeScope.takeIf { it.isNotBlank() },
                        )
                    )
                }
            ) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        }
    )
}

/** 分组管理弹窗，支持新增/重命名/删除分组 */
@Composable
private fun ExploreBlockRuleGroupManageContent(
    groups: List<String>,
    onAddGroup: (String) -> Unit,
    onRenameGroup: (oldName: String, newName: String) -> Unit,
    onDeleteGroup: (name: String) -> Unit,
    onDismiss: () -> Unit
) {
    var localGroups by remember { mutableStateOf(groups) }
    var inputDialog by remember { mutableStateOf<GroupInput?>(null) }
    var deleteDialog by remember { mutableStateOf<String?>(null) }

    // Input dialog for add/rename
    inputDialog?.let { dialog ->
        var inputName by remember { mutableStateOf(dialog.initialName) }
        AlertDialog(
            onDismissRequest = { inputDialog = null },
            title = { Text(dialog.title) },
            text = {
                OutlinedTextField(
                    value = inputName,
                    onValueChange = { inputName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = inputName.isNotBlank(),
                    onClick = {
                        dialog.onConfirm(inputName.trim())
                        inputDialog = null
                    }
                ) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { inputDialog = null }) { Text(stringResource(android.R.string.cancel)) }
            }
        )
    }

    // Delete confirm dialog
    deleteDialog?.let { groupName ->
        AlertDialog(
            onDismissRequest = { deleteDialog = null },
            title = { Text(stringResource(R.string.explore_block_rule_group_manage)) },
            text = { Text("确定删除分组「$groupName」？规则将移至默认分组。") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteGroup(groupName)
                    localGroups = localGroups.filterNot { it == groupName }
                    deleteDialog = null
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteDialog = null }) { Text(stringResource(android.R.string.cancel)) }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.explore_block_rule_group_manage)) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                items(localGroups) { group ->
                    ListItem(
                        leadingContent = {
                            Icon(Icons.Filled.Folder, contentDescription = null)
                        },
                        headlineContent = { Text(group) },
                        trailingContent = {
                            Row {
                                if (group != ExploreBlockRuleGroupStore.DEFAULT_GROUP) {
                                    IconButton(onClick = {
                                        inputDialog = GroupInput("重命名分组", group) { newName ->
                                            onRenameGroup(group, newName)
                                            localGroups = localGroups.map { if (it == group) newName else it }
                                        }
                                    }) {
                                        Icon(Icons.Filled.Edit, contentDescription = "重命名")
                                    }
                                    IconButton(onClick = { deleteDialog = group }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "删除")
                                    }
                                }
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                inputDialog = GroupInput("添加分组") { name ->
                    onAddGroup(name)
                    localGroups = (localGroups + name).distinct()
                }
            }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        }
    )
}

private data class GroupInput(
    val title: String,
    val initialName: String = "",
    val onConfirm: (String) -> Unit
)

private fun importFromClipboard(context: android.content.Context, onRefresh: () -> Unit) {
    val clip = context.getClipText()
    if (clip.isNullOrBlank()) {
        context.toastOnUi(R.string.explore_block_rule_clipboard_empty)
        return
    }
    val imported = GSON.fromJsonArray<ExploreBlockRule>(clip).getOrNull()
    if (imported.isNullOrEmpty()) {
        context.toastOnUi(R.string.explore_block_rule_import_invalid)
        return
    }
    val existing = ExploreBlockRuleStore.load(context)
    val newRules = imported.map { rule ->
        var normalized = ExploreBlockRuleStore.sanitizeRule(rule)
        if (existing.any { it.id == normalized.id }) {
            normalized = normalized.copyWithNewId()
        }
        normalized
    }
    ExploreBlockRuleStore.save(context, existing + newRules)
    context.toastOnUi(R.string.explore_block_rule_import_success)
    onRefresh()
}

private fun exportToClipboard(context: android.content.Context, rules: List<ExploreBlockRule>) {
    if (rules.isEmpty()) {
        context.toastOnUi(R.string.explore_block_rule_empty)
        return
    }
    context.sendToClip(GSON.toJson(rules))
    context.toastOnUi(R.string.explore_block_rule_export_success)
}
