package androidx.compose.runtime

import androidx.compose.runtime.saveable.rememberSaveable as rememberSaveableDelegate

/**
 * 兼容旧调用点错误导入的 androidx.compose.runtime.rememberSaveable。
 * Compose 的实际实现位于 runtime.saveable，这里仅做转发。
 */
@Composable
fun <T> rememberSaveable(init: () -> T): T = rememberSaveableDelegate { init() }
