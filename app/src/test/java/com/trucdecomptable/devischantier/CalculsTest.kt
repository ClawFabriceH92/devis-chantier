package com.trucdecomptable.devischantier

import com.trucdecomptable.devischantier.data.Ligne
import com.trucdecomptable.devischantier.devis.Calculs
import com.trucdecomptable.devischantier.util.Formats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculsTest {

    private fun ligne(
        q: Double = 1.0,
        pu: Double = 100.0,
        tva: Double = 20.0,
        remise: Double = 0.0,
        section: String? = null,
        cout: Double = 0.0,
    ) = Ligne(
        designation = "Test",
        quantite = q,
        prixUnitaireHT = pu,
        tauxTva = tva,
        remisePct = remise,
        section = section,
        coutAchatUnitaire = cout,
    )

    @Test
    fun ligne_simple_calcule_ht_tva_ttc() {
        val t = Calculs.ligne(ligne(q = 2.0, pu = 50.0, tva = 20.0))
        assertEquals(100.0, t.ht, 0.001)
        assertEquals(20.0, t.tva, 0.001)
        assertEquals(120.0, t.ttc, 0.001)
    }

    @Test
    fun remise_de_ligne_reduit_le_ht() {
        val t = Calculs.ligne(ligne(q = 1.0, pu = 100.0, remise = 10.0))
        assertEquals(90.0, t.ht, 0.001)
    }

    @Test
    fun remise_globale_s_applique_a_chaque_ligne() {
        val t = Calculs.ligne(ligne(q = 1.0, pu = 100.0), remiseGlobalePct = 20.0)
        assertEquals(80.0, t.ht, 0.001)
    }

    @Test
    fun tva_multi_taux_est_ventilee() {
        val totaux = Calculs.totaux(
            listOf(
                ligne(q = 1.0, pu = 100.0, tva = 20.0),
                ligne(q = 1.0, pu = 100.0, tva = 10.0),
                ligne(q = 1.0, pu = 100.0, tva = 5.5),
            ),
        )
        assertEquals(300.0, totaux.totalHT, 0.001)
        assertEquals(35.5, totaux.totalTVA, 0.001)
        assertEquals(335.5, totaux.totalTTC, 0.001)
        assertEquals(3, totaux.tvaParTaux.size)
        assertEquals(20.0, totaux.tvaParTaux[20.0]!!, 0.001)
        assertEquals(5.5, totaux.tvaParTaux[5.5]!!, 0.001)
    }

    @Test
    fun sous_totaux_par_section() {
        val totaux = Calculs.totaux(
            listOf(
                ligne(q = 1.0, pu = 100.0, section = "Salle de bain"),
                ligne(q = 1.0, pu = 50.0, section = "Salle de bain"),
                ligne(q = 1.0, pu = 30.0, section = "Cuisine"),
                ligne(q = 1.0, pu = 10.0),
            ),
        )
        assertEquals(150.0, totaux.sousTotauxSections["Salle de bain"]!!, 0.001)
        assertEquals(30.0, totaux.sousTotauxSections["Cuisine"]!!, 0.001)
        assertEquals(10.0, totaux.sousTotauxSections[""]!!, 0.001)
    }

    @Test
    fun marge_exemple_du_cahier_des_charges() {
        // achat 50 € HT, vente 80 € HT
        val m = Calculs.marge(80.0, 50.0)
        assertEquals(30.0, m.margeBrute, 0.001)
        assertEquals(60.0, m.tauxMarge, 0.001)
        assertEquals(37.5, m.tauxMarque, 0.001)
        assertEquals(1.6, m.coefficient, 0.001)
    }

    @Test
    fun marge_sans_cout_d_achat_ne_divise_pas_par_zero() {
        val m = Calculs.marge(100.0, 0.0)
        assertEquals(100.0, m.margeBrute, 0.001)
        assertEquals(0.0, m.tauxMarge, 0.001)
        assertEquals(0.0, m.coefficient, 0.001)
    }

    @Test
    fun cout_achat_somme_des_achats_lies_avec_quantites() {
        val liens = listOf(1L to 2.0, 2L to 1.0)
        val achats = mapOf(1L to 10.0, 2L to 45.0)
        assertEquals(65.0, Calculs.coutArticle(liens, achats), 0.001)
    }

    @Test
    fun cout_achat_ignore_les_achats_inconnus() {
        assertEquals(10.0, Calculs.coutArticle(listOf(1L to 1.0, 99L to 3.0), mapOf(1L to 10.0)), 0.001)
    }

    @Test
    fun ligne_sous_le_cout_est_signalee() {
        assertTrue(Calculs.sousLeCout(ligne(pu = 40.0, cout = 50.0)))
        assertFalse(Calculs.sousLeCout(ligne(pu = 60.0, cout = 50.0)))
        assertFalse(Calculs.sousLeCout(ligne(pu = 10.0, cout = 0.0)))
    }

    @Test
    fun numerotation_commence_a_0001() {
        assertEquals("D-2026-0001", Calculs.numeroSuivant("D", 2026, emptyList()))
    }

    @Test
    fun numerotation_continue_apres_le_maximum() {
        val existants = listOf("D-2026-0001", "D-2026-0007", "D-2026-0003")
        assertEquals("D-2026-0008", Calculs.numeroSuivant("D", 2026, existants))
    }

    @Test
    fun numerotation_ignore_les_autres_annees_et_prefixes() {
        val existants = listOf("D-2025-0012", "F-2026-0009", "D-2026-0002")
        assertEquals("D-2026-0003", Calculs.numeroSuivant("D", 2026, existants))
    }

    @Test
    fun prefixe_vide_retombe_sur_D() {
        assertEquals("D-2026-0001", Calculs.numeroSuivant("   ", 2026, emptyList()))
    }

    @Test
    fun indice_de_version_suit_l_alphabet() {
        assertEquals("B", Calculs.indiceSuivant("A"))
        assertEquals("Z", Calculs.indiceSuivant("Y"))
        assertEquals("AA", Calculs.indiceSuivant("Z"))
        assertEquals("A", Calculs.indiceSuivant(""))
    }

    @Test
    fun acompte_calcule_sur_le_ttc() {
        assertEquals(36.0, Calculs.montantAcompte(120.0, 30.0), 0.001)
        assertEquals(0.0, Calculs.montantAcompte(120.0, 0.0), 0.001)
    }

    @Test
    fun ttc_avec_remise_recalcule_en_direct() {
        val lignes = listOf(ligne(q = 1.0, pu = 100.0, tva = 20.0))
        assertEquals(120.0, Calculs.ttcAvecRemise(lignes, 0.0), 0.001)
        assertEquals(108.0, Calculs.ttcAvecRemise(lignes, 10.0), 0.001)
    }

    @Test
    fun totaux_comptent_le_cout_d_achat_du_devis() {
        val totaux = Calculs.totaux(listOf(ligne(q = 2.0, pu = 100.0, cout = 40.0)))
        assertEquals(80.0, totaux.coutAchat, 0.001)
        assertEquals(120.0, totaux.marge, 0.001)
    }

    @Test
    fun formats_euros_et_pourcent() {
        assertEquals("1 234,56 €", Formats.euros(1234.56))
        assertEquals("100,00 €", Formats.euros(100.0))
        assertEquals("60,0 %", Formats.pourcent(60.0))
    }

    @Test
    fun formats_montant_accepte_les_saisies_francaises() {
        assertEquals(1234.56, Formats.montant("1 234,56 €")!!, 0.001)
        assertEquals(85.0, Formats.montant("85")!!, 0.001)
        assertNull(Formats.montant("abc"))
    }
}
