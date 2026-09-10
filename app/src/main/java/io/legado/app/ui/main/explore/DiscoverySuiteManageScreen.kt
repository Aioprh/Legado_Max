package io.legado.app.ui.main.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.data.entities.BookSourcePart

/**
 * 套件管理界面：新建套件、增删改 widget、保存到 [DiscoverySuiteStore]。
 */
@Composable
fun DiscoverySuiteManageScreen(
    viewModel: DiscoverySuiteManageViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) { viewModel.reload() }
    val state by viewModel.uiState.collectAsState()

    var editing by remember { mutableStateOf<WidgetEditorTarget?>(null) }
    var creatingSuite by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ManageTopBar(title = stringResource(R.string.discovery_suite_manage_title), onBackClick = onBackClick)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 14.dp, end = 14.dp, bottom = 96.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.suites, key = { it.id }) { suite ->
                    SuiteCard(
                        suite = suite,
                        isCurrent = suite.id == state.selectedSuiteId,
                        onSetCurrent = { viewModel.setCurrent(suite.id) },
                        onDelete = { viewModel.deleteSuite(suite.id) },
                        onRename = { newName, alias -> viewModel.renameSuite(suite.id, newName, alias) },
                        onSetOpacity = { mult -> viewModel.setOpacity(suite.id, mult) },
                        onAddWidget = {
                            editing = WidgetEditorTarget(suiteId = suite.id, widget = newWidgetFor(null))
                        },
                        onEditWidget = { widget ->
                            editing = WidgetEditorTarget(suiteId = suite.id, widget = widget)
                        },
                        onDeleteWidget = { widget -> viewModel.deleteWidget(suite.id, widget.id) },
                        onMoveWidget = { widget, delta -> viewModel.moveWidget(suite.id, widget.id, delta) }
                    )
                }
            }
        }
        AddSuiteFloatingButton(onClick = { creatingSuite = true })
    }

    if (creatingSuite) {
        CreateSuiteDialog(
            onDismiss = { creatingSuite = false },
            onCreate = { name, alias ->
                viewModel.createSuite(name, alias)
                creatingSuite = false
            }
        )
    }

    editing?.let { target ->
        WidgetEditorDialog(
            target = target,
            onDismiss = { editing = null },
            onSave = { widget ->
                if (target.widget.id.isBlank()) {
                    viewModel.addWidget(target.suiteId, widget)
                } else {
                    viewModel.updateWidget(target.suiteId, widget)
                }
                editing = null
            }
        )
    }
}

private data class WidgetEditorTarget(
    val suiteId: String,
    val widget: DiscoverySuiteWidget
)

@Composable
private fun ManageTopBar(title: String, onBackClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun SuiteCard(
    suite: DiscoverySuite,
    isCurrent: Boolean,
    onSetCurrent: () -> Unit,
    onDelete: () -> Unit,
    onRename: (String, String) -> Unit,
    onSetOpacity: (Float) -> Unit,
    onAddWidget: () -> Unit,
    onEditWidget: (DiscoverySuiteWidget) -> Unit,
    onDeleteWidget: (DiscoverySuiteWidget) -> Unit,
    onMoveWidget: (DiscoverySuiteWidget, Int) -> Unit
) {
    var expanded by remember(suite.id) { mutableStateOf(true) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = suite.displayName,
                    modifier = Modifier.weight(1f),
                    fontSize = 17.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (isCurrent) {
                    Text(
                        text = stringResource(R.string.discovery_suite_current, "").trim(),
                        modifier = Modifier.padding(end = 8.dp),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = { onSetCurrent() }) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = stringResource(R.string.discovery_suite_set_current),
                        tint = if (isCurrent) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            if (expanded) {
                if (suite.widgets.isEmpty()) {
                    Text(
                        text = stringResource(R.string.discovery_suite_no_widgets_summary),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                } else {
                    suite.widgets.forEach { widget ->
                        WidgetRow(
                            widget = widget,
                            onEdit = { onEditWidget(widget) },
                            onDelete = { onDeleteWidget(widget) },
                            onUp = { onMoveWidget(widget, -1) },
                            onDown = { onMoveWidget(widget, 1) }
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onAddWidget,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.discovery_suite_add_widget))
                    }
                    OutlinedButton(
                        onClick = { onRename(suite.name, suite.alias) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.discovery_suite_rename))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.discovery_suite_delete),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    var opacity by remember(suite.id) { mutableStateOf(suite.opacityMultiplier) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.discovery_suite_opacity, suite.opacityMultiplier.toString()),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Slider(
                        value = opacity,
                        onValueChange = {
                            opacity = it
                            onSetOpacity(it)
                        },
                        valueRange = 1f..4f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) stringResource(R.string.discovery_suite_cancel) else "…")
                }
            }
        }
    }
}

@Composable
private fun WidgetRow(
    widget: DiscoverySuiteWidget,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = widget.displayTitle(),
            modifier = Modifier.weight(1f),
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = widget.typeLabel(),
            modifier = Modifier.padding(end = 4.dp),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.edit), modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onUp, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Filled.ArrowUpward, contentDescription = stringResource(R.string.discovery_suite_move_up), modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onDown, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Filled.ArrowDownward, contentDescription = stringResource(R.string.discovery_suite_move_down), modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.discovery_suite_delete), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun AddSuiteFloatingButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(16.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        Button(onClick = onClick) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.discovery_suite_create))
        }
    }
}

@Composable
private fun CreateSuiteDialog(onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var alias by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.discovery_suite_create)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.discovery_suite_name)) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = alias,
                    onValueChange = { alias = it },
                    label = { Text(stringResource(R.string.discovery_suite_alias)) },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name.trim(), alias.trim()) },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.discovery_suite_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.discovery_suite_cancel))
            }
        }
    )
}

