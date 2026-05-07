package io.legado.app.ui.book.readRecord

import androidx.compose.runtime.Composable
import io.legado.app.ui.theme.pageAccentColor
import io.legado.app.ui.theme.pageCardElevatedContainerColor
import io.legado.app.ui.theme.pageHeaderContainerColor
import io.legado.app.ui.theme.pageMutedIconTint
import io.legado.app.ui.theme.pageSecondaryTextColor
import io.legado.app.ui.theme.pageSurfaceVariantColor
import io.legado.app.ui.theme.pageTopBarContainerColor

@Composable
fun readRecordTopBarContainerColor() = pageTopBarContainerColor()

@Composable
fun readRecordCardContainerColor() = pageCardElevatedContainerColor()

@Composable
fun readRecordSummaryCardContainerColor() = pageCardElevatedContainerColor()

@Composable
fun readRecordHeaderContainerColor() = pageHeaderContainerColor()

@Composable
fun readRecordSecondaryTextColor() = pageSecondaryTextColor()

@Composable
fun readRecordTimelineAccentColor() = pageAccentColor()

@Composable
fun readRecordBookStackSurfaceColor() = pageSurfaceVariantColor()

@Composable
fun readRecordMutedIconTint() = pageMutedIconTint()
