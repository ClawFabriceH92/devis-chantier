package com.trucdecomptable.devischantier

import com.trucdecomptable.devischantier.data.Catalogues
import com.trucdecomptable.devischantier.sauvegarde.Sauvegarde
import com.trucdecomptable.devischantier.util.Formats
import com.trucdecomptable.devischantier.util.NombresEnLettres
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogueEtSauvegardeTest {

    private val catalogueJson = """
        {
          "metier": "Plombier",
          "familles": [
            {"nom": "Plomberie", "sousFamilles": [
              {"nom": "Robinetterie", "articles": [
                {"libelle": "Mitigeur lavabo", "unite": "u", "prix_ht": 85.0, "tva": 10.0,
                 "nature": "fourniture", "synonymes": "mitigeur, robinet"},
                {"libelle": "", "unite": "u", "prix_ht": 10.0, "tva": 10.0, "nature": "fourniture", "synonymes": ""}
              ]}
            ]}
          ]
        }
    """.trimIndent()

    @Test
    fun catalogue_de_depart_est_lu() {
        val catalogue = Catalogues.parse("plombier", catalogueJson)
        assertNotNull(catalogue)
        assertEquals("Plombier", catalogue!!.metier)
        assertEquals(1, catalogue.familles.size)
        assertEquals(1, catalogue.nombreArticles)
        assertEquals("Mitigeur lavabo", catalogue.familles[0].sousFamilles[0].articles[0].libelle)
        assertEquals(10.0, catalogue.familles[0].sousFamilles[0].articles[0].tauxTva, 0.001)
    }

    @Test
    fun catalogue_invalide_est_ignore() {
        assertNull(Catalogues.parse("plombier", "{}"))
        assertNull(Catalogues.parse("plombier", "pas du json"))
    }

    @Test
    fun les_cinq_metiers_sont_proposes() {
        assertEquals(5, Catalogues.METIERS.size)
        assertEquals("Plombier", Catalogues.libelleMetier("plombier"))
        assertEquals("Carreleur", Catalogues.libelleMetier("carreleur"))
    }

    @Test
    fun les_fichiers_de_catalogue_existent_dans_les_assets() {
        val racine = java.io.File("src/main/assets/catalogues")
        assertTrue("dossier assets absent", racine.isDirectory)
        Catalogues.METIERS.forEach { (id, _) ->
            val fichier = java.io.File(racine, "$id.json")
            assertTrue("catalogue manquant : $id", fichier.exists())
            val catalogue = Catalogues.parse(id, fichier.readText())
            assertNotNull(catalogue)
            assertTrue("catalogue vide : $id", catalogue!!.nombreArticles >= 8)
            val prixPositifs = catalogue.familles.flatMap { f -> f.sousFamilles.flatMap { it.articles } }
                .all { it.prixHT > 0 }
            assertTrue("prix nul dans $id", prixPositifs)
        }
    }

    // ---------------------------------------------------------- sauvegarde

    @Test
    fun sauvegarde_chiffree_se_relit_avec_la_bonne_phrase() {
        val clair = "devis, clients, catalogue".toByteArray()
        val paquet = Sauvegarde.chiffrer(clair, "phrase-secrete")
        assertEquals("DCB1", String(paquet.copyOfRange(0, 4), Charsets.US_ASCII))
        val relu = Sauvegarde.dechiffrer(paquet, "phrase-secrete")
        assertNotNull(relu)
        assertEquals("devis, clients, catalogue", String(relu!!))
    }

    @Test
    fun mauvaise_phrase_ne_rend_rien() {
        val paquet = Sauvegarde.chiffrer("données".toByteArray(), "phrase-secrete")
        assertNull(Sauvegarde.dechiffrer(paquet, "autre-phrase"))
    }

    @Test
    fun fichier_etranger_est_refuse() {
        assertNull(Sauvegarde.dechiffrer("n'importe quoi".toByteArray(), "phrase"))
    }

    @Test
    fun deux_chiffrements_du_meme_contenu_different() {
        val a = Sauvegarde.chiffrer("même contenu".toByteArray(), "phrase")
        val b = Sauvegarde.chiffrer("même contenu".toByteArray(), "phrase")
        assertTrue(!a.contentEquals(b))
    }

    // ------------------------------------------------------------- formats

    @Test
    fun nombres_en_lettres() {
        assertEquals(2.0, NombresEnLettres.valeur("deux")!!, 0.001)
        assertEquals(1.0, NombresEnLettres.valeur("une")!!, 0.001)
        assertEquals(21.0, NombresEnLettres.valeur("vingt et un")!!, 0.001)
        assertNull(NombresEnLettres.valeur("huitante"))
    }

    @Test
    fun quantite_accepte_chiffres_et_lettres() {
        assertEquals(2.5, Formats.quantite("2,5")!!, 0.001)
        assertEquals(3.0, Formats.quantite("trois")!!, 0.001)
        assertNull(Formats.quantite("beaucoup"))
    }

    @Test
    fun dates_et_jours() {
        val aujourdHui = Formats.aujourdHui()
        assertEquals(7, Formats.joursEntre(aujourdHui, Formats.plusDeJours(aujourdHui, 7)))
        assertEquals("—", Formats.date(null))
        assertEquals("—", Formats.date(0L))
    }
}
