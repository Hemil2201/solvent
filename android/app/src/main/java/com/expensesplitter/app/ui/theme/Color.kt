package com.expensesplitter.app.ui.theme

import androidx.compose.ui.graphics.Color

// 2026 fintech palette — replaces the flat single-tone Splitwise-green
// system. Base numbers (#0F172A ink, #1E40AF/#3B82F6 blue, #059669 green)
// anchor a modern personal-finance-app color story; green and red were then
// brightened/warmed to read as confident rather than muted, and the dark
// ink tone was repurposed as a bold "hero card" treatment (see
// BalanceHeroCard/SectionCard) instead of the whole app's base surface, to
// match Cash App's black balance card sitting on an otherwise light shell
// rather than a fully dark app.

// Ink — the dark hero-card surface (Cash App-style bold card on a light
// shell), and primary text on light surfaces.
val Ink = Color(0xFF0F172A)
val InkElevated = Color(0xFF1B2436) // hero card surface, one step lighter than Ink
val OnInk = Color(0xFFF8FAFC)

val GreenPrimaryLight = Color(0xFF10B981)
val OnGreenPrimaryLight = Color(0xFF04140D)
val GreenPrimaryContainerLight = Color(0xFFD1FAE9)
val OnGreenPrimaryContainerLight = Color(0xFF00382A)
// Brighter tint for the same green when it sits on the dark Ink surface.
val GreenOnInk = Color(0xFF34E3A1)

val RedSecondaryLight = Color(0xFFFF5A5F)
val OnRedSecondaryLight = Color(0xFFFFFFFF)
val RedSecondaryContainerLight = Color(0xFFFFDCDD)
val OnRedSecondaryContainerLight = Color(0xFF4A0E10)
val RedOnInk = Color(0xFFFF8A8E)

val IndigoTertiaryLight = Color(0xFF6366F1)
val OnIndigoTertiaryLight = Color(0xFFFFFFFF)
val IndigoTertiaryContainerLight = Color(0xFFE2E1FF)
val OnIndigoTertiaryContainerLight = Color(0xFF1D1876)

val BackgroundLight = Color(0xFFFAFAF9)
val OnBackgroundLight = Color(0xFF14181F)
val SurfaceVariantLight = Color(0xFFF0F1F3)
val OnSurfaceVariantLight = Color(0xFF5C6370)
val OutlineLight = Color(0xFFE2E4E8)
val OutlineVariantLight = Color(0xFFEBEDF0)

val SurfaceDimLight = Color(0xFFE7E7E5)
val SurfaceBrightLight = Color(0xFFFAFAF9)
val SurfaceContainerLowestLight = Color(0xFFFFFFFF)
val SurfaceContainerLowLight = Color(0xFFF7F7F6)
val SurfaceContainerLight = Color(0xFFF1F2F1)
val SurfaceContainerHighLight = Color(0xFFEBECEB)
val SurfaceContainerHighestLight = Color(0xFFE5E6E5)
val InverseSurfaceLight = Color(0xFF2B3140)
val InverseOnSurfaceLight = Color(0xFFF1F2F1)
val ScrimLight = Color(0xFF000000)

val ErrorLight = Color(0xFFBA1A1A)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFFFDAD6)
val OnErrorContainerLight = Color(0xFF410002)

val GreenPrimaryDark = Color(0xFF34E3A1)
val OnGreenPrimaryDark = Color(0xFF00382A)
val GreenPrimaryContainerDark = Color(0xFF00513F)
val OnGreenPrimaryContainerDark = Color(0xFFD1FAE9)

val RedSecondaryDark = Color(0xFFFF8A8E)
val OnRedSecondaryDark = Color(0xFF5F1216)
val RedSecondaryContainerDark = Color(0xFF7D2C10)
val OnRedSecondaryContainerDark = Color(0xFFFFDCDD)

val IndigoTertiaryDark = Color(0xFFC5C4FF)
val OnIndigoTertiaryDark = Color(0xFF2D2A87)
val IndigoTertiaryContainerDark = Color(0xFF44409E)
val OnIndigoTertiaryContainerDark = Color(0xFFE2E1FF)

val BackgroundDark = Color(0xFF0F1115)
val OnBackgroundDark = Color(0xFFE3E4E6)
val SurfaceVariantDark = Color(0xFF2A2E33)
val OnSurfaceVariantDark = Color(0xFFB9BDC4)
val OutlineDark = Color(0xFF3A3F45)
val OutlineVariantDark = Color(0xFF44494F)

val SurfaceDimDark = Color(0xFF0F1115)
val SurfaceBrightDark = Color(0xFF3A3F45)
val SurfaceContainerLowestDark = Color(0xFF0A0C0F)
val SurfaceContainerLowDark = Color(0xFF181B20)
val SurfaceContainerDark = Color(0xFF1C1F25)
val SurfaceContainerHighDark = Color(0xFF262A30)
val SurfaceContainerHighestDark = Color(0xFF31353B)
val InverseSurfaceDark = Color(0xFFE3E4E6)
val InverseOnSurfaceDark = Color(0xFF2B2F35)
val ScrimDark = Color(0xFF000000)

val ErrorDark = Color(0xFFFFB4AB)
val OnErrorDark = Color(0xFF690005)
val ErrorContainerDark = Color(0xFF93000A)
val OnErrorContainerDark = Color(0xFFFFDAD6)

// Balance semantics: green = owed to you, red = you owe.
object BalanceColors {
    val positiveLight = GreenPrimaryLight
    val positiveContainerLight = GreenPrimaryContainerLight
    val negativeLight = RedSecondaryLight
    val negativeContainerLight = RedSecondaryContainerLight

    val positiveDark = GreenPrimaryDark
    val positiveContainerDark = GreenPrimaryContainerDark
    val negativeDark = RedSecondaryDark
    val negativeContainerDark = RedSecondaryContainerDark

    // Brighter variants tuned for the dark Ink hero card.
    val positiveOnInk = GreenOnInk
    val negativeOnInk = RedOnInk

    // Budget "getting close to the limit" state — distinct from the
    // red/green over/under semantics above, so it needs its own color
    // rather than reusing either.
    val warningLight = Color(0xFFF59E0B)
}

// Vivid, saturated category palette — replaces the previous pastel-muted
// set so category dots/icons read as intentional accents rather than
// washed-out fills.
val CategoryPalette = listOf(
    Color(0xFF10B981), // emerald
    Color(0xFFFF5A5F), // coral
    Color(0xFF6366F1), // indigo
    Color(0xFFF59E0B), // amber
    Color(0xFF0EA5E9), // sky
    Color(0xFFEC4899), // magenta
    Color(0xFF84CC16), // lime
    Color(0xFFF97316), // terracotta
)

fun categoryColorFor(categoryName: String): Color {
    val index = categoryName.fold(0) { acc, c -> acc + c.code }
    return CategoryPalette[index % CategoryPalette.size]
}
