package com.trucdecomptable.devischantier

import com.trucdecomptable.devischantier.devis.ContexteMentions
import com.trucdecomptable.devischantier.devis.Mentions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class MentionsTest {

    private fun date(annee: Int, mois: Int, jour: Int): Long = Calendar.getInstance().apply {
        clear()
        set(annee, mois - 1, jour, 12, 0, 0)
    }.timeInMillis

    private fun contexteComplet() = ContexteMentions(
        entrepriseNom = "Plomberie Martin",
        adresseEntreprise = "1 rue des Artisans, 92600 Asnières",
        siren = "123456789",
        siret = "12345678900012",
        telephone = "0102030405",
        email = "contact@plomberie.fr",
        clientNom = "M. Durand",
        professionalClient = false,
        objet = "Rénovation salle de bain",
        dateDevis = date(2026, 6, 1),
        validiteJours = 30,
        devisGratuit = true,
        franchiseTva = true,
        assureurNom = "AXA",
        assureurCoordonnees = "12 avenue de la Paix, Paris",
        couvertureGeo = "France métropolitaine",
        attestationExpiration = date(2027, 6, 1),
        mediateur = "CM2C, 14 rue Saint-Jean, Paris",
        acomptePct = 30.0,
        modalitesPaiement = "30 % à la commande",
        nombreLignes = 3,
    )

    @Test
    fun mention_franchise_en_2026_reste_l_article_293_B() {
        assertEquals(
            "TVA non applicable, art. 293 B du CGI",
            Mentions.mentionFranchise(date(2026, 11, 8)),
        )
    }

    @Test
    fun mention_franchise_change_au_1er_janvier_2027() {
        val mention = Mentions.mentionFranchise(date(2027, 1, 5))
        assertTrue(mention.contains("L. 233-3 du CIBS"))
        assertTrue(mention.contains("293 B"))
    }

    @Test
    fun mention_franchise_ancienne_formulation_achevee_au_30_juin_2028() {
        val avant = Mentions.mentionFranchise(date(2028, 6, 30))
        val apres = Mentions.mentionFranchise(date(2028, 7, 1))
        assertTrue(avant.contains("293 B"))
        assertFalse(apres.contains("293 B"))
        assertTrue(apres.contains("L. 233-3 du CIBS"))
    }

    @Test
    fun mention_personnalisee_prime_sur_la_regle() {
        assertEquals(
            "TVA non applicable, art. 293 B",
            Mentions.mentionFranchise(date(2030, 1, 1), "TVA non applicable, art. 293 B"),
        )
    }

    @Test
    fun contexte_complet_est_conforme() {
        assertTrue(Mentions.manquantes(contexteComplet()).isEmpty())
        assertTrue(Mentions.conforme(contexteComplet()))
    }

    @Test
    fun nom_d_entreprise_manquant_bloque_l_envoi() {
        val manques = Mentions.manquantes(contexteComplet().copy(entrepriseNom = ""))
        assertTrue(manques.any { it.contains("Nom de l'entreprise") })
    }

    @Test
    fun adresse_manquante_bloque_l_envoi() {
        val manques = Mentions.manquantes(contexteComplet().copy(adresseEntreprise = ""))
        assertTrue(manques.any { it.contains("Adresse de l'entreprise") })
    }

    @Test
    fun siren_ou_immatriculation_est_indispensable() {
        assertTrue(
            Mentions.manquantes(contexteComplet().copy(siren = "", immatriculation = ""))
                .any { it.contains("SIREN") },
        )
        assertTrue(Mentions.manquantes(contexteComplet().copy(siren = "", immatriculation = "RCS Paris 123")).isEmpty())
    }

    @Test
    fun client_et_objet_sont_indispensables() {
        assertTrue(Mentions.manquantes(contexteComplet().copy(clientNom = "")).any { it.contains("Client") })
        assertTrue(Mentions.manquantes(contexteComplet().copy(objet = "")).any { it.contains("Objet") })
    }

    @Test
    fun devis_sans_ligne_est_refuse() {
        assertTrue(
            Mentions.manquantes(contexteComplet().copy(nombreLignes = 0))
                .any { it.lowercase().contains("ligne chiffrée") },
        )
    }

    @Test
    fun assurance_obligatoire_sans_assureur_bloque() {
        val manques = Mentions.manquantes(contexteComplet().copy(assureurNom = "", assureurCoordonnees = ""))
        assertTrue(manques.any { it.contains("Assureur") })
        assertTrue(manques.any { it.contains("Coordonnées de l'assureur") })
    }

    @Test
    fun assurance_non_obligatoire_pour_un_metier_sans_obligation() {
        val manques = Mentions.manquantes(
            contexteComplet().copy(assuranceObligatoire = false, assureurNom = "", assureurCoordonnees = ""),
        )
        assertTrue(manques.isEmpty())
    }

    @Test
    fun attestation_perimee_bloque_l_envoi() {
        val contexte = contexteComplet().copy(attestationExpiration = date(2025, 1, 1))
        assertTrue(Mentions.attestationPerimee(contexte))
        assertTrue(Mentions.manquantes(contexte).any { it.contains("Attestation") })
    }

    @Test
    fun attestation_valide_ne_bloque_pas() {
        assertFalse(Mentions.attestationPerimee(contexteComplet()))
    }

    @Test
    fun mediateur_obligatoire_pour_un_particulier() {
        assertTrue(
            Mentions.manquantes(contexteComplet().copy(mediateur = ""))
                .any { it.contains("Médiateur") },
        )
        assertTrue(
            Mentions.manquantes(contexteComplet().copy(mediateur = "", professionalClient = true)).isEmpty(),
        )
    }

    @Test
    fun bloc_assurance_est_rendu_avec_couverture() {
        val bloc = Mentions.blocAssurance(contexteComplet())
        assertNotNull(bloc)
        assertTrue(bloc!!.contains("AXA"))
        assertTrue(bloc.contains("France métropolitaine"))
    }

    @Test
    fun bloc_assurance_absent_sans_assureur() {
        assertNull(Mentions.blocAssurance(contexteComplet().copy(assureurNom = "")))
    }

    @Test
    fun texte_complet_contient_les_mentions_du_devis() {
        val texte = Mentions.texteComplet(contexteComplet())
        assertTrue(texte.contains("Plomberie Martin"))
        assertTrue(texte.contains("SIREN 123456789"))
        assertTrue(texte.contains("art. 293 B du CGI"))
        assertTrue(texte.contains("AXA"))
        assertTrue(texte.contains("Acompte de 30 %"))
        assertTrue(texte.contains("Médiateur de la consommation"))
    }

    @Test
    fun texte_complet_ajoute_la_retractation_si_signe_chez_le_client() {
        val texte = Mentions.texteComplet(contexteComplet().copy(signeChezClient = true))
        assertTrue(texte.contains("rétractation de 14 jours"))
        assertTrue(texte.contains("7 jours"))
    }

    @Test
    fun mention_taux_reduit_reprend_les_conditions() {
        val mention = Mentions.mentionTauxReduit(contexteComplet().copy(mentionTauxReduit = true, tauxReduit = 10.0))
        assertNotNull(mention)
        assertTrue(mention!!.contains("TVA à 10 %"))
        assertNull(Mentions.mentionTauxReduit(contexteComplet()))
    }

    @Test
    fun texte_complet_ne_contient_jamais_de_marge() {
        val texte = Mentions.texteComplet(contexteComplet().copy(margeAffichee = true))
        assertFalse(texte.contains("marge", ignoreCase = true))
    }
}
