package com.trucdecomptable.devischantier.rappels

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.trucdecomptable.devischantier.MainActivity
import com.trucdecomptable.devischantier.R
import com.trucdecomptable.devischantier.data.Devis
import com.trucdecomptable.devischantier.data.Depots
import com.trucdecomptable.devischantier.data.Reglages
import com.trucdecomptable.devischantier.data.Statut
import com.trucdecomptable.devischantier.sauvegarde.Sauvegarde
import com.trucdecomptable.devischantier.util.Diag
import com.trucdecomptable.devischantier.util.Formats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Analyse pure des échéances (§5.4, F-38) : devis qui expirent bientôt, devis
 * à relancer, devis échus, attestation d'assurance à renouveler. Testable en
 * JUnit, sans Android.
 */
object AnalyseRappels {

    data class Alerte(
        val type: String,
        val devisId: Long?,
        val titre: String,
        val texte: String,
    ) {
        companion object {
            const val EXPIRATION = "EXPIRATION"
            const val RELANCE = "RELANCE"
            const val ECHEANCE = "ECHEANCE"
            const val ASSURANCE = "ASSURANCE"
        }
    }

    private const val JOUR = 86_400_000L

    fun dateLimite(devis: Devis): Long = devis.dateDevis + devis.validiteJours * JOUR

    /** Devis envoyés dont la validité expire dans [joursAvant] jours ou moins. */
    fun devisExpirant(devis: List<Devis>, maintenant: Long, joursAvant: Int): List<Devis> =
        devis.filter { it.statut == Statut.ENVOYE }
            .filter { d ->
                val restant = dateLimite(d) - maintenant
                restant in 0..(joursAvant * JOUR)
            }
            .sortedBy { dateLimite(it) }

    /** Devis envoyés sans réponse depuis [joursApres] jours (relance intermédiaire). */
    fun devisARelancer(devis: List<Devis>, maintenant: Long, joursApres: Int): List<Devis> =
        devis.filter { it.statut == Statut.ENVOYE }
            .filter { d ->
                val envoi = d.dateEnvoi ?: d.dateDevis
                maintenant - envoi >= joursApres * JOUR && maintenant - envoi < (joursApres + 30) * JOUR
            }

    /** Devis dont la date de validité est passée et qui traînent encore en « envoyé ». */
    fun devisEchus(devis: List<Devis>, maintenant: Long): List<Devis> =
        devis.filter { it.statut == Statut.ENVOYE && dateLimite(it) < maintenant }

    /** L'attestation d'assurance figure sur chaque devis : elle ne doit pas être périmée. */
    fun attestationARenouveler(expiration: Long, maintenant: Long, joursAvant: Int): Boolean {
        if (expiration <= 0L) return false
        val restant = expiration - maintenant
        return restant <= joursAvant * JOUR
    }

    fun messageExpiration(d: Devis): String {
        val restant = ((dateLimite(d) - System.currentTimeMillis()) / JOUR).toInt()
        val echeance = if (restant <= 0) "expire aujourd'hui" else "expire dans $restant jour(s)"
        return "Devis ${d.numero} - ${d.clientNom} - ${Formats.euros(d.totalTTC)} TTC : $echeance"
    }

    fun recap(lignes: List<Devis>): String =
        "${lignes.size} devis · ${Formats.euros(lignes.sumOf { it.totalTTC })} TTC"

    fun dansPlageHoraire(heure: Int, debut: Int, fin: Int, dimanche: Boolean, jourSemaine: Int): Boolean {
        if (heure < debut || heure >= fin) return false
        if (!dimanche && jourSemaine == Calendar.SUNDAY) return false
        return true
    }
}

object Notifications {

    const val CANAL_RAPPELS = "rappels_devis"
    const val CANAL_ASSURANCE = "assurance"
    const val CANAL_MISE_A_JOUR = "mises_a_jour"

