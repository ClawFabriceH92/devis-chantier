package com.trucdecomptable.devischantier.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trucdecomptable.devischantier.data.Statut

/**
 * Les briques d'interface du §3.7 : zones tactiles d'au moins 64 dp pour les
 * actions principales et 56 dp pour les autres, espacées de 12 dp, chaque icône
 * accompagnée d'un mot. Aucune action destructrice sans confirmation.
 *
 * Code couleur tenu partout : orange = l'action à faire maintenant, bleu =
 * navigation et second rang, vert = c'est bon, rouge = attention.
 */
object Tailles {
    val boutonPrincipal: Dp = 64.dp
    val boutonSecondaire: Dp = 56.dp
    val espace: Dp = 12.dp
}

// ------------------------------------------------------------------ en-têtes

/**
 * L'en-tête de tous les écrans : un titre, une ligne qui dit où l'on est ou ce
 * qu'on y fait, une flèche retour si l'écran s'empile, une action à droite.
 */
@Composable
fun EnTeteEcran(
    titre: String,
    sousTitre: String? = null,
    onRetour: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary)
            .padding(start = if (onRetour != null) 4.dp else 16.dp, end = 8.dp, top = 10.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onRetour != null) {
            IconButton(onClick = onRetour, modifier = Modifier.size(56.dp)) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Retour",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                titre,
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            if (!sousTitre.isNullOrBlank()) {
                Text(
                    sousTitre,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                    maxLines = 2,
                )
            }
        }
        action?.invoke()
    }
}

@Composable
fun EnTeteRetour(titre: String, onRetour: () -> Unit) = EnTeteEcran(titre = titre, onRetour = onRetour)

// ------------------------------------------------------------------ boutons

@Composable
fun BoutonGeant(
    texte: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icone: ImageVector? = null,
    couleur: Color = MaterialTheme.colorScheme.primary,
    actif: Boolean = true,
    hauteur: Dp = Tailles.boutonPrincipal,
    description: String? = null,
) {
    Button(
        onClick = onClick,
        enabled = actif,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = hauteur)
            .let { m -> if (description != null) m.semantics { contentDescription = description } else m },
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = couleur),
    ) {
        if (icone != null) {
            Icon(icone, contentDescription = null, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(texte, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

/** L'action principale de l'écran : orange, une seule par écran. */
@Composable
fun BoutonPrincipal(
    texte: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icone: ImageVector? = null,
    actif: Boolean = true,
    hauteur: Dp = Tailles.boutonPrincipal,
    description: String? = null,
) = BoutonGeant(
    texte = texte,
    onClick = onClick,
    modifier = modifier,
    icone = icone,
    couleur = Couleurs.orange,
    actif = actif,
    hauteur = hauteur,
    description = description,
)

@Composable
fun BoutonSecondaire(
    texte: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icone: ImageVector? = null,
    actif: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = actif,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Tailles.boutonSecondaire),
        shape = RoundedCornerShape(12.dp),
    ) {
        if (icone != null) {
            Icon(icone, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(texte, fontSize = 17.sp, textAlign = TextAlign.Center)
    }
}

/** Un choix parmi plusieurs (métier, fournisseur…) : le retenu est plein et coché. */
@Composable
fun BoutonChoix(
    texte: String,
    selectionne: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sousTexte: String? = null,
) {
    val fond = if (selectionne) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val bord = if (selectionne) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = fond,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Tailles.boutonSecondaire)
            .border(if (selectionne) 2.dp else 1.dp, bord, RoundedCornerShape(12.dp)),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(if (selectionne) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (selectionne) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    texte,
                    fontSize = 17.sp,
                    fontWeight = if (selectionne) FontWeight.SemiBold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (sousTexte != null) {
                    Text(sousTexte, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ cartes

@Composable
fun CarteBloc(
    titre: String? = null,
    modifier: Modifier = Modifier,
    couleurFond: Color = MaterialTheme.colorScheme.surface,
    icone: ImageVector? = null,
    sousTitre: String? = null,
    contenu: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = couleurFond),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (titre != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icone != null) {
                        Icon(icone, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(titre, style = MaterialTheme.typography.titleMedium)
                }
                if (sousTitre != null) {
                    Text(sousTitre, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(10.dp))
            }
            contenu()
        }
    }
}

/**
 * « Prochaine étape » : ce que l'artisan doit faire maintenant, en une phrase
 * et un bouton. Le seul bandeau orange de l'écran.
 */
@Composable
fun BandeauEtape(
    titre: String,
    texte: String,
    libelleBouton: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Couleurs.orange),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Prochaine étape", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(titre, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(texte, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                if (libelleBouton != null && onClick != null) {
                    Spacer(Modifier.height(8.dp))
                    BoutonPrincipal(libelleBouton, onClick = onClick, hauteur = 52.dp)
                }
            }
        }
    }
}

/** Une ligne de menu : icône, titre, état en une phrase, chevron. */
enum class EtatLigne { NEUTRE, OK, AVERTIR }

