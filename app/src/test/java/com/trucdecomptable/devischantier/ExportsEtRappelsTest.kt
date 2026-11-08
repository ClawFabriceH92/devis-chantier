package com.trucdecomptable.devischantier

import com.trucdecomptable.devischantier.data.Achat
import com.trucdecomptable.devischantier.data.Client
import com.trucdecomptable.devischantier.data.Devis
import com.trucdecomptable.devischantier.data.Ligne
import com.trucdecomptable.devischantier.data.Statut
import com.trucdecomptable.devischantier.export.Exports
import com.trucdecomptable.devischantier.rappels.AnalyseRappels
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ExportsEtRappelsTest {

    private val jour = 86_400_000L

    private fun devis(
        id: Long = 1,
        statut: String = Statut.ENVOYE,
        dateDevis: Long = System.currentTimeMillis() - 10 * jour,
        validite: Int = 30,
        dateEnvoi: Long? = System.currentTimeMillis() - 10 * jour,
        total: Double = 1200.0,
    ) = Devis(
        id = id,
        numero = "D-2026-000$id",
        statut = statut,
        clientNom = "M. Martin",
        objet = "Rénovation",
        dateDevis = dateDevis,
        validiteJours = validite,
        dateEnvoi = dateEnvoi,
        totalHT = 1000.0,
        totalTVA = 200.0,
        totalTTC = total,
    )

    private val lignes = listOf(
        Ligne(devisId = 1, designation = "Mitigeur lavabo", quantite = 2.0, prixUnitaireHT = 85.0, tauxTva = 10.0),
        Ligne(devisId = 1, designation = "Main-d'oeuvre", quantite = 3.0, prixUnitaireHT = 48.0, tauxTva = 10.0),
    )

    // ------------------------------------------------------------- exports

    @Test
    fun donnees_comptable_contiennent_le_siren_du_client_pro() {
        val client = Client(nom = "DURAND PLOMBERIE", professionnel = true, siren = "123456789")
        val json = JSONObject(Exports.donneesComptable(devis(), client, lignes))
        assertEquals("123456789", json.getJSONObject("client").getString("siren"))
        assertEquals(2, json.getJSONArray("lignes").length())
        assertTrue(json.has("tva_par_taux"))
        assertTrue(json.has("mentions"))
    }

    @Test
    fun donnees_comptable_excluent_la_marge_par_defaut() {
        val json = JSONObject(Exports.donneesComptable(devis(), null, lignes))
        assertFalse(json.has("marge"))
        assertFalse(json.has("cout_achat"))
    }

    @Test
    fun donnees_comptable_peuvent_inclure_la_marge_si_activee() {
        val json = JSONObject(Exports.donneesComptable(devis(), null, lignes, inclureMarge = true))
        assertTrue(json.has("marge"))
    }

    @Test
    fun csv_devis_commence_par_le_bom_et_utilise_le_point_virgule() {
        val csv = Exports.csvDevis(devis(), null, lignes)
        assertTrue(csv.startsWith("\uFEFF"))
        assertTrue(csv.contains("numero;indice;date"))
        assertEquals(3, csv.trim().lines().size)
    }

    @Test
    fun import_catalogue_lit_les_colonnes_dans_un_ordre_quelconque() {
        val csv = """
            libelle;prix_ht;unite;taux_tva;famille
            Mitigeur lavabo;85,50;u;10;Robinetterie
            Pose WC;180;u;10;Sanitaires
        """.trimIndent()
        val lignes = Exports.importerCatalogue(csv)
        assertEquals(2, lignes.size)
        assertEquals("Mitigeur lavabo", lignes[0].libelle)
        assertEquals(85.5, lignes[0].prixHT, 0.001)
        assertEquals("Robinetterie", lignes[0].famille)
    }

    @Test
    fun import_catalogue_accepte_les_entetes_accentues() {
        val csv = "Désignation;Prix HT;Unité\nTube cuivre;9,5;ml"
        val lignes = Exports.importerCatalogue(csv)
        assertEquals(1, lignes.size)
        assertEquals("Tube cuivre", lignes[0].libelle)
        assertEquals(9.5, lignes[0].prixHT, 0.001)
    }

    @Test
    fun import_catalogue_ignore_les_lignes_vides() {
        val csv = "libelle;prix_ht\n\n;12\nPrise;5"
        val lignes = Exports.importerCatalogue(csv)
        assertEquals(1, lignes.size)
        assertEquals("Prise", lignes[0].libelle)
    }

    @Test
    fun csv_achats_est_exportable() {
        val csv = Exports.csvAchats(listOf(Achat(libelle = "BA13", fournisseur = "Point P", prixHT = 8.5)))
        assertTrue(csv.contains("BA13"))
        assertTrue(csv.startsWith("\uFEFF"))
    }

    // ------------------------------------------------------------- rappels

    @Test
    fun devis_expirant_dans_la_fenetre_est_detecte() {
        val maintenant = System.currentTimeMillis()
        val d = devis(dateDevis = maintenant - 28 * jour, validite = 30)
        assertEquals(1, AnalyseRappels.devisExpirant(listOf(d), maintenant, 3).size)
    }

    @Test
    fun devis_valide_longtemps_n_est_pas_un_rappel() {
        val maintenant = System.currentTimeMillis()
        val d = devis(dateDevis = maintenant, validite = 30)
        assertTrue(AnalyseRappels.devisExpirant(listOf(d), maintenant, 3).isEmpty())
    }

    @Test
    fun devis_brouillon_n_est_jamais_relance() {
        val maintenant = System.currentTimeMillis()
        val d = devis(statut = Statut.BROUILLON, dateDevis = maintenant - 2 * jour, dateEnvoi = null)
        assertTrue(AnalyseRappels.devisExpirant(listOf(d), maintenant, 10).isEmpty())
        assertTrue(AnalyseRappels.devisARelancer(listOf(d), maintenant, 7).isEmpty())
    }

    @Test
    fun devis_sans_reponse_est_a_relancer() {
        val maintenant = System.currentTimeMillis()
        val d = devis(dateEnvoi = maintenant - 8 * jour, dateDevis = maintenant - 8 * jour)
        assertEquals(1, AnalyseRappels.devisARelancer(listOf(d), maintenant, 7).size)
    }

    @Test
    fun devis_echu_est_detecte() {
        val maintenant = System.currentTimeMillis()
        val d = devis(dateDevis = maintenant - 40 * jour, validite = 30)
        assertEquals(1, AnalyseRappels.devisEchus(listOf(d), maintenant).size)
    }

    @Test
    fun attestation_bientot_perimee_declenche_un_rappel() {
        val maintenant = System.currentTimeMillis()
        assertTrue(AnalyseRappels.attestationARenouveler(maintenant + 10 * jour, maintenant, 45))
        assertFalse(AnalyseRappels.attestationARenouveler(maintenant + 300 * jour, maintenant, 45))
        assertFalse(AnalyseRappels.attestationARenouveler(0L, maintenant, 45))
    }

    @Test
    fun message_d_expiration_contient_numero_client_et_montant() {
        val d = devis(dateDevis = System.currentTimeMillis() - 28 * jour, validite = 30)
        val message = AnalyseRappels.messageExpiration(d)
        assertTrue(message.contains("D-2026-0001"))
        assertTrue(message.contains("M. Martin"))
        assertTrue(message.contains("TTC"))
    }

    @Test
    fun plage_horaire_respecte_le_dimanche() {
        assertTrue(AnalyseRappels.dansPlageHoraire(9, 8, 20, false, Calendar.TUESDAY))
        assertFalse(AnalyseRappels.dansPlageHoraire(7, 8, 20, false, Calendar.TUESDAY))
        assertFalse(AnalyseRappels.dansPlageHoraire(9, 8, 20, false, Calendar.SUNDAY))
        assertTrue(AnalyseRappels.dansPlageHoraire(9, 8, 20, true, Calendar.SUNDAY))
    }
}
