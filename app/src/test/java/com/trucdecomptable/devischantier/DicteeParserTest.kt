package com.trucdecomptable.devischantier

import com.trucdecomptable.devischantier.data.Article
import com.trucdecomptable.devischantier.voice.Commande
import com.trucdecomptable.devischantier.voice.DicteeParser
import com.trucdecomptable.devischantier.voice.Rapprochement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DicteeParserTest {

    @Test
    fun dictee_exemple_du_cahier_des_charges() {
        val resultat = DicteeParser.analyser(
            "deux mitigeurs lavabo, trois heures de main-d'oeuvre, un forfait déplacement",
        )
        assertEquals(3, resultat.lignes.size)
        assertEquals(2.0, resultat.lignes[0].quantite, 0.001)
        assertEquals("Mitigeurs lavabo", resultat.lignes[0].designation)
        assertEquals(3.0, resultat.lignes[1].quantite, 0.001)
        assertEquals("h", resultat.lignes[1].unite)
        assertEquals("forfait", resultat.lignes[2].unite)
    }

    @Test
    fun quantite_en_lettres_est_convertie() {
        assertEquals(4.0, DicteeParser.analyserSegment("quatre plaques de plâtre").quantite, 0.001)
        assertEquals(1.0, DicteeParser.analyserSegment("une prise électrique").quantite, 0.001)
        assertEquals(10.0, DicteeParser.analyserSegment("dix chevilles").quantite, 0.001)
    }

    @Test
    fun prix_dicté_est_extrait() {
        val ligne = DicteeParser.analyserSegment("une plaque de plâtre à 8,50 euros hors taxe")
        assertEquals(8.5, ligne.prixUnitaireHT!!, 0.001)
        assertEquals("Plaque de plâtre", ligne.designation)
    }

    @Test
    fun tva_dictée_est_extraite_et_retirée_de_la_designation() {
        val ligne = DicteeParser.analyserSegment("mitigeur lavabo TVA 10 %")
        assertEquals(10.0, ligne.tauxTva!!, 0.001)
        assertFalse(ligne.designation.contains("TVA"))
    }

    @Test
    fun surface_en_metres_carres_est_reconnue() {
        val ligne = DicteeParser.analyserSegment("douze mètres carrés de carrelage")
        assertEquals(12.0, ligne.quantite, 0.001)
        assertEquals("m²", ligne.unite)
    }

    @Test
    fun designation_sans_quantite_vaut_un() {
        val ligne = DicteeParser.analyserSegment("forfait déplacement")
        assertEquals(1.0, ligne.quantite, 0.001)
        // « forfait » est l'unité, la désignation est « Déplacement ».
        assertEquals("Déplacement", ligne.designation)
    }

    @Test
    fun commande_nouvelle_section() {
        val commande = DicteeParser.reconnaitreCommande("nouvelle section salle de bain")
        assertTrue(commande is Commande.NouvelleSection)
        assertEquals("Salle de bain", (commande as Commande.NouvelleSection).nom)
    }

    @Test
    fun commande_supprimer_derniere_ligne() {
        assertTrue(DicteeParser.reconnaitreCommande("supprime la dernière ligne") is Commande.SupprimerDerniereLigne)
    }

    @Test
    fun commande_remise() {
        val commande = DicteeParser.reconnaitreCommande("remise de 5 %")
        assertTrue(commande is Commande.Remise)
        assertEquals(5.0, (commande as Commande.Remise).pourcentage, 0.001)
    }

    @Test
    fun analyse_separe_commandes_et_lignes() {
        val resultat = DicteeParser.analyser("deux mitigeurs lavabo, remise de 10 %, une heure de main-d'oeuvre")
        assertEquals(2, resultat.lignes.size)
        assertEquals(1, resultat.commandes.size)
    }

    @Test
    fun rapprochement_trouve_le_mitigeur() {
        val articles = listOf(
            Article(id = 1, libelle = "Mitigeur lavabo", synonymes = "robinet melangeur"),
            Article(id = 2, libelle = "Pose de carrelage au sol"),
        )
        val trouve = Rapprochement.meilleur("mitigeur lavabo", articles)
        assertNotNull(trouve)
        assertEquals(1L, trouve!!.first.id)
    }

    @Test
    fun rapprochement_utilise_les_synonymes() {
        val articles = listOf(Article(id = 5, libelle = "Tube PER 16", synonymes = "multicouche, gaine per"))
        val trouve = Rapprochement.meilleur("multicouche", articles)
        assertNotNull(trouve)
        assertEquals(5L, trouve!!.first.id)
    }

    @Test
    fun rapprochement_ne_force_pas_une_correspondance() {
        val articles = listOf(Article(id = 1, libelle = "Mitigeur lavabo"))
        assertNull(Rapprochement.meilleur("échafaudage roulant", articles))
    }

    @Test
    fun score_est_borne_et_positif() {
        val article = Article(id = 1, libelle = "Mitigeur lavabo")
        val score = Rapprochement.score("mitigeur lavabo", article)
        assertTrue(score > 0.5)
        assertTrue(score <= 1.0)
    }

    @Test
    fun normalisation_retire_les_accents() {
        assertEquals("mitigeur lavabo", DicteeParser.normaliser("Mitigeur LAVABO"))
        assertEquals("tva 10%", DicteeParser.normaliser("TVA 10%"))
    }
}
