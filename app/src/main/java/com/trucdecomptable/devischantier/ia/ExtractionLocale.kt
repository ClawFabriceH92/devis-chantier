package com.trucdecomptable.devischantier.ia

import com.trucdecomptable.devischantier.api.ClaudeClient
import com.trucdecomptable.devischantier.api.PropositionLigne
import com.trucdecomptable.devischantier.data.Article
import com.trucdecomptable.devischantier.voice.DicteeParser
import com.trucdecomptable.devischantier.voice.Rapprochement
import java.util.Locale

/**
 * Tout ce que le modèle embarqué reçoit et renvoie, sans Android : testable en
 * JUnit. Un petit modèle (1,7 milliard de paramètres) travaille bien quand on
 * lui donne peu de choses à lire et un formulaire à remplir :
 *
 * 1. le catalogue n'est pas envoyé en entier : les règles locales présélectionnent
 *    les articles qui ressemblent à la dictée (plus les favoris) ;
 * 2. la sortie est contrainte par une grammaire GBNF : un tableau JSON, clés dans
 *    un ordre fixe, nombres bornés, jamais de texte libre ni de clé inventée ;
 * 3. le résultat repasse par le rapprochement local (prix du catalogue, M-09).
 *
 * Ce qui ne part JAMAIS, même sur le téléphone : prix d'achat et marges.
 */
object ExtractionLocale {

    const val SOURCE = "LOCAL"
    const val MAX_ARTICLES = 24
    const val MAX_LIGNES = 15

    /**
     * Grammaire GBNF du tableau de lignes. Les mêmes clés que le mode cloud
     * ([ClaudeClient.parseLignes] relit les deux). Les répétitions sont bornées
     * pour qu'un modèle qui boucle s'arrête de lui-même.
     */
    val GRAMMAIRE: String = """
        root ::= "[" sp (ligne (sp "," sp ligne){0,${MAX_LIGNES - 1}})? sp "]"
        ligne ::= "{" sp "\"designation\"" sp ":" sp texte sp "," sp "\"quantite\"" sp ":" sp nombre sp "," sp "\"unite\"" sp ":" sp unite sp "," sp "\"prix_ht\"" sp ":" sp prix sp "," sp "\"tva\"" sp ":" sp tva sp "," sp "\"article_id\"" sp ":" sp identifiant sp "," sp "\"confiance\"" sp ":" sp confiance sp "}"
        texte ::= "\"" car{1,90} "\""
        car ::= [^"\\\x7F\x00-\x1F] | "\\" (["\\/bfnrt] | "u" [0-9a-fA-F]{4})
        unite ::= "\"" [^"\\\x7F\x00-\x1F]{1,12} "\""
        nombre ::= ("0" | [1-9] [0-9]{0,6}) ("." [0-9]{1,3})?
        prix ::= nombre | "null"
        tva ::= ("0" | "2.1" | "5.5" | "10" | "20") | "null"
        identifiant ::= [1-9] [0-9]{0,11} | "null"
        confiance ::= ("0" ("." [0-9]{1,2})? | "1")
        sp ::= [ ]?
    """.trimIndent()

    fun promptSysteme(): String = """
        Tu es l'assistant d'un artisan du bâtiment en France. Tu transformes une dictée en lignes de devis.
        Réponds uniquement par un tableau JSON, sans aucun texte autour.
        Chaque ligne : {"designation", "quantite", "unite", "prix_ht", "tva", "article_id", "confiance"}.
        Règles :
        - une ligne par fourniture ou prestation dictée, dans l'ordre de la dictée, sans rien inventer ;
        - si la dictée correspond à un article du catalogue fourni, recopie exactement son libellé, son unité, son prix_ht, sa tva et son id dans article_id ;
        - sinon article_id vaut null, prix_ht vaut null (sauf si un prix est dicté) et tva vaut null ;
        - quantite est un nombre ; « deux » vaut 2, « une douzaine » vaut 12, « trois heures » vaut 3 avec l'unité h ;
        - unites possibles : u, h, m², ml, m, forfait, kg, l, sac, lot, jour ;
        - prix_ht est un prix unitaire hors taxe, jamais un total ;
        - confiance entre 0 et 1 : 0.9 pour un article du catalogue évident, 0.5 pour une ligne libre.
    """.trimIndent()

    /** Le catalogue réduit, une ligne par article : id | libellé | unité | prix HT | TVA. */
    fun presenterCatalogue(articles: List<Article>): String =
        if (articles.isEmpty()) {
            "(catalogue vide)"
        } else {
            articles.joinToString("\n") { a ->
                "${a.id} | ${a.libelle} | ${a.unite} | ${nombre(a.prixHT)} | ${nombre(a.tauxTva)}"
            }
        }

    fun promptUtilisateur(dictee: String, candidats: List<Article>): String =
        "Catalogue (id | libellé | unité | prix HT | TVA) :\n" +
            presenterCatalogue(candidats) +
            "\n\nDictée :\n« ${dictee.trim()} »"

    /**
     * Présélection : les articles dont le libellé ou les synonymes partagent des
     * mots avec la dictée (segment par segment, puis la phrase entière), puis les
     * favoris et les plus utilisés pour compléter, dans la limite de [max].
     */
    fun candidats(dictee: String, articles: List<Article>, max: Int = MAX_ARTICLES): List<Article> {
        if (articles.isEmpty()) return emptyList()
        val segments = DicteeParser.decouper(dictee) + dictee
        val scores = mutableMapOf<Long, Double>()
        for (segment in segments) {
            val designation = DicteeParser.analyserSegment(segment).designation.ifBlank { segment }
            for (article in articles) {
                val s = maxOf(Rapprochement.score(designation, article), Rapprochement.score(segment, article))
                if (s > 0.0) scores[article.id] = maxOf(scores[article.id] ?: 0.0, s)
            }
        }
        val parScore = articles
            .filter { (scores[it.id] ?: 0.0) >= 0.2 }
            .sortedByDescending { scores[it.id] }
        val complement = articles
            .filter { it.id !in parScore.map { a -> a.id } }
            .sortedWith(compareByDescending<Article> { it.favori }.thenByDescending { it.usages })
        return (parScore + complement).take(max)
    }

    /** La sortie du modèle, relue comme en mode cloud, puis étiquetée « LOCAL ». */
    fun parse(sortie: String): List<PropositionLigne> =
        ClaudeClient.parseLignes(sortie, source = SOURCE)

    private fun nombre(valeur: Double): String =
        if (valeur == Math.rint(valeur)) valeur.toLong().toString()
        else String.format(Locale.ROOT, "%.2f", valeur).trimEnd('0')
}
