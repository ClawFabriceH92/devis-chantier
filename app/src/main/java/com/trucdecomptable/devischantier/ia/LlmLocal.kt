package com.trucdecomptable.devischantier.ia

import android.content.Context
import com.trucdecomptable.devischantier.util.Diag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File

/**
 * Le moteur d'inférence sur le téléphone : un seul modèle chargé à la fois,
 * tous les appels natifs sur un thread unique (llama.cpp n'est pas réentrant).
 * Le modèle reste en mémoire entre deux dictées (le recharger coûte quelques
 * secondes) et se libère quand Android réclame de la mémoire ou après dix
 * minutes sans usage.
 */
object LlmLocal {

    /** Contexte de 4096 tokens : prompt court + 25 lignes de devis tiennent dix fois dedans. */
    const val N_CTX = 4096
    const val DELAI_INACTIVITE_MS = 10 * 60_000L

    data class Reponse(val texte: String, val dureeMs: Long, val description: String)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val fil = Dispatchers.IO.limitedParallelism(1)
    private val portee = CoroutineScope(SupervisorJob() + fil)

    @Volatile private var initialise = false
    @Volatile private var poignee = 0L
    @Volatile private var cheminCharge: String? = null
    @Volatile private var dernierUsage = 0L
    @Volatile var infosSysteme: String = ""
        private set

    val chargeEnMemoire: Boolean get() = poignee != 0L

    private fun initialiser(context: Context) {
        if (initialise) return
        check(LlmNatif.chargee) { "bibliothèque d'inférence indisponible sur ce téléphone" }
        val dossierLibs = context.applicationInfo.nativeLibraryDir.orEmpty()
        infosSysteme = LlmNatif.nativeInit(dossierLibs)
        initialise = true
        Diag.record("ia/natif", "llama.cpp prêt · $infosSysteme".take(300))
    }

    /** Charge le modèle si besoin (idempotent). Lève une exception explicite sinon. */
    suspend fun charger(context: Context, fichier: File, nThreads: Int = ModeleLocal.nombreThreads()) =
        withContext(fil) {
            initialiser(context)
            if (poignee != 0L && cheminCharge == fichier.absolutePath) return@withContext
            libererInterne()
            require(fichier.exists() && fichier.length() > 0) { "fichier du modèle absent" }
            val debut = System.currentTimeMillis()
            val p = LlmNatif.nativeCharger(fichier.absolutePath, nThreads, N_CTX)
            check(p != 0L) { "le modèle n'a pas pu être chargé (fichier abîmé ou mémoire insuffisante)" }
            poignee = p
            cheminCharge = fichier.absolutePath
            dernierUsage = System.currentTimeMillis()
            Diag.record("ia", "modèle chargé en ${System.currentTimeMillis() - debut} ms · ${LlmNatif.nativeDescription(p)}")
            programmerLiberation()
        }

    /**
     * Génère la réponse du modèle. [delaiMs] borne l'attente : au-delà, la
     * génération est annulée côté natif et une exception est levée. Le repli
     * sur les règles locales appartient à l'appelant.
     */
    suspend fun generer(
        context: Context,
        fichier: File,
        systeme: String,
        utilisateur: String,
        grammaire: String,
        maxTokens: Int = 700,
        delaiMs: Long = 90_000L,
    ): Reponse {
        charger(context, fichier)
        val debut = System.currentTimeMillis()
        return try {
            withTimeout(delaiMs) {
                withContext(fil) {
                    val p = poignee
                    check(p != 0L) { "modèle déchargé" }
                    dernierUsage = System.currentTimeMillis()
                    val texte = LlmNatif.nativeGenerer(p, systeme, utilisateur, grammaire, maxTokens)
                        ?: error(LlmNatif.nativeDerniereErreur(p).ifBlank { "génération impossible" })
                    dernierUsage = System.currentTimeMillis()
                    Reponse(texte, System.currentTimeMillis() - debut, LlmNatif.nativeDescription(p))
                }
            }
        } catch (e: TimeoutCancellationException) {
            annuler()
            throw IllegalStateException("le modèle n'a pas répondu en ${delaiMs / 1000} s", e)
        }
    }

    /** Interrompt la génération en cours (depuis n'importe quel thread). */
    fun annuler() {
        val p = poignee
        if (p != 0L) LlmNatif.nativeAnnuler(p)
    }

    fun liberer() {
        portee.launch { libererInterne() }
    }

    /**
     * Appelé par l'application quand Android manque de mémoire ou que l'interface
     * passe en arrière-plan (TRIM_MEMORY_RUNNING_LOW = 10, UI_HIDDEN = 20 et
     * au-delà) : un gigaoctet projeté en mémoire n'a rien à faire dans une
     * application invisible. Le prochain usage le recharge en quelques secondes.
     */
    fun surPressionMemoire(niveau: Int) {
        if (niveau >= 10 && chargeEnMemoire) {
            Diag.record("ia", "mémoire basse (niveau $niveau) : modèle libéré")
            liberer()
        }
    }

    private fun libererInterne() {
        val p = poignee
        if (p != 0L) {
            LlmNatif.nativeAnnuler(p)
            LlmNatif.nativeLiberer(p)
        }
        poignee = 0L
        cheminCharge = null
    }

    private fun programmerLiberation() {
        portee.launch {
            kotlinx.coroutines.delay(DELAI_INACTIVITE_MS)
            if (chargeEnMemoire && System.currentTimeMillis() - dernierUsage >= DELAI_INACTIVITE_MS) {
                Diag.record("ia", "modèle libéré après inactivité")
                libererInterne()
            } else if (chargeEnMemoire) {
                programmerLiberation()
            }
        }
    }
}
