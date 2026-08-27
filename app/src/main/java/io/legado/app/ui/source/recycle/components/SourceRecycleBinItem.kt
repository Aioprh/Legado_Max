package io.legado.app.ui.source.recycle.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.entities.SourceRecycleBin
import io.legado.app.help.source.SourceRecycleBinHelp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 回收站条目卡片
 */
@Composable
fun SourceRecycleBinItem(
    item: SourceRecycleBin,
    selected: Boolean,
    secondaryTextColor: Color,
    menuExpanded: Boolean,
    onToggleSelected: () -> Unit,
    onMenuOpen: () -> Unit,
    onMenuDismiss: () -> Unit,
    onRestoreClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            // 无障碍：合并卡片内文本节点（accessibility.md §15.2）
            .semantics(mergeDescendants = true) {}
            .border(1.dp, borderColor, MaterialTheme.shapes.medium)
            .clickable(onClick = onToggleSelected),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggleSelected() },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name.ifBlank { item.key },
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(
                        R.string.source_recycle_bin_type_group,
                        typeLabel(item.type),
                        item.groupName.orEmpty().ifBlank { stringResource(R.string.no_group) }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryTextColor
                )
                Text(
                    text = stringResource(
                        R.string.source_recycle_bin_time_left,
                        formatTime(item.deletedAt),
                        remainingDays(item.expireAt)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryTextColor
                )
            }
            Box {
                IconButton(onClick = onMenuOpen) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more))
                }
                SourceRecycleDropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = onMenuDismiss
                ) {
                    SourceRecycleDropdownMenuItem(
                        text = stringResource(R.string.restore),
                        leadingIcon = {
                            Icon(Icons.Default.RestoreFromTrash, contentDescription = null)
                        },
                        onClick = onRestoreClick
                    )
                    SourceRecycleDropdownMenuItem(
                        text = stringResource(R.string.delete_forever),
                        leadingIcon = {
                            Icon(Icons.Default.DeleteForever, contentDescription = null)
                        },
                        destructive = true,
                        onClick = onDeleteClick
                    )
                }
            }
        }
    }
}

@Composable
private fun typeLabel(type: String): String {
    return when (type) {
        SourceRecycleBinHelp.TYPE_BOOK_SOURCE -> stringResource(R.string.book_source)
        SourceRecycleBinHelp.TYPE_RSS_SOURCE -> stringResource(R.string.rss_source)
        SourceRecycleBinHelp.TYPE_REPLACE_RULE -> stringResource(R.string.replace_rule)
        SourceRecycleBinHelp.TYPE_TXT_TOC_RULE -> stringResource(R.string.txt_toc_rule)
        SourceRecycleBinHelp.TYPE_HTTP_TTS -> stringResource(R.string.speak_engine)
        SourceRecycleBinHelp.TYPE_DICT_RULE -> stringResource(R.string.dict_rule)
        SourceRecycleBinHelp.TYPE_HIGHLIGHT_RULE -> stringResource(R.string.highlight_rule_config)
        SourceRecycleBinHelp.TYPE_SEARCH_ENGINE -> stringResource(R.string.search_engine_rule)
        else -> type
    }
}

private fun formatTime(time: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(time))
}

private fun remainingDays(expireAt: Long): Long {
    val millis = expireAt - System.currentTimeMillis()
    return TimeUnit.MILLISECONDS.toDays(millis).coerceAtLeast(0)
}
