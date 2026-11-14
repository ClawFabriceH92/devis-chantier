package com.trucdecomptable.devischantier

import com.trucdecomptable.devischantier.data.Devis
import com.trucdecomptable.devischantier.data.Statut
import com.trucdecomptable.devischantier.devis.ProchaineEtape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProchaineEtapeTest {

    private val jour = 86_400_000L
    private val maintenant = 1_800_000_000_000L

    @Test
    fun un_brouillon_vide_renvoie_vers_la_saisie() {
        val etape = ProchaineEtape.pour(Devis(numero = "D-2026-0001"), nombreLignes = 0, maintenant = maintenant)
        assertEquals(ProchaineEtape.MODIFIER, etape.action)
    }

    @Test
    fun un_brouillon_avec_des_lignes_propose_l_envoi() {
        val etape = ProchaineEtape.pour(Devis(numero = "D-2026-0001"), nombreLignes = 3, maintenant = maintenant)
        assertEquals(ProchaineEtape.ENVOYER, etape.action)
        assertEquals("Envoyer au client", etape.libelleBouton)
    }

    @Test
    fun un_devis_envoye_recemment_attend_la_signature() {
        val devis = Devis(numero = "D-2026-0001", statut = Statut.ENVOYE, dateDevis = maintenant - 2 * jour, dateEnvoi = maintenant - 2 * jour, validiteJours = 30)
        val etape = ProchaineEtape.pour(devis, 3, relanceJoursApres = 7, maintenant = maintenant)
        assertEquals(ProchaineEtape.SIGNER, etape.action)
    }

    @Test
    fun un_devis_sans_reponse_depuis_une_semaine_se_relance() {
        val devis = Devis(numero = "D-2026-0001", statut = Statut.ENVOYE, dateDevis = maintenant - 9 * jour, dateEnvoi = maintenant - 9 * jour, validiteJours = 30)
        val etape = ProchaineEtape.pour(devis, 3, relanceJoursApres = 7, maintenant = maintenant)
        assertEquals(ProchaineEtape.RELANCER, etape.action)
        assertTrue(etape.titre.contains("9 jours"))
    }

    @Test
    fun les_autres_statuts_ont_chacun_leur_action() {
        val base = Devis(numero = "D-2026-0001")
        assertEquals(ProchaineEtape.TRANSMETTRE, ProchaineEtape.pour(base.copy(statut = Statut.ACCEPTE), 3, maintenant = maintenant).action)
        assertEquals(ProchaineEtape.FACTURER, ProchaineEtape.pour(base.copy(statut = Statut.TRANSMIS), 3, maintenant = maintenant).action)
        assertEquals(ProchaineEtape.APERCU, ProchaineEtape.pour(base.copy(statut = Statut.FACTURE), 3, maintenant = maintenant).action)
        assertEquals(ProchaineEtape.VERSION, ProchaineEtape.pour(base.copy(statut = Statut.REFUSE), 3, maintenant = maintenant).action)
        assertEquals(ProchaineEtape.VERSION, ProchaineEtape.pour(base.copy(statut = Statut.EXPIRE), 3, maintenant = maintenant).action)
    }
}
