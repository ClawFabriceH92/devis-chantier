package com.trucdecomptable.devischantier.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint as PaintAndroid
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trucdecomptable.devischantier.data.Article
import com.trucdecomptable.devischantier.data.Client
import com.trucdecomptable.devischantier.data.Dao
import com.trucdecomptable.devischantier.data.Devis
import com.trucdecomptable.devischantier.data.Ligne
import com.trucdecomptable.devischantier.data.Reglages
import com.trucdecomptable.devischantier.data.Statut
import com.trucdecomptable.devischantier.devis.Calculs
import com.trucdecomptable.devischantier.devis.OperationsDevis
import com.trucdecomptable.devischantier.util.Diag
import com.trucdecomptable.devischantier.util.Formats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Création et modification d'un devis (§3.2). Le devis envoyé est verrouillé :
 * seule une nouvelle version repart en brouillon (F-15).
 */
@Composable
fun EcranDevis(
    contexte: Context,
    dao: Dao,
    reglages: Reglages,
    devisId: Long,
    clients: List<Client>,
    articles: List<Article>,
    onRetour: () -> Unit,
    onDictee: () -> Unit,
    onDetail: () -> Unit,
) {
    val portee = rememberCoroutineScope()
    val devis by remember(devisId) { dao.devisFlux(devisId) }.collectAsStateWithLifecycle(initialValue = null)
    val lignes by remember(devisId) { dao.lignes(devisId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    var ligneEnEdition by remember { mutableStateOf<Ligne?>(null) }
    var nouvelleLigne by remember { mutableStateOf(false) }
    var choixClient by remember { mutableStateOf(false) }
    var margeVisible by remember { mutableStateOf(false) }

    val courant = devis
    if (courant == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Devis introuvable") }
        return
    }
    val verrouille = Statut.verrouille(courant.statut)
    val totaux = Calculs.totaux(lignes, courant.remiseGlobalePct)
    val client = clients.firstOrNull { it.id == courant.clientId }

    val franchise = !reglages.assujetti
    // Les champs écrivent à chaque frappe : les écritures sont sérialisées et
    // partent de l'état en base (pas d'un instantané périmé), puis les totaux
    // sont recalculés (acompte, remise).
    val verrou = remember { Mutex() }
    fun maj(transformation: (Devis) -> Devis) {
        portee.launch(Dispatchers.IO) {
            verrou.withLock {
                val actuel = dao.devis(devisId) ?: return@withLock
                OperationsDevis.enregistrer(transformation(actuel), dao.lignesListe(devisId), dao, franchise)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        EnTeteRetour(
            titre = "${courant.numero}${if (courant.indice != "A") " (${courant.indice})" else ""}" +
                if (verrouille) " · verrouillé" else "",
            onRetour = onRetour,
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            if (verrouille) {
                BlocAlerte(
                    "Devis ${Statut.libelle(courant.statut).lowercase()} : il n'est plus modifiable. " +
                        "Toute correction crée une nouvelle version (B, C…).",
                )
                Spacer(Modifier.height(12.dp))
            }

            CarteBloc(titre = "Client et objet") {
                BoutonSecondaire(
                    texte = courant.clientNom.ifBlank { "Choisir un client" },
                    onClick = { if (!verrouille) choixClient = true },
                    actif = !verrouille,
                )
                Spacer(Modifier.height(8.dp))
                ChampTexte(
                    "Objet du devis",
                    courant.objet,
                    onValeur = { objet -> maj { it.copy(objet = objet) } },
                )
                ChampTexte(
                    "Adresse du chantier (si différente)",
                    courant.adresseChantier.orEmpty(),
                    onValeur = { adresse -> maj { it.copy(adresseChantier = adresse.ifBlank { null }) } },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        ChampTexte(
                            "Validité (jours)",
                            courant.validiteJours.toString(),
                            onValeur = { jours -> maj { it.copy(validiteJours = jours.toIntOrNull() ?: 0) } },
                            clavier = KeyboardType.Number,
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        ChampTexte(
                            "Acompte (%)",
                            Formats.nombre(courant.acomptePct),
                            onValeur = { pct -> maj { it.copy(acomptePct = Formats.montant(pct) ?: 0.0) } },
                            clavier = KeyboardType.Decimal,
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        ChampTexte(
                            "Remise (%)",
                            Formats.nombre(courant.remiseGlobalePct),
                            onValeur = { pct -> maj { it.copy(remiseGlobalePct = Formats.montant(pct) ?: 0.0) } },
                            clavier = KeyboardType.Decimal,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = courant.signeChezClient,
                        onCheckedChange = { coche -> maj { it.copy(signeChezClient = coche) } },
                        modifier = Modifier.size(48.dp),
                    )
                    Text("Signé chez le client (rétractation 14 jours)", style = MaterialTheme.typography.bodyMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = courant.mentionTauxReduit,
                        onCheckedChange = { coche -> maj { it.copy(mentionTauxReduit = coche) } },
                        modifier = Modifier.size(48.dp),
                    )
                    Text("Travaux à TVA réduite (mention à signer)", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(12.dp))

            CarteBloc(titre = "Lignes du devis") {
                if (lignes.isEmpty()) {
                    Text(
                        "Aucune ligne. Ajoutez à la main ou dictez : « deux mitigeurs lavabo, trois heures de main-d'œuvre ».",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Couleurs.gris,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                var sectionAffichee: String? = null
                lignes.forEach { ligne ->
                    val section = ligne.section?.takeIf { it.isNotBlank() }
                    if (section != null && section != sectionAffichee) {
                        sectionAffichee = section
                        Text(
                            section.uppercase(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Couleurs.orange.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                .padding(8.dp),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    val totalLigne = Calculs.ligne(ligne, courant.remiseGlobalePct)
                    Card(
                        onClick = { if (!verrouille) ligneEnEdition = ligne },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 72.dp)
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (Calculs.sousLeCout(ligne)) {
                                Couleurs.rouge.copy(alpha = 0.12f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                        ),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(ligne.designation, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "${Formats.nombre(ligne.quantite)} ${ligne.unite} × " +
                                        "${Formats.euros(ligne.prixUnitaireHT)} · TVA ${Formats.nombre(ligne.tauxTva)} %",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Couleurs.gris,
                                )
                            }
                            Text(
                                Formats.euros(totalLigne.ht),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    if (margeVisible && ligne.coutAchatUnitaire > 0) {
                        val marge = Calculs.marge(totalLigne.ht, ligne.quantite * ligne.coutAchatUnitaire)
                        Text(
                            "coût ${Formats.euros(ligne.quantite * ligne.coutAchatUnitaire)} · " +
                                "marge ${Formats.euros(marge.margeBrute)} (${Formats.pourcent(marge.tauxMarge)})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Couleurs.gris,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (!verrouille) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BoutonSecondaire(
                            "Ajouter une ligne",
                            onClick = { nouvelleLigne = true },
                            icone = Icons.Default.Add,
                            modifier = Modifier.weight(1f),
                        )
                        BoutonSecondaire(
                            "Dicter",
                            onClick = onDictee,
                            icone = Icons.Default.Mic,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            CarteBloc(titre = "Totaux") {
                LigneValeur("Total HT", Formats.euros(totaux.totalHT))
                totaux.tvaParTaux.forEach { (taux, montant) ->
                    LigneValeur("TVA ${Formats.nombre(taux)} %", Formats.euros(montant))
                }
                LigneValeur("Total TTC", Formats.euros(totaux.totalTTC), gras = true)
                if (courant.acomptePct > 0) {
                    LigneValeur(
                        "Acompte ${Formats.nombre(courant.acomptePct)} %",
                        Formats.euros(Calculs.montantAcompte(totaux.totalTTC, courant.acomptePct)),
                    )
                }
                Spacer(Modifier.height(8.dp))
                BoutonSecondaire(
                    if (margeVisible) "Masquer la marge" else "Afficher la marge (interne)",
                    onClick = { margeVisible = !margeVisible },
                )
                if (margeVisible) {
                    Spacer(Modifier.height(6.dp))
                    if (totaux.coutAchat > 0) {
                        val marge = Calculs.marge(totaux.totalHT, totaux.coutAchat)
                        LigneValeur("Coût d'achat", Formats.euros(totaux.coutAchat))
                        LigneValeur("Marge brute", Formats.euros(marge.margeBrute), gras = true)
                        LigneValeur("Taux de marge", Formats.pourcent(marge.tauxMarge))
                        LigneValeur("Taux de marque", Formats.pourcent(marge.tauxMarque))
                        LigneValeur("Coefficient", String.format(java.util.Locale.FRANCE, "%.2f", marge.coefficient))
                        if (marge.tauxMarque < reglages.seuilMarge) {
                            Spacer(Modifier.height(6.dp))
                            BlocAlerte(
                                "Sous le seuil de marge paramétré (${Formats.nombre(reglages.seuilMarge)} %).",
                            )
                        }
                    } else {
                        Text(
                            "Aucun achat lié aux lignes : renseignez les prix d'achat dans l'onglet Marges.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Couleurs.gris,
                        )
                    }
                }
            }
            Spacer(Modifier.height(90.dp))
        }

        Column(Modifier.padding(12.dp)) {
            BoutonGeant(
                texte = if (verrouille) "Voir le devis et l'envoyer" else "Enregistrer et prévisualiser",
                onClick = {
                    portee.launch(Dispatchers.IO) {
                        verrou.withLock {
                            OperationsDevis.enregistrer(dao.devis(devisId) ?: courant, dao.lignesListe(devisId), dao, franchise)
                        }
                        withContext(Dispatchers.Main) { onDetail() }
                    }
                },
            )
        }
    }

    if (choixClient) {
        DialogueChoixClient(
            clients = clients,
            onChoisir = { c ->
                maj {
                    it.copy(
                        clientId = c.id,
                        clientNom = c.nom,
                        clientAdresse = c.adresse,
                        clientSiren = c.siren,
                    )
                }
                choixClient = false
            },
            onFermer = { choixClient = false },
        )
    }

    if (nouvelleLigne || ligneEnEdition != null) {
        DialogueLigne(
            ligne = ligneEnEdition ?: Ligne(devisId = devisId, position = lignes.size, tauxTva = if (franchise) 0.0 else 20.0),
            articles = articles,
            franchiseTva = franchise,
            onEnregistrer = { l ->
                portee.launch(Dispatchers.IO) {
                    val article = l.articleId?.let { id -> articles.firstOrNull { it.id == id } }
                    dao.enregistrerLigne(
                        l.copy(
                            devisId = devisId,
                            coutAchatUnitaire = article?.let { a ->
                                Calculs.coutArticle(
                                    dao.liens(a.id).map { it.achatId to it.quantite },
                                    dao.achatsListe().associate { it.id to it.prixHT },
                                )
                            } ?: l.coutAchatUnitaire,
                        ),
                    )
                    article?.let { dao.comptabiliserUsage(it.id) }
                    val apres = dao.lignesListe(devisId)
                    OperationsDevis.enregistrer(dao.devis(devisId) ?: courant, apres, dao, franchise)
                }
                ligneEnEdition = null
                nouvelleLigne = false
            },
            onSupprimer = ligneEnEdition?.let { l ->
                {
                    portee.launch(Dispatchers.IO) {
                        dao.supprimerLigne(l.id)
                        val apres = dao.lignesListe(devisId)
                        OperationsDevis.enregistrer(dao.devis(devisId) ?: courant, apres, dao, franchise)
                    }
                    ligneEnEdition = null
                }
            },
            onFermer = {
                ligneEnEdition = null
                nouvelleLigne = false
            },
        )
    }
}

@Composable
fun DialogueChoixClient(clients: List<Client>, onChoisir: (Client) -> Unit, onFermer: () -> Unit) {
    var recherche by remember { mutableStateOf("") }
    val filtres = clients.filter { it.nom.contains(recherche, ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text("Choisir le client", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(Modifier.heightIn(max = 420.dp)) {
                ChampTexte("Rechercher", recherche, onValeur = { recherche = it })
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(filtres) { c ->
                        BoutonSecondaire(
                            texte = c.nom + if (c.professionnel) " · pro" else "",
                            onClick = { onChoisir(c) },
                            modifier = Modifier.padding(vertical = 3.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onFermer) { Text("Fermer") } },
    )
}

/** Saisie d'une ligne : grand pavé numérique, article du catalogue ou ligne libre (F-11). */
@Composable
fun DialogueLigne(
    ligne: Ligne,
    articles: List<Article>,
    onEnregistrer: (Ligne) -> Unit,
    onSupprimer: (() -> Unit)?,
    onFermer: () -> Unit,
    franchiseTva: Boolean = false,
) {
    var designation by remember { mutableStateOf(ligne.designation) }
    var quantite by remember { mutableStateOf(Formats.nombre(ligne.quantite)) }
    var unite by remember { mutableStateOf(ligne.unite) }
    var prix by remember { mutableStateOf(Formats.nombre(ligne.prixUnitaireHT)) }
    var tva by remember { mutableStateOf(Formats.nombre(ligne.tauxTva)) }
    var remise by remember { mutableStateOf(Formats.nombre(ligne.remisePct)) }
    var section by remember { mutableStateOf(ligne.section.orEmpty()) }
    var articleId by remember { mutableStateOf(ligne.articleId) }
    var choixArticle by remember { mutableStateOf(false) }
    var recherche by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text(if (ligne.id == 0L) "Nouvelle ligne" else "Modifier la ligne", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ChampTexte("Désignation", designation, onValeur = { designation = it })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        ChampTexte("Quantité", quantite, onValeur = { quantite = it }, clavier = KeyboardType.Decimal)
                    }
                    Column(Modifier.weight(1f)) {
                        ChampTexte("Unité", unite, onValeur = { unite = it })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        ChampTexte("Prix unitaire HT", prix, onValeur = { prix = it }, clavier = KeyboardType.Decimal)
                    }
                    Column(Modifier.weight(1f)) {
                        if (franchiseTva) {
                            Text("TVA : non applicable (franchise en base)", style = MaterialTheme.typography.bodyMedium, color = Couleurs.gris)
                        } else {
                            ChampTexte("TVA %", tva, onValeur = { tva = it }, clavier = KeyboardType.Decimal)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        ChampTexte("Remise %", remise, onValeur = { remise = it }, clavier = KeyboardType.Decimal)
                    }
                    Column(Modifier.weight(1f)) {
                        ChampTexte("Section", section, onValeur = { section = it })
                    }
                }
                BoutonSecondaire("Choisir dans le catalogue", onClick = { choixArticle = true })
                if (choixArticle) {
                    ChampTexte("Rechercher un article", recherche, onValeur = { recherche = it })
                    LazyColumn(Modifier.heightIn(max = 260.dp)) {
                        items(
                            articles.filter {
                                recherche.isBlank() ||
                                    it.libelle.contains(recherche, ignoreCase = true) ||
                                    it.synonymes.contains(recherche, ignoreCase = true)
                            }.take(60),
                        ) { a ->
                            BoutonSecondaire(
                                texte = "${a.libelle} · ${Formats.euros(a.prixHT)} / ${a.unite}",
                                onClick = {
                                    articleId = a.id
                                    designation = a.libelle
                                    unite = a.unite
                                    prix = Formats.nombre(a.prixHT)
                                    tva = if (franchiseTva) "0" else Formats.nombre(a.tauxTva)
                                    choixArticle = false
                                },
                                modifier = Modifier.padding(vertical = 2.dp),
                            )
                        }
                    }
                }
                if (onSupprimer != null) {
                    Spacer(Modifier.height(10.dp))
                    BoutonSecondaire("Supprimer cette ligne", onClick = onSupprimer, icone = Icons.Default.Delete)
                }
            }
        },
        confirmButton = {
            BoutonGeant(
                "Valider la ligne",
                onClick = {
                    onEnregistrer(
                        ligne.copy(
                            designation = designation,
                            quantite = Formats.montant(quantite) ?: 1.0,
                            unite = unite.ifBlank { "u" },
                            prixUnitaireHT = Formats.montant(prix) ?: 0.0,
                            tauxTva = if (franchiseTva) 0.0 else Formats.montant(tva) ?: 20.0,
                            remisePct = Formats.montant(remise) ?: 0.0,
                            section = section.ifBlank { null },
                            articleId = articleId,
                        ),
                    )
                },
                hauteur = 56.dp,
            )
        },
        dismissButton = { TextButton(onClick = onFermer) { Text("Annuler", fontSize = 17.sp) } },
    )
}
