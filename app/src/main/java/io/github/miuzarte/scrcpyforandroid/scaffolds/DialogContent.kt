package io.github.miuzarte.scrcpyforandroid.scaffolds

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max

@Composable
fun Modifier.dialogContentHeightLimit(minHeight: Dp = 240.dp): Modifier {
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val windowHeightPx = with(density) { windowInfo.containerDpSize.height.toPx() }
    val imePx = WindowInsets.ime.getBottom(density)
    val topPx = maxOf(
        WindowInsets.statusBars.getTop(density),
        WindowInsets.displayCutout.getTop(density),
        WindowInsets.captionBar.getTop(density),
    )
    val bottomPx = maxOf(
        WindowInsets.navigationBars.getBottom(density),
        WindowInsets.captionBar.getBottom(density),
    )
    val availablePx = windowHeightPx - imePx - topPx - bottomPx - with(density) { 16.dp.toPx() }
    val maxHeight = with(density) { availablePx.toDp() }.coerceAtLeast(minHeight)
    return this.heightIn(max = maxHeight)
}

@Composable
fun ScrollableDialogContent(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        content = content,
    )
}
