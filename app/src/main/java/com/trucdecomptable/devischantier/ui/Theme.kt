package com.trucdecomptable.devischantier.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Palette de l'app : bleu chantier, orange pour les actions, sable en fond. */
object Couleurs {
    val bleu = Color(0xFF0F3D5C)
    val bleuClair = Color(0xFF2E7DAF)
    val orange = Color(0xFFE4761B)
    val sable = Color(0xFFF5F2EC)
    val vert = Color(0xFF1B6B4A)
    val rouge = Color(0xFFB3261E)
    val gris = Color(0xFF6E7A80)
    val contrasteFond = Color(0xFF101418)
    val contrasteSurface = Color(0xFF1B2228)
}

private val clair = lightColorScheme(
    primary = Couleurs.bleu,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E4EC),
    secondary = Couleurs.orange,
    onSecondary = Color.White,
    tertiary = Couleurs.vert,
    background = Couleurs.sable,
    surface = Color.White,
    error = Couleurs.rouge,
)

/** Mode chantier : contraste élevé et fond sombre, lisible en plein soleil (U-08). */
private val contraste = darkColorScheme(
    primary = Color(0xFF7FD3FF),
    onPrimary = Color(0xFF00243A),
    secondary = Color(0xFFFFB86B),
    onSecondary = Color(0xFF3A1D00),
    background = Couleurs.contrasteFond,
    surface = Couleurs.contrasteSurface,
    onSurface = Color(0xFFF2F5F7),
    error = Color(0xFFFF9E9A),
)

/**
 * Typographie volontairement plus grande : un artisan lit son écran avec des
 * lunettes de sécurité et des gants (U-07). Les tailles suivent ensuite le
 * réglage d'Android.
 */
private val typographie = Typography(
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 18.sp),
    bodyMedium = TextStyle(fontSize = 16.sp),
    labelLarge = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun DevisTheme(modeChantier: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (modeChantier) contraste else clair,
        typography = typographie,
        content = content,
    )
}
