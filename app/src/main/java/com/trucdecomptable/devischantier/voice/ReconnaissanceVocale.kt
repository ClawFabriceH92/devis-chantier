package com.trucdecomptable.devischantier.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.trucdecomptable.devischantier.util.Diag
import java.util.Locale

/**
 * La dictée gratuite : le moteur vocal d'Android, sans compte ni clé (§4.4).
 * Selon le téléphone, la reconnaissance se fait en local ou chez le
 * fournisseur du moteur Android ; l'audio n'est jamais conservé par l'app
 * (V-22), et rien n'est inséré sans validation.
 */
class ReconnaissanceVocale(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null

    fun disponible(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun demarrer(
        onPartiel: (String) -> Unit,
        onFinal: (String) -> Unit,
        onErreur: (String) -> Unit,
    ) {
        if (!disponible()) {
            onErreur("Aucun moteur vocal sur ce téléphone. Utilisez la saisie classique.")
            return
        }
        arreter()
        val moteur = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = moteur
        moteur.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { Diag.record("dictee", "micro prêt") }
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit

            override fun onError(error: Int) {
                Diag.record("dictee", "erreur moteur vocal : $error")
                onErreur(texteErreur(error))
            }

            override fun onResults(results: Bundle?) {
                val texte = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                if (texte.isBlank()) onErreur("Rien n'a été compris, réessayez.") else onFinal(texte)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?.let(onPartiel)
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        val intention = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.FRANCE.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        runCatching { moteur.startListening(intention) }
            .onFailure {
                Diag.record("dictee/start", it)
                onErreur("Le micro n'a pas pu démarrer : ${it.message}")
            }
    }

    fun arreter() {
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    fun texteErreur(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Problème de micro. Vérifiez qu'aucune autre application ne l'utilise."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Autorisation micro refusée : accordez-la pour dicter."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "Réseau indisponible. La dictée repartira au retour du réseau (V-21)."
        SpeechRecognizer.ERROR_NO_MATCH -> "Rien n'a été compris. Parlez un peu plus fort."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Le moteur vocal est occupé, réessayez dans un instant."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Aucune parole détectée."
        else -> "Dictée interrompue (code $code)."
    }
}
