package com.trucdecomptable.devischantier.voice

import android.content.Context
import com.trucdecomptable.devischantier.api.ClaudeClient
import com.trucdecomptable.devischantier.api.PropositionLigne
import com.trucdecomptable.devischantier.data.Article
import com.trucdecomptable.devischantier.data.Reglages
import com.trucdecomptable.devischantier.ia.ExtractionLocale
import com.trucdecomptable.devischantier.ia.LlmLocal
import com.trucdecomptable.devischantier.ia.ModeleLocal
import com.trucdecomptable.devischantier.util.Diag

/**
 * Chaîne de la dictée (§4) : les règles locales d'abord, complétées par un
 * modèle de langage quand l'artisan l'a choisi :
 *
 * - « Sur le téléphone » (défaut) : LFM3-1.7B via llama.cpp, hors ligne, gratuit,
 *   une fois le modèle téléchargé depuis les réglages ;
 * - Claude (Anthropic) ou un service compatible OpenAI, avec la clé de l'artisan.
 *
 * Sans modèle, sans clé, ou si l'appel échoue, on retombe sur le résultat des
 * règles avec un message clair (V-33). Rien n'est ajouté sans validation.
 */
object MoteurDictee {

    object Mode {
        const val REGLES = "RÈGLES"
        const val IA_LOCALE = "IA LOCALE"
        const val IA_CLOUD = "IA"
    }

    object Fournisseur {
        const val LOCAL = "LOCAL"
        const val CLAUDE = "CLAUDE"
        const val AUTRE = "AUTRE"
    }

    data class Resultat(
        val propositions: List<PropositionLigne>,
        val commandes: List<Commande>,
        val mode: String,
        val message: String = "",
        val dureeMs: Long = 0L,
    )

    suspend fun traiter(
        texte: String,
        articles: List<Article>,
        reglages: Reglages,
        contexte: Context? = null,
    ): Resultat {
        val local = DicteeParser.analyser(texte)
        val locales = local.lignes.map { propositionLocale(it, articles) }
        val replis = Resultat(locales, local.commandes, Mode.REGLES, "Mode gratuit : lignes déduites par les règles du téléphone.")

        return when (reglages.iaFournisseur) {
            Fournisseur.LOCAL -> traiterSurLeTelephone(texte, articles, reglages, contexte, local, replis)
            else -> traiterDansLeCloud(texte, articles, reglages, local, replis)
        }
    }

    private suspend fun traiterSurLeTelephone(
        texte: String,
        articles: List<Article>,
        reglages: Reglages,
        contexte: Context?,
        local: ResultatDictee,
        replis: Resultat,
    ): Resultat {
        if (contexte == null || !ModeleLocal.pret(contexte)) {
            return replis.copy(
                message = "Modèle embarqué non installé : lignes déduites par les règles. " +
                    "Téléchargez-le dans les réglages pour une dictée mieux comprise.",
            )
        }
        val candidats = ExtractionLocale.candidats(texte, articles)
        val debut = System.currentTimeMillis()
        val reponse = runCatching {
            LlmLocal.generer(
                context = contexte,
                fichier = ModeleLocal.fichier(contexte),
                systeme = ExtractionLocale.promptSysteme(),
                utilisateur = ExtractionLocale.promptUtilisateur(texte, candidats),
                grammaire = ExtractionLocale.GRAMMAIRE,
            )
        }
        return reponse.fold(
            onSuccess = { r ->
                val propositions = ExtractionLocale.parse(r.texte).map { rattacher(it, articles) }
                if (propositions.isEmpty()) {
                    Diag.record("dictee/locale", "aucune ligne lue dans : ${r.texte.take(200)}")
                    return replis.copy(message = "Le modèle n'a rien compris : lignes déduites par les règles.", dureeMs = r.dureeMs)
                }
                reglages.iaDictees = reglages.iaDictees + 1
                Diag.record("dictee", "IA locale : ${propositions.size} ligne(s) en ${r.dureeMs} ms")
                Resultat(
                    propositions = propositions,
                    commandes = local.commandes,
                    mode = Mode.IA_LOCALE,
                    message = "Compréhension par le modèle embarqué (${ModeleLocal.MODELE_PAR_DEFAUT.nom}, hors ligne) " +
                        "en ${String.format(java.util.Locale.FRANCE, "%.1f", r.dureeMs / 1000.0)} s.",
                    dureeMs = r.dureeMs,
                )
            },
            onFailure = { erreur ->
                Diag.record("dictee/locale", erreur)
                replis.copy(
                    message = "Modèle embarqué indisponible (${erreur.message}). Lignes déduites par les règles.",
                    dureeMs = System.currentTimeMillis() - debut,
                )
            },
        )
    }

