package com.trucdecomptable.devischantier.api

import com.trucdecomptable.devischantier.data.Article
import com.trucdecomptable.devischantier.util.Diag
import com.trucdecomptable.devischantier.voice.LigneDictee
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Une ligne proposée à l'écran de validation (V-05). */
data class PropositionLigne(
    val ligne: LigneDictee,
    val articleId: Long? = null,
    val confiance: Double = 0.5,
    val source: String = "REGLE",
)

/**
 * Mode « amélioré » (§4.4) : le texte dicté et un extrait du catalogue partent
 * chez le fournisseur choisi. Claude (Anthropic) est le fournisseur par
 * défaut ; un service compatible OpenAI (OVHcloud, autre) est accepté.
 *
 * Ce qui ne part JAMAIS : prix d'achat et marges (M-09). Seuls libellé, unité,
 * prix de vente HT, taux de TVA et identifiant technique sont transmis.
 */
object ClaudeClient {

    const val BASE = "https://api.anthropic.com"
    const val VERSION_API = "2023-06-01"
    /**
     * Meilleur modèle disponible chez Anthropic : la dictée doit comprendre une
     * phrase libre du premier coup, donc on prend le haut de gamme et non le
     * petit modèle. Les identifiants changent souvent (le préfixe daté
     * « claude-haiku-4-5 » a été retiré) : la liste est de toute façon relue
     * chez le fournisseur par [listerModeles], ce nom n'est qu'un repli.
     */
    const val MODELE_DEFAUT = "claude-opus-5-5"

    /**
     * Repli pour un service « compatible OpenAI » dont la liste des modèles est
     * inaccessible : un nom répandu chez les fournisseurs européens (Mistral),
     * jamais un identifiant Claude qui y produirait une erreur 404.
     */
    const val MODELE_DEFAUT_OPENAI = "mistral-small-latest"

    /** Niveaux proposés à l'artisan, du plus fin au plus économique. */
    object Niveau {
        const val QUALITE = "QUALITE"
        const val EQUILIBRE = "EQUILIBRE"
        const val ECONOMIE = "ECONOMIE"
    }

    private val GAMMES = mapOf(
        Niveau.QUALITE to listOf("opus", "fable"),
        Niveau.EQUILIBRE to listOf("sonnet"),
        Niveau.ECONOMIE to listOf("haiku", "mini", "flash"),
    )

    /** Numéro de version d'un identifiant (« claude-opus-5-5 » -> [5, 5]). */
    fun versionDe(modele: String): List<Int> =
        Regex("""claude-[a-z]+(?:-\d+(?:\.\d+)?)+""").find(modele)?.value
            ?.split("-")
            ?.mapNotNull { it.toIntOrNull() }
            .orEmpty()

    fun comparerVersions(a: List<Int>, b: List<Int>): Int {
        for (i in 0 until maxOf(a.size, b.size)) {
            val va = a.getOrElse(i) { 0 }
            val vb = b.getOrElse(i) { 0 }
            if (va != vb) return va.compareTo(vb)
        }
        return 0
    }

    /**
     * Choisit, dans la liste réelle du fournisseur, le meilleur modèle de chaque
     * gamme : le plus récent des « opus » pour la qualité, des « sonnet » pour
     * l'équilibre, des « haiku » pour l'économie.
     */
    fun classerModeles(liste: List<String>): Map<String, String> {
        val choix = mutableMapOf<String, String>()
        for ((niveau, gammes) in GAMMES) {
            val candidats = liste.filter { modele ->
                gammes.any { gamme -> modele.contains(gamme, ignoreCase = true) }
            }
            choix[niveau] = candidats.maxWithOrNull { a, b -> comparerVersions(versionDe(a), versionDe(b)) }
                ?: ""
        }
        return choix
    }

    fun libelleNiveau(niveau: String): String = when (niveau) {
        Niveau.QUALITE -> "Qualité maximale"
        Niveau.EQUILIBRE -> "Équilibré"
        Niveau.ECONOMIE -> "Économique"
        else -> niveau
    }
    private const val TIMEOUT_MS = 25_000

    data class Resultat(
        val ok: Boolean,
        val texte: String = "",
        val erreur: String? = null,
        val code: Int = 0,
    ) {
        val quotaAtteint: Boolean get() = code == 429 || texte.contains("credit", ignoreCase = true)
    }

    // ---------------------------------------------------------------- modèles

