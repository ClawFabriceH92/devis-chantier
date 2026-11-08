package com.trucdecomptable.devischantier.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trucdecomptable.devischantier.data.Article
import com.trucdecomptable.devischantier.data.Catalogues
import com.trucdecomptable.devischantier.data.Dao
import com.trucdecomptable.devischantier.data.Famille
import com.trucdecomptable.devischantier.data.Reglages
import com.trucdecomptable.devischantier.export.Exports
import com.trucdecomptable.devischantier.util.Diag
import com.trucdecomptable.devischantier.util.Formats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Le catalogue (§3.3) : familles, articles, favoris, import et export CSV, et
 * les catalogues de départ par métier pour ne pas partir d'une page blanche.
 */
@Composable
fun EcranArticles(
    contexte: Context,
    dao: Dao,
    reglages: Reglages,
) {
    val portee = rememberCoroutineScope()
    val articles by dao.articles().collectAsStateWithLifecycle(initialValue = emptyList())
    val familles by dao.familles().collectAsStateWithLifecycle(initialValue = emptyList())
    var recherche by remember { mutableStateOf("") }
    var enEdition by remember { mutableStateOf<Article?>(null) }
    var catalogueDemande by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var familleDemandee by remember { mutableStateOf(false) }

    val nomsFamilles = familles.associate { it.id to it.nom }
    val visibles = articles.filter {
        recherche.isBlank() ||
            it.libelle.contains(recherche, ignoreCase = true) ||
            it.synonymes.contains(recherche, ignoreCase = true)
    }

    val importateur = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        portee.launch(Dispatchers.IO) {
            val texte = runCatching {
                contexte.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            }.getOrDefault("")
            val lignes = Exports.importerCatalogue(texte)
            val existantes = dao.famillesListe()
            var ajoutes = 0
            for (ligne in lignes) {
                val idFamille = ligne.famille?.let { nom ->
                    existantes.firstOrNull { it.nom.equals(nom, ignoreCase = true) }?.id
                        ?: dao.enregistrerFamille(Famille(nom = nom))
                }
                dao.enregistrerArticle(
                    Article(
                        familleId = idFamille,
                        libelle = ligne.libelle,
                        reference = ligne.reference,
                        unite = ligne.unite,
                        prixHT = ligne.prixHT,
                        tauxTva = ligne.tauxTva,
                        nature = ligne.nature,
                        synonymes = ligne.synonymes,
                    ),
                )
                ajoutes++
            }
            withContext(Dispatchers.Main) {
                message = if (ajoutes == 0) {
                    "Import : aucune ligne lue. Colonnes attendues : famille;libelle;prix_ht;taux_tva;unite"
                } else {
                    "$ajoutes articles importés."
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Articles", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { enEdition = Article(libelle = "") }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Default.Add, contentDescription = "Nouvel article", modifier = Modifier.size(30.dp))
            }
        }
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            ChampTexte("Rechercher un article", recherche, onValeur = { recherche = it })
            if (message.isNotBlank()) {
                BlocAlerte(message)
                Spacer(Modifier.height(8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BoutonSecondaire(
                    "Catalogue de départ",
                    onClick = { catalogueDemande = true },
                    modifier = Modifier.weight(1f),
                )
                BoutonSecondaire(
                    "Famille",
                    onClick = { familleDemandee = true },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BoutonSecondaire(
                    "Importer un CSV",
                    onClick = { importateur.launch(arrayOf("text/*", "text/comma-separated-values", "*/*")) },
                    modifier = Modifier.weight(1f),
                )
                BoutonSecondaire(
                    "Exporter le catalogue",
                    onClick = {
                        portee.launch(Dispatchers.IO) {
                            val dossier = File(contexte.filesDir, "exports").apply { mkdirs() }
                            val fichier = File(dossier, "catalogue-articles.csv")
                            fichier.writeText(Exports.csvCatalogue(articles, nomsFamilles))
                            withContext(Dispatchers.Main) {
                                Partage.partagerFichier(contexte, fichier, "Catalogue articles")
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            if (visibles.isEmpty()) {
                Text(
                    "Catalogue vide. Choisissez un métier dans « Catalogue de départ » pour démarrer avec des prix.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Couleurs.gris,
                )
            }
            LazyColumn(Modifier.weight(1f)) {
                items(visibles) { a ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 68.dp)
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = {
                                portee.launch(Dispatchers.IO) {
                                    dao.enregistrerArticle(a.copy(favori = !a.favori))
                                }
                            },
                            modifier = Modifier.size(56.dp),
                        ) {
                            Icon(
                                if (a.favori) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = if (a.favori) "Retirer des favoris" else "Mettre en favori",
                            )
                        }
                        Column(
                            Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp),
                        ) {
                            Text(a.libelle, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${Formats.euros(a.prixHT)} / ${a.unite} · TVA ${Formats.nombre(a.tauxTva)} %" +
                                    (nomsFamilles[a.familleId]?.let { " · $it" } ?: ""),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Couleurs.gris,
                            )
                        }
                        BoutonSecondaire(
                            "Modifier",
                            onClick = { enEdition = a },
                            modifier = Modifier.width(120.dp),
                        )
                    }
                }
                item { Spacer(Modifier.height(90.dp)) }
            }
        }
    }

    enEdition?.let { article ->
        DialogueArticle(
            article = article,
            familles = familles,
            onEnregistrer = { a ->
                portee.launch(Dispatchers.IO) { dao.enregistrerArticle(a) }
                enEdition = null
            },
            onSupprimer = if (article.id != 0L) {
                {
                    portee.launch(Dispatchers.IO) { dao.supprimerArticle(article.id) }
                    enEdition = null
                }
            } else null,
            onFermer = { enEdition = null },
        )
    }

    if (catalogueDemande) {
        AlertDialog(
            onDismissRequest = { catalogueDemande = false },
            title = { Text("Catalogue de départ", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "Un catalogue prêt par métier : vous ajustez ensuite vos prix.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(10.dp))
                    Catalogues.METIERS.forEach { (id, libelle) ->
                        BoutonSecondaire(
                            "Ajouter le catalogue ${libelle}",
                            onClick = {
                                portee.launch(Dispatchers.IO) {
                                    val catalogue = Catalogues.lire(contexte, id)
                                    val nombre = catalogue?.let { Catalogues.installer(dao, it) } ?: 0
                                    withContext(Dispatchers.Main) {
                                        message = if (nombre > 0) {
                                            "Catalogue $libelle installé : $nombre articles."
                                        } else {
                                            "Catalogue $libelle illisible."
                                        }
                                    }
                                }
                                catalogueDemande = false
                            },
                            modifier = Modifier.padding(vertical = 3.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { catalogueDemande = false }) { Text("Fermer") } },
        )
    }

    if (familleDemandee) {
        var nom by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { familleDemandee = false },
            title = { Text("Nouvelle famille", style = MaterialTheme.typography.titleLarge) },
            text = { ChampTexte("Nom de la famille", nom, onValeur = { nom = it }) },
            confirmButton = {
                BoutonGeant(
                    "Créer la famille",
                    onClick = {
                        portee.launch(Dispatchers.IO) {
                            if (nom.isNotBlank()) dao.enregistrerFamille(Famille(nom = nom))
                        }
                        familleDemandee = false
                    },
                    hauteur = 56.dp,
                )
            },
            dismissButton = { TextButton(onClick = { familleDemandee = false }) { Text("Annuler") } },
        )
    }
}

@Composable
fun DialogueArticle(
    article: Article,
    familles: List<Famille>,
    onEnregistrer: (Article) -> Unit,
    onSupprimer: (() -> Unit)?,
    onFermer: () -> Unit,
) {
    var libelle by remember { mutableStateOf(article.libelle) }
    var reference by remember { mutableStateOf(article.reference.orEmpty()) }
    var unite by remember { mutableStateOf(article.unite) }
    var prix by remember { mutableStateOf(Formats.nombre(article.prixHT)) }
    var tva by remember { mutableStateOf(Formats.nombre(article.tauxTva)) }
    var nature by remember { mutableStateOf(article.nature) }
    var synonymes by remember { mutableStateOf(article.synonymes) }
    var familleId by remember { mutableStateOf(article.familleId) }
    var choixFamille by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text(if (article.id == 0L) "Nouvel article" else "Fiche article", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ChampTexte("Libellé", libelle, onValeur = { libelle = it })
                ChampTexte("Référence", reference, onValeur = { reference = it })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        ChampTexte("Unité (u, m, m², ml, h, forfait)", unite, onValeur = { unite = it })
                    }
                    Column(Modifier.weight(1f)) {
                        ChampTexte("Prix HT", prix, onValeur = { prix = it }, clavier = KeyboardType.Decimal)
                    }
                    Column(Modifier.weight(1f)) {
                        ChampTexte("TVA %", tva, onValeur = { tva = it }, clavier = KeyboardType.Decimal)
                    }
                }
                BoutonSecondaire(
                    "Nature : $nature",
                    onClick = {
                        nature = when (nature) {
                            "fourniture" -> "main-d'oeuvre"
                            "main-d'oeuvre" -> "deplacement"
                            else -> "fourniture"
                        }
                    },
                )
                Spacer(Modifier.height(8.dp))
                BoutonSecondaire(
                    "Famille : ${familles.firstOrNull { it.id == familleId }?.nom ?: "aucune"}",
                    onClick = { choixFamille = true },
                )
                Spacer(Modifier.height(8.dp))
                ChampTexte(
                    "Synonymes pour la dictée",
                    synonymes,
                    onValeur = { synonymes = it },
                    aide = "Séparez par des virgules : « mitigeur, robinet mélangeur »",
                )
                if (choixFamille) {
                    familles.forEach { f ->
                        BoutonSecondaire(
                            f.nom,
                            onClick = { familleId = f.id; choixFamille = false },
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
                if (onSupprimer != null) {
                    Spacer(Modifier.height(8.dp))
                    BoutonSecondaire("Supprimer cet article", onClick = onSupprimer)
                }
            }
        },
        confirmButton = {
            BoutonGeant(
                "Enregistrer",
                onClick = {
                    onEnregistrer(
                        article.copy(
                            libelle = libelle,
                            reference = reference.ifBlank { null },
                            unite = unite.ifBlank { "u" },
                            prixHT = Formats.montant(prix) ?: 0.0,
                            tauxTva = Formats.montant(tva) ?: 20.0,
                            nature = nature,
                            synonymes = synonymes,
                            familleId = familleId,
                        ),
                    )
                },
                hauteur = 56.dp,
            )
        },
        dismissButton = { TextButton(onClick = onFermer) { Text("Annuler") } },
    )
}
