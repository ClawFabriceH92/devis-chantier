package com.trucdecomptable.devischantier.ui

import android.content.Context
import android.net.Uri
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trucdecomptable.devischantier.BuildConfig
import com.trucdecomptable.devischantier.api.ClaudeClient
import com.trucdecomptable.devischantier.api.RechercheEntreprises
import com.trucdecomptable.devischantier.data.Coffre
import com.trucdecomptable.devischantier.data.Declencheur
import com.trucdecomptable.devischantier.data.Dao
import com.trucdecomptable.devischantier.data.Reglages
import com.trucdecomptable.devischantier.sauvegarde.Sauvegarde
import com.trucdecomptable.devischantier.update.UpdateManager
import com.trucdecomptable.devischantier.util.Diag
import com.trucdecomptable.devischantier.util.Formats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

/**
 * La roue dentée (§3.4). Chaque étape peut être laissée de côté et reprise :
 * l'assistant de premier lancement affiche les mêmes blocs.
 */
@Composable
fun EcranReglages(
    contexte: Context,
    dao: Dao,
    reglages: Reglages,
    onRetour: () -> Unit,
) {
    val portee = rememberCoroutineScope()
    var version by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf("") }
    var rechercheEntreprise by remember { mutableStateOf("") }
    var resultats by remember { mutableStateOf<List<com.trucdecomptable.devischantier.api.EntrepriseResultat>>(emptyList()) }
    var cleSaisie by remember { mutableStateOf("") }
    var testCle by remember { mutableStateOf("") }
    var modeles by remember { mutableStateOf<List<String>>(emptyList()) }
    var motDePasse by remember { mutableStateOf("") }
    var majEtat by remember { mutableStateOf("") }

    fun rafraichir() {
        version++
    }

    val etatMaj by UpdateManager.state.collectAsStateWithLifecycle()

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
            }
        } else if (uri != null) {
            message = "Saisissez d'abord la phrase secrète de la sauvegarde."
        }
    }

    Column(Modifier.fillMaxSize()) {
        EnTeteRetour("Réglages", onRetour)
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

            // -------------------------------------------------- entreprise
            CarteBloc(titre = "Mon entreprise") {
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
                    BoutonSecondaire(
                        "Supprimer le logo",
                        onClick = { reglages.logoChemin = null; rafraichir() },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            // -------------------------------------------------------- TVA
            CarteBloc(titre = "Régime de TVA") {
                BoutonSecondaire(
                    if (reglages.assujetti) "Assujetti à la TVA" else "Franchise en base de TVA",
                    onClick = {
                        reglages.regimeTva = if (reglages.assujetti) "FRANCHISE" else "ASSUJETTI"
                        rafraichir()
                    },
                )
                if (!reglages.assujetti) {
                    ChampTexte(
                        "Mention de franchise",
                        reglages.mentionFranchise,
                        onValeur = { reglages.mentionFranchise = it },
                        aide = "Mention utilisée selon la date du devis : en 2026 « art. 293 B du CGI », " +
                            "puis « art. L. 233-3 du CIBS » à partir de 2027 (ancienne admise jusqu'au 30/06/2028).",
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            // -------------------------------------------------- assurance
            CarteBloc(titre = "Assurance professionnelle") {
                ChampTexte("Assureur", reglages.assureurNom, onValeur = { reglages.assureurNom = it })
                ChampTexte("Coordonnées de l'assureur", reglages.assureurCoordonnees, onValeur = { reglages.assureurCoordonnees = it }, lignes = 2)
                ChampTexte("Couverture géographique", reglages.couvertureGeo, onValeur = { reglages.couvertureGeo = it })
                Text(
                    "Expiration de l'attestation : ${Formats.date(reglages.attestationExpiration)}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                BoutonSecondaire(
                    "Choisir la date d'expiration (dans 1 an)",
                    onClick = {
                        reglages.attestationExpiration = Formats.plusDeJours(System.currentTimeMillis(), 365)
                        rafraichir()
                    },
                )
                BoutonSecondaire(
                    if (reglages.attestationChemin == null) "Joindre l'attestation (PDF)" else "Remplacer l'attestation",
                    onClick = { choixAttestation.launch("application/pdf") },
                )
                ChampTexte(
                    "Rappel avant expiration (jours)",
                    reglages.rappelAssuranceJoursAvant.toString(),
                    onValeur = { reglages.rappelAssuranceJoursAvant = it.toIntOrNull() ?: 45 },
                    clavier = KeyboardType.Number,
                    aide = "L'attestation figure sur chaque devis : une attestation périmée passerait inaperçue.",
                )
            }
            Spacer(Modifier.height(12.dp))

            // ------------------------------------------- destinataire admin
            CarteBloc(titre = "Destinataire administratif") {
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
                BoutonSecondaire(
                    if (reglages.declencheurTransmission == Declencheur.ACCEPTATION) {
                        "Transmission à l'acceptation du devis"
                    } else {
                        "Transmission dès l'envoi au client"
                    },
                    onClick = {
                        reglages.declencheurTransmission =
                            if (reglages.declencheurTransmission == Declencheur.ACCEPTATION) Declencheur.ENVOI
                            else Declencheur.ACCEPTATION
                        rafraichir()
                    },
                )
            }
            Spacer(Modifier.height(12.dp))

            // -------------------------------------------- conditions défaut
            CarteBloc(titre = "Conditions par défaut") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        ChampTexte("Validité (jours)", reglages.validiteJours.toString(), onValeur = { reglages.validiteJours = it.toIntOrNull() ?: 30 }, clavier = KeyboardType.Number)
                    }
                    Column(Modifier.weight(1f)) {
                        ChampTexte("Acompte %", Formats.nombre(reglages.acomptePct), onValeur = { reglages.acomptePct = Formats.montant(it) ?: 0.0 }, clavier = KeyboardType.Decimal)
                    }
                }
                ChampTexte("Modalités de paiement", reglages.modalitesPaiement, onValeur = { reglages.modalitesPaiement = it }, lignes = 2)
                ChampTexte("Médiateur de la consommation", reglages.mediateur, onValeur = { reglages.mediateur = it })
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
            Spacer(Modifier.height(12.dp))

            // -------------------------------------------------- modèles mail
            CarteBloc(titre = "Modèles d'emails") {
                ChampTexte("Objet (client)", reglages.modeleClientObjet, onValeur = { reglages.modeleClientObjet = it })
                ChampTexte("Texte (client)", reglages.modeleClientCorps, onValeur = { reglages.modeleClientCorps = it }, lignes = 5)
                ChampTexte("Objet (comptable)", reglages.modeleComptableObjet, onValeur = { reglages.modeleComptableObjet = it })
                ChampTexte("Texte (comptable)", reglages.modeleComptableCorps, onValeur = { reglages.modeleComptableCorps = it }, lignes = 5)
                Text(
                    "Variables : {numero} {client} {objet} {ht} {ttc} {limite} {entreprise} {date}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Couleurs.gris,
                )
            }
            Spacer(Modifier.height(12.dp))

            // ------------------------------------------------------ IA
            CarteBloc(titre = "Intelligence artificielle (dictée assistée)") {
                Text(
                    "Facultatif. Sans clé, la dictée fonctionne entièrement, sans compte ni abonnement. " +
                        "Avec une clé, le texte dicté et un extrait du catalogue (libellés, prix de vente) " +
                        "sont envoyés au fournisseur choisi. Les prix d'achat et les marges ne partent jamais.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                BoutonSecondaire(
                    if (reglages.iaFournisseur == "CLAUDE") "Fournisseur : Claude (Anthropic)" else "Fournisseur : autre (compatible OpenAI)",
                    onClick = {
                        reglages.iaFournisseur = if (reglages.iaFournisseur == "CLAUDE") "AUTRE" else "CLAUDE"
                        rafraichir()
                    },
                )
                if (reglages.iaFournisseur == "AUTRE") {
                    ChampTexte(
                        "Adresse de l'API (ex. https://…/v1)",
                        reglages.iaBaseUrl,
                        onValeur = { reglages.iaBaseUrl = it },
                    )
                }
                ChampTexte(
                    if (reglages.iaCle == null) "Clé API" else "Clé API (${Coffre.masquer(reglages.iaCle.orEmpty())})",
                    cleSaisie,
                    onValeur = { cleSaisie = it },
                    aide = "La clé est chiffrée par le Keystore d'Android et n'est jamais incluse dans les sauvegardes.",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BoutonSecondaire(
                        "Enregistrer la clé",
                        onClick = {
                            if (cleSaisie.isNotBlank()) {
                                reglages.iaCle = cleSaisie
                                reglages.iaConsentement = true
                                cleSaisie = ""
                                message = "Clé enregistrée (chiffrée)."
                                rafraichir()
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    BoutonSecondaire(
                        "Tester la clé",
                        onClick = {
                            val cle = reglages.iaCle
                            if (cle.isNullOrBlank()) {
                                testCle = "Aucune clé enregistrée."
                            } else {
                                portee.launch(Dispatchers.IO) {
                                    val base = reglages.iaBaseUrl.ifBlank { ClaudeClient.BASE }
                                    val resultat = ClaudeClient.listerModeles(cle, base)
                                    withContext(Dispatchers.Main) {
                                        testCle = resultat.fold(
                                            onSuccess = { liste ->
                                                modeles = liste
                                                // L'identifiant enregistré peut avoir été retiré :
                                                // on garde celui de la liste s'il n'y figure plus.
                                                if (liste.isNotEmpty() && liste.none { it == reglages.iaModele }) {
                                                    reglages.iaModele =
                                                        ClaudeClient.classerModeles(liste)[reglages.iaNiveau]
                                                            ?.takeIf { it.isNotBlank() }
                                                            ?: liste.first()
                                                }
                                                "Clé valide. Modèle utilisé : ${reglages.iaModele}"
                                            },
                                            onFailure = { "Échec : ${it.message}" },
                                        )
                                        rafraichir()
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (testCle.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    BlocAlerte(testCle)
                }
                Text(
                    "Niveau de qualité : ${ClaudeClient.libelleNiveau(reglages.iaNiveau)}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BoutonSecondaire(
                        "Qualité maximale",
                        onClick = { reglages.iaNiveau = ClaudeClient.Niveau.QUALITE; reglages.iaModele = ""; rafraichir() },
                        modifier = Modifier.weight(1f),
                    )
                    BoutonSecondaire(
                        "Équilibré",
                        onClick = { reglages.iaNiveau = ClaudeClient.Niveau.EQUILIBRE; reglages.iaModele = ""; rafraichir() },
                        modifier = Modifier.weight(1f),
                    )
                    BoutonSecondaire(
                        "Économique",
                        onClick = { reglages.iaNiveau = ClaudeClient.Niveau.ECONOMIE; reglages.iaModele = ""; rafraichir() },
                        modifier = Modifier.weight(1f),
                    )
                }
                ChampTexte(
                    "Modèle (vide = le meilleur du niveau, choisi chez le fournisseur)",
                    reglages.iaModele,
                    onValeur = { reglages.iaModele = it },
                    aide = "Le modèle réellement retenu est relu dans la liste du fournisseur : " +
                        "les identifiants changent souvent.",
                )
                if (modeles.isNotEmpty()) {
                    Text(
                        "Modèles disponibles chez le fournisseur (appuyez pour choisir) :",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Couleurs.gris,
                    )
                    modeles.take(10).forEach { modele ->
                        BoutonSecondaire(
                            (if (reglages.iaModele == modele) "✓ " else "") + modele,
                            onClick = { reglages.iaModele = modele; rafraichir() },
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
                BoutonSecondaire(
                    "Tester la dictée assistée",
                    onClick = {
                        val cle = reglages.iaCle
                        if (cle.isNullOrBlank()) {
                            testCle = "Aucune clé enregistrée : la dictée fonctionne en mode gratuit."
                        } else {
                            portee.launch(Dispatchers.IO) {
                                val articles = dao.articlesListe().take(20)
                                val resultat = ClaudeClient.extraireLignes(
                                    cle = cle,
                                    modele = reglages.iaModele,
                                    texte = "deux mitigeurs lavabo et trois heures de main d'oeuvre",
                                    articles = articles,
                                    baseUrl = reglages.iaBaseUrl.ifBlank { ClaudeClient.BASE },
                                    compatibleOpenAi = reglages.iaFournisseur == "AUTRE",
                                    niveau = reglages.iaNiveau,
                                )
                                withContext(Dispatchers.Main) {
                                    testCle = resultat.fold(
                                        onSuccess = { propositions ->
                                            if (propositions.isEmpty()) {
                                                "Appel réussi, aucune ligne proposée (réponse illisible ?)."
                                            } else {
                                                "Appel réussi : " + propositions.joinToString(" · ") {
                                                    "${it.ligne.quantite} ${it.ligne.unite ?: "u"} ${it.ligne.designation}"
                                                }
                                            }
                                        },
                                        onFailure = { "Échec : ${it.message}" },
                                    )
                                    rafraichir()
                                }
                            }
                        }
                    },
                )
                Text(
                    "Dictées assistées par l'IA : ${reglages.iaDictees}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (reglages.iaCle != null) {
                    BoutonSecondaire(
                        "Supprimer la clé",
                        onClick = {
                            reglages.iaCle = null
                            reglages.iaConsentement = false
                            message = "Clé supprimée. Retour au mode gratuit."
                            rafraichir()
                        },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            // ------------------------------------------------ notifications
            CarteBloc(titre = "Notifications et relances") {
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
            Spacer(Modifier.height(12.dp))

            // ---------------------------------------------------- sauvegarde
            CarteBloc(titre = "Sauvegarde") {
                Text(
                    "La sauvegarde contient la base complète (devis, clients, catalogue, achats) et les " +
                        "réglages, jamais la clé API. Elle est chiffrée par une phrase secrète : sans elle, " +
                        "aucune restauration n'est possible sur un nouveau téléphone.",
                    style = MaterialTheme.typography.bodyMedium,
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
                    if (reglages.sauvegardeDossier == null) "Choisir le dossier (Drive, SD, Nextcloud…)"
                    else "Dossier choisi · changer",
                    onClick = { choixDossier.launch(null) },
                )
                if (reglages.sauvegardeDerniere > 0) {
                    Text(
                        "Dernière sauvegarde : ${Formats.dateHeure(reglages.sauvegardeDerniere)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Couleurs.gris,
                    )
                }
                BoutonSecondaire(
                    "Restaurer une sauvegarde",
                    onClick = { choixRestauration.launch(arrayOf("*/*")) },
                )
                ChampTexte(
                    "Conservation des données (mois)",
                    reglages.conservationMois.toString(),
                    onValeur = { reglages.conservationMois = it.toIntOrNull() ?: 120 },
                    clavier = KeyboardType.Number,
                )
            }
            Spacer(Modifier.height(12.dp))

            // -------------------------------------------------- apparence
            CarteBloc(titre = "Confort de lecture") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = reglages.modeChantier,
                        onCheckedChange = { reglages.modeChantier = it; rafraichir() },
                        modifier = Modifier.size(48.dp),
                    )
                    Text("Mode chantier (contraste élevé, fond sombre)", style = MaterialTheme.typography.bodyLarge)
                }
            }
            Spacer(Modifier.height(12.dp))

            // -------------------------------------------------- mise à jour
            CarteBloc(titre = "Mise à jour") {
                Text("Version installée : ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyLarge)
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
                    BoutonGeant(
                        "Installer la version ${installable.version}",
                        onClick = {
                            if (UpdateManager.canInstall(contexte)) {
                                UpdateManager.install(contexte)
                            } else {
                                UpdateManager.openInstallSettings(contexte)
                            }
                        },
                        hauteur = 56.dp,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            CarteBloc(titre = "Journal technique") {
                Text(
                    "Dernières actions enregistrées par l'application (utile en cas de souci).",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    Diag.dump().lines().takeLast(12).joinToString("\n").ifBlank { "Aucune action enregistrée." },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Couleurs.gris,
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
            Spacer(Modifier.height(30.dp))
        }
    }
}
