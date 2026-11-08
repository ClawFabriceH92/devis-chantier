package com.trucdecomptable.devischantier.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trucdecomptable.devischantier.data.Achat
import com.trucdecomptable.devischantier.data.Article
import com.trucdecomptable.devischantier.data.Dao
import com.trucdecomptable.devischantier.data.LienAchat
import com.trucdecomptable.devischantier.data.PrixAchat
import com.trucdecomptable.devischantier.data.Reglages
import com.trucdecomptable.devischantier.devis.Calculs
import com.trucdecomptable.devischantier.util.Formats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Les achats et la marge (§3.6) : ce que l'artisan achète, relié à ce qu'il
 * vend. La marge est visible à l'écran seulement, jamais sur le PDF client.
 */
@Composable
fun EcranAchats(
    contexte: Context,
    dao: Dao,
    reglages: Reglages,
    onRetour: () -> Unit,
) {
    val portee = rememberCoroutineScope()
    val achats by dao.achats().collectAsStateWithLifecycle(initialValue = emptyList())
    val articles by dao.articles().collectAsStateWithLifecycle(initialValue = emptyList())
    val liens by dao.tousLesLiens().collectAsStateWithLifecycle(initialValue = emptyList())
    val devis by dao.devis().collectAsStateWithLifecycle(initialValue = emptyList())
    var enEdition by remember { mutableStateOf<Achat?>(null) }
    var lienPour by remember { mutableStateOf<Achat?>(null) }

    val totalAchats = achats.sumOf { it.prixHT }
    val devisAcceptes = devis.filter { it.statut == "ACCEPTE" || it.statut == "TRANSMIS" || it.statut == "FACTURE" }
    val margePrevue = devis.sumOf { it.totalHT - it.coutAchat }
    val margeReelle = devisAcceptes.sumOf { it.totalHT - it.coutAchat }

    Column(Modifier.fillMaxSize()) {
        EnTeteRetour("Achats et marge", onRetour)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            CarteBloc(titre = "Tableau de bord") {
                LigneValeur("Achats enregistrés", "${achats.size} · ${Formats.euros(totalAchats)}")
                LigneValeur("Marge prévue (tous devis)", Formats.euros(margePrevue), gras = true)
                LigneValeur("Marge sur devis acceptés", Formats.euros(margeReelle), gras = true)
                if (achats.isEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Sans prix d'achat, la marge reste à zéro : enregistrez vos tarifs fournisseurs " +
                            "pour voir la marge de chaque devis.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Couleurs.gris,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            CarteBloc(titre = "Mes achats") {
                BoutonGeant("Ajouter un achat", onClick = { enEdition = Achat(libelle = "") })
                Spacer(Modifier.height(10.dp))
                achats.forEach { achat ->
                    val lies = liens.filter { it.achatId == achat.id }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 68.dp)
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(achat.libelle, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${Formats.euros(achat.prixHT)} / ${achat.unite}" +
                                    (achat.fournisseur?.let { " · $it" } ?: "") +
                                    " · ${Formats.date(achat.datePrix)}" +
                                    if (lies.isNotEmpty()) " · lié à ${lies.size} article(s)" else "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Couleurs.gris,
                            )
                        }
                        BoutonSecondaire(
                            "Ouvrir",
                            onClick = { enEdition = achat },
                            modifier = Modifier.heightIn(min = 56.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            CarteBloc(titre = "Comment la marge est calculée") {
                Text(
                    "Marge brute = prix de vente HT − coût d'achat HT.\n" +
                        "Taux de marge = marge ÷ coût d'achat. Taux de marque = marge ÷ prix de vente.\n" +
                        "Coefficient = prix de vente ÷ coût d'achat.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(6.dp))
                val exemple = Calculs.marge(80.0, 50.0)
                Text(
                    "Exemple (50 € d'achat, 80 € de vente) : marge ${Formats.euros(exemple.margeBrute)}, " +
                        "taux de marge ${Formats.pourcent(exemple.tauxMarge)}, taux de marque " +
                        "${Formats.pourcent(exemple.tauxMarque)}, coefficient " +
                        String.format(java.util.Locale.FRANCE, "%.2f", exemple.coefficient) + ".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Couleurs.gris,
                )
            }
            Spacer(Modifier.height(30.dp))
        }
    }

    enEdition?.let { achat ->
        DialogueAchat(
            achat = achat,
            onEnregistrer = { a ->
                portee.launch(Dispatchers.IO) {
                    val id = dao.enregistrerAchat(a)
                    if (a.id != 0L && a.prixHT != achat.prixHT) {
                        dao.enregistrerPrix(PrixAchat(achatId = id, prixHT = a.prixHT))
                    }
                    val lie = a.id != 0L || id != 0L
                    if (lie) lienPour = a.copy(id = id)
                }
                enEdition = null
            },
            onSupprimer = if (achat.id != 0L) {
                {
                    portee.launch(Dispatchers.IO) { dao.supprimerAchat(achat.id) }
                    enEdition = null
                }
            } else null,
            onFermer = { enEdition = null },
        )
    }

    lienPour?.let { achat ->
        DialogueLien(
            achat = achat,
            articles = articles,
            onLier = { article, quantite ->
                portee.launch(Dispatchers.IO) {
                    dao.enregistrerLien(LienAchat(articleId = article.id, achatId = achat.id, quantite = quantite))
                }
                lienPour = null
            },
            onFermer = { lienPour = null },
        )
    }
}

@Composable
private fun DialogueAchat(
    achat: Achat,
    onEnregistrer: (Achat) -> Unit,
    onSupprimer: (() -> Unit)?,
    onFermer: () -> Unit,
) {
    var libelle by remember { mutableStateOf(achat.libelle) }
    var fournisseur by remember { mutableStateOf(achat.fournisseur.orEmpty()) }
    var unite by remember { mutableStateOf(achat.unite) }
    var prix by remember { mutableStateOf(Formats.nombre(achat.prixHT)) }
    var famille by remember { mutableStateOf(achat.famille.orEmpty()) }

    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text(if (achat.id == 0L) "Nouvel achat" else "Achat", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ChampTexte("Produit ou service acheté", libelle, onValeur = { libelle = it })
                ChampTexte("Fournisseur", fournisseur, onValeur = { fournisseur = it })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        ChampTexte("Unité", unite, onValeur = { unite = it })
                    }
                    Column(Modifier.weight(1f)) {
                        ChampTexte("Prix d'achat HT", prix, onValeur = { prix = it }, clavier = KeyboardType.Decimal)
                    }
                }
                ChampTexte("Famille (facultatif)", famille, onValeur = { famille = it })
                if (onSupprimer != null) {
                    Spacer(Modifier.height(10.dp))
                    BoutonSecondaire("Supprimer cet achat", onClick = onSupprimer)
                }
            }
        },
        confirmButton = {
            BoutonGeant(
                "Enregistrer et lier à un article",
                onClick = {
                    onEnregistrer(
                        achat.copy(
                            libelle = libelle,
                            fournisseur = fournisseur.ifBlank { null },
                            unite = unite.ifBlank { "u" },
                            prixHT = Formats.montant(prix) ?: 0.0,
                            famille = famille.ifBlank { null },
                        ),
                    )
                },
                hauteur = 56.dp,
            )
        },
        dismissButton = { TextButton(onClick = onFermer) { Text("Annuler") } },
    )
}

@Composable
private fun DialogueLien(
    achat: Achat,
    articles: List<Article>,
    onLier: (Article, Double) -> Unit,
    onFermer: () -> Unit,
) {
    var recherche by remember { mutableStateOf("") }
    var quantite by remember { mutableStateOf("1") }
    val filtres = articles.filter { it.libelle.contains(recherche, ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text("Lier « ${achat.libelle} » à un article vendu", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                ChampTexte("Quantité d'achat par unité vendue", quantite, onValeur = { quantite = it }, clavier = KeyboardType.Decimal)
                ChampTexte("Rechercher un article", recherche, onValeur = { recherche = it })
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    items(filtres.take(60)) { a ->
                        BoutonSecondaire(
                            "${a.libelle} · ${Formats.euros(a.prixHT)}",
                            onClick = { onLier(a, Formats.montant(quantite) ?: 1.0) },
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onFermer) { Text("Terminer") } },
    )
}