    fun creerCanaux(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val gestionnaire = context.getSystemService(NotificationManager::class.java)
        gestionnaire.createNotificationChannel(
            NotificationChannel(CANAL_RAPPELS, "Rappels de devis", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Fin de validité des devis envoyés, relances proposées" },
        )
        gestionnaire.createNotificationChannel(
            NotificationChannel(CANAL_ASSURANCE, "Assurance professionnelle", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Renouvellement de l'attestation d'assurance" },
        )
        gestionnaire.createNotificationChannel(
            NotificationChannel(CANAL_MISE_A_JOUR, "Mises à jour", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Nouvelles versions de l'application" },
        )
    }

    fun permissionAccordee(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else true

    fun afficher(
        context: Context,
        identifiant: Int,
        canal: String,
        titre: String,
        texte: String,
        devisId: Long? = null,
        avecActions: Boolean = false,
    ) {
        if (!permissionAccordee(context)) {
            Diag.record("notifications", "permission refusée, rappel non affiché")
            return
        }
        val ouverture = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            devisId?.let { putExtra(MainActivity.EXTRA_DEVIS_ID, it) }
        }
        val drapeau = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val contenu = PendingIntent.getActivity(context, identifiant, ouverture, drapeau)

        val construction = NotificationCompat.Builder(context, canal)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(titre)
            .setContentText(texte)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texte))
            .setContentIntent(contenu)
            .setAutoCancel(true)

        if (avecActions && devisId != null) {
            construction.addAction(0, "Relancer", action(context, ActionRappelReceiver.ACTION_RELANCER, devisId, identifiant + 1))
            construction.addAction(0, "Plus tard", action(context, ActionRappelReceiver.ACTION_PLUS_TARD, devisId, identifiant + 2))
            construction.addAction(0, "Marquer refusé", action(context, ActionRappelReceiver.ACTION_REFUSE, devisId, identifiant + 3))
        }
        runCatching {
            NotificationManagerCompat.from(context).notify(identifiant, construction.build())
        }.onFailure { Diag.record("notifications", it) }
    }

    private fun action(context: Context, action: String, devisId: Long, code: Int): PendingIntent {
        val intent = Intent(context, ActionRappelReceiver::class.java).apply {
            this.action = action
            putExtra(ActionRappelReceiver.EXTRA_DEVIS, devisId)
        }
        return PendingIntent.getBroadcast(
            context,
            code,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

/** Boutons de la notification : chaque geste est explicite, rien n'est automatique. */
class ActionRappelReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_RELANCER = "com.trucdecomptable.devischantier.RELANCER"
        const val ACTION_PLUS_TARD = "com.trucdecomptable.devischantier.PLUS_TARD"
        const val ACTION_REFUSE = "com.trucdecomptable.devischantier.REFUSE"
        const val EXTRA_DEVIS = "devis_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val devisId = intent.getLongExtra(EXTRA_DEVIS, 0L)
        val action = intent.action ?: return
        val resultat = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = Depots.dao(context)
                val devis = dao.devis(devisId)
                if (devis != null) {
                    when (action) {
                        ACTION_REFUSE -> dao.enregistrerDevis(
                            devis.copy(statut = Statut.REFUSE, dateRefus = System.currentTimeMillis()),
                        )
                        ACTION_PLUS_TARD -> Notifications.afficher(
                            context,
                            9000 + devisId.toInt(),
                            Notifications.CANAL_RAPPELS,
                            "Rappel reporté",
                            "Le devis ${devis.numero} sera rappelé demain.",
                        )
                        ACTION_RELANCER -> context.startActivity(
                            Intent(context, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                putExtra(MainActivity.EXTRA_DEVIS_ID, devisId)
                                putExtra(MainActivity.EXTRA_RELANCER, true)
                            },
                        )
                    }
                    Diag.record("rappel", "action $action sur ${devis.numero}")
                }
            } catch (e: Exception) {
                Diag.record("rappel/action", e)
            } finally {
                resultat.finish()
            }
        }
    }
}

/**
 * Vérification quotidienne : passages en « expiré », rappels, attestation,
 * sauvegarde automatique et purge. Une seule notification récapitulative quand
 * plusieurs devis sont concernés le même jour (N-07).
 */
