package com.trucdecomptable.devischantier.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trucdecomptable.devischantier.BuildConfig
import com.trucdecomptable.devischantier.api.RechercheEntreprises
import com.trucdecomptable.devischantier.data.Dao
import com.trucdecomptable.devischantier.data.Declencheur
import com.trucdecomptable.devischantier.data.Devis
import com.trucdecomptable.devischantier.data.Reglages
import com.trucdecomptable.devischantier.devis.Mentions
import com.trucdecomptable.devischantier.devis.OperationsDevis
import com.trucdecomptable.devischantier.ia.ModeleLocal
import com.trucdecomptable.devischantier.sauvegarde.Sauvegarde
import com.trucdecomptable.devischantier.update.UpdateManager
import com.trucdecomptable.devischantier.util.Diag
import com.trucdecomptable.devischantier.util.Formats
import com.trucdecomptable.devischantier.voice.MoteurDictee
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * La roue dentée (§3.4), en deux niveaux : un sommaire où chaque rubrique dit
 * son état en une ligne (ce qui manque pour qu'un devis parte est en orange),
 * puis la rubrique elle-même. Chaque étape peut être laissée de côté et
 * reprise : l'assistant de premier lancement affiche les mêmes blocs.
 */
@Composable
fun EcranReglages(
    contexte: Context,
    dao: Dao,
    reglages: Reglages,
    onRetour: () -> Unit,
    onReinitialiser: () -> Unit = {},
) {
    val portee = rememberCoroutineScope()
    var section by rememberSaveable { mutableStateOf<String?>(null) }
    var version by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf("") }
    var rechercheEntreprise by remember { mutableStateOf("") }
    var resultats by remember { mutableStateOf<List<com.trucdecomptable.devischantier.api.EntrepriseResultat>>(emptyList()) }
    var motDePasse by remember { mutableStateOf("") }
    var majEtat by remember { mutableStateOf("") }
    var confirmationReinitialisation by remember { mutableStateOf(false) }

    // Les réglages vivent dans SharedPreferences, qui ne notifie pas Compose :
    // chaque modification incrémente `version`, lue ci-dessous, pour que
    // l'écran se redessine avec les valeurs à jour.
    fun rafraichir() {
        version++
    }

    @Suppress("UNUSED_VARIABLE")
    val revision = version // lecture volontaire : voir rafraichir()

    val etatMaj by UpdateManager.state.collectAsStateWithLifecycle()

    BackHandler(enabled = section != null) { section = null; message = "" }

    val choixLogo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val fichier = File(contexte.filesDir, "logo.png")
            runCatching {
                contexte.contentResolver.openInputStream(uri)?.use { entree ->
                    fichier.outputStream().use { sortie -> entree.copyTo(sortie) }
                }
                reglages.logoChemin = fichier.absolutePath
                message = "Logo enregistré."
            }.onFailure { message = "Logo illisible : ${it.message}" }
            rafraichir()
        }
    }

    val choixAttestation = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val dossier = File(contexte.filesDir, "attestations").apply { mkdirs() }
            val fichier = File(dossier, "attestation-assurance.pdf")
            runCatching {
                contexte.contentResolver.openInputStream(uri)?.use { entree ->
                    fichier.outputStream().use { sortie -> entree.copyTo(sortie) }
                }
                reglages.attestationChemin = fichier.absolutePath
                message = "Attestation enregistrée."
            }.onFailure { message = "Fichier illisible : ${it.message}" }
            rafraichir()
        }
    }

    val choixDossier = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                contexte.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            reglages.sauvegardeDossier = uri.toString()
            message = "Dossier de sauvegarde choisi."
            rafraichir()
        }
    }

    val choixRestauration = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null && motDePasse.isNotBlank()) {
            portee.launch(Dispatchers.IO) {
                val octets = runCatching {
                    contexte.contentResolver.openInputStream(uri)?.readBytes()
                }.getOrNull()
                val resultat = if (octets == null) {
                    Sauvegarde.Resultat(false, "Fichier illisible.")
                } else {
                    Sauvegarde.restaurer(contexte, octets, motDePasse)
                }
                withContext(Dispatchers.Main) { message = resultat.message }
                if (resultat.ok) {
                    // Les écrans tiennent encore l'ancienne base : on relance
                    // l'application proprement plutôt que de la laisser planter.
                    delay(1500)
                    val relance = contexte.packageManager.getLaunchIntentForPackage(contexte.packageName)
                        ?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    if (relance != null) contexte.startActivity(relance)
                    Runtime.getRuntime().exit(0)
                }
            }
        } else if (uri != null) {
            message = "Saisissez d'abord la phrase secrète de la sauvegarde."
        }
    }

    // --------------------------------------------------------------- sommaire

    data class Rubrique(
        val id: String,
        val titre: String,
        val icone: androidx.compose.ui.graphics.vector.ImageVector,
        val resume: String,
        val etat: EtatLigne = EtatLigne.NEUTRE,
    )

    val entrepriseComplete = reglages.entrepriseNom.isNotBlank() && reglages.adresseEntreprise.isNotBlank() &&
        (reglages.siren.isNotBlank() || reglages.immatriculation.isNotBlank())
    val assuranceComplete = reglages.assureurNom.isNotBlank() && reglages.assureurCoordonnees.isNotBlank()
    val attestationPerimee = reglages.attestationExpiration in 1..System.currentTimeMillis()
    val modeleLocalPret = ModeleLocal.pret(contexte)

    val rubriques = listOf(
        Rubrique(
            "entreprise", "Mon entreprise", Icons.Default.Business,
            if (entrepriseComplete) "${reglages.entrepriseNom} · SIREN ${reglages.siren.ifBlank { reglages.immatriculation }}"
            else "À compléter : nom, adresse, SIREN. Figurent sur chaque devis.",
            if (entrepriseComplete) EtatLigne.OK else EtatLigne.AVERTIR,
        ),
        Rubrique(
            "tva", "TVA", Icons.Default.Percent,
            if (reglages.assujetti) "Assujetti à la TVA" else "Franchise en base : TVA non applicable sur les devis",
            EtatLigne.NEUTRE,
        ),
        Rubrique(
            "assurance", "Assurance professionnelle", Icons.Default.Security,
            when {
                !assuranceComplete -> "À compléter : assureur et coordonnées, obligatoires sur le devis."
                attestationPerimee -> "${reglages.assureurNom} · attestation périmée le ${Formats.date(reglages.attestationExpiration)}"
                reglages.attestationExpiration > 0 -> "${reglages.assureurNom} · attestation valable jusqu'au ${Formats.date(reglages.attestationExpiration)}"
                else -> "${reglages.assureurNom} · date d'expiration de l'attestation à renseigner"
            },
            if (assuranceComplete && !attestationPerimee && reglages.attestationExpiration > 0) EtatLigne.OK else EtatLigne.AVERTIR,
        ),
        Rubrique(
            "comptable", "Comptable et transmission", Icons.Default.AccountBalance,
            if (reglages.destinatairesComptable().isEmpty()) "Aucun destinataire : le devis accepté ne pourra pas être transmis."
            else reglages.destinatairesComptable().joinToString(", "),
            if (reglages.destinatairesComptable().isEmpty()) EtatLigne.AVERTIR else EtatLigne.OK,
        ),
        Rubrique(
            "conditions", "Conditions et médiateur", Icons.Default.Receipt,
            "Validité ${reglages.validiteJours} j · acompte ${Formats.nombre(reglages.acomptePct)} % · " +
                if (reglages.mediateur.isBlank()) "médiateur à renseigner (clients particuliers)" else "médiateur renseigné",
            if (reglages.mediateur.isBlank()) EtatLigne.AVERTIR else EtatLigne.OK,
        ),
        Rubrique(
            "emails", "Modèles d'emails", Icons.Default.Email,
            "Objet client : « ${reglages.modeleClientObjet} »",
        ),
        Rubrique(
            "ia", "Dictée et intelligence artificielle", Icons.Default.Mic,
            when (reglages.iaFournisseur) {
                MoteurDictee.Fournisseur.LOCAL -> if (modeleLocalPret) "Modèle embarqué prêt : dictée comprise hors ligne" else "Règles seules · modèle embarqué (1 Go) à télécharger"
                else -> "${MoteurDictee.libelleFournisseur(reglages.iaFournisseur)} · " + if (reglages.iaCle.isNullOrBlank()) "clé absente" else "clé enregistrée"
            },
            if (reglages.iaFournisseur == MoteurDictee.Fournisseur.LOCAL && modeleLocalPret || !reglages.iaCle.isNullOrBlank()) EtatLigne.OK else EtatLigne.NEUTRE,
        ),
        Rubrique(
            "rappels", "Rappels et relances", Icons.Default.Notifications,
            if (reglages.notificationsActives) "Activés · de ${reglages.heureDebut} h à ${reglages.heureFin} h · rappel ${reglages.rappelJoursAvant} j avant échéance"
            else "Désactivés",
        ),
        Rubrique(
            "sauvegarde", "Sauvegarde", Icons.Default.Backup,
            when {
                reglages.sauvegardeDerniere > 0 -> "Dernière : ${Formats.dateHeure(reglages.sauvegardeDerniere)}" +
                    if (reglages.sauvegardeAuto) " · automatique chaque jour" else " · manuelle"
                else -> "Aucune sauvegarde pour l'instant : un téléphone perdu emporte les devis."
            },
            if (reglages.sauvegardeDerniere > 0) EtatLigne.OK else EtatLigne.AVERTIR,
        ),
        Rubrique(
            "affichage", "Affichage", Icons.Default.Visibility,
            if (reglages.modeChantier) "Mode chantier : contraste élevé, fond sombre" else "Affichage standard",
        ),
        Rubrique(
            "maj", "Mise à jour", Icons.Default.SystemUpdate,
            "Version ${BuildConfig.VERSION_NAME}" + when (val e = etatMaj) {
                is UpdateManager.State.Available -> " · version ${e.version} disponible"
                is UpdateManager.State.ReadyToInstall -> " · version ${e.version} prête à installer"
                else -> ""
            },
            if (etatMaj is UpdateManager.State.Available || etatMaj is UpdateManager.State.ReadyToInstall) EtatLigne.AVERTIR else EtatLigne.NEUTRE,
        ),
        Rubrique("reinit", "Réinitialisation", Icons.Default.RestartAlt, "Relancer l'assistant de démarrage, revenir aux valeurs par défaut"),
        Rubrique("journal", "Journal technique", Icons.Default.Description, "Les dernières actions de l'application, à envoyer en cas de souci"),
    )

    val rubriqueCourante = rubriques.firstOrNull { it.id == section }

    Column(Modifier.fillMaxSize()) {
        EnTeteEcran(
            titre = rubriqueCourante?.titre ?: "Réglages",
            sousTitre = if (rubriqueCourante == null) "Tout ce qui figure sur vos devis, et le fonctionnement de l'application" else null,
            onRetour = { if (section == null) onRetour() else { section = null; message = "" } },
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            if (message.isNotBlank()) {
                BlocAlerte(message)
                Spacer(Modifier.height(10.dp))
            }

            when (section) {
                null -> {
                    // Ce qui manque pour qu'un devis puisse partir, calculé avec les mêmes
                    // règles que le contrôle avant envoi.
                    val manques = Mentions.manquantes(
                        OperationsDevis.contexte(Devis(numero = "", clientNom = "—", objet = "—"), null, reglages, 1),
                    )
                    if (manques.isEmpty()) {
                        BlocAlerte("Tout est en place : vos devis peuvent partir avec toutes les mentions obligatoires.", Couleurs.vert, Icons.Default.CheckCircle)
                    } else {
                        BlocAlerte(
                            "Pour envoyer un devis, il manque encore : ${manques.joinToString(" ; ") { it.lowercase() }}.",
                            Couleurs.orange, Icons.Default.Warning,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    rubriques.forEach { r ->
                        LigneMenu(r.icone, r.titre, r.resume, r.etat, onClick = { message = ""; section = r.id })
                        Spacer(Modifier.height(8.dp))
                    }
                }

                // -------------------------------------------------- entreprise
                "entreprise" -> CarteBloc(titre = "Mon entreprise", sousTitre = "Ces informations figurent en tête de chaque devis.") {
                    ChampTexte(
                        "Nom ou SIREN (remplissage automatique)",
                        rechercheEntreprise,
                        onValeur = { rechercheEntreprise = it },
                        aide = "Annuaire des Entreprises de l'État : dénomination, adresse, SIREN, SIRET, NAF, TVA.",
                    )
                    BoutonSecondaire(
                        "Rechercher mon entreprise",
                        onClick = {
                            portee.launch(Dispatchers.IO) {
                                val reponse = RechercheEntreprises.rechercher(rechercheEntreprise)
                                withContext(Dispatchers.Main) {
                                    resultats = reponse.getOrElse {
                                        message = it.message ?: "Recherche impossible"
                                        emptyList()
                                    }
                                    if (resultats.isEmpty() && message.isBlank()) {
                                        message = "Aucune entreprise trouvée : complétez à la main."
                                    }
                                }
                            }
                        },
                    )
                    resultats.take(4).forEach { r ->
                        BoutonSecondaire(
                            "${r.nom} · ${r.siren}",
                            onClick = {
                                reglages.entrepriseNom = r.nom
                                reglages.siren = r.siren
                                reglages.siret = r.siret.orEmpty()
                                reglages.formeJuridique = r.formeJuridique.orEmpty()
                                reglages.adresseEntreprise = r.adresse.orEmpty()
                                reglages.codeNaf = r.naf.orEmpty()
                                reglages.tvaIntra = r.tvaIntra.orEmpty()
                                resultats = emptyList()
                                rechercheEntreprise = ""
                                message = "Fiche entreprise reprise. Complétez téléphone et email."
                                rafraichir()
                            },
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    ChampTexte("Dénomination", reglages.entrepriseNom, onValeur = { reglages.entrepriseNom = it; rafraichir() })
                    ChampTexte("Adresse", reglages.adresseEntreprise, onValeur = { reglages.adresseEntreprise = it }, lignes = 2)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            ChampTexte("SIREN", reglages.siren, onValeur = { reglages.siren = it }, clavier = KeyboardType.Number)
                        }
                        Column(Modifier.weight(1f)) {
                            ChampTexte("SIRET", reglages.siret, onValeur = { reglages.siret = it }, clavier = KeyboardType.Number)
                        }
                    }
                    ChampTexte("Forme juridique", reglages.formeJuridique, onValeur = { reglages.formeJuridique = it })
                    ChampTexte("Numéro de TVA intracommunautaire", reglages.tvaIntra, onValeur = { reglages.tvaIntra = it })
                    ChampTexte("Immatriculation (si applicable)", reglages.immatriculation, onValeur = { reglages.immatriculation = it })
                    ChampTexte("Téléphone", reglages.telephone, onValeur = { reglages.telephone = it }, clavier = KeyboardType.Phone)
                    ChampTexte("Email", reglages.email, onValeur = { reglages.email = it })
                    BoutonSecondaire(
                        if (reglages.logoChemin == null) "Ajouter un logo" else "Remplacer le logo",
                        onClick = { choixLogo.launch("image/*") },
                    )
                    if (reglages.logoChemin != null) {
                        Spacer(Modifier.height(6.dp))
                        BoutonSecondaire("Supprimer le logo", onClick = { reglages.logoChemin = null; rafraichir() })
                    }
                }

                // ---------------------------------------------------------- TVA
                "tva" -> CarteBloc(titre = "Régime de TVA", sousTitre = "Décide si vos devis portent de la TVA et quelle mention y figure.") {
                    BoutonChoix(
                        "Franchise en base de TVA",
                        selectionne = !reglages.assujetti,
                        onClick = { reglages.regimeTva = "FRANCHISE"; rafraichir() },
                        sousTexte = "Pas de TVA sur les devis, mention légale automatique",
                    )
                    Spacer(Modifier.height(8.dp))
                    BoutonChoix(
                        "Assujetti à la TVA",
                        selectionne = reglages.assujetti,
                        onClick = { reglages.regimeTva = "ASSUJETTI"; rafraichir() },
                        sousTexte = "TVA calculée ligne par ligne (20 %, 10 %, 5,5 %)",
                    )
                    if (!reglages.assujetti) {
                        Spacer(Modifier.height(10.dp))
                        ChampTexte(
                            "Mention de franchise",
                            reglages.mentionFranchise,
                            onValeur = { reglages.mentionFranchise = it },
                            aide = "Mention utilisée selon la date du devis : en 2026 « art. 293 B du CGI », " +
                                "puis « art. L. 233-3 du CIBS » à partir de 2027 (ancienne admise jusqu'au 30/06/2028).",
                        )
                    }
                }

                // ---------------------------------------------------- assurance
                "assurance" -> CarteBloc(titre = "Assurance professionnelle", sousTitre = "Obligatoire sur chaque devis d'un artisan du bâtiment.") {
                    ChampTexte("Assureur", reglages.assureurNom, onValeur = { reglages.assureurNom = it })
                    ChampTexte("Coordonnées de l'assureur", reglages.assureurCoordonnees, onValeur = { reglages.assureurCoordonnees = it }, lignes = 2)
                    ChampTexte("Couverture géographique", reglages.couvertureGeo, onValeur = { reglages.couvertureGeo = it })
                    Text(
                        "Expiration de l'attestation : ${Formats.date(reglages.attestationExpiration)}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(6.dp))
                    BoutonSecondaire(
                        "Attestation valable un an à partir d'aujourd'hui",
                        onClick = {
                            reglages.attestationExpiration = Formats.plusDeJours(System.currentTimeMillis(), 365)
                            rafraichir()
                        },
                    )
                    Spacer(Modifier.height(6.dp))
                    BoutonSecondaire(
                        if (reglages.attestationChemin == null) "Joindre l'attestation (PDF)" else "Remplacer l'attestation",
                        onClick = { choixAttestation.launch("application/pdf") },
                    )
                    Spacer(Modifier.height(6.dp))
                    ChampTexte(
                        "Rappel avant expiration (jours)",
                        reglages.rappelAssuranceJoursAvant.toString(),
                        onValeur = { reglages.rappelAssuranceJoursAvant = it.toIntOrNull() ?: 45 },
                        clavier = KeyboardType.Number,
                        aide = "L'attestation figure sur chaque devis : une attestation périmée passerait inaperçue.",
                    )
                }

                // ---------------------------------------------------- comptable
                "comptable" -> CarteBloc(titre = "Comptable et transmission", sousTitre = "Le devis accepté part chez la personne qui établit la facture.") {
                    ChampTexte(
                        "Email(s) du comptable ou de l'assistant",
                        reglages.comptableEmails,
                        onValeur = { reglages.comptableEmails = it },
                        aide = "Séparez plusieurs adresses par une virgule.",
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = reglages.copieArtisan,
                            onCheckedChange = { reglages.copieArtisan = it; rafraichir() },
                            modifier = Modifier.size(48.dp),
                        )
                        Text("M'envoyer une copie", style = MaterialTheme.typography.bodyLarge)
                    }
                    Spacer(Modifier.height(6.dp))
                    BoutonChoix(
                        "Transmission à l'acceptation du devis",
                        selectionne = reglages.declencheurTransmission == Declencheur.ACCEPTATION,
                        onClick = { reglages.declencheurTransmission = Declencheur.ACCEPTATION; rafraichir() },
                    )
                    Spacer(Modifier.height(8.dp))
                    BoutonChoix(
                        "Transmission dès l'envoi au client",
                        selectionne = reglages.declencheurTransmission == Declencheur.ENVOI,
                        onClick = { reglages.declencheurTransmission = Declencheur.ENVOI; rafraichir() },
                    )
                }

                // --------------------------------------------------- conditions
                "conditions" -> CarteBloc(titre = "Conditions et médiateur", sousTitre = "Valeurs proposées sur chaque nouveau devis.") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            ChampTexte("Validité (jours)", reglages.validiteJours.toString(), onValeur = { reglages.validiteJours = it.toIntOrNull() ?: 30 }, clavier = KeyboardType.Number)
                        }
                        Column(Modifier.weight(1f)) {
                            ChampTexte("Acompte %", Formats.nombre(reglages.acomptePct), onValeur = { reglages.acomptePct = Formats.montant(it) ?: 0.0 }, clavier = KeyboardType.Decimal)
                        }
                    }
                    ChampTexte("Modalités de paiement", reglages.modalitesPaiement, onValeur = { reglages.modalitesPaiement = it }, lignes = 2)
                    ChampTexte(
                        "Médiateur de la consommation",
                        reglages.mediateur,
                        onValeur = { reglages.mediateur = it },
                        aide = "Obligatoire pour les devis aux particuliers : nom et coordonnées du médiateur auquel vous adhérez.",
                    )
                    ChampTexte("Conditions générales (facultatif)", reglages.conditionsGenerales, onValeur = { reglages.conditionsGenerales = it }, lignes = 3)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = reglages.devisGratuit,
                            onCheckedChange = { reglages.devisGratuit = it; rafraichir() },
                            modifier = Modifier.size(48.dp),
                        )
                        Text("Devis gratuit", style = MaterialTheme.typography.bodyLarge)
                    }
                    ChampTexte("Seuil de marge (%)", Formats.nombre(reglages.seuilMarge), onValeur = { reglages.seuilMarge = Formats.montant(it) ?: 20.0 }, clavier = KeyboardType.Decimal)
                }

                // ------------------------------------------------------- emails
                "emails" -> CarteBloc(titre = "Modèles d'emails", sousTitre = "Pré-remplis à l'envoi ; vous pouvez toujours les modifier avant d'envoyer.") {
                    ChampTexte("Objet (client)", reglages.modeleClientObjet, onValeur = { reglages.modeleClientObjet = it })
                    ChampTexte("Texte (client)", reglages.modeleClientCorps, onValeur = { reglages.modeleClientCorps = it }, lignes = 5)
                    ChampTexte("Objet (comptable)", reglages.modeleComptableObjet, onValeur = { reglages.modeleComptableObjet = it })
                    ChampTexte("Texte (comptable)", reglages.modeleComptableCorps, onValeur = { reglages.modeleComptableCorps = it }, lignes = 5)
                    Text(
                        "Variables : {numero} {client} {objet} {ht} {ttc} {limite} {entreprise} {date}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // ----------------------------------------------------------- IA
                "ia" -> SectionIntelligenceArtificielle(
                    contexte = contexte,
                    dao = dao,
                    reglages = reglages,
                    rafraichir = { rafraichir() },
                    onMessage = { message = it },
                )

                // ------------------------------------------------------ rappels
                "rappels" -> CarteBloc(titre = "Rappels et relances", sousTitre = "Une notification récapitulative par jour, jamais la nuit.") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = reglages.notificationsActives,
                            onCheckedChange = { reglages.notificationsActives = it; rafraichir() },
                            modifier = Modifier.size(48.dp),
                        )
                        Text("Rappels activés", style = MaterialTheme.typography.bodyLarge)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            ChampTexte("Rappel avant échéance (j)", reglages.rappelJoursAvant.toString(), onValeur = { reglages.rappelJoursAvant = it.toIntOrNull() ?: 3 }, clavier = KeyboardType.Number)
                        }
                        Column(Modifier.weight(1f)) {
                            ChampTexte("Relance sans réponse (j)", reglages.relanceJoursApres.toString(), onValeur = { reglages.relanceJoursApres = it.toIntOrNull() ?: 7 }, clavier = KeyboardType.Number)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            ChampTexte("Pas avant (h)", reglages.heureDebut.toString(), onValeur = { reglages.heureDebut = it.toIntOrNull() ?: 8 }, clavier = KeyboardType.Number)
                        }
                        Column(Modifier.weight(1f)) {
                            ChampTexte("Pas après (h)", reglages.heureFin.toString(), onValeur = { reglages.heureFin = it.toIntOrNull() ?: 20 }, clavier = KeyboardType.Number)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = reglages.dimancheAutorise,
                            onCheckedChange = { reglages.dimancheAutorise = it; rafraichir() },
                            modifier = Modifier.size(48.dp),
                        )
                        Text("Autoriser le dimanche", style = MaterialTheme.typography.bodyLarge)
                    }
                }

                // --------------------------------------------------- sauvegarde
                "sauvegarde" -> CarteBloc(titre = "Sauvegarde", sousTitre = "Base complète et réglages, chiffrés par une phrase secrète. Jamais la clé API.") {
                    Text(
                        "Sans la phrase secrète, aucune restauration n'est possible sur un nouveau téléphone : notez-la.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    ChampTexte(
                        "Phrase secrète (6 caractères minimum)",
                        if (motDePasse.isBlank() && reglages.motDePasseSauvegarde != null) "•••••• (déjà définie)" else motDePasse,
                        onValeur = { motDePasse = it },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BoutonSecondaire(
                            "Enregistrer la phrase",
                            onClick = {
                                if (motDePasse.length >= 6) {
                                    reglages.motDePasseSauvegarde = motDePasse
                                    message = "Phrase secrète enregistrée. Notez-la : elle est indispensable en cas de téléphone perdu."
                                } else {
                                    message = "Phrase secrète trop courte."
                                }
                                rafraichir()
                            },
                            modifier = Modifier.weight(1f),
                        )
                        BoutonSecondaire(
                            "Sauvegarder maintenant",
                            onClick = {
                                val mdp = motDePasse.ifBlank { reglages.motDePasseSauvegarde.orEmpty() }
                                if (mdp.length < 6) {
                                    message = "Choisissez d'abord une phrase secrète."
                                } else {
                                    portee.launch(Dispatchers.IO) {
                                        val resultat = Sauvegarde.exporter(contexte, reglages, mdp)
                                        withContext(Dispatchers.Main) {
                                            message = resultat.message
                                            resultat.fichier?.let { Partage.partagerFichier(contexte, it, "Sauvegarde Devis Chantier") }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = reglages.sauvegardeAuto,
                            onCheckedChange = { reglages.sauvegardeAuto = it; rafraichir() },
                            modifier = Modifier.size(48.dp),
                        )
                        Text("Sauvegarde automatique chaque jour", style = MaterialTheme.typography.bodyLarge)
                    }
                    BoutonSecondaire(
                        if (reglages.sauvegardeDossier == null) "Choisir le dossier (Drive, SD, Nextcloud…)" else "Dossier choisi · changer",
                        onClick = { choixDossier.launch(null) },
                    )
                    if (reglages.sauvegardeDerniere > 0) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Dernière sauvegarde : ${Formats.dateHeure(reglages.sauvegardeDerniere)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    BoutonSecondaire("Restaurer une sauvegarde", onClick = { choixRestauration.launch(arrayOf("*/*")) })
                    Spacer(Modifier.height(6.dp))
                    ChampTexte(
                        "Conservation des données (mois)",
                        reglages.conservationMois.toString(),
                        onValeur = { reglages.conservationMois = it.toIntOrNull() ?: 120 },
                        clavier = KeyboardType.Number,
                    )
                }

                // ---------------------------------------------------- affichage
                "affichage" -> CarteBloc(titre = "Affichage", sousTitre = "Pour lire l'écran en plein soleil ou avec des lunettes de sécurité.") {
                    BoutonChoix(
                        "Affichage standard",
                        selectionne = !reglages.modeChantier,
                        onClick = { reglages.modeChantier = false; rafraichir() },
                        sousTexte = "Fond clair, couleurs douces",
                    )
                    Spacer(Modifier.height(8.dp))
                    BoutonChoix(
                        "Mode chantier",
                        selectionne = reglages.modeChantier,
                        onClick = { reglages.modeChantier = true; rafraichir() },
                        sousTexte = "Contraste élevé, fond sombre",
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Le changement s'applique au prochain affichage de l'accueil.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // -------------------------------------------------- mise à jour
                "maj" -> CarteBloc(titre = "Mise à jour", sousTitre = "Version installée : ${BuildConfig.VERSION_NAME}") {
                    LaunchedEffect(etatMaj) {
                        majEtat = when (val etat = etatMaj) {
                            is UpdateManager.State.Available -> "Version ${etat.version} disponible."
                            is UpdateManager.State.Downloading -> "Téléchargement… ${(etat.progress * 100).toInt()} %"
                            is UpdateManager.State.ReadyToInstall -> "Version ${etat.version} prête à installer."
                            is UpdateManager.State.UpToDate -> "À jour."
                            is UpdateManager.State.Failed -> "Vérification impossible : ${etat.detail}"
                            else -> ""
                        }
                    }
                    if (majEtat.isNotBlank()) {
                        BlocAlerte(majEtat)
                        Spacer(Modifier.height(8.dp))
                    }
                    BoutonSecondaire("Vérifier maintenant", onClick = { UpdateManager.checkNow(contexte) })
                    val installable = etatMaj as? UpdateManager.State.ReadyToInstall
                    if (installable != null) {
                        Spacer(Modifier.height(8.dp))
                        BoutonPrincipal(
                            "Installer la version ${installable.version}",
                            onClick = {
                                if (UpdateManager.canInstall(contexte)) UpdateManager.install(contexte) else UpdateManager.openInstallSettings(contexte)
                            },
                            hauteur = 56.dp,
                        )
                    }
                }

                // ----------------------------------------------- réinitialisation
                "reinit" -> CarteBloc(titre = "Réinitialisation") {
                    Text(
                        "L'assistant de premier lancement peut être rejoué à tout moment : il reprend " +
                            "les valeurs déjà saisies et vous laisse les corriger.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    BoutonSecondaire(
                        "Relancer l'assistant de démarrage",
                        onClick = {
                            reglages.onboardingFait = false
                            onReinitialiser()
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    BoutonSecondaire(
                        "Réinitialiser les réglages (valeurs par défaut)",
                        onClick = { confirmationReinitialisation = true },
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Remet à zéro entreprise, TVA, assurance, destinataires, conditions, modèles " +
                            "d'emails et rappels, puis rouvre l'assistant. Les devis, les clients, le " +
                            "catalogue, la clé API et la phrase secrète des sauvegardes sont conservés. " +
                            "Pour tout effacer, utilisez « Effacer les données » dans les paramètres Android.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // ------------------------------------------------------ journal
                "journal" -> CarteBloc(titre = "Journal technique", sousTitre = "Dernières actions enregistrées par l'application (utile en cas de souci).") {
                    Text(
                        Diag.dump().lines().takeLast(12).joinToString("\n").ifBlank { "Aucune action enregistrée." },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    BoutonSecondaire(
                        "Envoyer le journal par email",
                        onClick = {
                            Partage.envoyerTexte(
                                contexte,
                                "fabrice.heuvrard@gmail.com",
                                "Journal Devis Chantier ${BuildConfig.VERSION_NAME}",
                                Diag.dump(),
                            )
                        },
                    )
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }

    if (confirmationReinitialisation) {
        DialogueConfirmation(
            titre = "Réinitialiser les réglages ?",
            texte = "Les réglages reviennent à leurs valeurs par défaut et l'assistant de démarrage " +
                "s'ouvre. Devis, clients, catalogue, clé API et phrase secrète sont conservés.",
            libelleConfirmer = "Réinitialiser",
            onConfirmer = {
                reglages.reinitialiser()
                onReinitialiser()
            },
            onAnnuler = { confirmationReinitialisation = false },
        )
    }
}
