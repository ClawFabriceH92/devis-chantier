package com.trucdecomptable.devischantier.sauvegarde

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.trucdecomptable.devischantier.data.BaseLocale
import com.trucdecomptable.devischantier.data.Reglages
import com.trucdecomptable.devischantier.util.Diag
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Sauvegarde complète, chiffrée par une phrase secrète choisie par l'artisan
 * (S-03) : elle se restaure sur un nouveau téléphone, ce qu'un chiffrement lié
 * au Keystore du téléphone ne permettrait pas. Le fichier contient la base
 * (devis, clients, catalogue, achats) et les réglages, jamais la clé API.
 *
 * Sauvegarde automatique : un dossier choisi par l'artisan (un dossier Google
 * Drive via l'application Fichiers fonctionne) reçoit la dernière archive.
 */
object Sauvegarde {

    private const val MAGIE = "DCB1"
    private const val ITERATIONS = 120_000
    private const val LONGUEUR_CLE = 256

    data class Resultat(val ok: Boolean, val message: String, val fichier: File? = null)

    fun nomFichier(): String {
        val horodatage = SimpleDateFormat("yyyyMMdd-HHmm", Locale.FRANCE).format(Date())
        return "devis-chantier-$horodatage.dcb"
    }

    /** Archive chiffrée dans filesDir/sauvegardes, prête à partager. */
    fun exporter(context: Context, reglages: Reglages, motDePasse: String): Resultat = runCatching {
        require(motDePasse.length >= 6) { "La phrase secrète doit faire au moins 6 caractères." }
        val archive = construireArchive(context, reglages)
        val dossier = File(context.filesDir, "sauvegardes").apply { mkdirs() }
        val fichier = File(dossier, nomFichier())
        fichier.writeBytes(chiffrer(archive, motDePasse))
        reglages.sauvegardeDerniere = System.currentTimeMillis()
        Diag.record("sauvegarde", "archive chiffrée ${fichier.name} (${fichier.length() / 1024} Ko)")
        Resultat(true, "Sauvegarde chiffrée créée : ${fichier.name}", fichier)
    }.getOrElse {
        Diag.record("sauvegarde/export", it)
        Resultat(false, "Échec de la sauvegarde : ${it.message}")
    }

    /** Écrit la sauvegarde dans le dossier choisi (SAF : Drive, SD, Nextcloud…). */
    fun exporterVersDossier(
        context: Context,
        reglages: Reglages,
        uriDossier: Uri,
        motDePasse: String,
    ): Resultat = runCatching {
        require(motDePasse.length >= 6) { "Phrase secrète trop courte." }
        val archive = construireArchive(context, reglages)
        val contenu = chiffrer(archive, motDePasse)
        val nom = nomFichier()
        val resolver = context.contentResolver
        // Le sélecteur de dossier rend une URI d'arborescence (« tree ») :
        // createDocument attend l'URI du document racine de cette arborescence.
        val racine = uriDocumentRacine(uriDossier)
        val document = DocumentsContract.createDocument(
            resolver,
            racine,
            "application/octet-stream",
            nom,
        ) ?: error("Impossible de créer le fichier dans le dossier choisi.")
        resolver.openOutputStream(document)?.use { it.write(contenu) }
            ?: error("Dossier en écriture impossible.")
        reglages.sauvegardeDerniere = System.currentTimeMillis()
        Diag.record("sauvegarde", "copie automatique écrite : $nom")
        Resultat(true, "Sauvegarde automatique écrite : $nom")
    }.getOrElse {
        Diag.record("sauvegarde/auto", it)
        Resultat(false, "Sauvegarde automatique impossible : ${it.message}")
    }

    /** « content://…/tree/X » -> « content://…/tree/X/document/X » ; une URI de document est rendue telle quelle. */
    fun uriDocumentRacine(uri: Uri): Uri =
        if (DocumentsContract.isTreeUri(uri) && !uri.pathSegments.contains("document")) {
            DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
        } else {
            uri
        }

