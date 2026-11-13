package com.trucdecomptable.devischantier

import android.app.Application
import com.trucdecomptable.devischantier.ia.LlmLocal
import com.trucdecomptable.devischantier.rappels.Notifications
import com.trucdecomptable.devischantier.rappels.PlanificateurRappels
import com.trucdecomptable.devischantier.util.Diag

class DevisApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Notifications.creerCanaux(this)
        PlanificateurRappels.planifier(this)
        try {
            val maj = com.trucdecomptable.devischantier.update.UpdateManager
            maj.demarrer(this)
        } catch (e: Throwable) {
            Diag.record("demarrage/maj", e)
        }
        Diag.record("demarrage", "Devis Chantier ${BuildConfig.VERSION_NAME}")
    }

    /** Le modèle de langage embarqué occupe ~1 Go : il est libéré dès qu'Android le demande. */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        LlmLocal.surPressionMemoire(level)
    }
}