/** 新建 widget：按类型生成默认 title/displayLimit。 */
private fun newWidgetFor(type: String?): DiscoverySuiteWidget {
    val resolved = type ?: DiscoverySuiteWidgetType.RandomBooks.value
    return DiscoverySuiteStore.newBookWidget("", when (resolved) {
        DiscoverySuiteWidgetType.TagBar.value -> DiscoverySuiteWidgetType.TagBar.value
        DiscoverySuiteWidgetType.RankButtons.value -> DiscoverySuiteWidgetType.RankButtons.value
        DiscoverySuiteWidgetType.HorizontalBooks.value -> DiscoverySuiteWidgetType.HorizontalBooks.value
        DiscoverySuiteWidgetType.RankedList.value -> DiscoverySuiteWidgetType.RankedList.value
        DiscoverySuiteWidgetType.WaterfallBooks.value -> DiscoverySuiteWidgetType.WaterfallBooks.value
        else -> DiscoverySuiteWidgetType.RandomBooks.value
    })
}

private fun DiscoverySuiteWidget.typeLabel(): String {
    return when (type) {
        DiscoverySuiteWidgetType.RandomBooks.value -> "Random"
        DiscoverySuiteWidgetType.TagBar.value -> "Tags"
        DiscoverySuiteWidgetType.RankButtons.value -> "Ranks"
        DiscoverySuiteWidgetType.HorizontalBooks.value -> "H"
        DiscoverySuiteWidgetType.RankedList.value -> "Ranked"
        DiscoverySuiteWidgetType.WaterfallBooks.value -> "Waterfall"
        else -> "List"
    }
}

// ---- Widget editor ----

private data class WidgetEditorState(
    val suiteId: String,
    val widget: DiscoverySuiteWidget
)

@Composable
private fun WidgetEditorDialog(
    target: WidgetEditorTarget,
    selectedSuite: List<DiscoverySuite> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (DiscoverySuiteWidget) -> Unit
) {
    WidgetEditorDialogContent(
        initial = target.widget,
        onDismiss = onDismiss,
        onSave = onSave
    )
}

@Composable
private fun WidgetEditorDialogContent(
    initial: DiscoverySuiteWidget,
    onDismiss: () -> Unit,
    onSave: (DiscoverySuiteWidget) -> Unit
) {
    val isNew = initial.id.isBlank()
    var title by remember { mutableStateOf(initial.title) }
    var type by remember { mutableStateOf(initial.type) }
    var displayLimit by remember { mutableStateOf(initial.displayLimit.toFloat()) }
    var targets by remember { mutableStateOf(initial.targets) }
    var pickingSource by remember { mutableStateOf(false) }

    val typeLabel = widgetTypeLabel(type)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isNew) stringResource(R.string.discovery_suite_add_widget) else stringResource(R.string.edit))
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.discovery_suite_widget_display_limit).let { stringResource(R.string.edit) }.let { "" } .ifBlank { "Title" }) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.discovery_suite_widget_display_limit), modifier = Modifier.weight(1f))
                    Slider(
                        value = displayLimit,
                        onValueChange = { displayLimit = it },
                        valueRange = 4f..60f,
                        modifier = Modifier.weight(1f)
                    )
                    Text(displayLimit.toInt().toString(), modifier = Modifier.width(30.dp))
                }
                Text(
                    text = "Type: $typeLabel",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                targets.forEach { target ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = target.title,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = target.sourceUrl,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { targets = targets - target }) {
                            Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                OutlinedButton(onClick = { pickingSource = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.discovery_suite_choose_tags))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        initial.copy(
                            title = title.trim(),
                            type = type,
                            displayLimit = displayLimit.toInt(),
                            targets = targets
                        )
                    )
                },
                enabled = targets.isNotEmpty()
            ) {
                Text(stringResource(R.string.discovery_suite_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.discovery_suite_cancel))
            }
        }
    )

    if (pickingSource) {
        SourcePickerDialog(
            onDismiss = { pickingSource = false },
            onSourcePicked = { sourcePart ->
                pickingSource = false
                // 交由上层 TagPicker 承载（简化：直接以该源作为单一目标）
                val displayName = sourcePart.bookSourceName
                val sourceUrl = sourcePart.bookSourceUrl
                val tag = "${displayName} - Default"
                targets = targets + DiscoverySuiteWidgetTarget(
                    sourceUrl = sourceUrl,
                    tagUrl = sourcePart.bookSourceUrl,
                    title = tag
                )
            }
        )
    }
}

private fun widgetTypeLabel(type: String): String {
    return when (type) {
        DiscoverySuiteWidgetType.RandomBooks.value -> "Random books"
        DiscoverySuiteWidgetType.TagBar.value -> "Tag bar"
        DiscoverySuiteWidgetType.RankButtons.value -> "Ranking buttons"
        DiscoverySuiteWidgetType.HorizontalBooks.value -> "Horizontal books"
        DiscoverySuiteWidgetType.RankedList.value -> "Ranking list"
        DiscoverySuiteWidgetType.WaterfallBooks.value -> "Waterfall books"
        else -> "Books"
    }
}

@Composable
private fun SourcePickerDialog(
    onDismiss: () -> Unit,
    onSourcePicked: (BookSourcePart) -> Unit
) {
    val sources by remember { mutableStateOf(loadExploreSources()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.discovery_suite_choose_source)) },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            ) {
                items(sources, key = { it.bookSourceUrl }) { source ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSourcePicked(source) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = source.bookSourceName,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = source.bookSourceUrl,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(140.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.discovery_suite_cancel))
            }
        }
    )
}