class RappelsWorker(context: Context, parametres: WorkerParameters) :
    CoroutineWorker(context, parametres) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val reglages = Reglages(context)
        val dao = Depots.dao(context)
        val maintenant = System.currentTimeMillis()

        val calendrier = Calendar.getInstance()
        val heure = calendrier.get(Calendar.HOUR_OF_DAY)
        val jourSemaine = calendrier.get(Calendar.DAY_OF_WEEK)

        if (!AnalyseRappels.dansPlageHoraire(
                heure, reglages.heureDebut, reglages.heureFin,
                reglages.dimancheAutorise, jourSemaine,
            )
        ) {
            Diag.record("rappel", "hors plage horaire ($heure h), report")
            return Result.retry()
        }

        val tous = dao.devisListe()

        // Passage en « expiré » des devis dont la validité est dépassée.
        AnalyseRappels.devisEchus(tous, maintenant).forEach { devis ->
            dao.enregistrerDevis(devis.copy(statut = Statut.EXPIRE))
            dao.ajouterEvenement(
                com.trucdecomptable.devischantier.data.Evenement(
                    devisId = devis.id,
                    type = "EXPIRATION",
                    detail = "validité dépassée",
                ),
            )
        }

        if (reglages.notificationsActives) {
            val expirants = AnalyseRappels.devisExpirant(tous, maintenant, reglages.rappelJoursAvant)
            if (expirants.isNotEmpty()) {
                val premier = expirants.first()
                val texte = if (expirants.size == 1) {
                    AnalyseRappels.messageExpiration(premier)
                } else {
                    "${expirants.size} devis arrivent à échéance. Le plus proche : " +
                        AnalyseRappels.messageExpiration(premier)
                }
                Notifications.afficher(
                    context, 1001, Notifications.CANAL_RAPPELS,
                    "Devis à relancer", texte, premier.id, avecActions = true,
                )
            }

            val aRelancer = AnalyseRappels.devisARelancer(tous, maintenant, reglages.relanceJoursApres)
            if (aRelancer.isNotEmpty()) {
                Notifications.afficher(
                    context, 1002, Notifications.CANAL_RAPPELS,
                    "Sans réponse du client",
                    "${aRelancer.size} devis envoyé(s) il y a plus de ${reglages.relanceJoursApres} jours : " +
                        AnalyseRappels.recap(aRelancer),
                    aRelancer.first().id, avecActions = true,
                )
            }

            if (AnalyseRappels.attestationARenouveler(
                    reglages.attestationExpiration, maintenant, reglages.rappelAssuranceJoursAvant,
                )
            ) {
                val perimee = reglages.attestationExpiration <= maintenant
                Notifications.afficher(
                    context, 1003, Notifications.CANAL_ASSURANCE,
                    if (perimee) "Attestation d'assurance périmée" else "Attestation d'assurance à renouveler",
                    if (perimee) {
                        "L'attestation expire le ${Formats.date(reglages.attestationExpiration)}. " +
                            "Elle figure sur vos devis : mettez-la à jour avant le prochain envoi."
                    } else {
                        "L'attestation expire le ${Formats.date(reglages.attestationExpiration)}. " +
                            "Demandez la nouvelle à votre assureur."
                    },
                )
            }
        }

        // Sauvegarde automatique : un téléphone perdu ne doit pas coûter le catalogue.
        if (reglages.sauvegardeAuto) {
            val dossier = reglages.sauvegardeDossier
            val motDePasse = reglages.motDePasseSauvegarde
            val derniere = reglages.sauvegardeDerniere
            val due = maintenant - derniere > 86_400_000L
            if (dossier != null && motDePasse != null && due) {
                runCatching {
                    val uri = android.net.Uri.parse(dossier)
                    val resultat = Sauvegarde.exporterVersDossier(context, reglages, uri, motDePasse)
                    Diag.record("sauvegarde/auto", resultat.message)
                }.onFailure { Diag.record("sauvegarde/auto", it) }
            }
        }
        Sauvegarde.purger(context, reglages.conservationMois)
        return Result.success()
    }
}

object PlanificateurRappels {

    private const val NOM = "rappels-devis-chantier"

    fun planifier(context: Context) {
        val demande = PeriodicWorkRequestBuilder<RappelsWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(1, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            NOM,
            ExistingPeriodicWorkPolicy.UPDATE,
            demande,
        )
        Diag.record("rappel", "vérification quotidienne planifiée")
    }
}
