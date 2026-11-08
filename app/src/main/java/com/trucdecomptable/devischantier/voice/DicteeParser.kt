package com.trucdecomptable.devischantier.voice

import com.trucdecomptable.devischantier.data.Article
import com.trucdecomptable.devischantier.util.Formats
import com.trucdecomptable.devischantier.util.NombresEnLettres
import java.text.Normalizer
import java.util.Locale

/** Une ligne comprise dans une dictée, avant validation par l'artisan (V-05). */
data class LigneDictee(
    val designation: String,
    val quantite: Double = 1.0,
    val unite: String? = null,
    val prixUnitaireHT: Double? = null,
    val tauxTva: Double? = null,
    val texteOriginal: String = "",
)

/** Commandes simples reconnues dans la dictée (V-06). */
sealed interface Commande {
    data class NouvelleSection(val nom: String) : Commande
    data object SupprimerDerniereLigne : Commande
    data class Remise(val pourcentage: Double) : Commande
}

data class ResultatDictee(
    val lignes: List<LigneDictee> = emptyList(),
    val commandes: List<Commande> = emptyList(),
)

/**
 * Extraction des lignes d'une phrase dictée, par des règles locales, sans
 * réseau ni clé API : c'est le mode gratuit, complet par construction (V-30).
 * Le mode « amélioré » (Claude) réutilise exactement ces structures.
 */
object DicteeParser {

    private val UNITES = listOf(
        "mètres carrés" to "m²",
        "metres carres" to "m²",
        "mètre carré" to "m²",
        "metre carre" to "m²",
        "m2" to "m²",
        "mètres linéaires" to "ml",
        "metres lineaires" to "ml",
        "mètre linéaire" to "ml",
        "metre lineaire" to "ml",
        "ml" to "ml",
        "heures" to "h",
        "heure" to "h",
        "h" to "h",
        "forfaits" to "forfait",
        "forfait" to "forfait",
        "unités" to "u",
        "unites" to "u",
        "unité" to "u",
        "unite" to "u",
        "kilos" to "kg",
        "kilo" to "kg",
        "kg" to "kg",
        "litres" to "l",
        "litre" to "l",
        "sacs" to "sac",
        "sac" to "sac",
        "lots" to "lot",
        "lot" to "lot",
    )

    private val MOTS_VIDES = setOf(
        "de", "du", "des", "le", "la", "les", "un", "une", "pour", "avec", "et", "sur",
        "au", "aux", "en", "dans", "il", "faut", "ajoute", "ajouter", "mets", "mettre",
        "prends", "prendre", "svp", "s'il", "te", "plait", "plaît", "donc", "environ",
    )

    /** « deux mitigeurs lavabo, trois heures de main d'œuvre » -> lignes. */
    fun analyser(phrase: String): ResultatDictee {
        val commandes = mutableListOf<Commande>()
        val lignes = mutableListOf<LigneDictee>()

        for (morceau in decouper(phrase)) {
            val texte = morceau.trim()
            if (texte.isBlank()) continue
            if (texte.endsWith("?") || texte.contains("dernière ligne")) continue
            val commande = reconnaitreCommande(texte)
            if (commande != null) {
                commandes.add(commande)
                continue
            }
            lignes.add(analyserSegment(texte))
        }
        return ResultatDictee(lignes.filter { it.designation.isNotBlank() }, commandes)
    }

    fun decouper(phrase: String): List<String> {
        val remplace = phrase
            .replace(" ; ", "|")
            .replace(";", "|")
            .replace(" puis ", "|")
            .replace(", ", "|")
            .replace(" et ", "|")
        return remplace.split("|", "\n").map { it.trim() }.filter { it.isNotBlank() }
    }

    fun reconnaitreCommande(texte: String): Commande? {
        val t = normaliser(texte)
        if (t.startsWith("nouvelle section") || t.startsWith("section ")) {
            val nom = texte.substringAfter("section", "").trim().removePrefix(":").trim()
            if (nom.isNotBlank()) return Commande.NouvelleSection(nom.replaceFirstChar { it.uppercase() })
        }
        if (t.contains("supprime la derniere ligne") || t.contains("enleve la derniere ligne") ||
            t.contains("retire la derniere ligne")
        ) {
            return Commande.SupprimerDerniereLigne
        }
        Regex("""remise (?:de )?(\d+(?:[.,]\d+)?) ?%""").find(t)?.let {
            Formats.montant(it.groupValues[1])?.let { p -> return Commande.Remise(p) }
        }
        return null
    }

