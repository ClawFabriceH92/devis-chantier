package com.trucdecomptable.devischantier.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trucdecomptable.devischantier.data.Statut

/**
 * Les briques d'interface du §3.7 : zones tactiles d'au moins 64 dp pour les
 * actions principales et 56 dp pour les autres, espacées de 12 dp, chaque icône
 * accompagnée d'un mot. Aucune action destructrice sans confirmation.
 */
object Tailles {
    val boutonPrincipal: Dp = 64.dp
    val boutonSecondaire: Dp = 56.dp
    val espace: Dp = 12.dp
}

@Composable
fun BoutonGeant(
    texte: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icone: ImageVector? = null,
    couleur: Color = MaterialTheme.colorScheme.primary,
    actif: Boolean = true,
    hauteur: Dp = Tailles.boutonPrincipal,
) {
    Button(
        onClick = onClick,
        enabled = actif,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = hauteur),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = couleur),
    ) {
        if (icone != null) {
            Icon(icone, contentDescription = null, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(texte, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
    }
}

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
        Text(texte, fontSize = 17.sp)
    }
}

@Composable
fun CarteBloc(
    titre: String? = null,
    modifier: Modifier = Modifier,
    couleurFond: Color = MaterialTheme.colorScheme.surface,
    contenu: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = couleurFond),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (titre != null) {
                Text(titre, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
            }
            contenu()
        }
    }
}

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
    if (valeur != dernierExterne) {
        dernierExterne = valeur
        if (!enSaisie) texte = valeur
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
        )
        if (aide != null) {
            Text(aide, style = MaterialTheme.typography.bodyMedium, color = Couleurs.gris)
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
        Text(etiquette, style = MaterialTheme.typography.bodyLarge, color = Couleurs.gris)
        Text(
            valeur,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (gras) FontWeight.Bold else FontWeight.Normal,
            color = if (couleur == Color.Unspecified) MaterialTheme.colorScheme.onSurface else couleur,
        )
    }
}

@Composable
fun BadgeStatut(statut: String) {
    val couleur = when (statut) {
        Statut.BROUILLON -> Couleurs.gris
        Statut.ENVOYE -> Couleurs.bleuClair
        Statut.ACCEPTE -> Couleurs.vert
        Statut.TRANSMIS -> Couleurs.bleu
        Statut.FACTURE -> Couleurs.vert
        Statut.REFUSE, Statut.EXPIRE -> Couleurs.rouge
        else -> Couleurs.gris
    }
    Text(
        Statut.libelle(statut),
        color = Color.White,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .background(couleur, RoundedCornerShape(8.dp))
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

@Composable
fun BlocAlerte(texte: String, couleur: Color = Couleurs.orange) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(couleur.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(texte, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