@Composable
fun LigneMenu(
    icone: ImageVector,
    titre: String,
    sousTitre: String,
    etat: EtatLigne = EtatLigne.NEUTRE,
    onClick: () -> Unit,
) {
    val couleurEtat = when (etat) {
        EtatLigne.OK -> Couleurs.vert
        EtatLigne.AVERTIR -> Couleurs.orange
        EtatLigne.NEUTRE -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icone, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(titre, style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (etat != EtatLigne.NEUTRE) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(couleurEtat),
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        sousTitre,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (etat == EtatLigne.AVERTIR) Couleurs.orangeFonce else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Écran sans contenu : une icône, une phrase, et quoi faire. */
@Composable
fun EtatVide(
    icone: ImageVector,
    titre: String,
    texte: String,
    contenu: @Composable (() -> Unit)? = null,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icone, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(titre, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(texte, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (contenu != null) {
            Spacer(Modifier.height(16.dp))
            contenu()
        }
    }
}

/** Chiffre clé de l'en-tête de l'accueil. */
@Composable
fun Tuile(valeur: String, libelle: String, modifier: Modifier = Modifier, alerte: Boolean = false) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (alerte) Couleurs.orange else Color.White.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(valeur, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(libelle, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp, maxLines = 2)
    }
}

// ------------------------------------------------------------------ champs

/**
 * Champ de saisie dont la valeur vient souvent d'un stockage qui ne notifie pas
 * Compose (SharedPreferences) ou qui répond en différé (Room). Le texte tapé
 * vit donc ici : chaque frappe est transmise par [onValeur], et une valeur
 * poussée de l'extérieur (fiche reprise de l'Annuaire, autre écran) n'est
 * adoptée que si elle change pendant que le champ n'est pas en cours de saisie.
 * Sans cela, un champ relié directement à une préférence revient en arrière à
 * chaque lettre.
 */
@Composable
fun ChampTexte(
    etiquette: String,
    valeur: String,
    onValeur: (String) -> Unit,
    modifier: Modifier = Modifier,
    clavier: KeyboardType = KeyboardType.Text,
    lignes: Int = 1,
    aide: String? = null,
) {
    var texte by remember { mutableStateOf(valeur) }
    var dernierExterne by remember { mutableStateOf(valeur) }
    var enSaisie by remember { mutableStateOf(false) }
    LaunchedEffect(valeur) {
        if (valeur != dernierExterne) {
            dernierExterne = valeur
            if (!enSaisie) texte = valeur
        }
    }
    Column(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = texte,
            onValueChange = {
                texte = it
                onValeur(it)
            },
            label = { Text(etiquette, fontSize = 16.sp) },
            singleLine = lignes == 1,
            minLines = lignes,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = clavier),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Tailles.boutonSecondaire)
                .onFocusChanged { enSaisie = it.isFocused },
            textStyle = MaterialTheme.typography.bodyLarge,
            shape = RoundedCornerShape(12.dp),
        )
        if (aide != null) {
            Text(aide, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun LigneValeur(
    etiquette: String,
    valeur: String,
    gras: Boolean = false,
    couleur: Color = Color.Unspecified,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(etiquette, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            valeur,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (gras) FontWeight.Bold else FontWeight.Normal,
            color = if (couleur == Color.Unspecified) MaterialTheme.colorScheme.onSurface else couleur,
        )
    }
}

/** Pastille de statut : fond pâle, texte sombre, lisible partout. */
@Composable
fun BadgeStatut(statut: String) {
    val (fond, texte) = Couleurs.statut(statut)
    Text(
        Statut.libelle(statut),
        color = texte,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .background(fond, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/** Menu à gros boutons : jamais un petit bouton dans la ligne (U-05). */
@Composable
fun MenuActions(
    titre: String,
    actions: List<Pair<String, () -> Unit>>,
    fermer: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = fermer,
        title = { Text(titre, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Tailles.espace)) {
                actions.forEach { (libelle, action) ->
                    BoutonSecondaire(libelle, onClick = { fermer(); action() })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = fermer) { Text("Fermer", fontSize = 17.sp) }
        },
    )
}

@Composable
fun DialogueConfirmation(
    titre: String,
    texte: String,
    libelleConfirmer: String = "Confirmer",
    onConfirmer: () -> Unit,
    onAnnuler: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onAnnuler,
        title = { Text(titre, style = MaterialTheme.typography.titleLarge) },
        text = { Text(texte, style = MaterialTheme.typography.bodyLarge) },
        confirmButton = {
            Button(onClick = { onAnnuler(); onConfirmer() }, modifier = Modifier.heightIn(min = 56.dp)) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(libelleConfirmer, fontSize = 17.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onAnnuler) { Text("Annuler", fontSize = 17.sp) }
        },
    )
}

/** Message d'information ou d'alerte : fond pâle de la couleur, texte sombre, icône. */
@Composable
fun BlocAlerte(texte: String, couleur: Color = Couleurs.orange, icone: ImageVector = Icons.Default.Info) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(couleur.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icone, contentDescription = null, tint = couleur, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(texte, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
    }
}
