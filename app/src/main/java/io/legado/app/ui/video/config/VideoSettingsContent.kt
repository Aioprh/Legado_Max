package io.legado.app.ui.video.config

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.model.VideoPlay
import io.legado.app.ui.widget.number.NumberPickerDialog

/**
 * 视频播放器设置界面 - Compose实现
 */
@Composable
fun VideoSettingsContent(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    // 状态变量
    var autoPlay by remember { mutableStateOf(VideoPlay.autoPlay) }
    var startFull by remember { mutableStateOf(VideoPlay.startFull) }
    var fullBottomProgressBar by remember { mutableStateOf(VideoPlay.fullBottomProgressBar) }
    var mutePlay by remember { mutableStateOf(VideoPlay.mutePlay) }
    var longPressSpeed by remember { mutableIntStateOf(VideoPlay.longPressSpeed) }

    var doubleTapSeekEnabled by remember { mutableStateOf(VideoPlay.doubleTapSeekEnabled) }
    var doubleTapSeekSeconds by remember { mutableIntStateOf(VideoPlay.doubleTapSeekSeconds) }

    var quickJumpButtonsEnabled by remember { mutableStateOf(VideoPlay.quickJumpButtonsEnabled) }
    var quickJumpMinutesA by remember { mutableIntStateOf(VideoPlay.quickJumpMinutesA) }
    var quickJumpMinutesB by remember { mutableIntStateOf(VideoPlay.quickJumpMinutesB) }

    var leftSlideBrightnessEnabled by remember { mutableStateOf(VideoPlay.leftSlideBrightnessEnabled) }
    var rightSlideVolumeEnabled by remember { mutableStateOf(VideoPlay.rightSlideVolumeEnabled) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // 标题
        Text(
            text = stringResource(R.string.config_settings),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // 自动播放
        SettingSwitchItem(
            title = stringResource(R.string.auto_play),
            checked = autoPlay,
            onCheckedChange = { checked ->
                autoPlay = checked
                VideoPlay.autoPlay = checked
            }
        )

        // 直接全屏（仅在自动播放开启时显示）
        if (autoPlay) {
            SettingSwitchItem(
                title = stringResource(R.string.start_full),
                checked = startFull,
                onCheckedChange = { checked ->
                    startFull = checked
                    VideoPlay.startFull = checked
                }
            )
        }

        // 全屏底部进度条
        SettingSwitchItem(
            title = stringResource(R.string.full_bottom_progress),
            checked = fullBottomProgressBar,
            onCheckedChange = { checked ->
                fullBottomProgressBar = checked
                VideoPlay.fullBottomProgressBar = checked
            }
        )

        // 静音播放
        SettingSwitchItem(
            title = stringResource(R.string.mute_play),
            checked = mutePlay,
            onCheckedChange = { checked ->
                mutePlay = checked
                VideoPlay.mutePlay = checked
            }
        )

        // 长按倍速
        SettingClickItem(
            title = stringResource(R.string.press_speed),
            summary = stringResource(R.string.press_speed_summary, longPressSpeed / 10.0f),
            onClick = {
                NumberPickerDialog(context, true)
                    .setTitle(context.getString(R.string.press_speed))
                    .setMaxValue(60)
                    .setMinValue(5)
                    .setValue(longPressSpeed)
                    .setCustomButton(R.string.btn_default_s) {
                        VideoPlay.longPressSpeed = 30
                        longPressSpeed = 30
                    }
                    .show { value ->
                        VideoPlay.longPressSpeed = value
                        longPressSpeed = value
                    }
            }
        )

        // 分隔标题：手势控制
        Text(
            text = stringResource(R.string.double_tap_seek_enabled),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
        )

        // 双击快退/快进开关
        SettingSwitchItem(
            title = stringResource(R.string.double_tap_seek_enabled),
            checked = doubleTapSeekEnabled,
            onCheckedChange = { checked ->
                doubleTapSeekEnabled = checked
                VideoPlay.doubleTapSeekEnabled = checked
            }
        )

        // 双击跳转秒数（仅在双击快退/快进开启时显示）
        if (doubleTapSeekEnabled) {
            SettingClickItem(
                title = stringResource(R.string.double_tap_seek_seconds),
                summary = stringResource(R.string.double_tap_seek_seconds_summary, doubleTapSeekSeconds),
                onClick = {
                    NumberPickerDialog(context)
                        .setTitle(context.getString(R.string.double_tap_seek_seconds))
                        .setMaxValue(60)
                        .setMinValue(5)
                        .setValue(doubleTapSeekSeconds)
                        .setCustomButton(R.string.btn_default_s) {
                            VideoPlay.doubleTapSeekSeconds = 10
                            doubleTapSeekSeconds = 10
                        }
                        .show { value ->
                            VideoPlay.doubleTapSeekSeconds = value
                            doubleTapSeekSeconds = value
                        }
                }
            )
        }

        // 快捷跳转按钮开关
        SettingSwitchItem(
            title = stringResource(R.string.quick_jump_buttons_enabled),
            checked = quickJumpButtonsEnabled,
            onCheckedChange = { checked ->
                quickJumpButtonsEnabled = checked
                VideoPlay.quickJumpButtonsEnabled = checked
            }
        )

        // 快捷跳转分钟数（仅在快捷跳转按钮开启时显示）
        if (quickJumpButtonsEnabled) {
            SettingClickItem(
                title = stringResource(R.string.quick_jump_minutes_a),
                summary = stringResource(R.string.quick_jump_minutes_summary, quickJumpMinutesA),
                onClick = {
                    NumberPickerDialog(context)
                        .setTitle(context.getString(R.string.quick_jump_minutes_a))
                        .setMaxValue(60)
                        .setMinValue(1)
                        .setValue(quickJumpMinutesA)
                        .setCustomButton(R.string.btn_default_s) {
                            VideoPlay.quickJumpMinutesA = 5
                            quickJumpMinutesA = 5
                        }
                        .show { value ->
                            VideoPlay.quickJumpMinutesA = value
                            quickJumpMinutesA = value
                        }
                }
            )

            SettingClickItem(
                title = stringResource(R.string.quick_jump_minutes_b),
                summary = stringResource(R.string.quick_jump_minutes_summary, quickJumpMinutesB),
                onClick = {
                    NumberPickerDialog(context)
                        .setTitle(context.getString(R.string.quick_jump_minutes_b))
                        .setMaxValue(60)
                        .setMinValue(1)
                        .setValue(quickJumpMinutesB)
                        .setCustomButton(R.string.btn_default_s) {
                            VideoPlay.quickJumpMinutesB = 1
                            quickJumpMinutesB = 1
                        }
                        .show { value ->
                            VideoPlay.quickJumpMinutesB = value
                            quickJumpMinutesB = value
                        }
                }
            )
        }

        // 分隔标题：滑动控制
        Text(
            text = stringResource(R.string.left_slide_brightness_enabled),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
        )

        // 左侧滑动调节亮度
        SettingSwitchItem(
            title = stringResource(R.string.left_slide_brightness_enabled),
            checked = leftSlideBrightnessEnabled,
            onCheckedChange = { checked ->
                leftSlideBrightnessEnabled = checked
                VideoPlay.leftSlideBrightnessEnabled = checked
            }
        )

        // 右侧滑动调节音量
        SettingSwitchItem(
            title = stringResource(R.string.right_slide_volume_enabled),
            checked = rightSlideVolumeEnabled,
            onCheckedChange = { checked ->
                rightSlideVolumeEnabled = checked
                VideoPlay.rightSlideVolumeEnabled = checked
            }
        )

        // 关闭按钮
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.close))
            }
        }
    }
}

/**
 * 设置开关项
 */
@Composable
fun SettingSwitchItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

/**
 * 设置点击项（带摘要）
 */
@Composable
fun SettingClickItem(
    title: String,
    summary: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}