package io.legado.app.ui.main.my

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.main.my.components.MyEntryRow
import io.legado.app.ui.main.my.components.MyGroupCard
import io.legado.app.ui.main.my.components.MyHeaderCard
import io.legado.app.ui.main.my.components.MySwitchRow
import io.legado.app.ui.main.my.components.MyThemeModeDialog

/**
 * 「我的」页：顶部阅读信息卡 + 四个功能分组。
 *
 * 页面只负责编排，行组件在 `components/` 下；所有跳转通过 [onEntryClick] 抛给宿主 Fragment。
 */
@Composable
internal fun MyScreen(
    modifier: Modifier = Modifier,
    state: MyUiState,
    bottomContentPadding: Dp,
    onEntryClick: (MyEntry) -> Unit,
    onThemeModeSelected: (Int) -> Unit,
    onWebServiceToggle: (Boolean) -> Unit,
    onWebServiceAddressClick: () -> Unit
) {
    var showThemeModeDialog by rememberSaveable { mutableStateOf(false) }
    val themeModes = LocalContext.current.resources.getStringArray(R.array.theme_mode)
    val themeModeLabel = themeModes.getOrElse(state.themeMode) { themeModes.firstOrNull().orEmpty() }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 12.dp,
            bottom = bottomContentPadding
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "header") {
            MyHeaderCard(stats = state.stats)
        }

        item(key = "content") {
            MyGroupCard(title = stringResource(R.string.my_section_content)) {
                MyEntryRow(
                    iconRes = R.drawable.ic_cfg_source,
                    title = stringResource(R.string.book_source_manage),
                    summary = stringResource(R.string.book_source_manage_desc),
                    onClick = { onEntryClick(MyEntry.BOOK_SOURCE) }
                )
                MyEntryRow(
                    iconRes = R.drawable.ic_toc,
                    title = stringResource(R.string.txt_toc_rule),
                    summary = stringResource(R.string.config_txt_toc_rule),
                    onClick = { onEntryClick(MyEntry.TXT_TOC_RULE) }
                )
                MyEntryRow(
                    iconRes = R.drawable.ic_cfg_replace,
                    title = stringResource(R.string.replace_purify),
                    summary = stringResource(R.string.replace_purify_desc),
                    onClick = { onEntryClick(MyEntry.REPLACE_RULE) }
                )
                MyEntryRow(
                    iconRes = R.drawable.ic_translate,
                    title = stringResource(R.string.dict_rule),
                    summary = stringResource(R.string.config_dict_rule),
                    onClick = { onEntryClick(MyEntry.DICT_RULE) }
                )
                MyEntryRow(
                    iconRes = R.drawable.ic_bookmark,
                    title = stringResource(R.string.bookmark),
                    summary = stringResource(R.string.my_bookmark_summary, state.stats.bookmarkCount),
                    onClick = { onEntryClick(MyEntry.BOOKMARK) }
                )
                MyEntryRow(
                    iconRes = R.drawable.ic_history,
                    title = stringResource(R.string.read_record),
                    summary = stringResource(R.string.read_record_summary),
                    showDivider = false,
                    onClick = { onEntryClick(MyEntry.READ_RECORD) }
                )
            }
        }

        item(key = "tools") {
            MyGroupCard(title = stringResource(R.string.my_section_tools)) {
                if (state.showDebugTools) {
                    MyEntryRow(
                        iconRes = R.drawable.ic_code,
                        title = stringResource(R.string.debug_tools),
                        summary = stringResource(R.string.debug_tools_desc),
                        onClick = { onEntryClick(MyEntry.DEBUG_TOOLS) }
                    )
                }
                MyEntryRow(
                    iconRes = R.drawable.ic_folder,
                    title = stringResource(R.string.file_manage),
                    summary = stringResource(R.string.file_manage_summary),
                    onClick = { onEntryClick(MyEntry.FILE_MANAGE) }
                )
                MyEntryRow(
                    iconRes = R.drawable.ic_storage_black_24dp,
                    title = stringResource(R.string.storage_manage),
                    summary = stringResource(R.string.storage_manage_summary),
                    onClick = { onEntryClick(MyEntry.STORAGE_MANAGE) }
                )
                MyEntryRow(
                    iconRes = R.drawable.ic_download,
                    title = stringResource(R.string.download_manage),
                    summary = stringResource(R.string.download_manage_summary),
                    onClick = { onEntryClick(MyEntry.DOWNLOAD_MANAGE) }
                )
                MyEntryRow(
                    iconRes = R.drawable.ic_link,
                    title = stringResource(R.string.url_record),
                    onClick = { onEntryClick(MyEntry.URL_RECORD) }
                )
                MyEntryRow(
                    iconRes = R.drawable.ic_check_source,
                    title = stringResource(R.string.precise_manage),
                    summary = stringResource(R.string.precise_manage_summary),
                    showDivider = false,
                    onClick = { onEntryClick(MyEntry.PRECISE_MANAGE) }
                )
            }
        }

        item(key = "appearance") {
            MyGroupCard(title = stringResource(R.string.my_section_appearance)) {
                MyEntryRow(
                    iconRes = R.drawable.ic_brightness,
                    title = stringResource(R.string.theme_mode),
                    summary = themeModeLabel,
                    onClick = { showThemeModeDialog = true }
                )
                MyEntryRow(
                    iconRes = R.drawable.ic_cfg_theme,
                    title = stringResource(R.string.theme_setting),
                    summary = stringResource(R.string.theme_setting_s),
                    onClick = { onEntryClick(MyEntry.THEME_SETTING) }
                )
                MyEntryRow(
                    iconRes = R.drawable.ic_cfg_backup,
                    title = stringResource(R.string.backup_restore),
                    summary = stringResource(R.string.web_dav_set_import_old),
                    onClick = { onEntryClick(MyEntry.BACKUP_RESTORE) }
                )
                MyEntryRow(
                    iconRes = R.drawable.ic_cfg_other,
                    title = stringResource(R.string.other_setting),
                    summary = stringResource(R.string.other_setting_s),
                    onClick = { onEntryClick(MyEntry.OTHER_SETTING) }
                )
                MySwitchRow(
                    iconRes = R.drawable.ic_cfg_web,
                    title = stringResource(R.string.web_service),
                    summary = if (state.webServiceRunning && state.webServiceAddress.isNotBlank()) {
                        state.webServiceAddress
                    } else {
                        stringResource(R.string.web_service_desc)
                    },
                    checked = state.webServiceRunning,
                    onCheckedChange = onWebServiceToggle,
                    onRowClick = {
                        if (state.webServiceRunning) onWebServiceAddressClick()
                        else onWebServiceToggle(true)
                    }
                )
            }
        }

        item(key = "about") {
            MyGroupCard(title = stringResource(R.string.my_section_about)) {
                MyEntryRow(
                    iconRes = R.drawable.ic_cfg_about,
                    title = stringResource(R.string.about),
                    summary = stringResource(R.string.my_version_summary, state.versionName),
                    accent = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { onEntryClick(MyEntry.ABOUT) }
                )
                MyEntryRow(
                    iconRes = R.drawable.ic_exit,
                    title = stringResource(R.string.exit),
                    accent = MaterialTheme.colorScheme.error,
                    showDivider = false,
                    onClick = { onEntryClick(MyEntry.EXIT) }
                )
            }
        }

        item(key = "footer") {
            Text(
                text = stringResource(R.string.my_footer),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }
    }

    if (showThemeModeDialog) {
        MyThemeModeDialog(
            selectedMode = state.themeMode,
            onDismiss = { showThemeModeDialog = false },
            onSelect = { mode ->
                showThemeModeDialog = false
                onThemeModeSelected(mode)
            }
        )
    }
}
