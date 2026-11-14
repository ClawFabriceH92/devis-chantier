package com.trucdecomptable.devischantier.ui

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trucdecomptable.devischantier.api.PropositionLigne
import com.trucdecomptable.devischantier.data.Dao
import com.trucdecomptable.devischantier.data.Ligne
import com.trucdecomptable.devischantier.data.Reglages
import com.trucdecomptable.devischantier.devis.Calculs
import com.trucdecomptable.devischantier.devis.OperationsDevis
import com.trucdecomptable.devischantier.ia.ModeleLocal
import com.trucdecomptable.devischantier.util.Diag
import com.trucdecomptable.devischantier.util.Formats
import com.trucdecomptable.devischantier.voice.Commande
import com.trucdecomptable.devischantier.voice.MoteurDictee
import com.trucdecomptable.devischantier.voice.ReconnaissanceVocale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * La dictée (§4) : on parle, l'application propose des lignes rapprochées du
 * catalogue, l'artisan valide. Rien n'est ajouté sans validation (V-05).
 */
@Composable
fun EcranDictee(
    contexte: Context,
    dao: Dao,
    reglages: Reglages,
    devisId: Long?,
    onTermine: (Long) -> Unit,
    onRetour: () -> Unit,
) {
    val portee = rememberCoroutineScope()
    val moteur = remember { ReconnaissanceVocale(contexte) }
    var transcription by remember { mutableStateOf("") }
    var ecoute by remember { mutableStateOf(false) }
    var propositions by remember { mutableStateOf<List<PropositionLigne>>(emptyList()) }
    var retenues by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var sectionCourante by remember { mutableStateOf<String?>(null) }
    var remise by remember { mutableStateOf(0.0) }
    var message by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("") }
    var analyse by remember { mutableStateOf(false) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { accordee ->
        if (!accordee) message = "Sans autorisation micro, la dictée est impossible. La saisie manuelle reste disponible."
    }

    DisposableEffect(Unit) { onDispose { moteur.arreter() } }

    Column(Modifier.fillMaxSize()) {
        EnTeteEcran("Devis vocal", sousTitre = "Dictez, vérifiez, ajoutez : rien n'entre dans le devis sans votre accord", onRetour = onRetour)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            CarteBloc {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    val demarrerOuArreter: () -> Unit = {
                        if (ecoute) {
                            moteur.arreter()
                            ecoute = false
                        } else {
                            permission.launch(Manifest.permission.RECORD_AUDIO)
                            transcription = ""
                            propositions = emptyList()
                            retenues = emptySet()
                            message = ""
                            ecoute = true
                            moteur.demarrer(
                                onPartiel = { transcription = it },
                                onFinal = {
                                    transcription = it
                                    ecoute = false
                                    portee.launch(Dispatchers.IO) {
                                        analyse = true
                                        val articles = dao.articlesListe()
                                        val resultat = MoteurDictee.traiter(it, articles, reglages, contexte)
                                        withContext(Dispatchers.Main) {
                                            analyse = false
                                            propositions = resultat.propositions
                                            retenues = resultat.propositions.indices.toSet()
                                            mode = resultat.mode
                                            message = resultat.message
                                            resultat.commandes.forEach { commande ->
                                                when (commande) {
                                                    is Commande.NouvelleSection -> sectionCourante = commande.nom
                                                    is Commande.Remise -> remise = commande.pourcentage
                                                    Commande.SupprimerDerniereLigne -> Unit
                                                }
                                            }
                                        }
                                    }
                                },
                                onErreur = {
                                    ecoute = false
                                    message = it
                                },
                            )
                        }
                    }
                    // Le micro : un seul grand bouton rond, orange pendant l'écoute.
                    Surface(
                        onClick = demarrerOuArreter,
                        shape = CircleShape,
                        color = if (ecoute) Couleurs.orange else MaterialTheme.colorScheme.primary,
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .size(132.dp)
                            .semantics { contentDescription = if (ecoute) "Arrêter la dictée" else "Parler" },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(64.dp))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (ecoute) "Je vous écoute… appuyez pour arrêter" else "Appuyez sur le micro et dictez",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        color = if (ecoute) Couleurs.orange else MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(10.dp))
                    if (transcription.isBlank()) {
                        Text(
                            "Par exemple : « deux mitigeurs lavabo, trois heures de main-d'œuvre, un forfait déplacement ». " +
                                "Vous pouvez aussi dire « nouvelle section salle de bain » ou « remise de 5 % ».",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Couleurs.gris,
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "« $transcription »",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                }
                if (analyse) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (reglages.iaFournisseur == MoteurDictee.Fournisseur.LOCAL && ModeleLocal.pret(contexte)) {
                            "Analyse en cours sur le téléphone (quelques secondes, plus au premier usage)…"
                        } else {
                            "Analyse en cours…"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (message.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    BlocAlerte(message)
                }
            }
            Spacer(Modifier.height(12.dp))

            if (sectionCourante != null || remise > 0) {
                BlocAlerte(
                    listOfNotNull(
                        sectionCourante?.let { "Section appliquée : $it" },
                        if (remise > 0) "Remise de ${Formats.nombre(remise)} %" else null,
                    ).joinToString(" · "),
                )
                Spacer(Modifier.height(12.dp))
            }

            if (propositions.isNotEmpty()) {
                CarteBloc(titre = "Lignes proposées (${propositions.size})") {
                    propositions.forEachIndexed { index, proposition ->
                        val retenue = retenues.contains(index)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 72.dp)
                                .padding(vertical = 4.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.Checkbox(
                                checked = retenue,
                                onCheckedChange = { coche ->
                                    retenues = if (coche) retenues + index else retenues - index
                                },
                                modifier = Modifier.size(48.dp),
                            )
                            Column(
                                Modifier
                                    .weight(1f)
                                    .padding(horizontal = 6.dp),
                            ) {
                                Text(proposition.ligne.designation, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "${Formats.nombre(proposition.ligne.quantite)} " +
                                        "${proposition.ligne.unite ?: "u"} × " +
                                        (proposition.ligne.prixUnitaireHT?.let { Formats.euros(it) } ?: "prix à saisir") +
                                        " · TVA ${Formats.nombre(proposition.ligne.tauxTva ?: 20.0)} %",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Couleurs.gris,
                                )
                                Text(
                                    "${MoteurDictee.confianceTexte(proposition.confiance)}" +
                                        if (proposition.articleId == null) " · article à créer" else " · article du catalogue",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (proposition.confiance >= 0.45) Couleurs.vert else Couleurs.orange,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    BoutonPrincipal(
                        "Ajouter ${retenues.size} ligne(s) au devis",
                        icone = Icons.Default.Check,
                        actif = retenues.isNotEmpty(),
                        onClick = {
                            portee.launch(Dispatchers.IO) {
                                val id = devisId ?: OperationsDevis.nouveau(dao, reglages)
                                val existantes = dao.lignesListe(id)
                                var position = existantes.size
                                propositions.forEachIndexed { index, proposition ->
                                    if (!retenues.contains(index)) return@forEachIndexed
                                    dao.enregistrerLigne(
                                        Ligne(
                                            devisId = id,
                                            section = sectionCourante,
                                            designation = proposition.ligne.designation,
                                            unite = proposition.ligne.unite ?: "u",
                                            quantite = proposition.ligne.quantite,
                                            prixUnitaireHT = proposition.ligne.prixUnitaireHT ?: 0.0,
                                            tauxTva = proposition.ligne.tauxTva ?: if (reglages.assujetti) 20.0 else 0.0,
                                            articleId = proposition.articleId,
                                            position = position++,
                                        ),
                                    )
                                    proposition.articleId?.let { dao.comptabiliserUsage(it) }
                                }
                                val devis = dao.devis(id)
                                if (devis != null) {
                                    val apres = dao.lignesListe(id)
                                    OperationsDevis.enregistrer(
                                        devis.copy(
                                            remiseGlobalePct = if (remise > 0) remise else devis.remiseGlobalePct,
                                        ),
                                        apres,
                                        dao,
                                        franchiseTva = !reglages.assujetti,
                                    )
                                }
                                withContext(Dispatchers.Main) { onTermine(id) }
                            }
                        },
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            if (mode.isNotBlank()) {
                Text(
                    "Mode : $mode · " + when (reglages.iaFournisseur) {
                        MoteurDictee.Fournisseur.LOCAL ->
                            if (ModeleLocal.pret(contexte)) "modèle embarqué, hors ligne" else "modèle embarqué non installé (réglages)"
                        else -> if (reglages.iaCle.isNullOrBlank()) "aucune clé API" else "clé API renseignée"
                    } + " · ${reglages.iaDictees} dictée(s) assistée(s)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Couleurs.gris,
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
