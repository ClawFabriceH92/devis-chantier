package com.trucdecomptable.devischantier.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trucdecomptable.devischantier.data.Devis
import com.trucdecomptable.devischantier.data.Statut
import com.trucdecomptable.devischantier.util.Formats
import java.util.Calendar

/**
 * L'accueil (§3.1) : en un coup d'œil, ce qui attend l'artisan (chiffres clés,
 * prochaine étape), ses devis du plus récent au plus ancien, et deux gros
 * boutons toujours sous le pouce : nouveau devis, dicter.
 */
@Composable
fun EcranAccueil(
    devis: List<Devis>,
    nomEntreprise: String,
    onOuvrir: (Long) -> Unit,
    onNouveau: () -> Unit,
    onDictee: () -> Unit,
    onReglages: () -> Unit,
    onAction: (Devis, String) -> Unit,
) {
    var filtre by remember { mutableStateOf("TOUS") }
    var recherche by remember { mutableStateOf("") }
    var menuPour by remember { mutableStateOf<Devis?>(null) }

    val maintenant = System.currentTimeMillis()
    val jour = 86_400_000L
    val enAttente = devis.filter { it.statut == Statut.ENVOYE }
    val aRelancer = enAttente.filter { maintenant - (it.dateEnvoi ?: it.dateDevis) > 7 * jour }
    val expirants = enAttente.filter { it.dateLimite in maintenant..(maintenant + 7 * jour) }
    val debutMois = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val acceptesMois = devis.filter {
        it.statut in setOf(Statut.ACCEPTE, Statut.TRANSMIS, Statut.FACTURE) && (it.dateAcceptation ?: it.dateDevis) >= debutMois
    }

    val filtres = listOf(
        "TOUS" to "Tous",
        Statut.BROUILLON to "Brouillons",
        Statut.ENVOYE to "Envoyés",
        "A_RELANCER" to "À relancer",
        Statut.ACCEPTE to "Acceptés",
        Statut.TRANSMIS to "Transmis",
        "CLOS" to "Refusés / expirés",
    )

    fun correspond(d: Devis, code: String): Boolean = when (code) {
        "TOUS" -> true
        "CLOS" -> d.statut == Statut.REFUSE || d.statut == Statut.EXPIRE
        "A_RELANCER" -> d.statut == Statut.ENVOYE && maintenant - (d.dateEnvoi ?: d.dateDevis) > 7 * jour
        else -> d.statut == code
    }

    val visibles = devis
        .filter { correspond(it, filtre) }
        .filter { d ->
            recherche.isBlank() || listOf(d.numero, d.clientNom, d.objet)
                .any { it.contains(recherche, ignoreCase = true) }
        }

    Column(Modifier.fillMaxSize()) {
        // ------------------------------------------------------------ en-tête
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary)
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Mes devis", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text(
                        nomEntreprise.ifBlank { "Complétez votre entreprise dans les réglages" },
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onReglages, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Default.Settings, contentDescription = "Réglages", tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
            if (devis.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tuile("${enAttente.size}", "en attente", Modifier.weight(1f))
                    Tuile("${aRelancer.size}", "à relancer", Modifier.weight(1f), alerte = aRelancer.isNotEmpty())
                    Tuile(Formats.euros(acceptesMois.sumOf { it.totalTTC }, decimales = false), "acceptés ce mois", Modifier.weight(1.3f))
                }
            }
        }

        Box(Modifier.weight(1f)) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 110.dp),
            ) {
                // ------------------------------------------------ prochaine étape
                if (aRelancer.isNotEmpty() || expirants.isNotEmpty()) {
                    item {
                        val texte = buildString {
                            if (aRelancer.isNotEmpty()) {
                                append(if (aRelancer.size == 1) "1 devis attend une réponse depuis plus de 7 jours" else "${aRelancer.size} devis attendent une réponse depuis plus de 7 jours")
                            }
                            if (expirants.isNotEmpty()) {
                                if (isNotEmpty()) append(" ; ")
                                append(if (expirants.size == 1) "1 devis expire cette semaine" else "${expirants.size} devis expirent cette semaine")
                                append(" (${expirants.first().numero}, le ${Formats.date(expirants.first().dateLimite)})")
                            }
                            append(".")
                        }
                        BandeauEtape(
                            titre = "Des clients à relancer",
                            texte = texte,
                            libelleBouton = "Voir les devis à relancer",
                            onClick = { filtre = if (aRelancer.isNotEmpty()) "A_RELANCER" else Statut.ENVOYE },
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }

                if (devis.isNotEmpty()) {
                    item {
                        ChampTexte(
                            etiquette = "Rechercher un client, un numéro, un objet",
                            valeur = recherche,
                            onValeur = { recherche = it },
                        )
                    }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(filtres) { (code, libelle) ->
                                val nombre = devis.count { correspond(it, code) }
                                FilterChip(
                                    selected = filtre == code,
                                    onClick = { filtre = code },
                                    label = { Text(if (code == "TOUS") libelle else "$libelle · $nombre", fontSize = 15.sp) },
                                    modifier = Modifier.heightIn(min = 44.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = Color.White,
                                    ),
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }

                // ------------------------------------------------ état vide
                if (devis.isEmpty()) {
                    item {
                        EtatVide(
                            icone = Icons.Default.Description,
                            titre = "Aucun devis pour l'instant",
                            texte = "Votre premier devis se fait en deux minutes : appuyez sur « Nouveau devis », " +
                                "choisissez le client, ajoutez les lignes depuis votre catalogue ou dictez-les.",
                        )
                    }
                } else if (visibles.isEmpty()) {
                    item {
                        EtatVide(
                            icone = Icons.Default.Description,
                            titre = "Rien dans cette catégorie",
                            texte = if (recherche.isNotBlank()) "Aucun devis ne correspond à « $recherche »." else "Aucun devis avec ce statut pour le moment.",
                        )
                    }
                }

                items(visibles, key = { it.id }) { d ->
                    CarteDevis(d, onClick = { onOuvrir(d.id) }, onMenu = { menuPour = d })
                    Spacer(Modifier.height(10.dp))
                }
            }

            // ------------------------------------------------ actions fixes
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 10.dp,
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BoutonPrincipal(
                        "Nouveau devis",
                        onClick = onNouveau,
                        icone = Icons.Default.Add,
                        modifier = Modifier.weight(1.35f),
                    )
                    BoutonGeant(
                        "Dicter",
                        onClick = onDictee,
                        icone = Icons.Default.Mic,
                        modifier = Modifier.weight(1f),
                        description = "Nouveau devis vocal",
                    )
                }
            }
        }
    }

    menuPour?.let { d ->
        MenuActions(
            titre = "Devis ${d.numero} · ${d.clientNom}",
            actions = buildList {
                add("Ouvrir le devis" to { onOuvrir(d.id) })
                add("Dupliquer" to { onAction(d, "DUPLIQUER") })
                if (d.statut == Statut.ENVOYE) {
                    add("Relancer le client" to { onAction(d, "RELANCER") })
                    add("Marquer accepté" to { onAction(d, "ACCEPTER") })
                }
                if (d.statut == Statut.ACCEPTE || d.statut == Statut.TRANSMIS) {
                    add("Transmettre au comptable" to { onAction(d, "TRANSMETTRE") })
                    add("Marquer facturé" to { onAction(d, "FACTURE") })
                }
                if (d.statut == Statut.BROUILLON) {
                    add("Supprimer le brouillon" to { onAction(d, "SUPPRIMER") })
                }
                if (d.statut != Statut.BROUILLON) {
                    add("Créer une nouvelle version" to { onAction(d, "VERSION") })
                }
            },
            fermer = { menuPour = null },
        )
    }
}

/**
 * Une carte de devis : la bande de couleur dit le statut, le client et le
 * montant se lisent en premier, le numéro et la date en second.
 */
@Composable
private fun CarteDevis(devis: Devis, onClick: () -> Unit, onMenu: () -> Unit) {
    val (_, couleurStatut) = Couleurs.statut(devis.statut)
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 92.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(7.dp)
                    .fillMaxHeight()
                    .background(couleurStatut),
            )
            Row(
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            devis.clientNom.ifBlank { "Client à renseigner" },
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            Formats.euros(devis.totalTTC),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    if (devis.objet.isNotBlank()) {
                        Text(
                            devis.objet,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BadgeStatut(devis.statut)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${devis.numero}${if (devis.indice != "A") " (${devis.indice})" else ""} · ${Formats.date(devis.dateDevis)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                IconButton(onClick = onMenu, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Actions du devis ${devis.numero}", modifier = Modifier.size(26.dp))
                }
            }
        }
    }
}
