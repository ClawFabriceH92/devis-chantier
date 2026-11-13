package com.trucdecomptable.devischantier.ia

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.trucdecomptable.devischantier.util.Diag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Téléchargement du modèle (1 Go) avec reprise : coupure réseau, application
 * fermée, le fichier .part reprend là où il s'est arrêté (en-tête Range). À la
 * fin, l'empreinte SHA-256 est comparée à celle publiée : un fichier tronqué ou
 * remplacé est refusé.
 */
object TelechargementModele {

    sealed interface Etat {
        data object Inactif : Etat
        data class EnCours(val octets: Long, val total: Long) : Etat {
            val progression: Float get() = if (total > 0) (octets.toFloat() / total).coerceIn(0f, 1f) else 0f
        }
        data object Verification : Etat
        data object Termine : Etat
        data class Echec(val detail: String) : Etat
    }

    private val _etat = MutableStateFlow<Etat>(Etat.Inactif)
    val etat: StateFlow<Etat> = _etat.asStateFlow()

    private val portee = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var travail: Job? = null

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    fun enCours(): Boolean = travail?.isActive == true

    fun reseauNonFacture(context: Context): Boolean = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val reseau = cm.activeNetwork ?: return false
        val capacites = cm.getNetworkCapabilities(reseau) ?: return false
        capacites.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capacites.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }.getOrDefault(false)

    /** Lance (ou reprend) le téléchargement. Rien ne se passe si un transfert est déjà en cours. */
    fun demarrer(context: Context, modele: DescriptionModele = ModeleLocal.MODELE_PAR_DEFAUT) {
        if (enCours()) return
        val application = context.applicationContext
        travail = portee.launch {
            val resultat = runCatching { telecharger(application, modele) }
            _etat.value = resultat.fold(
                onSuccess = { Etat.Termine },
                onFailure = {
                    Diag.record("ia/telechargement", it)
                    if (it is kotlinx.coroutines.CancellationException) Etat.Inactif else Etat.Echec(it.message ?: "échec")
                },
            )
        }
    }

    fun annuler() {
        travail?.cancel()
        travail = null
        _etat.value = Etat.Inactif
    }

    private suspend fun telecharger(context: Context, modele: DescriptionModele) = withContext(Dispatchers.IO) {
        val cible = ModeleLocal.fichier(context, modele)
        val partiel = ModeleLocal.fichierPartiel(context, modele)
        if (cible.exists() && cible.length() == modele.taille && ModeleLocal.enTeteValide(cible)) {
            return@withContext
        }
        cible.delete()
        partiel.parentFile?.mkdirs()
        if (partiel.length() > modele.taille) partiel.delete()

        var deja = partiel.length()
        _etat.value = Etat.EnCours(deja, modele.taille)

        val requete = Request.Builder().url(modele.url).get().apply {
            enTeteReprise(deja)?.let { header("Range", it) }
        }.build()
        http.newCall(requete).execute().use { reponse ->
            if (reponse.code == 416) {
                // Le serveur dit que la plage est hors fichier : on repart de zéro.
                partiel.delete()
                error("reprise refusée par le serveur, relancez le téléchargement")
            }
            if (!reponse.isSuccessful) error(messageHttp(reponse.code))
            val corps = reponse.body ?: error("réponse vide")
            val reprise = reponse.code == 206
            if (!reprise) {
                partiel.delete()
                deja = 0L
            }
            val attendu = if (reprise) modele.taille - deja else corps.contentLength()
            if (attendu > 0 && deja + attendu != modele.taille) {
                error("taille inattendue (${deja + attendu} octets au lieu de ${modele.taille}) : le fichier publié a changé")
            }
            corps.byteStream().use { entree ->
                java.io.FileOutputStream(partiel, reprise).use { sortie ->
                    val tampon = ByteArray(256 * 1024)
                    var lu = entree.read(tampon)
                    var dernierAffichage = 0L
                    while (lu != -1) {
                        ensureActive()
                        sortie.write(tampon, 0, lu)
                        deja += lu
                        if (deja - dernierAffichage > 2_000_000L) {
                            _etat.value = Etat.EnCours(deja, modele.taille)
                            dernierAffichage = deja
                        }
                        lu = entree.read(tampon)
                    }
                }
            }
        }

        if (partiel.length() != modele.taille) {
            error("téléchargement incomplet (${partiel.length()} / ${modele.taille} octets)")
        }
        _etat.value = Etat.Verification
        val empreinte = sha256(partiel)
        if (!empreinte.equals(modele.sha256, ignoreCase = true)) {
            partiel.delete()
            error("empreinte SHA-256 différente de celle publiée : fichier rejeté")
        }
        if (!ModeleLocal.enTeteValide(partiel)) {
            partiel.delete()
            error("le fichier reçu n'est pas un modèle GGUF")
        }
        if (!partiel.renameTo(cible)) error("impossible de finaliser le fichier")
        Diag.record("ia", "modèle ${modele.nom} téléchargé et vérifié (${modele.tailleLisible})")
    }

    /** « bytes=N- » quand il y a déjà N octets, sinon rien. */
    fun enTeteReprise(dejaLa: Long): String? = if (dejaLa > 0) "bytes=$dejaLa-" else null

    fun messageHttp(code: Int): String = when (code) {
        401, 403 -> "accès refusé par le serveur (HTTP $code) : le dépôt du modèle exige peut-être un compte. Téléchargez le fichier GGUF à la main puis importez-le."
        404 -> "fichier introuvable (HTTP 404) : le modèle a changé d'adresse. Importez-le à la main."
        429 -> "serveur saturé (HTTP 429), réessayez plus tard."
        else -> "erreur HTTP $code"
    }

    fun sha256(fichier: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fichier.inputStream().use { entree ->
            val tampon = ByteArray(1024 * 1024)
            var lu = entree.read(tampon)
            while (lu != -1) {
                digest.update(tampon, 0, lu)
                lu = entree.read(tampon)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Import d'un GGUF choisi par l'artisan (téléchargé sur un ordinateur, copié
     * par câble…). On vérifie l'en-tête et la taille ; l'empreinte complète est
     * calculée si la taille correspond au modèle de référence.
     */
    suspend fun importer(context: Context, lire: () -> java.io.InputStream?, modele: DescriptionModele = ModeleLocal.MODELE_PAR_DEFAUT): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val partiel = ModeleLocal.fichierPartiel(context, modele)
                partiel.delete()
                _etat.value = Etat.EnCours(0, modele.taille)
                lire()?.use { entree ->
                    partiel.outputStream().use { sortie ->
                        val tampon = ByteArray(1024 * 1024)
                        var total = 0L
                        var lu = entree.read(tampon)
                        while (lu != -1) {
                            sortie.write(tampon, 0, lu)
                            total += lu
                            _etat.value = Etat.EnCours(total, modele.taille)
                            lu = entree.read(tampon)
                        }
                    }
                } ?: error("fichier illisible")
                if (!ModeleLocal.enTeteValide(partiel)) {
                    partiel.delete(); error("ce fichier n'est pas un modèle GGUF")
                }
                _etat.value = Etat.Verification
                val message = if (partiel.length() == modele.taille && sha256(partiel).equals(modele.sha256, ignoreCase = true)) {
                    "Modèle ${modele.nom} importé et vérifié."
                } else {
                    "Fichier GGUF importé (${partiel.length() / 1_000_000} Mo). Ce n'est pas le fichier de référence : " +
                        "il sera utilisé tel quel, sans garantie de résultat."
                }
                val cible = ModeleLocal.fichier(context, modele)
                cible.delete()
                if (!partiel.renameTo(cible)) error("impossible d'enregistrer le fichier")
                _etat.value = Etat.Termine
                Diag.record("ia", "modèle importé : ${cible.length()} octets")
                message
            }.onFailure {
                _etat.value = Etat.Echec(it.message ?: "import impossible")
                Diag.record("ia/import", it)
            }
        }
}
