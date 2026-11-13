package com.trucdecomptable.devischantier.ia

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import java.io.File

/**
 * Le modèle de langage embarqué : LFM3-1.7B de Liquid AI, quantifié Q4_0 par
 * distillation (QAD), au format GGUF, lu par llama.cpp. Environ 1 Go sur le
 * téléphone, tout tourne hors ligne : la dictée ne quitte jamais l'appareil.
 *
 * Source : dépôt Hugging Face `LiquidAI/LFM3-1.7B-GGUF` (public, non restreint),
 * fichier `LFM3-1.7B-QAD-Q4_0.gguf`, taille et empreinte SHA-256 relevées sur
 * l'index du dépôt le 13/11/2026. Licence : LFM Open License v1.0 (usage
 * commercial libre pour une entité sous 10 M$ de chiffre d'affaires annuel).
 */
data class DescriptionModele(
    val id: String,
    val nom: String,
    val editeur: String,
    val fichier: String,
    val url: String,
    val taille: Long,
    val sha256: String,
    val licence: String,
    val licenceUrl: String,
    val ramMinimaleMo: Int,
) {
    val tailleLisible: String get() = String.format(java.util.Locale.FRANCE, "%.2f Go", taille / 1_000_000_000.0)
}

object ModeleLocal {

    const val DOSSIER = "modeles"

    val LFM3_1_7B = DescriptionModele(
        id = "lfm3-1.7b-q4_0",
        nom = "LFM3-1.7B (Q4_0)",
        editeur = "Liquid AI",
        fichier = "LFM3-1.7B-QAD-Q4_0.gguf",
        url = "https://huggingface.co/LiquidAI/LFM3-1.7B-GGUF/resolve/main/LFM3-1.7B-QAD-Q4_0.gguf",
        taille = 1_049_615_680L,
        sha256 = "43a7d09a0e9cba91b63eb7052488b9564569bddfd65754d2e426465e572a7452",
        licence = "LFM Open License v1.0",
        licenceUrl = "https://huggingface.co/LiquidAI/LFM3-1.7B-GGUF/blob/main/LICENSE",
        ramMinimaleMo = 3_000,
    )

    val MODELE_PAR_DEFAUT = LFM3_1_7B

    /** Les fichiers vivent hors de la sauvegarde Android automatique : 1 Go n'a rien à y faire. */
    fun dossier(context: Context): File = File(context.noBackupFilesDir, DOSSIER).apply { mkdirs() }

    fun fichier(context: Context, modele: DescriptionModele = MODELE_PAR_DEFAUT): File =
        File(dossier(context), modele.fichier)

    fun fichierPartiel(context: Context, modele: DescriptionModele = MODELE_PAR_DEFAUT): File =
        File(dossier(context), modele.fichier + ".part")

    /** Modèle prêt : fichier complet et en-tête GGUF valide. */
    fun pret(context: Context, modele: DescriptionModele = MODELE_PAR_DEFAUT): Boolean {
        val f = fichier(context, modele)
        return f.exists() && f.length() == modele.taille && enTeteValide(f)
    }

    fun enTeteValide(fichier: File): Boolean = runCatching {
        val entete = ByteArray(8)
        fichier.inputStream().use { if (it.read(entete) < 8) return false }
        enTeteValide(entete)
    }.getOrDefault(false)

    /** Les 8 premiers octets d'un GGUF : « GGUF » puis la version (entier 32 bits petit-boutiste, 2 ou 3). */
    fun enTeteValide(octets: ByteArray): Boolean {
        if (octets.size < 8) return false
        if (octets[0] != 'G'.code.toByte() || octets[1] != 'G'.code.toByte() ||
            octets[2] != 'U'.code.toByte() || octets[3] != 'F'.code.toByte()
        ) return false
        val version = (octets[4].toInt() and 0xFF) or
            ((octets[5].toInt() and 0xFF) shl 8) or
            ((octets[6].toInt() and 0xFF) shl 16) or
            ((octets[7].toInt() and 0xFF) shl 24)
        return version in 2..3
    }

    fun supprimer(context: Context, modele: DescriptionModele = MODELE_PAR_DEFAUT) {
        fichier(context, modele).delete()
        fichierPartiel(context, modele).delete()
    }

    // ------------------------------------------------------- compatibilité

    data class Compatibilite(val ok: Boolean, val raisons: List<String>) {
        val texte: String get() = if (ok) "Téléphone compatible." else raisons.joinToString(" ")
    }

    /**
     * Ce qu'il faut pour faire tourner 1,7 milliard de paramètres : un processeur
     * 64 bits (la bibliothèque n'existe qu'en arm64-v8a et x86_64), de la
     * mémoire, et de la place pour le fichier.
     */
    fun compatibilite(context: Context, modele: DescriptionModele = MODELE_PAR_DEFAUT): Compatibilite {
        val raisons = mutableListOf<String>()
        val abis = Build.SUPPORTED_64_BIT_ABIS.orEmpty().toList()
        if (abis.none { it == "arm64-v8a" || it == "x86_64" }) {
            raisons.add("Processeur 32 bits : le modèle embarqué n'est pas disponible sur ce téléphone.")
        }
        val ramMo = ramTotaleMo(context)
        if (ramMo in 1 until modele.ramMinimaleMo) {
            raisons.add("Mémoire insuffisante (${ramMo / 1024} Go) : il en faut au moins ${modele.ramMinimaleMo / 1000} Go.")
        }
        val libre = dossier(context).usableSpace
        val dejaLa = fichier(context, modele).length()
        if (dejaLa < modele.taille && libre < modele.taille - dejaLa + 200_000_000L) {
            raisons.add("Espace libre insuffisant : il faut ${modele.tailleLisible} pour le modèle.")
        }
        if (!LlmNatif.chargee) {
            raisons.add("Bibliothèque d'inférence absente de cette installation.")
        }
        return Compatibilite(raisons.isEmpty(), raisons)
    }

    fun ramTotaleMo(context: Context): Long = runCatching {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        info.totalMem / (1024 * 1024)
    }.getOrDefault(0L)

    /**
     * Threads de calcul : les gros cœurs seulement. Sur un téléphone à 8 cœurs,
     * 4 threads vont plus vite que 8 (les petits cœurs ralentissent le groupe).
     */
    fun nombreThreads(coeurs: Int = Runtime.getRuntime().availableProcessors()): Int =
        when {
            coeurs >= 8 -> 4
            coeurs >= 4 -> coeurs - 1
            else -> coeurs.coerceAtLeast(1)
        }
}
