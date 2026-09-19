package io.legado.app.ui.main.homepage.modules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.ui.widget.components.card.GlassCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartFilterModule(
    kinds: List<ExploreKind>,
    sourceUrl: String,
    onSelect: (ExploreKind, String) -> Unit,
    onUrlClick: (String, String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (kinds.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        kinds.forEach { kind ->
            when (kind.type) {
                ExploreKind.Type.select -> {
                    val chars = kind.chars?.filterNotNull().orEmpty()
                    if (chars.isNotEmpty()) {
                        var expanded by remember(kind.title, chars) { mutableStateOf(false) }
                        var selected by remember(kind.title, chars) {
                            mutableStateOf(kind.default?.takeIf { it.isNotBlank() } ?: chars.first())
                        }
                        GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 12.dp) {
                            ExposedDropdownMenuBox(
                                expanded = expanded,
                                onExpandedChange = { expanded = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = selected,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(kind.title) },
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                                    },
                                    modifier = Modifier
                                        .menuAnchor()
                                        .fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    chars.forEach { value ->
                                        DropdownMenuItem(
                                            text = { Text(value) },
                                            onClick = {
                                                selected = value
                                                expanded = false
                                                onSelect(kind, value)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                ExploreKind.Type.toggle -> {
                    val chars = kind.chars?.filterNotNull().orEmpty()
                    if (chars.isNotEmpty()) {
                        var index by remember(kind.title, chars) {
                            mutableIntStateOf(
                                (kind.default?.let { chars.indexOf(it) } ?: 0)
                                    .coerceIn(0, chars.lastIndex)
                            )
                        }
                        GlassCard(
                            onClick = {
                                index = (index + 1) % chars.size
                                onSelect(kind, chars[index])
                            },
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 12.dp
                        ) {
                            Row(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                                Text(
                                    text = kind.title,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(chars[index])
                            }
                        }
                    }
                }

                ExploreKind.Type.button -> {
                    GlassCard(
                        onClick = {
                            onSelect(kind, kind.default ?: kind.title)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 12.dp
                    ) {
                        Text(kind.title, modifier = Modifier.padding(14.dp))
                    }
                }

                ExploreKind.Type.url -> {
                    val url = kind.url?.takeIf { it.isNotBlank() }
                    if (url != null) {
                        GlassCard(
                            onClick = { onUrlClick(sourceUrl, url, kind.title) },
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 12.dp
                        ) {
                            Text(kind.title, modifier = Modifier.padding(14.dp))
                        }
                    }
                }

                // text/html 控件需要 WebView/输入框上下文，智能首页先保留在发现页。
                else -> Unit
            }
        }
    }
}
