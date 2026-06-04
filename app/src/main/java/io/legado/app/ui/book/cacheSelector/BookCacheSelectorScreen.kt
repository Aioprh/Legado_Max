package io.legado.app.ui.book.cacheSelector

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Help
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.legado.app.R
import io.legado.app.ui.book.cacheSelector.components.BookCacheItemCard
import io.legado.app.ui.theme.pageCardContainerColor
import io.legado.app.utils.ConvertUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookCacheSelectorScreen(
    viewModel: BookCacheSelectorViewModel = viewModel(),
    onBackClick: () -> Unit,
    onSaveClick: () -> Unit,
    onExportClick: () -> Unit,
    onHelpClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val bookItems by viewModel.bookItems.collectAsState()
    val selectedCount by viewModel.selectedCount.collectAsState()
    val totalSelectedSize by viewModel.totalSelectedSize.collectAsState()

    val accentColor = cacheSelectorAccentColor()
    val topBarColor = pageCardContainerColor()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = topBarColor,
                    scrolledContainerColor = topBarColor,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                ),
                title = {
                    Text(
                        text = stringResource(R.string.bcs_title),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = onHelpClick) {
                        Icon(Icons.Default.Help, contentDescription = stringResource(R.string.help))
                    }
                    TextButton(onClick = {
                        if (viewModel.isAllSelected()) {
                            viewModel.deselectAll()
                        } else {
                            viewModel.selectAll()
                        }
                    }) {
                        Text(
                            text = if (viewModel.isAllSelected()) stringResource(R.string.bcs_deselect_all) else stringResource(R.string.select_all),
                            color = accentColor
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                color = pageCardContainerColor(),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onSaveClick,
                        modifier = Modifier.weight(1f),
                        enabled = selectedCount > 0,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentColor,
                            contentColor = Color.White,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        )
                    ) {
                        Text(stringResource(R.string.bcs_save_selection))
                    }
                    OutlinedButton(
                        onClick = onExportClick,
                        modifier = Modifier.weight(1f),
                        enabled = selectedCount > 0,
                        border = BorderStroke(
                            1.dp,
                            if (selectedCount > 0) accentColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = accentColor,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        )
                    ) {
                        Text(stringResource(R.string.bcs_export_selected))
                    }
                }
            }
        }
    ) { paddingValues ->
        when (uiState) {
            is BookCacheSelectorUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = accentColor)
                }
            }
            is BookCacheSelectorUiState.Idle -> {
                LazyColumn(
                    modifier = Modifier.padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 汇总条
                    item {
                        SummaryBar(
                            selectedCount = selectedCount,
                            totalSize = totalSelectedSize
                        )
                        Spacer(Modifier.height(4.dp))
                    }

                    items(
                        items = bookItems,
                        key = { it.book.bookUrl }
                    ) { item ->
                        BookCacheItemCard(
                            item = item,
                            onToggleSelect = { viewModel.toggleSelect(item.book) },
                            accentColor = accentColor
                        )
                    }

                    item {
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
            is BookCacheSelectorUiState.Exporting -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = accentColor)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = (uiState as BookCacheSelectorUiState.Exporting).message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            is BookCacheSelectorUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (uiState as BookCacheSelectorUiState.Error).message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryBar(
    selectedCount: Int,
    totalSize: Long
) {
    val accentColor = cacheSelectorAccentColor()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
        color = cacheSelectorTintContainerColor(accentColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.bcs_selected_count, selectedCount),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.bcs_total_size, ConvertUtils.formatFileSize(totalSize)),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = accentColor
            )
        }
    }
}

@Composable
internal fun cacheSelectorAccentColor(): Color {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.18f
    return if (isDark) Color(0xFF5AB9A8) else Color(0xFF2F7D6B)
}

@Composable
internal fun cacheSelectorTintContainerColor(accentColor: Color): Color {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.18f
    return if (isDark) {
        accentColor.copy(alpha = 0.16f)
    } else {
        accentColor.copy(alpha = 0.10f)
    }
}