    fun analyserSegment(segment: String): LigneDictee {
        var reste = " $segment "
        var quantite: Double? = null
        var unite: String? = null
        var prix: Double? = null
        var tva: Double? = null

        // TVA dictée : « TVA 10 % »
        Regex("""(?i)\btva\s*(?:à\s*)?(\d+(?:[.,]\d+)?)\s*%""").find(reste)?.let {
            tva = Formats.montant(it.groupValues[1])
            reste = reste.replace(it.value, " ")
        }

        // Prix dicté : « à 85 euros hors taxe », « 8,50 euros HT »
        Regex("""(?i)(?:à|a|pour)?\s*(\d+(?:[.,]\d+)?)\s*(?:euros?|€)\s*(ht|hors taxe|ttc)?""").find(reste)?.let {
            prix = Formats.montant(it.groupValues[1])
            reste = reste.replace(it.value, " ")
        }

        // Quantité en tête, en chiffres ou en lettres
        val mots = reste.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.toMutableList()
        if (mots.isNotEmpty()) {
            mots[0].lowercase(Locale.FRANCE).toDoubleOrNull()?.let {
                quantite = it
                mots.removeAt(0)
            } ?: run {
                val valeur = NombresEnLettres.valeur(normaliser(mots[0]))
                if (valeur != null) {
                    quantite = valeur
                    mots.removeAt(0)
                }
            }
        }

        // Unité, juste après la quantité ou à la fin. Certaines s'écrivent en
        // deux mots (« mètres carrés », « mètres linéaires ») : on teste la
        // paire avant le mot seul.
        var indexUnite = -1
        var longueurUnite = 1
        for (i in mots.indices) {
            val paire = if (i + 1 < mots.size) normaliser("${mots[i]} ${mots[i + 1]}") else ""
            val trouve = UNITES.firstOrNull { (cle, _) -> normaliser(cle) == paire }
            if (trouve != null) {
                unite = trouve.second
                indexUnite = i
                longueurUnite = 2
                break
            }
            val seul = UNITES.firstOrNull { (cle, _) -> normaliser(cle) == normaliser(mots[i]) }
            if (seul != null) {
                unite = seul.second
                indexUnite = i
                longueurUnite = 1
                break
            }
        }
        if (indexUnite >= 0) {
            repeat(longueurUnite) { mots.removeAt(indexUnite) }
        }

        val designation = mots
            .joinToString(" ")
            .replace(Regex("(?i)\\b(hors taxe|ht|ttc|h t|tva)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim(',', '.', '-')

        return LigneDictee(
            designation = designation.replaceFirstChar { it.uppercase(Locale.FRANCE) },
            quantite = quantite ?: 1.0,
            unite = unite,
            prixUnitaireHT = prix,
            tauxTva = tva,
            texteOriginal = segment,
        )
    }

    fun normaliser(texte: String): String {
        val sansAccent = Normalizer.normalize(texte, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return sansAccent.lowercase(Locale.FRANCE)
            .replace(Regex("[^a-z0-9%² ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}

/** Rapprochement d'une désignation dictée avec le catalogue (V-04). */
object Rapprochement {

    private val MOTS_VIDES = setOf(
        "de", "du", "des", "le", "la", "les", "un", "une", "pour", "avec", "et", "sur",
        "au", "aux", "en", "d", "l", "s",
    )

    fun jetons(texte: String): Set<String> =
        DicteeParser.normaliser(texte).split(" ").filter { it.length > 1 && it !in MOTS_VIDES }.toSet()

    /**
     * Score [0,1] : mots communs pondérés par leur spécificité, plus les
     * synonymes saisis sur la fiche article (F-21).
     */
    fun score(designation: String, article: Article): Double {
        val cible = jetons(designation)
        if (cible.isEmpty()) return 0.0
        val candidats = jetons(article.libelle) +
            article.synonymes.split(',', ';').flatMap { jetons(it) }
        if (candidats.isEmpty()) return 0.0
        val communs = cible.intersect(candidats)
        if (communs.isEmpty()) return 0.0
        val rappel = communs.size.toDouble() / candidats.size
        val precision = communs.size.toDouble() / cible.size
        val f1 = if (rappel + precision == 0.0) 0.0 else 2 * rappel * precision / (rappel + precision)
        // Un mot rare trouvé ensemble (ex. « mitigeur ») vaut mieux que « pose ».
        val bonus = communs.maxOfOrNull { it.length }?.let { (it - 3).coerceAtLeast(0) * 0.02 } ?: 0.0
        return (f1 + bonus).coerceAtMost(1.0)
    }

    /** Meilleur article au-dessus du seuil, sinon null : on proposera une ligne libre. */
    fun meilleur(designation: String, articles: List<Article>, seuil: Double = 0.45): Pair<Article, Double>? =
        articles
            .map { it to score(designation, it) }
            .filter { it.second >= seuil }
            .maxByOrNull { it.second }
}
