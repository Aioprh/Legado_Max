package io.legado.app.ui.debuglog.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.legado.app.model.debug.FlowStage

/**
 * 流程阶段过滤选择器
 */
@Composable
fun FlowStageFilter(
    selectedStage: FlowStage?,
    onStageSelected: (FlowStage?) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedStage == null,
                onClick = { onStageSelected(null) },
                label = { Text("全部") }
            )
            
            FlowStage.entries.forEach { stage ->
                FilterChip(
                    selected = selectedStage == stage,
                    onClick = { onStageSelected(stage) },
                    label = { Text("${stage.icon} ${stage.displayName}") }
                )
            }
        }
    }
}
