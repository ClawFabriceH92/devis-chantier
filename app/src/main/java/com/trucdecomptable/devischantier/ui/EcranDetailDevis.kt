package com.trucdecomptable.devischantier.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trucdecomptable.devischantier.data.Client
import com.trucdecomptable.devischantier.data.Dao
import com.trucdecomptable.devischantier.data.Devis
import com.trucdecomptable.devischantier.data.Evenement
import com.trucdecomptable.devischantier.data.Reglages
import com.trucdecomptable.devischantier.data.Statut
import com.trucdecomptable.devischantier.devis.Calculs
import com.trucdecomptable.devischantier.devis.Mentions
import com.trucdecomptable.devischantier.devis.OperationsDevis
import com.trucdecomptable.devischantier.util.Diag
import com.trucdecomptable.devischantier.util.Formats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Le devis vu de haut : statut, montants, journal des envois, et les actions du
 * cycle de vie (envoi au client, transmission au comptable, signature, versions).
 */
@Composable
fun EcranDetailDevis(
    contexte: Context,
    dao: Dao,
    reglages: Reglages,
    devisId: Long,
    clients: List<Client>,
    relancerDemande: Boolean = false,
    onRetour: () -> Unit,
    onModifier: () -> Unit,
    onSupprime: () -> Unit,
) {
    val portee = rememberCoroutineScope()
    val devis by remember(devisId) { dao.devisFlux(devisId) }.collectAsStateWithLifecycle(initialValue = null)
    val lignes by remember(devisId) { dao.lignes(devisId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val journal by remember(devisId) { dao.journal(devisId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    var message by remember { mutableStateOf("") }
    var manquantes by remember { mutableStateOf<List<String>>(emptyList()) }
    var signatureDemandee by remember { mutableStateOf(false) }
    var confirmationSuppression by remember { mutableStateOf(false) }
    var relanceFaite by remember { mutableStateOf(false) }

    val courant = devis
    if (courant == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Devis introuvable") }
        return
    }
    val client = clients.firstOrNull { it.id == courant.clientId }
    val totaux = Calculs.totaux(lignes, courant.remiseGlobalePct)

    fun preparerEtEnvoyer(destinataires: List<String>, sujet: String, corps: String) {
        portee.launch(Dispatchers.IO) {
            val preparation = OperationsDevis.preparer(contexte, courant, lignes, client, reglages)
            withContext(Dispatchers.Main) {
                if (!preparation.ok) {
                    manquantes = preparation.manquantes
                    message = preparation.message
                    return@withContext
                }
                val fichiers = listOfNotNull(preparation.pdf, preparation.donnees)
                Partage.envoyer(contexte, fichiers, destinataires, sujet, corps)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        EnTeteRetour("Devis ${courant.numero}", onRetour)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            CarteBloc {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    BadgeStatut(courant.statut)
                    Text(
                        "indice ${courant.indice}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Couleurs.gris,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(courant.clientNom.ifBlank { "Client à renseigner" }, style = MaterialTheme.typography.titleMedium)
                if (courant.objet.isNotBlank()) {
                    Text(courant.objet, style = MaterialTheme.typography.bodyMedium, color = Couleurs.gris)
                }
                Spacer(Modifier.height(8.dp))
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
                LigneValeur("Date", Formats.date(courant.dateDevis))
                LigneValeur("Valable jusqu'au", Formats.date(courant.dateLimite))
                if (courant.dateEnvoi != null) LigneValeur("Envoyé le", Formats.dateHeure(courant.dateEnvoi))
                if (courant.dateAcceptation != null) LigneValeur("Accepté le", Formats.dateHeure(courant.dateAcceptation))
                if (courant.dateTransmission != null) LigneValeur("Transmis le", Formats.dateHeure(courant.dateTransmission))
            }
            Spacer(Modifier.height(12.dp))

            if (message.isNotBlank()) {
                BlocAlerte(message)
                Spacer(Modifier.height(8.dp))
            }
            if (manquantes.isNotEmpty()) {
                CarteBloc(titre = "Mentions obligatoires manquantes") {
                    manquantes.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "L'envoi est bloqué tant que ces mentions manquent : elles se règlent dans la roue dentée.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Couleurs.gris,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            CarteBloc(titre = "Actions") {
                BoutonGeant(
                    "Aperçu du PDF",
                    onClick = {
                        portee.launch(Dispatchers.IO) {
                            val preparation = OperationsDevis.preparer(contexte, courant, lignes, client, reglages)
                            withContext(Dispatchers.Main) {
                                if (preparation.ok && preparation.pdf != null) {
                                    Partage.ouvrirPdf(contexte, preparation.pdf)
                                } else {
                                    manquantes = preparation.manquantes
                                    message = if (preparation.manquantes.isEmpty()) {
                                        "Aperçu impossible : ${preparation.message}"
                                    } else {
                                        "Mentions obligatoires incomplètes"
                                    }
                                }
                            }
                        }
                    },
                )
                Spacer(Modifier.height(10.dp))
                BoutonGeant(
                    "Envoyer au client",
                    icone = Icons.Default.Share,
                    couleur = Couleurs.bleuClair,
                    onClick = {
                        val objet = Partage.modele(
                            reglages.modeleClientObjet, courant.numero, courant.clientNom, courant.objet,
                            Formats.euros(totaux.totalTTC), Formats.euros(totaux.totalHT),
                            Formats.date(courant.dateLimite), reglages.entrepriseNom, Formats.date(courant.dateDevis),
                        )
                        val corps = Partage.modele(
                            reglages.modeleClientCorps, courant.numero, courant.clientNom, courant.objet,
                            Formats.euros(totaux.totalTTC), Formats.euros(totaux.totalHT),
                            Formats.date(courant.dateLimite), reglages.entrepriseNom, Formats.date(courant.dateDevis),
                        )
                        preparerEtEnvoyer(listOfNotNull(client?.email), objet, corps)
                        portee.launch(Dispatchers.IO) {
                            OperationsDevis.marquerEnvoye(dao, courant, client, lignes, reglages)
                        }
                    },
                )
                Spacer(Modifier.height(10.dp))
                BoutonSecondaire(
                    "Faire signer sur place (bon pour accord)",
                    onClick = { signatureDemandee = true },
                    icone = Icons.Default.Edit,
                )
                Spacer(Modifier.height(10.dp))
                BoutonSecondaire(
                    "Transmettre au comptable",
                    onClick = {
                        val destinataires = reglages.destinatairesComptable() +
                            if (reglages.copieArtisan && reglages.email.isNotBlank()) listOf(reglages.email) else emptyList()
                        if (destinataires.isEmpty()) {
                            message = "Aucun destinataire administratif : renseignez l'email du comptable dans les réglages."
                        } else {
                            val objet = Partage.modele(
                                reglages.modeleComptableObjet, courant.numero, courant.clientNom, courant.objet,
                                Formats.euros(totaux.totalTTC), Formats.euros(totaux.totalHT),
                                Formats.date(courant.dateLimite), reglages.entrepriseNom, Formats.date(courant.dateDevis),
                            )
                            val corps = Partage.modele(
                                reglages.modeleComptableCorps, courant.numero, courant.clientNom, courant.objet,
                                Formats.euros(totaux.totalTTC), Formats.euros(totaux.totalHT),
                                Formats.date(courant.dateLimite), reglages.entrepriseNom, Formats.date(courant.dateDevis),
                            )
                            preparerEtEnvoyer(destinataires, objet, corps)
                            portee.launch(Dispatchers.IO) {
                                OperationsDevis.marquerTransmis(dao, courant, destinataires.joinToString(", "))
                            }
                        }
                    },
                )
                Spacer(Modifier.height(10.dp))
                if (courant.statut == Statut.ENVOYE || courant.statut == Statut.EXPIRE) {
                    BoutonSecondaire(
                        if (relanceFaite) "Relance envoyée" else "Relancer le client",
                        onClick = {
                            val objet = "Relance devis ${courant.numero}"
                            val corps = "Bonjour,\n\nJe me permets de revenir vers vous au sujet du devis " +
                                "${courant.numero} (${Formats.euros(totaux.totalTTC)} TTC), valable jusqu'au " +
                                "${Formats.date(courant.dateLimite)}.\n\nBien cordialement,\n${reglages.entrepriseNom}"
                            Partage.envoyerTexte(contexte, client?.email.orEmpty(), objet, corps)
                            portee.launch(Dispatchers.IO) {
                                OperationsDevis.marquerRelance(dao, courant, "email")
                            }
                            relanceFaite = true
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                }
                if (courant.statut != Statut.BROUILLON && courant.statut != Statut.FACTURE) {
                    BoutonSecondaire(
                        "Marquer facturé",
                        onClick = {
                            portee.launch(Dispatchers.IO) { OperationsDevis.marquerFacture(dao, courant) }
                            message = "Devis marqué facturé."
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                }
                if (courant.statut != Statut.BROUILLON) {
                    BoutonSecondaire(
                        "Créer une nouvelle version",
                        onClick = {
                            portee.launch(Dispatchers.IO) {
                                OperationsDevis.nouvelleVersion(dao, courant.id)
                            }
                            message = "Nouvelle version créée en brouillon (indice suivant)."
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                }
                if (courant.statut == Statut.BROUILLON) {
                    BoutonSecondaire(
                        "Modifier le brouillon",
                        onClick = onModifier,
                    )
                    Spacer(Modifier.height(10.dp))
                    BoutonSecondaire(
                        "Supprimer le brouillon",
                        onClick = { confirmationSuppression = true },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            CarteBloc(titre = "Journal des envois") {
                if (journal.isEmpty()) {
                    Text("Aucun envoi pour l'instant.", style = MaterialTheme.typography.bodyMedium, color = Couleurs.gris)
                }
                journal.forEach { e ->
                    LigneValeur(
                        "${Formats.dateHeure(e.date)} · ${e.type.lowercase().replace('_', ' ')}",
                        e.detail.ifBlank { "—" },
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    if (signatureDemandee) {
        DialogueSignature(
            onValider = { bitmap ->
                val fichier = File(File(contexte.filesDir, "signatures").apply { mkdirs() }, "signature-${courant.numero}.png")
                runCatching {
                    FileOutputStream(fichier).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
                portee.launch(Dispatchers.IO) {
                    OperationsDevis.marquerAccepte(dao, courant, fichier.absolutePath)
                }
                message = "Bon pour accord enregistré le ${Formats.date(System.currentTimeMillis())}."
                signatureDemandee = false
            },
            onFermer = { signatureDemandee = false },
        )
    }

    if (confirmationSuppression) {
        DialogueConfirmation(
            titre = "Supprimer ce brouillon ?",
            texte = "Le devis ${courant.numero} sera supprimé définitivement.",
            libelleConfirmer = "Supprimer",
            onConfirmer = {
                portee.launch(Dispatchers.IO) {
                    dao.supprimerLignes(courant.id)
                    dao.supprimerDevis(courant.id)
                    withContext(Dispatchers.Main) { onSupprime() }
                }
            },
            onAnnuler = { confirmationSuppression = false },
        )
    }
}

/** Signature au doigt, sur l'écran (E-02). */
@Composable
fun DialogueSignature(onValider: (Bitmap) -> Unit, onFermer: () -> Unit) {
    val traits = remember { mutableListOf<List<Offset>>() }
    var traitCourant by remember { mutableStateOf(listOf<Offset>()) }
    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text("Signature du client", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                Text("Le client signe avec le doigt dans le cadre.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                ) {
                    Canvas(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { traitCourant = listOf(it) },
                                    onDragEnd = {
                                        if (traitCourant.isNotEmpty()) traits.add(traitCourant)
                                        traitCourant = emptyList()
                                    },
                                ) { change, _ ->
                                    traitCourant = traitCourant + change.position
                                }
                            },
                    ) {
                        drawRect(Color.White)
                        drawRect(Color.LightGray, style = Stroke(width = 2f))
                        traits.forEach { trait ->
                            for (i in 1 until trait.size) {
                                drawLine(Color.Black, trait[i - 1], trait[i], strokeWidth = 4f)
                            }
                        }
                        for (i in 1 until traitCourant.size) {
                            drawLine(Color.Black, traitCourant[i - 1], traitCourant[i], strokeWidth = 4f)
                        }
                    }
                }
            }
        },
        confirmButton = {
            BoutonGeant(
                "Valider la signature",
                onClick = {
                    val tous = traits + listOfNotNull(traitCourant.takeIf { it.isNotEmpty() })
                    onValider(rendreBitmap(tous))
                },
                hauteur = 56.dp,
            )
        },
        dismissButton = { TextButton(onClick = onFermer) { Text("Annuler", fontSize = 17.sp) } },
    )
}

private fun rendreBitmap(traits: List<List<Offset>>): Bitmap {
    val largeur = 600
    val hauteur = 300
    val bitmap = Bitmap.createBitmap(largeur, hauteur, Bitmap.Config.ARGB_8888)
    val toile = android.graphics.Canvas(bitmap)
    toile.drawColor(android.graphics.Color.WHITE)
    val pinceau = android.graphics.Paint().apply {
        color = android.graphics.Color.BLACK
        strokeWidth = 6f
        isAntiAlias = true
        style = android.graphics.Paint.Style.STROKE
        strokeCap = android.graphics.Paint.Cap.ROUND
    }
    traits.forEach { trait ->
        val chemin = android.graphics.Path()
        trait.forEachIndexed { index, point ->
            if (index == 0) chemin.moveTo(point.x, point.y) else chemin.lineTo(point.x, point.y)
        }
        toile.drawPath(chemin, pinceau)
    }
    return bitmap
}
