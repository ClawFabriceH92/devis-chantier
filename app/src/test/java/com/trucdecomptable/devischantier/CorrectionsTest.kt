package com.trucdecomptable.devischantier

import com.trucdecomptable.devischantier.data.Devis
import com.trucdecomptable.devischantier.devis.ContexteMentions
import com.trucdecomptable.devischantier.devis.Mentions
import com.trucdecomptable.devischantier.devis.Totaux
import com.trucdecomptable.devischantier.pdf.GenerateurPdf
import com.trucdecomptable.devischantier.sauvegarde.Sauvegarde
import com.trucdecomptable.devischantier.util.Formats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Les corrections de la revue de novembre 2026, verrouillées par des tests. */
class CorrectionsTest {

    // ---------------------------------------------------------- sauvegarde

    private fun enTeteSqlite(version: Int): ByteArray {
        val octets = ByteArray(200)
        "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII).copyInto(octets)
        octets[60] = (version shr 24).toByte()
        octets[61] = (version shr 16).toByte()
        octets[62] = (version shr 8).toByte()
        octets[63] = version.toByte()
        return octets
    }

    @Test
    fun une_base_valide_du_bon_schema_est_acceptee() {
        Sauvegarde.verifierBase(enTeteSqlite(Sauvegarde.VERSION_SCHEMA))
        Sauvegarde.verifierBase(enTeteSqlite(0))
    }

    @Test
    fun un_fichier_qui_n_est_pas_une_base_est_refuse() {
        try {
            Sauvegarde.verifierBase("<html>pas une base</html>".toByteArray())
            fail("un fichier HTML ne doit pas passer")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("base de données"))
        }
    }

    @Test
    fun une_base_d_une_version_plus_recente_est_refusee() {
        try {
            Sauvegarde.verifierBase(enTeteSqlite(Sauvegarde.VERSION_SCHEMA + 1))
            fail("un schéma futur ne doit pas passer")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("plus récente"))
        }
    }

    @Test
    fun les_reglages_font_l_aller_retour_en_json() {
        val valeurs = mapOf<String, Any?>(
            "entreprise_nom" to "Durand Plomberie",
            "validite_jours" to 45,
            "acompte_pct" to 30.5f,
            "copie_artisan" to true,
            "attestation_expiration" to 1_800_000_000_000L,
            "mediateur" to null,
        )
        val relus = Sauvegarde.reglagesDepuisJson(Sauvegarde.reglagesEnJson(valeurs))
        assertEquals("Durand Plomberie", relus["entreprise_nom"])
        assertEquals(45, relus["validite_jours"])
        assertEquals(30.5f, relus["acompte_pct"] as Float, 0.001f)
        assertEquals(true, relus["copie_artisan"])
        assertEquals(1_800_000_000_000L, relus["attestation_expiration"])
        assertEquals(null, relus["mediateur"])
    }

    @Test
    fun les_cles_privees_couvrent_les_secrets() {
        assertTrue(com.trucdecomptable.devischantier.data.CLES_PRIVEES.contains("ia_cle_chiffree"))
        assertTrue(com.trucdecomptable.devischantier.data.CLES_PRIVEES.contains("sauvegarde_mdp_chiffre"))
    }

    // -------------------------------------------------------------- mentions

    @Test
    fun le_taux_reduit_de_cinq_et_demi_est_ecrit_en_entier() {
        val texte = Mentions.mentionTauxReduit(ContexteMentions(mentionTauxReduit = true, tauxReduit = 5.5))
        assertTrue(texte!!.startsWith("TVA à 5,50 %"))
        val dix = Mentions.mentionTauxReduit(ContexteMentions(mentionTauxReduit = true, tauxReduit = 10.0))
        assertTrue(dix!!.startsWith("TVA à 10 %"))
    }

    // -------------------------------------------------------------------- pdf

    @Test
    fun la_hauteur_du_bloc_totaux_suit_son_contenu() {
        val sansTva = Totaux(100.0, 0.0, 100.0, mapOf(0.0 to 0.0), emptyMap(), 0.0)
        val devisSimple = Devis(numero = "D-2026-0001")
        val base = GenerateurPdf.hauteurTotaux(sansTva, devisSimple)
        val avecTva = Totaux(100.0, 15.0, 115.0, mapOf(10.0 to 5.0, 20.0 to 10.0), emptyMap(), 0.0)
        assertTrue(GenerateurPdf.hauteurTotaux(avecTva, devisSimple) > base)
        val avecAcompteEtRemise = devisSimple.copy(acomptePct = 30.0, remiseGlobalePct = 5.0)
        assertTrue(GenerateurPdf.hauteurTotaux(avecTva, avecAcompteEtRemise) > GenerateurPdf.hauteurTotaux(avecTva, devisSimple))
    }

    // ------------------------------------------------------------- catalogue

    @Test
    fun l_ajustement_de_prix_arrondit_au_centime_le_plus_proche() {
        // 24,00 × 0,85 = 20,40 : la troncature donnait 20,39
        assertEquals(20.40, Formats.arrondi2(24.0 * 0.85), 0.0001)
        assertEquals(97.75, Formats.arrondi2(85.0 * 1.15), 0.0001)
    }
}
