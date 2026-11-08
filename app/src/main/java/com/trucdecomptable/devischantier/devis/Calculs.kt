package com.trucdecomptable.devischantier.devis

import com.trucdecomptable.devischantier.data.Article
import com.trucdecomptable.devischantier.data.Ligne
import com.trucdecomptable.devischantier.util.Formats
import java.util.Locale
import kotlin.math.max

/** Totaux d'une ligne, TVA comprise. */
data class TotauxLigne(
    val ht: Double,
    val tva: Double,
    val ttc: Double,
)

/** Totaux d'un devis : par taux de TVA et par section, pour l'aperçu et le PDF. */
data class Totaux(
    val totalHT: Double,
    val totalTVA: Double,
    val totalTTC: Double,
    val tvaParTaux: Map<Double, Double>,
    val sousTotauxSections: Map<String, Double>,
    val coutAchat: Double,
) {
    val marge: Double get() = Formats.arrondi2(totalHT - coutAchat)
    val tauxMarge: Double get() = if (coutAchat > 0.0) marge / coutAchat * 100 else 0.0
    val tauxMarque: Double get() = if (totalHT > 0.0) marge / totalHT * 100 else 0.0
    val coefficient: Double get() = if (coutAchat > 0.0) totalHT / coutAchat else 0.0
    val sansCoutAchat: Int get() = 0
}

data class Marge(
    val margeBrute: Double,
    val tauxMarge: Double,
    val tauxMarque: Double,
    val coefficient: Double,
)

/**
 * Tous les calculs du devis, sans Android : testables en JUnit et rejoués à
 * l'identique par l'aperçu, le PDF et l'export au comptable.
 */
object Calculs {

    /** Remise de ligne appliquée au prix unitaire, puis remise globale du devis. */
    fun ligne(
        quantite: Double,
        prixUnitaireHT: Double,
        tauxTva: Double,
        remisePct: Double = 0.0,
        remiseGlobalePct: Double = 0.0,
    ): TotauxLigne {
        val brut = quantite * prixUnitaireHT
        val apresRemiseLigne = brut * (1 - remisePct.coerceIn(0.0, 100.0) / 100.0)
        val ht = Formats.arrondi2(apresRemiseLigne * (1 - remiseGlobalePct.coerceIn(0.0, 100.0) / 100.0))
        val tva = Formats.arrondi2(ht * tauxTva.coerceAtLeast(0.0) / 100.0)
        return TotauxLigne(ht, tva, Formats.arrondi2(ht + tva))
    }

    fun ligne(ligne: Ligne, remiseGlobalePct: Double = 0.0): TotauxLigne =
        ligne(ligne.quantite, ligne.prixUnitaireHT, ligne.tauxTva, ligne.remisePct, remiseGlobalePct)

    fun totaux(lignes: List<Ligne>, remiseGlobalePct: Double = 0.0): Totaux {
        var totalHT = 0.0
        var totalTVA = 0.0
        val parTaux = linkedMapOf<Double, Double>()
        val sections = linkedMapOf<String, Double>()
        var cout = 0.0

        for (l in lignes) {
            val t = ligne(l, remiseGlobalePct)
            totalHT += t.ht
            totalTVA += t.tva
            parTaux[l.tauxTva] = (parTaux[l.tauxTva] ?: 0.0) + t.tva
            val nomSection = l.section?.takeIf { it.isNotBlank() } ?: ""
            sections[nomSection] = (sections[nomSection] ?: 0.0) + t.ht
            cout += l.quantite * l.coutAchatUnitaire
        }

        val ht = Formats.arrondi2(totalHT)
        val tva = Formats.arrondi2(totalTVA)
        return Totaux(
            totalHT = ht,
            totalTVA = tva,
            totalTTC = Formats.arrondi2(ht + tva),
            tvaParTaux = parTaux.mapValues { Formats.arrondi2(it.value) },
            sousTotauxSections = sections.mapValues { Formats.arrondi2(it.value) },
            coutAchat = Formats.arrondi2(cout),
        )
    }

    fun marge(prixVenteHT: Double, coutAchatHT: Double): Marge {
        val margeBrute = Formats.arrondi2(prixVenteHT - coutAchatHT)
        return Marge(
            margeBrute = margeBrute,
            tauxMarge = if (coutAchatHT > 0.0) margeBrute / coutAchatHT * 100 else 0.0,
            tauxMarque = if (prixVenteHT > 0.0) margeBrute / prixVenteHT * 100 else 0.0,
            coefficient = if (coutAchatHT > 0.0) prixVenteHT / coutAchatHT else 0.0,
        )
    }

    /** Coût d'achat d'un article vendu : somme des achats liés, quantités comprises (M-03). */
    fun coutArticle(
        liens: List<Pair<Long, Double>>,
        achatsParId: Map<Long, Double>,
    ): Double = Formats.arrondi2(
        liens.sumOf { (achatId, quantite) -> (achatsParId[achatId] ?: 0.0) * quantite },
    )

    /** « D-2026-0001 » : préfixe, année, compteur sur 4 chiffres (F-13). */
    fun numeroSuivant(prefixe: String, annee: Int, existants: List<String>): String {
        val debut = "${prefixe.trim().ifBlank { "D" }}-$annee-"
        val max = existants
            .filter { it.startsWith(debut) }
            .mapNotNull { it.removePrefix(debut).toIntOrNull() }
            .maxOrNull() ?: 0
        return debut + String.format(Locale.FRANCE, "%04d", max + 1)
    }

    /** Indice de version : A, B, C… puis AA (F-15). */
    fun indiceSuivant(indice: String): String {
        val brut = indice.trim().uppercase(Locale.FRANCE)
        if (brut.isBlank()) return "A"
        val propre = brut
        val codes = propre.map { it - 'A' + 1 }
        if (codes.any { it < 1 || it > 26 }) return "A"
        val suivant = codes.toMutableList()
        var i = suivant.lastIndex
        while (i >= 0) {
            if (suivant[i] < 26) {
                suivant[i] += 1
                return suivant.joinToString("") { ('A' + it - 1).toString() }
            }
            suivant[i] = 1
            i--
        }
        return "A".repeat(propre.length + 1)
    }

    /** Une ligne est sous son prix d'achat (M-05). */
    fun sousLeCout(ligne: Ligne): Boolean =
        ligne.coutAchatUnitaire > 0.0 && ligne.prixUnitaireHT < ligne.coutAchatUnitaire

    /** Effet d'une remise sur le TTC, recalculé en direct (M-05). */
    fun ttcAvecRemise(lignes: List<Ligne>, remisePct: Double): Double =
        totaux(lignes, remisePct).totalTTC

    fun montantAcompte(totalTTC: Double, acomptePct: Double): Double =
        Formats.arrondi2(totalTTC * max(0.0, acomptePct) / 100.0)

    fun ligneDepuisArticle(article: Article, quantite: Double, section: String? = null): Ligne = Ligne(
        section = section,
        designation = article.libelle,
        unite = article.unite,
        quantite = quantite,
        prixUnitaireHT = article.prixHT,
        tauxTva = article.tauxTva,
        articleId = article.id,
    )
}
