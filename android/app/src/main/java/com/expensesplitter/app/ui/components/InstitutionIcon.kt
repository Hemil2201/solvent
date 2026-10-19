package com.expensesplitter.app.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Real bank logo (Plaid-hosted, base64-encoded PNG — genuinely absent for
// some institutions) over a badge in the bank's own real brand color.
// colorHex is effectively always present per Plaid's docs (a real color or
// a deterministic fallback), but parsed defensively anyway.
@Composable
fun InstitutionIcon(
    institutionName: String,
    colorHex: String?,
    logoBase64: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val color = remember(colorHex) {
        colorHex?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
    } ?: MaterialTheme.colorScheme.onSurfaceVariant

    val bitmap = remember(logoBase64) {
        logoBase64?.let { encoded ->
            runCatching {
                val bytes = Base64.decode(encoded, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull()
        }
    }

    Box(
        modifier = modifier.size(size).background(color.copy(alpha = 0.18f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(size).clip(CircleShape),
            )
        } else {
            Text(
                text = institutionName.take(1).uppercase(),
                fontSize = (size.value * 0.42).sp,
                fontWeight = FontWeight.SemiBold,
                color = color,
            )
        }
    }
}
