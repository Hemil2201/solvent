package com.expensesplitter.app.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

// Real merchant logo (Plaid-hosted, when it has one for this specific
// transaction) over the generic category badge — falls back to exactly
// CategoryIcon's rendering when there's no logo, so an unrecognized
// merchant looks the same as it always has rather than a blank circle.
@Composable
fun MerchantIcon(
    logoUrl: String?,
    categoryName: String,
    categoryIcon: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    if (logoUrl != null) {
        AsyncImage(
            model = logoUrl,
            contentDescription = null,
            modifier = modifier.size(size).clip(CircleShape),
        )
    } else {
        CategoryIcon(categoryName = categoryName, icon = categoryIcon, modifier = modifier, size = size)
    }
}
