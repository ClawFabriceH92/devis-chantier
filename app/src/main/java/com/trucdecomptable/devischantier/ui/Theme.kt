package com.trucdecomptable.devischantier.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trucdecomptable.devischantier.data.Statut

/**
 * Palette de l'application. Une règle simple, tenue partout :
 * - le bleu chantier structure (en-têtes, navigation, boutons de second rang) ;
 * - l'orange, et lui seul, signale « l'action à faire maintenant » ;
 * - le vert dit « c'est bon », le rouge « attention », le gris « en attente ».
 * Les fonds pâles servent aux badges et aux bandeaux : texte sombre sur fond
 * clair, lisible au soleil et avec des lunettes de sécurité.
 */
object Couleurs {
    val bleu = Color(0xFF0F3D5C)
    val bleuClair = Color(0xFF2E7DAF)
    val bleuPale = Color(0xFFDCE9F3)
    val orange = Color(0xFFE4761B)
    val orangePale = Color(0xFFFFE8D4)
    val orangeFonce = Color(0xFF8A4300)
    val sable = Color(0xFFF4F1EA)
    val vert = Color(0xFF1B6B4A)
    val vertPale = Color(0xFFD4EEDF)
    val rouge = Color(0xFFB3261E)
    val rougePale = Color(0xFFF9DAD6)
    val violet = Color(0xFF5B4A9E)
    val violetPale = Color(0xFFE5DFF7)
    val gris = Color(0xFF5F6B73)
    val grisPale = Color(0xFFE7EAED)
    val ardoise = Color(0xFF1C2630)
    val contrasteFond = Color(0xFF101418)
    val contrasteSurface = Color(0xFF1B2228)

    /** Fond pâle et texte sombre d'un statut : le même couple à l'accueil, au détail et sur la bande des cartes. */
    fun statut(statut: String): Pair<Color, Color> = when (statut) {
        Statut.BROUILLON -> grisPale to gris
        Statut.ENVOYE -> bleuPale to bleuClair
        Statut.ACCEPTE -> vertPale to vert
        Statut.TRANSMIS -> violetPale to violet
        Statut.FACTURE -> vertPale to Color(0xFF0E4A32)
        Statut.REFUSE -> rougePale to rouge
        Statut.EXPIRE -> orangePale to orangeFonce
        else -> grisPale to gris
    }
}

private val clair = lightColorScheme(
    primary = Couleurs.bleu,
    onPrimary = Color.White,
    primaryContainer = Couleurs.bleuPale,
    onPrimaryContainer = Color(0xFF0B2E47),
    secondary = Couleurs.orange,
    onSecondary = Color.White,
    secondaryContainer = Couleurs.orangePale,
    onSecondaryContainer = Couleurs.orangeFonce,
    tertiary = Couleurs.vert,
    onTertiary = Color.White,
    tertiaryContainer = Couleurs.vertPale,
    onTertiaryContainer = Color(0xFF0E4A32),
    background = Couleurs.sable,
    onBackground = Couleurs.ardoise,
    surface = Color.White,
    onSurface = Couleurs.ardoise,
    surfaceVariant = Color(0xFFEAEEF1),
    onSurfaceVariant = Couleurs.gris,
    outline = Color(0xFFB5C0C8),
    outlineVariant = Color(0xFFD9E0E5),
    error = Couleurs.rouge,
    onError = Color.White,
    errorContainer = Couleurs.rougePale,
    onErrorContainer = Color(0xFF7A1410),
)

/** Mode chantier : contraste élevé et fond sombre, lisible en plein soleil (U-08). */
private val contraste = darkColorScheme(
    primary = Color(0xFF7FD3FF),
    onPrimary = Color(0xFF00243A),
    primaryContainer = Color(0xFF1E4D6B),
    onPrimaryContainer = Color(0xFFDDF1FF),
    secondary = Color(0xFFFFB86B),
    onSecondary = Color(0xFF3A1D00),
    secondaryContainer = Color(0xFF6B3A08),
    onSecondaryContainer = Color(0xFFFFE3C6),
    tertiary = Color(0xFF8FE0B8),
    onTertiary = Color(0xFF003822),
    tertiaryContainer = Color(0xFF1E5A3F),
    onTertiaryContainer = Color(0xFFD4F5E2),
    background = Couleurs.contrasteFond,
    onBackground = Color(0xFFF2F5F7),
    surface = Couleurs.contrasteSurface,
    onSurface = Color(0xFFF2F5F7),
    surfaceVariant = Color(0xFF2A333B),
    onSurfaceVariant = Color(0xFFC3CCD3),
    outline = Color(0xFF6F7B85),
    error = Color(0xFFFF9E9A),
    onError = Color(0xFF4A0300),
    errorContainer = Color(0xFF7A1410),
    onErrorContainer = Color(0xFFFFDAD6),
)

/**
 * Typographie volontairement plus grande : un artisan lit son écran avec des
 * lunettes de sécurité et des gants (U-07). Les tailles suivent ensuite le
 * réglage d'Android.
 */
private val typographie = Typography(
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 18.sp),
    bodyMedium = TextStyle(fontSize = 16.sp),
    bodySmall = TextStyle(fontSize = 14.sp),
    labelLarge = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
)

private val formes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun DevisTheme(modeChantier: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (modeChantier) contraste else clair,
        typography = typographie,
        shapes = formes,
        content = content,
    )
}
