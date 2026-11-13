package com.trucdecomptable.devischantier.ia

import com.trucdecomptable.devischantier.util.Diag

/**
 * Déclarations JNI du pont vers llama.cpp (app/src/main/cpp/devis_llm.cpp).
 * Aucune logique ici : [LlmLocal] sérialise les appels sur un seul thread.
 */
object LlmNatif {

    /** Vrai une fois libdevis_llm.so chargée ; faux si l'ABI n'est pas fournie (émulateur 32 bits, etc.). */
    val chargee: Boolean by lazy {
        runCatching { System.loadLibrary("devis_llm") }
            .onFailure { Diag.record("ia/natif", it) }
            .isSuccess
    }

    external fun nativeInit(dossierLibs: String): String
    external fun nativeCharger(chemin: String, nThreads: Int, nCtx: Int): Long
    external fun nativeDescription(poignee: Long): String
    external fun nativeGenerer(poignee: Long, systeme: String, utilisateur: String, grammaire: String, maxTokens: Int): String?
    external fun nativeDerniereErreur(poignee: Long): String
    external fun nativeAnnuler(poignee: Long)
    external fun nativeLiberer(poignee: Long)
}
