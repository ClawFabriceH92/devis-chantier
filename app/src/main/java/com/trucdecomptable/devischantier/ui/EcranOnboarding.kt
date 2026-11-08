package com.trucdecomptable.devischantier.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trucdecomptable.devischantier.api.RechercheEntreprises
import com.trucdecomptable.devischantier.data.Catalogues
import com.trucdecomptable.devischantier.data.Dao
import com.trucdecomptable.devischantier.data.Reglages
import com.trucdecomptable.devischantier.util.Formats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Assistant de premier lancement (F-29) : entreprise, métier et catalogue de
 * départ, TVA, assurance, destinataire administratif. Chaque étape peut être
 * passée et reprise plus tard depuis la roue dentée.
 */
@Composable
fun EcranOnboarding(
    contexte: Context,
    dao: Dao,
    reglages: Reglages,
    onTermine: () -> Unit,
) {
    val portee = rememberCoroutineScope()
    var etape by remember { mutableStateOf(0) }
    var metier by remember { mutableStateOf("plombier") }
    var message by remember { mutableStateOf("") }

    val etapes = 4

    fun terminer() {
        reglages.onboardingFait = true
        onTermine()
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text("Devis Chantier", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Quelques écrans pour être prêt à faire votre premier devis.",
                style = MaterialTheme.typography.bodyMedium,
                color = Couleurs.gris,
            )
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { (etape + 1f) / etapes },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            when (etape) {
                0 -> CarteBloc(titre = "1. Votre métier") {
                    Text(
                        "Un catalogue de départ sera installé avec des articles et des prix : " +
                            "vous ajusterez les tarifs au lieu de tout créer.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(10.dp))
                    Catalogues.METIERS.forEach { (id, libelle) ->
                        BoutonSecondaire(
                            (if (metier == id) "✓ " else "") + libelle,
                            onClick = { metier = id },
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                    if (message.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        BlocAlerte(message)
                    }
                }

                1 -> CarteBloc(titre = "2. Votre entreprise") {
                    Text(
                        "Tapez votre nom ou votre SIREN : la fiche est reprise de l'Annuaire des " +
                            "Entreprises de l'État. Vous pourrez tout corriger dans la roue dentée.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    ChampTexte("Dénomination", reglages.entrepriseNom, onValeur = { reglages.entrepriseNom = it })
                    ChampTexte("SIREN", reglages.siren, onValeur = { reglages.siren = it }, clavier = KeyboardType.Number)
                    BoutonSecondaire(
                        "Remplir depuis l'Annuaire",
                        onClick = {
                            portee.launch(Dispatchers.IO) {
                                val requete = reglages.siren.ifBlank { reglages.entrepriseNom }
                                val reponse = RechercheEntreprises.rechercher(requete)
                                withContext(Dispatchers.Main) {
                                    val premier = reponse.getOrNull()?.firstOrNull()
                                    if (premier == null) {
                                        message = "Entreprise non trouvée : complétez à la main."
                                    } else {
                                        reglages.entrepriseNom = premier.nom
                                        reglages.siren = premier.siren
                                        reglages.siret = premier.siret.orEmpty()
                                        reglages.adresseEntreprise = premier.adresse.orEmpty()
                                        reglages.formeJuridique = premier.formeJuridique.orEmpty()
                                        reglages.tvaIntra = premier.tvaIntra.orEmpty()
                                        message = "Fiche reprise : complétez téléphone et email."
                                    }
                                }
                            }
                        },
                    )
                    ChampTexte("Adresse", reglages.adresseEntreprise, onValeur = { reglages.adresseEntreprise = it }, lignes = 2)
                    ChampTexte("Téléphone", reglages.telephone, onValeur = { reglages.telephone = it }, clavier = KeyboardType.Phone)
                    ChampTexte("Email", reglages.email, onValeur = { reglages.email = it })
                    if (message.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        BlocAlerte(message)
                    }
                }

                2 -> CarteBloc(titre = "3. TVA et assurance") {
                    BoutonSecondaire(
                        if (reglages.assujetti) "Assujetti à la TVA" else "Franchise en base de TVA",
                        onClick = {
                            reglages.regimeTva = if (reglages.assujetti) "FRANCHISE" else "ASSUJETTI"
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "L'assurance professionnelle figure sur chaque devis : sans elle, l'envoi reste bloqué.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    ChampTexte("Assureur", reglages.assureurNom, onValeur = { reglages.assureurNom = it })
                    ChampTexte("Coordonnées de l'assureur", reglages.assureurCoordonnees, onValeur = { reglages.assureurCoordonnees = it }, lignes = 2)
                    ChampTexte("Couverture géographique", reglages.couvertureGeo, onValeur = { reglages.couvertureGeo = it })
                    BoutonSecondaire(
                        "Attestation valable un an à partir d'aujourd'hui",
                        onClick = {
                            reglages.attestationExpiration = Formats.plusDeJours(System.currentTimeMillis(), 365)
                        },
                    )
                    Text(
                        "Expiration : ${Formats.date(reglages.attestationExpiration)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                else -> CarteBloc(titre = "4. Qui établit la facture ?") {
                    Text(
                        "Le devis part chez votre comptable ou votre assistante, qui établit la facture. " +
                            "Renseignez son adresse email : elle recevra le PDF et les données du devis.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    ChampTexte(
                        "Email du comptable ou de l'assistant",
                        reglages.comptableEmails,
                        onValeur = { reglages.comptableEmails = it },
                        aide = "Plusieurs adresses séparées par une virgule.",
                    )
                    ChampTexte("Médiateur de la consommation", reglages.mediateur, onValeur = { reglages.mediateur = it })
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Les notifications Android seront demandées au premier envoi de devis " +
                            "(rappel de fin de validité, relance proposée).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Couleurs.gris,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }
        Column(Modifier.padding(16.dp)) {
            if (etape < etapes - 1) {
                BoutonGeant(
                    "Continuer",
                    onClick = {
                        if (etape == 0) {
                            portee.launch(Dispatchers.IO) {
                                val catalogue = Catalogues.lire(contexte, metier)
                                val nombre = catalogue?.let { Catalogues.installer(dao, it) } ?: 0
                                withContext(Dispatchers.Main) {
                                    message = if (nombre > 0) {
                                        "Catalogue ${Catalogues.libelleMetier(metier)} : $nombre articles prêts."
                                    } else {
                                        "Catalogue indisponible, vous pourrez l'ajouter plus tard."
                                    }
                                    etape++
                                }
                            }
                        } else {
                            etape++
                        }
                    },
                )
            } else {
                BoutonGeant("Terminer et faire mon premier devis", onClick = { terminer() })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = { terminer() }) {
                    Text("Passer et compléter plus tard", fontSize = 16.sp, fontWeight = FontWeight.Normal)
                }
            }
        }
    }
}
