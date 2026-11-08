package com.trucdecomptable.devischantier.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trucdecomptable.devischantier.data.Devis
import com.trucdecomptable.devischantier.data.Statut
import com.trucdecomptable.devischantier.util.Formats

/**
 * L'accueil (§3.1) : les devis du plus récent au plus ancien, des filtres
 * larges, une recherche, et un bouton « + Nouveau devis » toujours visible.
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
    val expirants = devis.filter {
        it.statut == Statut.ENVOYE && it.dateLimite in maintenant..(maintenant + 7 * 86_400_000L)
    }

    val filtres = listOf(
        "TOUS" to "Tous",
        Statut.BROUILLON to "Brouillons",
        Statut.ENVOYE to "Envoyés",
        Statut.ACCEPTE to "Acceptés",
        Statut.TRANSMIS to "Transmis",
        "CLOS" to "Refusés / expirés",
        "A_RELANCER" to "À relancer",
    )

    val visibles = devis
        .filter { d ->
            when (filtre) {
                "TOUS" -> true
                "CLOS" -> d.statut == Statut.REFUSE || d.statut == Statut.EXPIRE
                "A_RELANCER" -> d.statut == Statut.ENVOYE &&
                    maintenant - (d.dateEnvoi ?: d.dateDevis) > 7 * 86_400_000L
                else -> d.statut == filtre
            }
        }
        .filter { d ->
            recherche.isBlank() || listOf(d.numero, d.clientNom, d.objet)
                .any { it.contains(recherche, ignoreCase = true) }
        }

    Box(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Devis", color = androidx.compose.ui.graphics.Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(
                        nomEntreprise.ifBlank { "Complétez votre entreprise dans les réglages" },
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
                        fontSize = 14.sp,
                    )
                }
                IconButton(onClick = onReglages, modifier = Modifier.size(56.dp)) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Réglages",
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            if (expirants.isNotEmpty()) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    BlocAlerte(
                        "${expirants.size} devis expirent cette semaine. " +
                            "Le plus proche : ${expirants.first().numero} le ${Formats.date(expirants.first().dateLimite)}.",
                    )
                }
            }

            ChampTexte(
                etiquette = "Rechercher un client, un numéro, un objet",
                valeur = recherche,
                onValeur = { recherche = it },
                modifier = Modifier.padding(horizontal = 12.dp),
            )

            LazyRow(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filtres) { (code, libelle) ->
                    FilterChip(
                        selected = filtre == code,
                        onClick = { filtre = code },
                        label = { Text(libelle, fontSize = 15.sp) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }

            if (visibles.isEmpty()) {
                Column(Modifier.padding(24.dp)) {
                    Text("Aucun devis pour ce filtre.", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Appuyez sur « + Nouveau devis » pour commencer : le catalogue et la dictée attendent.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Couleurs.gris,
                    )
                }
            }

            LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            ) {
                items(visibles) { d ->
                    CarteDevis(d, onClick = { onOuvrir(d.id) }, onMenu = { menuPour = d })
                    Spacer(Modifier.height(10.dp))
                }
                item { Spacer(Modifier.height(120.dp)) }
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FloatingActionButton(
                    onClick = onDictee,
                    modifier = Modifier.size(64.dp),
                    containerColor = Couleurs.orange,
                ) {
                    Icon(Icons.Default.Mic, contentDescription = "Nouveau devis vocal", modifier = Modifier.size(30.dp))
                }
                FloatingActionButton(
                    onClick = onNouveau,
                    modifier = Modifier
                        .height(64.dp)
                        .width(210.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Nouveau devis", fontSize = 17.sp)
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

@Composable
private fun CarteDevis(devis: Devis, onClick: () -> Unit, onMenu: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${devis.numero}${if (devis.indice != "A") " (${devis.indice})" else ""}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.width(8.dp))
                    BadgeStatut(devis.statut)
                }
                Spacer(Modifier.height(4.dp))
                Text(devis.clientNom.ifBlank { "Client à renseigner" }, style = MaterialTheme.typography.bodyLarge)
                if (devis.objet.isNotBlank()) {
                    Text(devis.objet, style = MaterialTheme.typography.bodyMedium, color = Couleurs.gris, maxLines = 2)
                }
                Text(
                    "${Formats.euros(devis.totalTTC)} TTC · ${Formats.date(devis.dateDevis)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            IconButton(onClick = onMenu, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Default.Search, contentDescription = "Actions du devis ${devis.numero}", modifier = Modifier.size(26.dp))
            }
        }
    }
}

@Composable
fun EnTeteRetour(titre: String, onRetour: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onRetour, modifier = Modifier.size(56.dp)) {
            Icon(
                Icons.Default.ArrowBack,
                contentDescription = "Retour",
                tint = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.size(28.dp),
            )
        }
        Text(
            titre,
            color = androidx.compose.ui.graphics.Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
