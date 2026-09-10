package io.legado.app.lib.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/** Minimal theme bridge used by the optional modern discovery surface. */
@Composable
fun LegadoTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
