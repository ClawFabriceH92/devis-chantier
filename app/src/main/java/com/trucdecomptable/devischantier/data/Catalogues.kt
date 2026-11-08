package com.trucdecomptable.devischantier.data

import android.content.Context
import com.trucdecomptable.devischantier.util.Diag
import org.json.JSONObject

/**
 * Catalogues de départ par métier : l'artisan n'a plus qu'à ajuster ses prix
 * au lieu de tout créer. Les fichiers vivent dans assets/catalogues.
 */
data class CatalogueDepart(
    val id: String,
    val metier: String,
    val familles: List<FamilleDepart>,
) {
    val nombreArticles: Int get() = familles.sumOf { f -> f.sousFamilles.sumOf { it.articles.size } }
}

data class FamilleDepart(val nom: String, val sousFamilles: List<SousFamilleDepart>)

data class SousFamilleDepart(val nom: String, val articles: List<ArticleDepart>)

data class ArticleDepart(
    val libelle: String,
    val unite: String,
    val prixHT: Double,
    val tauxTva: Double,
    val nature: String,
    val synonymes: String,
)

object Catalogues {

    val METIERS = listOf(
        "plombier" to "Plombier",
        "electricien" to "Électricien",
        "peintre" to "Peintre",
        "carreleur" to "Carreleur",
        "menuisier" to "Menuisier",
    )

    /** Pur, testable : lit le JSON d'un catalogue. */
    fun parse(id: String, corps: String): CatalogueDepart? = runCatching {
        val racine = JSONObject(corps)
        val familles = mutableListOf<FamilleDepart>()
        val arr = racine.optJSONArray("familles") ?: return null
        for (i in 0 until arr.length()) {
            val f = arr.optJSONObject(i) ?: continue
            val sous = mutableListOf<SousFamilleDepart>()
            f.optJSONArray("sousFamilles")?.let { s ->
                for (j in 0 until s.length()) {
                    val sf = s.optJSONObject(j) ?: continue
                    val articles = mutableListOf<ArticleDepart>()
                    sf.optJSONArray("articles")?.let { a ->
                        for (k in 0 until a.length()) {
                            val art = a.optJSONObject(k) ?: continue
                            val libelle = art.optString("libelle")
                            if (libelle.isBlank()) continue
                            articles.add(
                                ArticleDepart(
                                    libelle = libelle,
                                    unite = art.optString("unite", "u"),
                                    prixHT = art.optDouble("prix_ht", 0.0),
                                    tauxTva = art.optDouble("tva", 20.0),
                                    nature = art.optString("nature", "fourniture"),
                                    synonymes = art.optString("synonymes", ""),
                                ),
                            )
                        }
                    }
                    sous.add(SousFamilleDepart(sf.optString("nom", "Divers"), articles))
                }
            }
            familles.add(FamilleDepart(f.optString("nom", "Divers"), sous))
        }
        CatalogueDepart(id, racine.optString("metier", id), familles)
    }.onFailure { Diag.record("catalogue", it) }.getOrNull()

    fun lire(context: Context, id: String): CatalogueDepart? = runCatching {
        val corps = context.assets.open("catalogues/$id.json").bufferedReader().use { it.readText() }
        parse(id, corps)
    }.onFailure { Diag.record("catalogue/$id", it) }.getOrNull()

    fun libelleMetier(id: String): String = METIERS.firstOrNull { it.first == id }?.second ?: id

    /**
     * Installe un catalogue de départ : familles, sous-familles et articles.
     * [ajustementPct] permet d'appliquer une hausse ou une baisse globale des
     * prix proposés avant de valider.
     */
    suspend fun installer(dao: Dao, catalogue: CatalogueDepart, ajustementPct: Double = 0.0): Int {
        val facteur = 1 + ajustementPct / 100.0
        var installes = 0
        for (famille in catalogue.familles) {
            val idFamille = dao.enregistrerFamille(Famille(nom = famille.nom, metier = catalogue.id))
            for (sous in famille.sousFamilles) {
                val idSous = dao.enregistrerFamille(
                    Famille(nom = sous.nom, parentId = idFamille, metier = catalogue.id),
                )
                for (article in sous.articles) {
                    dao.enregistrerArticle(
                        Article(
                            familleId = idSous,
                            libelle = article.libelle,
                            unite = article.unite,
                            prixHT = (article.prixHT * facteur * 100).toLong() / 100.0,
                            tauxTva = article.tauxTva,
                            nature = article.nature,
                            synonymes = article.synonymes,
                        ),
                    )
                    installes++
                }
            }
        }
        Diag.record("catalogue", "${catalogue.metier} : $installes articles installés")
        return installes
    }
}
