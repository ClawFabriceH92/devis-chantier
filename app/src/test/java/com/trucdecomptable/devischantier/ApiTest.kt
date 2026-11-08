package com.trucdecomptable.devischantier

import com.trucdecomptable.devischantier.api.ClaudeClient
import com.trucdecomptable.devischantier.api.RechercheEntreprises
import com.trucdecomptable.devischantier.data.Article
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiTest {

    // --------------------------------------------------- Annuaire des Entreprises

    private val reponseEntreprise = """
        {
          "results": [
            {
              "siren": "123456789",
              "nom_complet": "DURAND PLOMBERIE",
              "nom_raison_sociale": "DURAND PLOMBERIE",
              "nature_juridique": "5710",
              "activite_principale": "43.22A",
              "etat_administratif": "A",
              "siege": {"siret": "12345678900012", "adresse": "1 rue des Artisans", "code_postal": "92600", "libelle_commune": "Asnières-sur-Seine"},
              "matching_etablissements": [
                {"siret": "12345678900020", "adresse": "5 rue de Paris", "code_postal": "75010", "libelle_commune": "Paris", "est_siege": false}
              ],
              "dirigeants": [{"nom": "DURAND", "prenoms": "Paul", "qualite": "Gérant"}]
            }
          ],
          "total_results": 1
        }
    """.trimIndent()

    @Test
    fun parse_entreprise_remplit_la_fiche() {
        val resultats = RechercheEntreprises.parse(reponseEntreprise)
        assertEquals(1, resultats.size)
        val e = resultats.first()
        assertEquals("DURAND PLOMBERIE", e.nom)
        assertEquals("123456789", e.siren)
        assertEquals("12345678900012", e.siret)
        assertEquals("SAS", e.formeJuridique)
        assertEquals("43.22A", e.naf)
        assertFalse(e.cessee)
        assertEquals("Paul DURAND (Gérant)", e.dirigeants.first())
    }

    @Test
    fun parse_entreprise_liste_les_etablissements() {
        val e = RechercheEntreprises.parse(reponseEntreprise).first()
        assertEquals(2, e.etablissements.size)
        assertTrue(e.etablissements.any { it.siret == "12345678900020" })
    }

    @Test
    fun entreprise_cessee_est_signalee() {
        val corps = """{"results":[{"siren":"987654321","nom_complet":"VIEUX PLOMB","etat_administratif":"C","siege":{"siret":"98765432100011","adresse":"2 rue X"}}]}"""
        val e = RechercheEntreprises.parse(corps).first()
        assertTrue(e.cessee)
    }

    @Test
    fun reponse_vide_ou_invalide_ne_plante_pas() {
        assertTrue(RechercheEntreprises.parse("{}").isEmpty())
        assertTrue(RechercheEntreprises.parse("<html>").isEmpty())
        assertTrue(RechercheEntreprises.parse("""{"results":null}""").isEmpty())
    }

    @Test
    fun tva_intracommunautaire_est_calculee_depuis_le_siren() {
        val tva = RechercheEntreprises.tvaIntracommunautaire("123456789")
        assertNotNull(tva)
        assertEquals(13, tva!!.length)
        assertTrue(tva.startsWith("FR"))
        // Clé = (12 + 3 × (SIREN mod 97)) mod 97
        val cle = (12 + 3 * (123456789L % 97)) % 97
        assertEquals("FR" + cle.toString().padStart(2, '0') + "123456789", tva)
    }

    @Test
    fun tva_intracommunautaire_refuse_un_siren_incomplet() {
        assertNull(RechercheEntreprises.tvaIntracommunautaire("12345"))
        assertNull(RechercheEntreprises.tvaIntracommunautaire(""))
    }

    @Test
    fun vers_client_reprend_l_etablissement_choisi() {
        val e = RechercheEntreprises.parse(reponseEntreprise).first()
        val etablissement = e.etablissements.first { it.siret == "12345678900020" }
        val client = RechercheEntreprises.versClient(e, etablissement)
        assertTrue(client.professionnel)
        assertEquals("12345678900020", client.siret)
        assertTrue(client.adresse!!.contains("Paris"))
    }

    @Test
    fun url_de_recherche_encode_la_requete() {
        val url = RechercheEntreprises.construireUrl("durand plomberie")
        assertTrue(url.contains("q=durand+plomberie") || url.contains("q=durand%20plomberie"))
        assertTrue(url.startsWith("https://recherche-entreprises.api.gouv.fr/search"))
    }

    // ------------------------------------------------------------------ Claude

    @Test
    fun reponse_claude_extrait_le_texte() {
        val corps = """{"content":[{"type":"text","text":"[{\"designation\":\"Mitigeur\"}]"}]}"""
        val texte = ClaudeClient.texteReponse(corps)
        assertNotNull(texte)
        assertTrue(texte!!.contains("Mitigeur"))
    }

    @Test
    fun reponse_claude_sans_contenu_est_ignoree() {
        assertNull(ClaudeClient.texteReponse("{}"))
        assertNull(ClaudeClient.texteReponse("pas du json"))
    }

    @Test
    fun lignes_proposees_sont_parsees() {
        val brut = """
            ```json
            [{"designation":"Mitigeur lavabo","quantite":2,"unite":"u","prix_ht":85,"tva":10,"article_id":7,"confiance":0.9},
             {"designation":"Main-d'oeuvre","quantite":3,"unite":"h","prix_ht":48,"tva":10,"article_id":null,"confiance":0.6}]
            ```
        """.trimIndent()
        val propositions = ClaudeClient.parseLignes(brut)
        assertEquals(2, propositions.size)
        assertEquals("Mitigeur lavabo", propositions[0].ligne.designation)
        assertEquals(7L, propositions[0].articleId)
        assertEquals(0.9, propositions[0].confiance, 0.01)
        assertNull(propositions[1].articleId)
    }

    @Test
    fun lignes_sans_designation_sont_ignorees() {
        val propositions = ClaudeClient.parseLignes("""[{"quantite":2},{"designation":"Prise","quantite":1}]""")
        assertEquals(1, propositions.size)
    }

    @Test
    fun extrait_catalogue_ne_contient_jamais_de_prix_d_achat() {
        val extrait = ClaudeClient.extraitCatalogue(listOf(Article(id = 1, libelle = "Mitigeur", prixHT = 85.0, tauxTva = 10.0)))
        assertTrue(extrait.contains("Mitigeur"))
        assertFalse(extrait.contains("achat"))
        assertFalse(extrait.contains("marge"))
        assertFalse(extrait.contains("cout"))
    }

    @Test
    fun message_erreur_est_explicite() {
        assertTrue(ClaudeClient.messageErreur(401, "{}").contains("Clé API refusée"))
        assertTrue(ClaudeClient.messageErreur(429, "{}").contains("Quota"))
    }

    @Test
    fun corps_requete_contient_le_modele_et_le_catalogue() {
        val corps = ClaudeClient.corpsRequete("claude-haiku-4-5", "deux mitigeurs", "[]")
        assertTrue(corps.contains("claude-haiku-4-5"))
        assertTrue(corps.contains("deux mitigeurs"))
    }

    @Test
    fun corps_requete_n_envoie_pas_temperature() {
        // Les modèles récents refusent `temperature` : l'appel doit rester
        // strictement au format minimal, sinon erreur 400.
        val corps = ClaudeClient.corpsRequete("claude-haiku-5-5", "deux mitigeurs", "[]")
        assertFalse(corps.contains("temperature"))
        assertTrue(corps.contains("max_tokens"))
        assertTrue(corps.contains("system"))
    }

    @Test
    fun modele_par_defaut_est_un_identifiant_plausible() {
        assertTrue(ClaudeClient.MODELE_DEFAUT.startsWith("claude-"))
        assertFalse(ClaudeClient.MODELE_DEFAUT.contains("-4-5"))
    }

    @Test
    fun erreur_de_modele_retire_est_expliquee() {
        val message = ClaudeClient.messageErreur(
            404,
            """{"type":"error","error":{"type":"not_found_error","message":"model: claude-haiku-4-5 not found"}}""",
        )
        assertTrue(message.contains("Modèle indisponible"))
    }

    @Test
    fun le_meilleur_modele_est_choisi_par_gamme() {
        val liste = listOf(
            "claude-haiku-5-5",
            "claude-sonnet-5-5",
            "claude-opus-5-5",
            "claude-opus-5",
            "claude-opus-4-8",
            "claude-haiku-4-5-20251001",
            "claude-sonnet-4-5-20250929",
        )
        val classe = ClaudeClient.classerModeles(liste)
        assertEquals("claude-opus-5-5", classe[ClaudeClient.Niveau.QUALITE])
        assertEquals("claude-sonnet-5-5", classe[ClaudeClient.Niveau.EQUILIBRE])
        assertEquals("claude-haiku-5-5", classe[ClaudeClient.Niveau.ECONOMIE])
    }

    @Test
    fun une_gamme_absente_donne_une_chaine_vide() {
        val classe = ClaudeClient.classerModeles(listOf("claude-haiku-5-5"))
        assertEquals("", classe[ClaudeClient.Niveau.QUALITE])
        assertEquals("claude-haiku-5-5", classe[ClaudeClient.Niveau.ECONOMIE])
    }

    @Test
    fun les_numeros_de_version_sont_lus_et_compares() {
        assertEquals(listOf(5, 5), ClaudeClient.versionDe("claude-opus-5-5"))
        assertEquals(listOf(4, 5, 20251101), ClaudeClient.versionDe("claude-opus-4-5-20251101"))
        assertTrue(ClaudeClient.comparerVersions(listOf(5, 5), listOf(4, 8)) > 0)
        assertTrue(ClaudeClient.comparerVersions(listOf(4, 5, 20251101), listOf(4, 8)) < 0)
    }

    @Test
    fun le_defaut_est_bien_un_modele_du_haut_de_gamme() {
        assertEquals("claude-opus-5-5", ClaudeClient.MODELE_DEFAUT)
        assertEquals("Qualité maximale", ClaudeClient.libelleNiveau(ClaudeClient.Niveau.QUALITE))
    }

    @Test
    fun modeles_sont_lus_depuis_le_fournisseur() {
        val corps = """{"data":[{"id":"claude-haiku-4-5"},{"id":"claude-sonnet-4-5"}]}"""
        assertEquals(listOf("claude-haiku-4-5", "claude-sonnet-4-5"), ClaudeClient.parseModeles(corps))
    }

    @Test
    fun reponse_compatible_openai_est_lue() {
        val corps = """{"choices":[{"message":{"content":"[{\"designation\":\"Prise\"}]"}}]}"""
        assertEquals("[{\"designation\":\"Prise\"}]", ClaudeClient.texteCompatibleOpenAi(corps))
    }
}
