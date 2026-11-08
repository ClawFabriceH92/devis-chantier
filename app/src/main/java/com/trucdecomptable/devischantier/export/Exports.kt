package com.trucdecomptable.devischantier.export

import com.trucdecomptable.devischantier.data.Achat
import com.trucdecomptable.devischantier.data.Article
import com.trucdecomptable.devischantier.data.Client
import com.trucdecomptable.devischantier.data.Devis
import com.trucdecomptable.devischantier.data.Ligne
import com.trucdecomptable.devischantier.devis.Calculs
import com.trucdecomptable.devischantier.util.Formats
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ce que reçoit le comptable (E-11) : le PDF signé plus un fichier de données
 * qui permet de facturer sans ressaisie, prêt pour la facture électronique
 * (SIREN du client professionnel, lignes, taux de TVA, montants).
 */
object Exports {

    const val SEPARATEUR = ";"
    const val BOM = "\uFEFF"

    private val iso = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE)

    fun donneesComptable(
        devis: Devis,
        client: Client?,
        lignes: List<Ligne>,
        inclureMarge: Boolean = false,
    ): String {
        val totaux = Calculs.totaux(lignes, devis.remiseGlobalePct)
        val racine = JSONObject()
            .put("numero", devis.numero)
            .put("indice", devis.indice)
            .put("date", iso.format(Date(devis.dateDevis)))
            .put("objet", devis.objet)
            .put("statut", devis.statut)
            .put("client", JSONObject().apply {
                put("nom", devis.clientNom)
                put("professionnel", client?.professionnel ?: false)
                put("siren", client?.siren ?: JSONObject.NULL)
                put("siret", client?.siret ?: JSONObject.NULL)
                put("tva_intra", client?.tvaIntra ?: JSONObject.NULL)
                put("adresse", devis.clientAdresse ?: client?.adresse ?: JSONObject.NULL)
            })
            .put("total_ht", totaux.totalHT)
            .put("total_tva", totaux.totalTVA)
            .put("total_ttc", totaux.totalTTC)
            .put("acompte", devis.montantAcompte)
            .put("tva_par_taux", JSONObject().apply {
                totaux.tvaParTaux.forEach { (taux, montant) -> put(Formats.nombre(taux), montant) }
            })
            .put("mentions", devis.mentionsTexte)
            .put("lignes", JSONArray().apply {
                lignes.forEach { l ->
                    val t = Calculs.ligne(l, devis.remiseGlobalePct)
                    put(
                        JSONObject()
                            .put("section", l.section ?: JSONObject.NULL)
                            .put("designation", l.designation)
                            .put("quantite", l.quantite)
                            .put("unite", l.unite)
                            .put("prix_unitaire_ht", l.prixUnitaireHT)
                            .put("taux_tva", l.tauxTva)
                            .put("remise_pct", l.remisePct)
                            .put("total_ht", t.ht),
                    )
                }
            })
        if (inclureMarge) {
            racine.put("cout_achat", totaux.coutAchat).put("marge", totaux.marge)
        }
        return racine.toString(2)
    }

    fun csvDevis(devis: Devis, client: Client?, lignes: List<Ligne>): String {
        val sb = StringBuilder(BOM)
        sb.append("numero;indice;date;client;siren;objet;designation;quantite;unite;pu_ht;taux_tva;total_ht\n")
        val date = iso.format(Date(devis.dateDevis))
        for (l in lignes) {
            val t = Calculs.ligne(l, devis.remiseGlobalePct)
            sb.append(
                listOf(
                    devis.numero,
                    devis.indice,
                    date,
                    devis.clientNom,
                    client?.siren.orEmpty(),
                    devis.objet,
                    l.designation,
                    Formats.nombre(l.quantite),
                    l.unite,
                    montantCsv(l.prixUnitaireHT),
                    Formats.nombre(l.tauxTva),
                    montantCsv(t.ht),
                ).joinToString(SEPARATEUR) { echapper(it) },
            ).append('\n')
        }
        return sb.toString()
    }

    fun csvCatalogue(articles: List<Article>, familles: Map<Long, String>): String {
        val sb = StringBuilder(BOM)
        sb.append("famille;libelle;reference;unite;prix_ht;taux_tva;nature;synonymes\n")
        for (a in articles) {
            sb.append(
                listOf(
                    familles[a.familleId] ?: "",
                    a.libelle,
                    a.reference.orEmpty(),
                    a.unite,
                    montantCsv(a.prixHT),
                    Formats.nombre(a.tauxTva),
                    a.nature,
                    a.synonymes,
                ).joinToString(SEPARATEUR) { echapper(it) },
            ).append('\n')
        }
        return sb.toString()
    }

    fun csvAchats(achats: List<Achat>): String {
        val sb = StringBuilder(BOM)
        sb.append("libelle;famille;fournisseur;unite;prix_ht;date\n")
        for (a in achats) {
            sb.append(
                listOf(
                    a.libelle,
                    a.famille.orEmpty(),
                    a.fournisseur.orEmpty(),
                    a.unite,
                    montantCsv(a.prixHT),
                    iso.format(Date(a.datePrix)),
                ).joinToString(SEPARATEUR) { echapper(it) },
            ).append('\n')
        }
        return sb.toString()
    }

    data class LigneCatalogue(
        val famille: String?,
        val libelle: String,
        val reference: String?,
        val unite: String,
        val prixHT: Double,
        val tauxTva: Double,
        val nature: String,
        val synonymes: String,
    )

    /**
     * Import d'un catalogue préparé sur ordinateur (F-23). Tolérant sur l'ordre
     * des colonnes et sur le séparateur (point-virgule d'Excel FR ou virgule).
     */
    fun importerCatalogue(contenu: String): List<LigneCatalogue> {
        val texte = contenu.removePrefix(BOM)
        val lignes = texte.split("\n").filter { it.isNotBlank() }
        if (lignes.isEmpty()) return emptyList()
        // Le séparateur se lit sur l'en-tête : découper aussi sur la virgule
        // casserait les prix écrits « 85,50 ».
        val separateur = if (lignes.first().count { it == ';' } >= lignes.first().count { it == ',' }) ';' else ','
        val entetes = lignes.first().split(separateur).map { nettoyerEntete(it) }
        val resultat = mutableListOf<LigneCatalogue>()
        for (ligne in lignes.drop(1)) {
            val cellules = decouperLigne(ligne, separateur)
            fun valeur(cle: String): String {
                val index = entetes.indexOf(cle)
                return if (index in cellules.indices) cellules[index].trim() else ""
            }
            val libelle = valeur("libelle").ifBlank { valeur("designation") }
            if (libelle.isBlank()) continue
            resultat.add(
                LigneCatalogue(
                    famille = valeur("famille").takeIf { it.isNotBlank() },
                    libelle = libelle,
                    reference = valeur("reference").takeIf { it.isNotBlank() },
                    unite = valeur("unite").ifBlank { "u" },
                    prixHT = Formats.montant(valeur("prix_ht")) ?: 0.0,
                    tauxTva = Formats.montant(valeur("taux_tva")) ?: 20.0,
                    nature = valeur("nature").ifBlank { "fourniture" },
                    synonymes = valeur("synonymes"),
                ),
            )
        }
        return resultat
    }

    fun nettoyerEntete(texte: String): String = DicteeNormaliseur.entete(texte)

    private fun decouperLigne(ligne: String, separateur: Char = ';'): List<String> {
        val cellules = mutableListOf<String>()
        val courant = StringBuilder()
        var guillemets = false
        for (c in ligne) {
            when {
                c == '"' -> guillemets = !guillemets
                c == separateur && !guillemets -> {
                    cellules.add(courant.toString()); courant.clear()
                }
                else -> courant.append(c)
            }
        }
        cellules.add(courant.toString())
        return cellules
    }

    private fun echapper(texte: String): String =
        if (texte.contains(SEPARATEUR) || texte.contains("\n") || texte.contains('"')) {
            "\"" + texte.replace("\"", "\"\"") + "\""
        } else texte

    private fun montantCsv(valeur: Double): String = String.format(Locale.FRANCE, "%.2f", valeur)
}

/** Normalisation des en-têtes de CSV (accents, majuscules, espaces). */
object DicteeNormaliseur {
    fun entete(texte: String): String = java.text.Normalizer
        .normalize(texte.trim(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase(Locale.FRANCE)
        .replace(Regex("[^a-z0-9_]+"), "_")
        .trim('_')
}