    /** Les noms et prix changent souvent : la liste vient du fournisseur (V-36). */
    suspend fun listerModeles(
        cle: String,
        baseUrl: String = BASE,
        compatibleOpenAi: Boolean = false,
    ): Result<List<String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val reponse = requete(
                    url = urlModeles(baseUrl, compatibleOpenAi),
                    corps = null,
                    entetes = if (compatibleOpenAi) {
                        mapOf("Authorization" to "Bearer $cle")
                    } else {
                        mapOf("x-api-key" to cle, "anthropic-version" to VERSION_API)
                    },
                )
                if (!reponse.ok) error(reponse.erreur ?: "HTTP ${reponse.code}")
                parseModeles(reponse.texte)
            }.onFailure { Diag.record("claude/modeles", it) }
        }

    /**
     * Anthropic expose /v1/models à la racine ; un service compatible OpenAI est
     * saisi avec son préfixe (« https://api.mistral.ai/v1 ») et expose /models.
     */
    fun urlModeles(baseUrl: String, compatibleOpenAi: Boolean): String {
        val base = baseUrl.trimEnd('/')
        return if (compatibleOpenAi) "$base/models" else "$base/v1/models"
    }

    fun parseModeles(corps: String): List<String> {
        val racine = runCatching { JSONObject(corps) }.getOrNull() ?: return emptyList()
        val data = racine.optJSONArray("data") ?: return emptyList()
        val liste = mutableListOf<String>()
        for (i in 0 until data.length()) {
            val obj = data.optJSONObject(i) ?: continue
            val id = obj.optString("id")
            if (id.isNotBlank()) liste.add(id)
        }
        return liste
    }

    suspend fun testerCle(cle: String, baseUrl: String = BASE, compatibleOpenAi: Boolean = false): Result<String> =
        listerModeles(cle, baseUrl, compatibleOpenAi).map {
            if (it.isEmpty()) (if (compatibleOpenAi) MODELE_DEFAUT_OPENAI else MODELE_DEFAUT) else it.first()
        }

    // ------------------------------------------------------------- extraction

    suspend fun extraireLignes(
        cle: String,
        modele: String,
        texte: String,
        articles: List<Article>,
        baseUrl: String = BASE,
        compatibleOpenAi: Boolean = false,
        niveau: String = Niveau.QUALITE,
    ): Result<List<PropositionLigne>> = withContext(Dispatchers.IO) {
        runCatching {
            val catalogue = extraitCatalogue(articles)
            // Un identifiant de modèle faux ou retiré produit une erreur 404 que
            // l'artisan ne peut pas interpréter : on demande la liste au
            // fournisseur quand aucun modèle n'est enregistré.
            val modeleEffectif = modele.ifBlank {
                if (compatibleOpenAi) {
                    listerModeles(cle, baseUrl, compatibleOpenAi = true).getOrNull()?.firstOrNull()
                        ?: MODELE_DEFAUT_OPENAI
                } else {
                    resoudreModele(cle, baseUrl, niveau)
                }
            }
            val reponse = if (compatibleOpenAi) {
                requeteCompatibleOpenAi(baseUrl, cle, modeleEffectif, texte, catalogue)
            } else {
                requete(
                    url = "${baseUrl.trimEnd('/')}/v1/messages",
                    corps = corpsRequete(modeleEffectif, texte, catalogue),
                    entetes = mapOf(
                        "x-api-key" to cle,
                        "anthropic-version" to VERSION_API,
                        "content-type" to "application/json",
                    ),
                )
            }
            if (!reponse.ok) {
                Diag.record("claude/extraction", "échec : ${reponse.erreur ?: reponse.code}")
                error(reponse.erreur ?: "HTTP ${reponse.code}")
            }
            val brut = if (compatibleOpenAi) {
                texteCompatibleOpenAi(reponse.texte) ?: error("réponse illisible")
            } else {
                texteReponse(reponse.texte) ?: error("réponse illisible")
            }
            parseLignes(brut)
        }.onFailure { Diag.record("claude/extraction", it) }
    }

    /**
     * Extrait du catalogue envoyé au modèle : libellé, unité, prix de vente,
     * TVA, identifiant. Rien d'autre (M-09, S-02).
     */
    fun extraitCatalogue(articles: List<Article>, max: Int = 120): String {
        val arr = JSONArray()
        articles.take(max).forEach { a ->
            arr.put(
                JSONObject()
                    .put("id", a.id)
                    .put("libelle", a.libelle)
                    .put("unite", a.unite)
                    .put("prix_ht", a.prixHT)
                    .put("tva", a.tauxTva),
            )
        }
        return arr.toString()
    }

    fun promptSysteme(): String = """
        Tu transformes une dictée d'artisan en lignes de devis.
        Réponds UNIQUEMENT par un tableau JSON, sans texte autour, sans balise markdown.
        Chaque élément : {"designation": str, "quantite": nombre, "unite": str,
        "prix_ht": nombre ou null, "tva": nombre ou null, "article_id": nombre ou null, "confiance": 0..1}
        Règles :
        - un élément par prestation dictée, dans l'ordre de la dictée ;
        - si la dictée correspond à un article du catalogue fourni, reprends son libellé exact,
          son unité, son prix et son article_id ; sinon article_id vaut null ;
        - quantite peut être en lettres dans la dictée (« deux » -> 2) ;
        - prix_ht reste un prix unitaire hors taxe ; ne recalcule pas les totaux ;
        - confiance exprime ta certitude sur la ligne (0.9 = article du catalogue évident).
    """.trimIndent()

    /**
     * Corps de l'appel Claude. Aucun paramètre d'échantillonnage n'est envoyé :
     * `temperature` est refusé par les modèles récents (« `temperature` is
     * deprecated for this model »). Le prompt demande déjà une sortie stricte.
     */
    fun corpsRequete(modele: String, dictee: String, catalogue: String): String =
        JSONObject()
            .put("model", modele.ifBlank { MODELE_DEFAUT })
            .put("max_tokens", 1500)
            .put("system", promptSysteme())
            .put(
                "messages",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put(
                            "content",
                            "Catalogue (extrait) :\n$catalogue\n\nDictée :\n$dictee",
                        ),
                ),
            )
            .toString()

    /** Réponse /v1/messages : content[].text concaténé. */
    fun texteReponse(corps: String): String? {
        val racine = runCatching { JSONObject(corps) }.getOrNull() ?: return null
        val contenu = racine.optJSONArray("content") ?: return null
        val morceaux = mutableListOf<String>()
        for (i in 0 until contenu.length()) {
            val bloc = contenu.optJSONObject(i) ?: continue
            if (bloc.optString("type", "text") == "text") morceaux.add(bloc.optString("text"))
        }
        return morceaux.joinToString("\n").takeIf { it.isNotBlank() }
    }

    /** Réponse compatible OpenAI : choices[0].message.content. */
    fun texteCompatibleOpenAi(corps: String): String? {
        val racine = runCatching { JSONObject(corps) }.getOrNull() ?: return null
        val choix = racine.optJSONArray("choices") ?: return null
        val premier = choix.optJSONObject(0) ?: return null
        val message = premier.optJSONObject("message") ?: return null
        return message.optString("content").takeIf { it.isNotBlank() }
    }

    fun parseLignes(brut: String, source: String = "IA"): List<PropositionLigne> {
        val nettoye = nettoyerBalises(brut)
        val tableau = runCatching { JSONArray(nettoye) }.getOrElse {
            val objet = runCatching { JSONObject(nettoye) }.getOrNull() ?: return emptyList()
            objet.optJSONArray("lignes") ?: objet.optJSONArray("lines") ?: return emptyList()
        }
        val propositions = mutableListOf<PropositionLigne>()
        for (i in 0 until tableau.length()) {
            val obj = tableau.optJSONObject(i) ?: continue
            val designation = obj.optString("designation").ifBlank { obj.optString("libelle") }.trim()
            if (designation.isBlank()) continue
            val quantite = obj.optDouble("quantite", 1.0).takeIf { !it.isNaN() } ?: 1.0
            val prix = obj.optDouble("prix_ht", Double.NaN).takeIf { !it.isNaN() }
            val tva = obj.optDouble("tva", Double.NaN).takeIf { !it.isNaN() }
            val articleId = obj.optLong("article_id", -1L).takeIf { it > 0 }
            val confiance = obj.optDouble("confiance", 0.6).takeIf { !it.isNaN() }?.coerceIn(0.0, 1.0) ?: 0.6
            propositions.add(
                PropositionLigne(
                    ligne = LigneDictee(
                        designation = designation,
                        quantite = quantite,
                        unite = obj.optString("unite").takeIf { it.isNotBlank() },
                        prixUnitaireHT = prix,
                        tauxTva = tva,
                        texteOriginal = brut.take(200),
                    ),
                    articleId = articleId,
                    confiance = confiance,
                    source = source,
                ),
            )
        }
        return propositions
    }

    /** Retire les balises markdown dont certains modèles entourent le JSON. */
    fun nettoyerBalises(brut: String): String {
        var texte = brut.trim()
        if (texte.startsWith("```")) {
            texte = texte.substringAfter("```")
            texte = texte.removePrefix("json").removePrefix("JSON")
            texte = texte.substringBefore("```")
        }
        val debut = texte.indexOfFirst { it == '[' || it == '{' }
        if (debut > 0) texte = texte.substring(debut)
        val fin = texte.lastIndexOfAny(charArrayOf(']', '}'))
        if (fin in 0 until texte.length - 1) texte = texte.substring(0, fin + 1)
        return texte.trim()
    }

    // ------------------------------------------------------------- transport

    private fun requete(url: String, corps: String?, entetes: Map<String, String>): Resultat {
        val connexion = URL(url).openConnection() as HttpURLConnection
        try {
            connexion.connectTimeout = TIMEOUT_MS
            connexion.readTimeout = TIMEOUT_MS
            connexion.requestMethod = if (corps == null) "GET" else "POST"
            entetes.forEach { (cle, valeur) -> connexion.setRequestProperty(cle, valeur) }
            connexion.setRequestProperty("User-Agent", "DevisChantier/1.0")
            if (corps != null) {
                connexion.doOutput = true
                connexion.outputStream.use { it.write(corps.toByteArray(Charsets.UTF_8)) }
            }
            val code = connexion.responseCode
            val flux = if (code in 200..299) connexion.inputStream else connexion.errorStream
            val texte = flux?.bufferedReader()?.use { it.readText() }.orEmpty()
            val erreur = if (code in 200..299) null else messageErreur(code, texte)
            return Resultat(ok = code in 200..299, texte = texte, erreur = erreur, code = code)
        } catch (e: Exception) {
            return Resultat(ok = false, erreur = e.message ?: "réseau indisponible", code = 0)
        } finally {
            connexion.disconnect()
        }
    }

    private fun requeteCompatibleOpenAi(
        baseUrl: String,
        cle: String,
        modele: String,
        texte: String,
        catalogue: String,
    ): Resultat {
        val corps = JSONObject()
            .put("model", modele.ifBlank { MODELE_DEFAUT_OPENAI })
            .put("temperature", 0)
            .put("max_tokens", 1500)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", promptSysteme()))
                    .put(
                        JSONObject().put("role", "user")
                            .put("content", "Catalogue (extrait) :\n$catalogue\n\nDictée :\n$texte"),
                    ),
            )
            .toString()
        val url = "${baseUrl.trimEnd('/')}/chat/completions"
        return requete(url, corps, mapOf("Authorization" to "Bearer $cle", "content-type" to "application/json"))
    }

    /**
     * Résout le modèle à utiliser pour un niveau de qualité donné, à partir de
     * la liste réellement proposée par le fournisseur.
     */
    suspend fun resoudreModele(
        cle: String,
        baseUrl: String = BASE,
        niveau: String = Niveau.QUALITE,
    ): String {
        val liste = listerModeles(cle, baseUrl).getOrNull().orEmpty()
        val classe = classerModeles(liste)
        return classe[niveau]?.takeIf { it.isNotBlank() }
            ?: classe[Niveau.QUALITE]?.takeIf { it.isNotBlank() }
            ?: liste.firstOrNull()
            ?: MODELE_DEFAUT
    }

    fun messageErreur(code: Int, corps: String): String {
        val detail = runCatching {
            JSONObject(corps).optJSONObject("error")?.optString("message")
        }.getOrNull()
        val modeleInconnu = code == 404 || corps.contains("model", ignoreCase = true) &&
            (corps.contains("not found", ignoreCase = true) || corps.contains("deprecat", ignoreCase = true))
        return when {
            modeleInconnu ->
                "Modèle indisponible chez le fournisseur (${detail ?: "identifiant retiré"}). " +
                    "Videz le champ « Modèle » ou appuyez sur « Tester la clé » pour reprendre la liste à jour."
            code == 401 -> "Clé API refusée (401). Vérifiez la clé dans les paramètres."
            code == 403 -> "Accès refusé par le fournisseur (403)."
            code == 429 -> "Quota ou crédit épuisé chez le fournisseur (429)."
            code == 400 -> "Requête refusée (400) : ${detail ?: corps.take(160)}"
            else -> "Erreur ${code} : ${detail ?: corps.take(160)}"
        }
    }
}
