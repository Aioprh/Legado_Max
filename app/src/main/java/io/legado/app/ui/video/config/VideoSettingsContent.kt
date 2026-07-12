package io.legado.app.ui.video.config

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.model.VideoPlay
import io.legado.app.constant.EventBus
import io.legado.app.ui.widget.number.NumberPickerDialog
import io.legado.app.ui.widget.components.settings.AppSettingClickItem as SettingClickItem
import io.legado.app.ui.widget.components.settings.AppSettingSwitchItem as SettingSwitchItem
import io.legado.app.utils.postEvent

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

    var skipIntroOutroEnabled by remember { mutableStateOf(VideoPlay.skipIntroOutroEnabled) }
    var skipIntroSeconds by remember { mutableIntStateOf(VideoPlay.skipIntroSeconds) }
    var skipOutroSeconds by remember { mutableIntStateOf(VideoPlay.skipOutroSeconds) }

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
            color = MaterialTheme.colorScheme.onSurface,
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
                postEvent(EventBus.VIDEO_CONFIG_CHANGED, true)
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
                postEvent(EventBus.VIDEO_CONFIG_CHANGED, true)
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
                        // 验证：A的绝对值必须大于等于B的绝对值
                        if (kotlin.math.abs(quickJumpMinutesB) > 5) {
                            VideoPlay.quickJumpMinutesB = 5
                            quickJumpMinutesB = 5
                        }
                    }
                        .show { value ->
                            VideoPlay.quickJumpMinutesA = value
                            quickJumpMinutesA = value
                            // 验证：A的绝对值必须大于等于B的绝对值
                            if (kotlin.math.abs(value) < kotlin.math.abs(quickJumpMinutesB)) {
                                VideoPlay.quickJumpMinutesB = kotlin.math.abs(value)
                                quickJumpMinutesB = kotlin.math.abs(value)
                            }
                            postEvent(EventBus.VIDEO_CONFIG_CHANGED, true)
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
                            // 验证：A的绝对值必须大于等于B的绝对值
                            if (kotlin.math.abs(quickJumpMinutesA) < 1) {
                                VideoPlay.quickJumpMinutesA = 1
                                quickJumpMinutesA = 1
                            }
                        }
                        .show { value ->
                            VideoPlay.quickJumpMinutesB = value
                            quickJumpMinutesB = value
                            // 验证：A的绝对值必须大于等于B的绝对值
                            if (kotlin.math.abs(value) > kotlin.math.abs(quickJumpMinutesA)) {
                                VideoPlay.quickJumpMinutesA = kotlin.math.abs(value)
                                quickJumpMinutesA = kotlin.math.abs(value)
                            }
                            postEvent(EventBus.VIDEO_CONFIG_CHANGED, true)
                        }
                }
            )
        }

        // 跳过片头片尾
        SettingSwitchItem(
            title = stringResource(R.string.skip_intro_outro_enabled),
            checked = skipIntroOutroEnabled,
            onCheckedChange = { checked ->
                skipIntroOutroEnabled = checked
                VideoPlay.skipIntroOutroEnabled = checked
                postEvent(EventBus.VIDEO_CONFIG_CHANGED, true)
            }
        )

        // 跳过片头秒数（仅在跳过片头片尾开启时显示）
        if (skipIntroOutroEnabled) {
            SettingClickItem(
                title = stringResource(R.string.skip_intro_seconds),
                summary = stringResource(R.string.skip_seconds_summary, skipIntroSeconds),
                onClick = {
                    NumberPickerDialog(context)
                        .setTitle(context.getString(R.string.skip_intro_seconds))
                        .setMaxValue(300)
                        .setMinValue(5)
                        .setValue(skipIntroSeconds)
                        .setCustomButton(R.string.btn_default_s) {
                            VideoPlay.skipIntroSeconds = 30
                            skipIntroSeconds = 30
                        }
                        .show { value ->
                            VideoPlay.skipIntroSeconds = value
                            skipIntroSeconds = value
                        }
                }
            )

            // 跳过片尾秒数
            SettingClickItem(
                title = stringResource(R.string.skip_outro_seconds),
                summary = stringResource(R.string.skip_seconds_summary, skipOutroSeconds),
                onClick = {
                    NumberPickerDialog(context)
                        .setTitle(context.getString(R.string.skip_outro_seconds))
                        .setMaxValue(300)
                        .setMinValue(5)
                        .setValue(skipOutroSeconds)
                        .setCustomButton(R.string.btn_default_s) {
                            VideoPlay.skipOutroSeconds = 30
                            skipOutroSeconds = 30
                        }
                        .show { value ->
                            VideoPlay.skipOutroSeconds = value
                            skipOutroSeconds = value
                        }
                }
            )
        }

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

