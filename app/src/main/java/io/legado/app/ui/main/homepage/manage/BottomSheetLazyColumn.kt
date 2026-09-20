package io.legado.app.ui.main.homepage.manage

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity

/**
 * LazyColumn used inside a ModalBottomSheet.
 *
 * A LazyColumn normally hands unconsumed boundary deltas to its parent through
 * nested scroll. ModalBottomSheet is itself draggable, so an upward drag at the
 * list's bottom can accidentally become a sheet drag and make the whole sheet
 * oscillate around its anchor.
 *
 * We only consume the one problematic direction:
 * - at the bottom + finger moving up: keep the delta inside the list boundary
 * - every other direction: leave it untouched so normal list scrolling and
 *   bottom-sheet dragging remain available
 *
 * Overscroll is disabled for this management list because the sheet itself is
 * the visual boundary effect; two competing edge effects make the jitter worse.
 */
@Composable
fun BottomSheetLazyColumn(
    state: LazyListState,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    val boundaryConnection = remember(state) {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero

                // Nested-scroll Y follows the pointer direction here:
                // negative = finger moving up. At the bottom this must not
                // escape to ModalBottomSheet.
                return if (!state.canScrollForward && available.y < 0f) {
                    Offset(x = 0f, y = available.y)
                } else {
                    Offset.Zero
                }
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero

                // Defensive second line: if the list itself reaches the
                // boundary during this gesture, consume the remaining upward
                // delta before it can reach the sheet.
                return if (!state.canScrollForward && available.y < 0f) {
                    Offset(x = 0f, y = available.y)
                } else {
                    Offset.Zero
                }
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                return if (!state.canScrollForward && available.y < 0f) {
                    Velocity(x = 0f, y = available.y)
                } else {
                    Velocity.Zero
                }
            }
        }
    }

    LazyColumn(
        state = state,
        modifier = modifier.nestedScroll(boundaryConnection),
        overscrollEffect = null,
        content = content,
    )
}
