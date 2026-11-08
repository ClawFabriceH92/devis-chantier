package com.trucdecomptable.devischantier.voice

import com.trucdecomptable.devischantier.api.ClaudeClient
import com.trucdecomptable.devischantier.api.PropositionLigne
import com.trucdecomptable.devischantier.data.Article
import com.trucdecomptable.devischantier.data.Reglages
import com.trucdecomptable.devischantier.util.Diag

/**
 * Chaîne de la dictée (§4) : les règles locales d'abord, complétées par le
 * fournisseur d'IA quand une clé est renseignée. Sans clé, ou si l'appel
 * échoue, on retombe sur le résultat local avec un message clair (V-33).
 */
object MoteurDictee {

    data class Resultat(
        val propositions: List<PropositionLigne>,
        val commandes: List<Commande>,
        val mode: String,
        val message: String = "",
    )

    suspend fun traiter(
        texte: String,
        articles: List<Article>,
        reglages: Reglages,
    ): Resultat {
        val local = DicteeParser.analyser(texte)
        val locales = local.lignes.map { propositionLocale(it, articles) }

        val cle = reglages.iaCle
        if (cle.isNullOrBlank() || !reglages.iaConsentement) {
            return Resultat(
                propositions = locales,
                commandes = local.commandes,
                mode = "RÈGLES",
                message = if (cle.isNullOrBlank()) {
                    "Mode gratuit : lignes déduites par les règles du téléphone."
                } else {
                    "Information sur les données envoyées non confirmée : mode gratuit."
                },
            )
        }

        val reponse = ClaudeClient.extraireLignes(
            cle = cle,
            modele = reglages.iaModele,
            texte = texte,
            articles = articles,
            baseUrl = reglages.iaBaseUrl.ifBlank { ClaudeClient.BASE },
            compatibleOpenAi = reglages.iaFournisseur == "AUTRE",
            niveau = reglages.iaNiveau,
        )

        return reponse.fold(
            onSuccess = { propositions ->
                reglages.iaDictees = reglages.iaDictees + 1
                Diag.record("dictee", "IA : ${propositions.size} ligne(s)")
                Resultat(
                    propositions = propositions.map { rattacher(it, articles) },
                    commandes = local.commandes,
                    mode = "IA",
                    message = "Compréhension assistée par ${libelleFournisseur(reglages.iaFournisseur)}.",
                )
            },
            onFailure = { erreur ->
                Diag.record("dictee/ia", erreur)
                Resultat(
                    propositions = locales,
                    commandes = local.commandes,
                    mode = "RÈGLES",
                    message = "IA indisponible (${erreur.message}). Lignes déduites localement.",
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

    /** Ligne renvoyée par l'IA : on complète avec le prix du catalogue local (M-09). */
    fun rattacher(proposition: PropositionLigne, articles: List<Article>): PropositionLigne {
        val parId = proposition.articleId?.let { id -> articles.firstOrNull { it.id == id } }
        val parNom = parId ?: Rapprochement.meilleur(proposition.ligne.designation, articles)?.first
        return proposition.copy(
            articleId = proposition.articleId ?: parNom?.id,
            ligne = proposition.ligne.copy(
                designation = parNom?.libelle ?: proposition.ligne.designation,
                unite = proposition.ligne.unite ?: parNom?.unite,
                prixUnitaireHT = proposition.ligne.prixUnitaireHT ?: parNom?.prixHT,
                tauxTva = proposition.ligne.tauxTva ?: parNom?.tauxTva,
            ),
        )
    }

    fun libelleFournisseur(code: String): String = when (code) {
        "CLAUDE" -> "Claude (Anthropic)"
        "AUTRE" -> "un service compatible OpenAI"
        else -> code
    }

    fun confianceTexte(confiance: Double): String = when {
        confiance >= 0.75 -> "Sûr"
        confiance >= 0.45 -> "Probable"
        else -> "À vérifier"
    }
}