    /**
     * Restaure une archive : la base est vérifiée (en-tête SQLite, version de
     * schéma connue) puis remplacée d'un seul mouvement (fichier temporaire puis
     * renommage), et les réglages sont réappliqués. L'application doit être
     * relancée : les écrans tiennent encore l'ancienne base.
     */
    fun restaurer(context: Context, octets: ByteArray, motDePasse: String): Resultat = runCatching {
        val archive = dechiffrer(octets, motDePasse) ?: error("Phrase secrète incorrecte ou fichier abîmé.")
        val entrees = lireArchive(archive)
        val base = entrees["devis-chantier.db"] ?: error("Archive sans base de données.")
        verifierBase(base)
        BaseLocale.fermer()
        val cible = context.getDatabasePath(BaseLocale.NOM_FICHIER)
        cible.parentFile?.mkdirs()
        val temporaire = File(cible.path + ".restauration")
        temporaire.writeBytes(base)
        // Un ancien journal WAL rejouerait l'état précédent : on l'efface.
        listOf("-wal", "-shm").forEach { suffixe ->
            File(cible.path + suffixe).takeIf { it.exists() }?.delete()
        }
        if (!temporaire.renameTo(cible)) {
            cible.delete()
            if (!temporaire.renameTo(cible)) error("Impossible de remplacer la base.")
        }
        entrees["reglages.json"]?.let { octetsReglages ->
            runCatching { Reglages(context).importer(reglagesDepuisJson(String(octetsReglages, Charsets.UTF_8))) }
                .onFailure { Diag.record("sauvegarde/reglages", it) }
        }
        Diag.record("sauvegarde", "base restaurée (${base.size / 1024} Ko)")
        Resultat(true, "Sauvegarde restaurée. L'application va redémarrer.")
    }.getOrElse {
        Diag.record("sauvegarde/restauration", it)
        Resultat(false, "Restauration impossible : ${it.message}")
    }

    // ------------------------------------------------------------- intérieur

    private fun construireArchive(context: Context, reglages: Reglages): ByteArray {
        val base = context.getDatabasePath(BaseLocale.NOM_FICHIER)
        val octetsBase = runCatching {
            // Checkpoint : sans lui, les dernières écritures restent dans le WAL.
            BaseLocale.obtenir(context).openHelper.writableDatabase
                .query("PRAGMA wal_checkpoint(TRUNCATE)")?.use { it.moveToFirst() }
            base.readBytes()
        }.getOrElse {
            Diag.record("sauvegarde/checkpoint", it)
            base.readBytes()
        }

        val informations = JSONObject()
            .put("application", "Devis Chantier")
            .put("date", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.FRANCE).format(Date()))
            .put("entreprise", reglages.entrepriseNom)
            .put("version_format", 2)
            .put("version_schema", VERSION_SCHEMA)
            .toString()

