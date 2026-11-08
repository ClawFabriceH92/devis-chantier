package com.trucdecomptable.devischantier.api

import com.trucdecomptable.devischantier.data.Client
import com.trucdecomptable.devischantier.util.Diag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Fiche d'une entreprise trouvée dans l'Annuaire des Entreprises de l'État
 * (API Recherche d'entreprises, publique et sans clé).
 */
data class EntrepriseResultat(
    val nom: String,
    val siren: String,
    val siret: String?,
    val formeJuridique: String?,
    val adresse: String?,
    val naf: String?,
    val dirigeants: List<String> = emptyList(),
    val etablissements: List<Etablissement> = emptyList(),
    val cessee: Boolean = false,
    val tvaIntra: String? = null,
)

data class Etablissement(
    val siret: String,
    val adresse: String?,
    val actif: Boolean = true,
)

object RechercheEntreprises {

    const val BASE = "https://recherche-entreprises.api.gouv.fr/search"
    private const val TIMEOUT_MS = 12_000

    /** Clé du numéro de TVA intracommunautaire, calculée à partir du SIREN (F-43). */
    fun tvaIntracommunautaire(siren: String): String? {
        val propre = siren.filter { it.isDigit() }
        if (propre.length != 9) return null
        val nombre = propre.toLongOrNull() ?: return null
        val cle = (12 + 3 * (nombre % 97)) % 97
        return "FR" + cle.toString().padStart(2, '0') + propre
    }

    /** Requête : nom, SIREN (9 chiffres) ou SIRET (14 chiffres), une seule barre (F-40). */
    fun construireUrl(requete: String, parPage: Int = 8): String {
        val propre = requete.trim()
        val q = URLEncoder.encode(propre, "UTF-8")
        return "$BASE?q=$q&per_page=$parPage"
    }

    suspend fun rechercher(requete: String): Result<List<EntrepriseResultat>> = withContext(Dispatchers.IO) {
        if (requete.trim().length < 3) return@withContext Result.success(emptyList())
        runCatching {
            // Une frappe = un appel : on temporise pour ne pas se faire jeter
            // en 429 (limite annoncée à 7 appels par seconde).
            Thread.sleep(250)
            val texte = lire(construireUrl(requete))
            parse(texte)
        }.onFailure { Diag.record("entreprises", it) }
    }

    private fun lire(url: String): String {
        val connexion = URL(url).openConnection() as HttpURLConnection
        try {
            connexion.connectTimeout = TIMEOUT_MS
            connexion.readTimeout = TIMEOUT_MS
            connexion.setRequestProperty("User-Agent", "DevisChantier (application artisan)")
            connexion.setRequestProperty("Accept", "application/json")
            val code = connexion.responseCode
            if (code == 429) error("L'Annuaire des Entreprises limite les appels (429). Réessayez dans un instant.")
            if (code != HttpURLConnection.HTTP_OK) error("HTTP $code")
            return connexion.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connexion.disconnect()
        }
    }

    /** Pur, testable sans réseau. */
    fun parse(corps: String): List<EntrepriseResultat> {
        val racine = runCatching { JSONObject(corps) }.getOrNull() ?: return emptyList()
        val resultats = racine.optJSONArray("results") ?: return emptyList()
        val liste = mutableListOf<EntrepriseResultat>()
        for (i in 0 until resultats.length()) {
            val obj = resultats.optJSONObject(i) ?: continue
            liste.add(depuisJson(obj))
        }
        return liste
    }

    private fun depuisJson(obj: JSONObject): EntrepriseResultat {
        val siege = obj.optJSONObject("siege")
        val siren = obj.optString("siren", "")
        val siret = siege?.optString("siret")?.takeIf { it.isNotBlank() }
        val nom = listOf(
            obj.optString("nom_complet"),
            obj.optString("nom_raison_sociale"),
            obj.optString("sigle"),
        ).firstOrNull { it.isNotBlank() } ?: "Entreprise $siren"

        val etablissements = mutableListOf<Etablissement>()
        siege?.let {
            etablissements.add(
                Etablissement(
                    siret = it.optString("siret", ""),
                    adresse = adresseDe(it),
                    actif = true,
                ),
            )
        }
        obj.optJSONArray("matching_etablissements")?.let { arr ->
            for (i in 0 until arr.length()) {
                val e = arr.optJSONObject(i) ?: continue
                val s = e.optString("siret", "")
                if (s.isNotBlank() && etablissements.none { it.siret == s }) {
                    etablissements.add(Etablissement(s, adresseDe(e), e.optBoolean("est_siege", false)))
                }
            }
        }

        val dirigeants = mutableListOf<String>()
        obj.optJSONArray("dirigeants")?.let { arr ->
            for (i in 0 until arr.length()) {
                val d = arr.optJSONObject(i) ?: continue
                val nomComplet = listOf(d.optString("prenoms"), d.optString("nom"))
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                val qualite = d.optString("qualite")
                if (nomComplet.isNotBlank()) {
                    dirigeants.add(if (qualite.isBlank()) nomComplet else "$nomComplet ($qualite)")
                }
            }
        }

        val etat = obj.optString("etat_administratif", "A")
        return EntrepriseResultat(
            nom = nom,
            siren = siren,
            siret = siret,
            formeJuridique = obj.optString("nature_juridique").takeIf { it.isNotBlank() }?.let { libelleForme(it) },
            adresse = siege?.let { adresseDe(it) },
            naf = obj.optString("activite_principale").takeIf { it.isNotBlank() },
            dirigeants = dirigeants,
            etablissements = etablissements,
            cessee = etat == "C",
            tvaIntra = tvaIntracommunautaire(siren),
        )
    }

    private fun adresseDe(obj: JSONObject): String {
        val morceaux = listOf(
            obj.optString("adresse"),
            obj.optString("code_postal") + " " + obj.optString("libelle_commune"),
        ).map { it.trim() }.filter { it.isNotBlank() && it != "" }
        return morceaux.joinToString(", ").ifBlank { "—" }
    }

    /** Code INSEE de la nature juridique -> libellé lisible. */
    fun libelleForme(code: String): String = when (code) {
        "1000" -> "Entrepreneur individuel"
        "5499" -> "SARL"
        "5498" -> "EURL"
        "5710" -> "SAS"
        "5720" -> "SASU"
        "5785" -> "SAS"
        "6540" -> "SCI"
        "2110" -> "Indivision"
        "9220" -> "Association"
        "5385" -> "SA"
        else -> code
    }

    /** Report de la fiche dans le carnet clients (F-41, F-45). */
    fun versClient(resultat: EntrepriseResultat, etablissementChoisi: Etablissement? = null): Client = Client(
        nom = resultat.nom,
        professionnel = true,
        siren = resultat.siren,
        siret = etablissementChoisi?.siret ?: resultat.siret,
        formeJuridique = resultat.formeJuridique,
        naf = resultat.naf,
        tvaIntra = resultat.tvaIntra,
        adresse = etablissementChoisi?.adresse ?: resultat.adresse,
        radie = resultat.cessee,
        majLe = System.currentTimeMillis(),
    )

    fun resumeEtablissement(e: Etablissement): String =
        "${e.siret} · ${e.adresse ?: "adresse non diffusée"}"

    fun formaterDirigeants(d: List<String>): String = if (d.isEmpty()) "" else d.joinToString(", ")

    fun compter(resultats: List<EntrepriseResultat>): Int = resultats.size

    fun tableauVide(): JSONArray = JSONArray()
}
