package com.trucdecomptable.devischantier.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.trucdecomptable.devischantier.util.Diag
import java.io.File

/**
 * Envoi par l'application mail du téléphone, via le partage Android (§5.3) :
 * aucun serveur, l'email part de l'adresse de l'artisan. La version en ligne
 * viendra au lot 4 (relances automatiques et suivi).
 */
object Partage {

    fun uri(context: Context, fichier: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", fichier)

    fun envoyer(
        context: Context,
        fichiers: List<File>,
        destinataires: List<String>,
        sujet: String,
        corps: String,
        typeMime: String = "*/*",
    ) {
        if (fichiers.isEmpty()) return
        runCatching {
            val uris = ArrayList(fichiers.map { uri(context, it) })
            val intention = Intent(
                if (fichiers.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE,
            ).apply {
                this.type = typeMime
                if (fichiers.size == 1) {
                    putExtra(Intent.EXTRA_STREAM, uris.first())
                } else {
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                }
                putExtra(Intent.EXTRA_SUBJECT, sujet)
                putExtra(Intent.EXTRA_TEXT, corps)
                if (destinataires.isNotEmpty()) {
                    putExtra(Intent.EXTRA_EMAIL, destinataires.toTypedArray())
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intention, "Envoyer le devis").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }.onFailure { Diag.record("partage", it) }
    }

    /** Partage libre : SMS, WhatsApp, Drive… pour le PDF seul (E-01). */
    fun partagerFichier(context: Context, fichier: File, sujet: String) {
        runCatching {
            val intention = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri(context, fichier))
                putExtra(Intent.EXTRA_SUBJECT, sujet)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intention, "Partager").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }.onFailure { Diag.record("partage", it) }
    }

    /** Ouvre le PDF dans un lecteur, pour l'aperçu avant envoi (F-14). */
    fun ouvrirPdf(context: Context, fichier: File) {
        runCatching {
            val intention = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri(context, fichier), "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intention)
        }.onFailure {
            Diag.record("ouverture pdf", it)
            partagerFichier(context, fichier, "Devis")
        }
    }

    fun envoyerTexte(context: Context, destinataire: String, sujet: String, corps: String) {
        runCatching {
            val intention = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
                putExtra(Intent.EXTRA_EMAIL, arrayOf(destinataire))
                putExtra(Intent.EXTRA_SUBJECT, sujet)
                putExtra(Intent.EXTRA_TEXT, corps)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intention)
        }.onFailure { Diag.record("mailto", it) }
    }

    fun modele(
        modele: String,
        numero: String,
        client: String,
        objet: String,
        ttc: String,
        ht: String,
        limite: String,
        entreprise: String,
        date: String,
    ): String = modele
        .replace("{numero}", numero)
        .replace("{client}", client)
        .replace("{objet}", objet)
        .replace("{ttc}", ttc)
        .replace("{ht}", ht)
        .replace("{limite}", limite)
        .replace("{entreprise}", entreprise)
        .replace("{date}", date)
}