    private suspend fun traiterDansLeCloud(
        texte: String,
        articles: List<Article>,
        reglages: Reglages,
        local: ResultatDictee,
        replis: Resultat,
    ): Resultat {
        val cle = reglages.iaCle
        if (cle.isNullOrBlank() || !reglages.iaConsentement) {
            return replis.copy(
                message = if (cle.isNullOrBlank()) {
                    "Mode gratuit : lignes déduites par les règles du téléphone."
                } else {
                    "Information sur les données envoyées non confirmée : mode gratuit."
                },
            )
        }

        val debut = System.currentTimeMillis()
        val reponse = ClaudeClient.extraireLignes(
            cle = cle,
            modele = reglages.iaModele,
            texte = texte,
            articles = articles,
            baseUrl = reglages.iaBaseUrl.ifBlank { ClaudeClient.BASE },
            compatibleOpenAi = reglages.iaFournisseur == Fournisseur.AUTRE,
            niveau = reglages.iaNiveau,
        )

        return reponse.fold(
            onSuccess = { propositions ->
                reglages.iaDictees = reglages.iaDictees + 1
                Diag.record("dictee", "IA : ${propositions.size} ligne(s)")
                Resultat(
                    propositions = propositions.map { rattacher(it, articles) },
                    commandes = local.commandes,
                    mode = Mode.IA_CLOUD,
                    message = "Compréhension assistée par ${libelleFournisseur(reglages.iaFournisseur)}.",
                    dureeMs = System.currentTimeMillis() - debut,
                )
            },
            onFailure = { erreur ->
                Diag.record("dictee/ia", erreur)
                replis.copy(
                    message = "IA indisponible (${erreur.message}). Lignes déduites localement.",
                    dureeMs = System.currentTimeMillis() - debut,
                )
            },
        )
    }

    /** Une ligne des règles : on complète avec l'article reconnu et son prix. */
    fun propositionLocale(ligne: LigneDictee, articles: List<Article>): PropositionLigne {
        val trouve = Rapprochement.meilleur(ligne.designation, articles)
        val article = trouve?.first
        val score = trouve?.second ?: 0.3
        return PropositionLigne(
            ligne = ligne.copy(
                designation = article?.libelle ?: ligne.designation,
                unite = ligne.unite ?: article?.unite,
                prixUnitaireHT = ligne.prixUnitaireHT ?: article?.prixHT,
                tauxTva = ligne.tauxTva ?: article?.tauxTva,
            ),
            articleId = article?.id,
            confiance = if (article != null) score else 0.35,
            source = "REGLE",
        )
    }

    /**
     * Ligne renvoyée par un modèle : on complète avec le prix du catalogue local
     * (M-09). Un article_id qui ne correspond à aucun article (identifiant
     * inventé) est ignoré, et le rapprochement par libellé prend le relais.
     */
    fun rattacher(proposition: PropositionLigne, articles: List<Article>): PropositionLigne {
        val parId = proposition.articleId?.let { id -> articles.firstOrNull { it.id == id } }
        val parNom = parId ?: Rapprochement.meilleur(proposition.ligne.designation, articles)?.first
        return proposition.copy(
            articleId = parNom?.id,
            ligne = proposition.ligne.copy(
                designation = parNom?.libelle ?: proposition.ligne.designation,
                unite = proposition.ligne.unite ?: parNom?.unite,
                prixUnitaireHT = proposition.ligne.prixUnitaireHT ?: parNom?.prixHT,
                tauxTva = proposition.ligne.tauxTva ?: parNom?.tauxTva,
            ),
        )
    }

    fun libelleFournisseur(code: String): String = when (code) {
        Fournisseur.LOCAL -> "le modèle embarqué (${ModeleLocal.MODELE_PAR_DEFAUT.nom}, sur le téléphone)"
        Fournisseur.CLAUDE -> "Claude (Anthropic)"
        Fournisseur.AUTRE -> "un service compatible OpenAI"
        else -> code
    }

    fun confianceTexte(confiance: Double): String = when {
        confiance >= 0.75 -> "Sûr"
        confiance >= 0.45 -> "Probable"
        else -> "À vérifier"
    }
}