        val sortie = ByteArrayOutputStream()
        ZipOutputStream(sortie).use { zip ->
            zip.putNextEntry(ZipEntry(BaseLocale.NOM_FICHIER))
            zip.write(octetsBase)
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("informations.json"))
            zip.write(informations.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            // Identité, assurance, médiateur, modèles d'emails : sans eux, un
            // nouveau téléphone ne peut plus émettre un devis conforme.
            zip.putNextEntry(ZipEntry("reglages.json"))
            zip.write(reglagesEnJson(reglages.exporter()).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return sortie.toByteArray()
    }

    /** Version du schéma Room que cette application sait ouvrir. */
    const val VERSION_SCHEMA = 1

    /**
     * JSON typé : SharedPreferences distingue Int, Long et Float, ce que JSON ne
     * fait pas (un getLong sur une valeur Int plante). Chaque entrée porte donc
     * son type : {"cle": {"type": "long", "valeur": 123}}.
     */
    fun reglagesEnJson(valeurs: Map<String, Any?>): String {
        val objet = JSONObject()
        for ((cle, valeur) in valeurs) {
            val type = when (valeur) {
                null -> "null"
                is Boolean -> "boolean"
                is Int -> "int"
                is Long -> "long"
                is Float, is Double -> "float"
                else -> "string"
            }
            val v: Any = when (valeur) {
                null -> JSONObject.NULL
                is Float -> valeur.toDouble()
                is Boolean, is Int, is Long, is Double -> valeur
                else -> valeur.toString()
            }
            objet.put(cle, JSONObject().put("type", type).put("valeur", v))
        }
        return objet.toString()
    }

    fun reglagesDepuisJson(texte: String): Map<String, Any?> {
        val objet = JSONObject(texte)
        val resultat = mutableMapOf<String, Any?>()
        for (cle in objet.keys()) {
            val entree = objet.optJSONObject(cle) ?: continue
            resultat[cle] = when (entree.optString("type")) {
                "null" -> null
                "boolean" -> entree.optBoolean("valeur")
                "int" -> entree.optInt("valeur")
                "long" -> entree.optLong("valeur")
                "float" -> entree.optDouble("valeur").toFloat()
                else -> entree.optString("valeur")
            }
        }
        return resultat
    }

    /**
     * Refuse ce qui n'est pas une base SQLite ou qui vient d'une version plus
     * récente de l'application : Room exigerait alors une migration inconnue et
     * l'application ne s'ouvrirait plus.
     */
    fun verifierBase(octets: ByteArray) {
        val entete = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
        if (octets.size < 100 || !octets.copyOfRange(0, entete.size).contentEquals(entete)) {
            error("Le fichier de l'archive n'est pas une base de données.")
        }
        // Octets 60..63 de l'en-tête : « user version » (gros-boutiste), posée par Room.
        val version = ((octets[60].toInt() and 0xFF) shl 24) or ((octets[61].toInt() and 0xFF) shl 16) or
            ((octets[62].toInt() and 0xFF) shl 8) or (octets[63].toInt() and 0xFF)
        if (version > VERSION_SCHEMA) {
            error("Cette sauvegarde vient d'une version plus récente de l'application (schéma $version) : mettez d'abord l'application à jour.")
        }
    }

    private fun lireArchive(octets: ByteArray): Map<String, ByteArray> {
        val entrees = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(octets)).use { zip ->
            var entree: ZipEntry? = zip.nextEntry
            while (entree != null) {
                val contenu = zip.readBytes()
                entrees[entree.name.substringAfterLast('/')] = contenu
                zip.closeEntry()
                entree = zip.nextEntry
            }
        }
        return entrees
    }

    /** PBKDF2-HMAC-SHA256 puis AES-GCM. Entête : magie + sel + IV. */
    fun chiffrer(clair: ByteArray, motDePasse: String): ByteArray {
        val sel = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cle = deriver(motDePasse, sel)
        val chiffreur = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, cle, GCMParameterSpec(128, iv))
        }
        val corps = chiffreur.doFinal(clair)
        return MAGIE.toByteArray(Charsets.US_ASCII) + sel + iv + corps
    }

    fun dechiffrer(paquet: ByteArray, motDePasse: String): ByteArray? = runCatching {
        if (paquet.size < 4 + 16 + 12 + 16) return null
        val magie = String(paquet.copyOfRange(0, 4), Charsets.US_ASCII)
        if (magie != MAGIE) return null
        val sel = paquet.copyOfRange(4, 20)
        val iv = paquet.copyOfRange(20, 32)
        val corps = paquet.copyOfRange(32, paquet.size)
        val cle = deriver(motDePasse, sel)
        val dechiffreur = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, cle, GCMParameterSpec(128, iv))
        }
        dechiffreur.doFinal(corps)
    }.onFailure { Diag.record("sauvegarde/dechiffrement", it) }.getOrNull()

    private fun deriver(motDePasse: String, sel: ByteArray): SecretKeySpec {
        val specification = PBEKeySpec(motDePasse.toCharArray(), sel, ITERATIONS, LONGUEUR_CLE)
        val usine = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(usine.generateSecret(specification).encoded, "AES")
    }

    /** Purge des archives locales au-delà de la durée de conservation (R-03). */
    fun purger(context: Context, moisConservation: Int) {
        val dossier = File(context.filesDir, "sauvegardes")
        if (!dossier.exists() || moisConservation <= 0) return
        val limite = System.currentTimeMillis() - moisConservation * 30L * 86_400_000L
        dossier.listFiles()?.forEach { if (it.lastModified() < limite) it.delete() }
    }
}
