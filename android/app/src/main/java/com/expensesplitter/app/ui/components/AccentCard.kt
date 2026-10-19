package com.expensesplitter.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.expensesplitter.app.ui.theme.Spacing

// A quiet near-black card with a thin accent-colored left stripe — the per-
// item identity (which bank, which category) comes from one restrained
// color cue rather than a full-bleed tint, keeping a list of these calm
// even when every item has its own color. The stripe is clipped to the
// card's own rounded corners since Surface clips its content to `shape`.
@Composable
fun AccentCard(
    accent: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(modifier = Modifier.width(3.dp).fillMaxHeight().background(accent))
            Row(modifier = Modifier.weight(1f).padding(Spacing.md)) {
                content()
            }
        }
    }
}
