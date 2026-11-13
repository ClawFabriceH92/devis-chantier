package com.trucdecomptable.devischantier.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trucdecomptable.devischantier.api.ClaudeClient
import com.trucdecomptable.devischantier.data.Coffre
import com.trucdecomptable.devischantier.data.Dao
import com.trucdecomptable.devischantier.data.Reglages
import com.trucdecomptable.devischantier.ia.LlmLocal
import com.trucdecomptable.devischantier.ia.ModeleLocal
import com.trucdecomptable.devischantier.ia.TelechargementModele
import com.trucdecomptable.devischantier.voice.MoteurDictee
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Le bloc « Intelligence artificielle » des réglages (§4.4, F-37). Trois choix :
 * le modèle embarqué sur le téléphone (défaut, gratuit, hors ligne), Claude, ou
 * un service compatible OpenAI. Les prix d'achat et les marges ne partent nulle
 * part, dans aucun des trois cas.
 */
@Composable
fun SectionIntelligenceArtificielle(
    contexte: Context,
    dao: Dao,
    reglages: Reglages,
    rafraichir: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val portee = rememberCoroutineScope()
    var cleSaisie by remember { mutableStateOf("") }
    var testCle by remember { mutableStateOf("") }
    var modeles by remember { mutableStateOf<List<String>>(emptyList()) }
    var testLocal by remember { mutableStateOf("") }
    var testEnCours by remember { mutableStateOf(false) }
    val etatTelechargement by TelechargementModele.etat.collectAsStateWithLifecycle()
    val modele = ModeleLocal.MODELE_PAR_DEFAUT
    val pret = ModeleLocal.fichier(contexte, modele).let { it.exists() && it.length() == modele.taille }
    val compatibilite = remember { ModeleLocal.compatibilite(contexte, modele) }
    val fournisseur = reglages.iaFournisseur

    val choixGguf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            portee.launch {
                val resultat = TelechargementModele.importer(contexte, { contexte.contentResolver.openInputStream(uri) }, modele)
                onMessage(resultat.getOrElse { "Import impossible : ${it.message}" })
                rafraichir()
            }
        }
    }

    CarteBloc(titre = "Intelligence artificielle (dictée assistée)") {
        Text(
            "Facultatif. Sans rien installer, la dictée fonctionne par règles. Pour une dictée " +
                "mieux comprise, choisissez un modèle : sur le téléphone (gratuit, hors ligne, rien ne " +
                "sort de l'appareil) ou chez un fournisseur avec votre clé. Les prix d'achat et les " +
                "marges ne partent jamais.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))

        listOf(
            MoteurDictee.Fournisseur.LOCAL to "Sur le téléphone · ${modele.nom} (gratuit, hors ligne)",
            MoteurDictee.Fournisseur.CLAUDE to "Claude (Anthropic) · clé API",
            MoteurDictee.Fournisseur.AUTRE to "Autre service compatible OpenAI · clé API",
        ).forEach { (code, libelle) ->
            BoutonSecondaire(
                (if (fournisseur == code) "✓ " else "") + libelle,
                onClick = { reglages.iaFournisseur = code; rafraichir() },
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
        Spacer(Modifier.height(8.dp))

        if (fournisseur == MoteurDictee.Fournisseur.LOCAL) {
            // ------------------------------------------------ modèle embarqué
            Text(
                "${modele.nom} · ${modele.editeur} · ${modele.tailleLisible} à télécharger une fois. " +
                    "Licence ${modele.licence} : usage libre, y compris professionnel, pour une " +
                    "entreprise sous 10 millions de dollars de chiffre d'affaires annuel.",
                style = MaterialTheme.typography.bodyMedium,
                color = Couleurs.gris,
            )
            Spacer(Modifier.height(6.dp))
            if (!compatibilite.ok) {
                BlocAlerte(compatibilite.texte, Couleurs.rouge)
                Spacer(Modifier.height(6.dp))
            }
            when (val etat = etatTelechargement) {
                is TelechargementModele.Etat.EnCours -> {
                    Text(
                        "Téléchargement : ${etat.octets / 1_000_000} / ${etat.total / 1_000_000} Mo",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    LinearProgressIndicator(progress = { etat.progression }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    BoutonSecondaire("Mettre en pause (reprise possible)", onClick = { TelechargementModele.annuler() })
                }
                TelechargementModele.Etat.Verification -> {
                    Text("Vérification de l'empreinte du fichier…", style = MaterialTheme.typography.bodyLarge)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                else -> {
                    if (etat is TelechargementModele.Etat.Echec) {
                        BlocAlerte("Téléchargement interrompu : ${etat.detail}", Couleurs.rouge)
                        Spacer(Modifier.height(6.dp))
                    }
                    if (pret) {
                        BlocAlerte(
                            "Modèle installé et vérifié. La dictée est comprise sur le téléphone, sans réseau." +
                                if (LlmLocal.chargeEnMemoire) " (chargé en mémoire)" else "",
                            Couleurs.vert,
                        )
                        Spacer(Modifier.height(6.dp))
                        BoutonSecondaire(
                            if (testEnCours) "Test en cours…" else "Tester la dictée sur le téléphone",
                            actif = !testEnCours,
                            onClick = {
                                testEnCours = true
                                testLocal = ""
                                portee.launch(Dispatchers.IO) {
                                    val articles = dao.articlesListe()
                                    val resultat = MoteurDictee.traiter(
                                        "deux mitigeurs lavabo et trois heures de main d'oeuvre",
                                        articles, reglages, contexte,
                                    )
                                    withContext(Dispatchers.Main) {
                                        testEnCours = false
                                        testLocal = if (resultat.mode == MoteurDictee.Mode.IA_LOCALE) {
                                            "Réponse en ${String.format(java.util.Locale.FRANCE, "%.1f", resultat.dureeMs / 1000.0)} s : " +
                                                resultat.propositions.joinToString(" · ") {
                                                    "${com.trucdecomptable.devischantier.util.Formats.nombre(it.ligne.quantite)} ${it.ligne.unite ?: "u"} ${it.ligne.designation}"
                                                }
                                        } else {
                                            resultat.message
                                        }
                                    }
                                }
                            },
                        )
                        if (testLocal.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            BlocAlerte(testLocal)
                        }
                        Spacer(Modifier.height(6.dp))
                        BoutonSecondaire(
                            "Supprimer le modèle (libère ${modele.tailleLisible})",
                            onClick = {
                                LlmLocal.liberer()
                                ModeleLocal.supprimer(contexte, modele)
                                onMessage("Modèle supprimé : la dictée repasse en mode règles.")
                                rafraichir()
                            },
                        )
                    } else {
                        val partiel = ModeleLocal.fichierPartiel(contexte, modele).length()
                        val reprise = if (partiel > 0) " (reprise à ${partiel / 1_000_000} Mo)" else ""
                        BoutonGeant(
                            "Télécharger le modèle$reprise",
                            actif = compatibilite.ok,
                            onClick = {
                                if (TelechargementModele.reseauNonFacture(contexte) || reglages.iaLocalDonneesMobiles) {
                                    TelechargementModele.demarrer(contexte, modele)
                                } else {
                                    onMessage(
                                        "Vous n'êtes pas en Wi-Fi : ${modele.tailleLisible} à télécharger. " +
                                            "Connectez-vous au Wi-Fi, ou autorisez les données mobiles ci-dessous.",
                                    )
                                }
                            },
                            hauteur = 56.dp,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BoutonSecondaire(
                                if (reglages.iaLocalDonneesMobiles) "✓ Données mobiles autorisées" else "Autoriser en données mobiles",
                                onClick = { reglages.iaLocalDonneesMobiles = !reglages.iaLocalDonneesMobiles; rafraichir() },
                                modifier = Modifier.weight(1f),
                            )
                            BoutonSecondaire(
                                "Importer un .gguf",
                                onClick = { choixGguf.launch(arrayOf("*/*")) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Text(
                            "Source : huggingface.co/LiquidAI/LFM3-1.7B-GGUF, fichier ${modele.fichier}. " +
                                "L'empreinte SHA-256 du fichier reçu est vérifiée avant toute utilisation.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Couleurs.gris,
                        )
                    }
                }
            }
        } else {
            // ------------------------------------------------ fournisseur cloud
            Text(
                "Le texte dicté et un extrait du catalogue (libellés, unités, prix de vente) sont envoyés " +
                    "à ${MoteurDictee.libelleFournisseur(fournisseur)}.",
                style = MaterialTheme.typography.bodyMedium,
                color = Couleurs.gris,
            )
            if (fournisseur == MoteurDictee.Fournisseur.AUTRE) {
                ChampTexte(
                    "Adresse de l'API (ex. https://api.mistral.ai/v1)",
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
                            onMessage("Clé enregistrée (chiffrée).")
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
                                val openAi = fournisseur == MoteurDictee.Fournisseur.AUTRE
                                val resultat = ClaudeClient.listerModeles(cle, base, compatibleOpenAi = openAi)
                                withContext(Dispatchers.Main) {
                                    testCle = resultat.fold(
                                        onSuccess = { liste ->
                                            modeles = liste
                                            if (liste.isNotEmpty() && liste.none { it == reglages.iaModele }) {
                                                reglages.iaModele = if (openAi) {
                                                    liste.first()
                                                } else {
                                                    ClaudeClient.classerModeles(liste)[reglages.iaNiveau]
                                                        ?.takeIf { it.isNotBlank() } ?: liste.first()
                                                }
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
            if (reglages.iaCleNonSecurisee) {
                Spacer(Modifier.height(6.dp))
                BlocAlerte(
                    "Ce téléphone n'a pas de coffre sécurisé disponible : la clé fonctionne pendant cette " +
                        "session mais n'est pas enregistrée. Elle sera à ressaisir au prochain démarrage.",
                    Couleurs.rouge,
                )
            }
            if (testCle.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                BlocAlerte(testCle)
            }
            if (fournisseur == MoteurDictee.Fournisseur.CLAUDE) {
                Text("Niveau de qualité : ${ClaudeClient.libelleNiveau(reglages.iaNiveau)}", style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        ClaudeClient.Niveau.QUALITE to "Qualité maximale",
                        ClaudeClient.Niveau.EQUILIBRE to "Équilibré",
                        ClaudeClient.Niveau.ECONOMIE to "Économique",
                    ).forEach { (niveau, libelle) ->
                        BoutonSecondaire(
                            libelle,
                            onClick = { reglages.iaNiveau = niveau; reglages.iaModele = ""; rafraichir() },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            ChampTexte(
                "Modèle (vide = choisi automatiquement chez le fournisseur)",
                reglages.iaModele,
                onValeur = { reglages.iaModele = it },
                aide = "Le modèle réellement retenu est relu dans la liste du fournisseur : les identifiants changent souvent.",
            )
            if (modeles.isNotEmpty()) {
                Text("Modèles disponibles (appuyez pour choisir) :", style = MaterialTheme.typography.bodyMedium, color = Couleurs.gris)
                modeles.take(10).forEach { m ->
                    BoutonSecondaire(
                        (if (reglages.iaModele == m) "✓ " else "") + m,
                        onClick = { reglages.iaModele = m; rafraichir() },
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
                                compatibleOpenAi = fournisseur == MoteurDictee.Fournisseur.AUTRE,
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
            if (reglages.iaCle != null) {
                BoutonSecondaire(
                    "Supprimer la clé",
                    onClick = {
                        reglages.iaCle = null
                        reglages.iaConsentement = false
                        onMessage("Clé supprimée. Retour au mode gratuit.")
                        rafraichir()
                    },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("Dictées assistées par un modèle : ${reglages.iaDictees}", style = MaterialTheme.typography.bodyMedium, color = Couleurs.gris)
    }
}
