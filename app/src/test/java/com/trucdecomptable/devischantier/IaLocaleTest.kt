package com.trucdecomptable.devischantier

import com.trucdecomptable.devischantier.api.ClaudeClient
import com.trucdecomptable.devischantier.data.Article
import com.trucdecomptable.devischantier.ia.ExtractionLocale
import com.trucdecomptable.devischantier.ia.ModeleLocal
import com.trucdecomptable.devischantier.ia.TelechargementModele
import com.trucdecomptable.devischantier.voice.MoteurDictee
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le moteur embarqué sans Android : ce que le modèle reçoit (catalogue réduit,
 * prompt, grammaire) et ce qu'on relit de sa sortie. L'inférence elle-même
 * n'est pas testée ici (elle exige la bibliothèque native et le fichier GGUF).
 */
class IaLocaleTest {

    private val articles = listOf(
        Article(id = 1, libelle = "Mitigeur lavabo", unite = "u", prixHT = 85.0, tauxTva = 10.0, synonymes = "robinet melangeur"),
        Article(id = 2, libelle = "Main d'œuvre plomberie", unite = "h", prixHT = 48.0, tauxTva = 10.0, synonymes = "main d'oeuvre, heure"),
        Article(id = 3, libelle = "Pose de carrelage au sol", unite = "m²", prixHT = 35.0, tauxTva = 10.0),
        Article(id = 4, libelle = "Forfait déplacement", unite = "forfait", prixHT = 40.0, tauxTva = 20.0, favori = true),
        Article(id = 5, libelle = "Tableau électrique 2 rangées", unite = "u", prixHT = 320.0, tauxTva = 20.0, usages = 9),
    )

    // ------------------------------------------------------------ catalogue

    @Test
    fun les_candidats_mettent_les_articles_qui_ressemblent_a_la_dictee_en_tete() {
        val candidats = ExtractionLocale.candidats("deux mitigeurs lavabo et trois heures de main d'oeuvre", articles)
        // le mitigeur et la main-d'œuvre sont les deux premiers, le carrelage non
        assertTrue(candidats.take(2).map { it.id }.containsAll(listOf(1L, 2L)))
        assertTrue(candidats.indexOfFirst { it.id == 3L } > 1)
        // les favoris et les plus utilisés complètent, dans la limite
        assertTrue(candidats.map { it.id }.contains(4L))
    }

    @Test
    fun les_candidats_sont_bornes() {
        val beaucoup = (1..200).map { Article(id = it.toLong(), libelle = "Article $it") }
        assertEquals(ExtractionLocale.MAX_ARTICLES, ExtractionLocale.candidats("une prise", beaucoup).size)
        assertTrue(ExtractionLocale.candidats("une prise", emptyList()).isEmpty())
    }

    @Test
    fun le_catalogue_presente_ne_contient_jamais_de_prix_d_achat() {
        val texte = ExtractionLocale.presenterCatalogue(articles)
        assertTrue(texte.contains("1 | Mitigeur lavabo | u | 85 | 10"))
        assertFalse(texte.contains("achat", ignoreCase = true))
        assertFalse(texte.contains("marge", ignoreCase = true))
        assertFalse(texte.contains("cout", ignoreCase = true))
    }

    @Test
    fun le_prompt_contient_la_dictee_et_le_catalogue_reduit() {
        val prompt = ExtractionLocale.promptUtilisateur("deux mitigeurs lavabo", articles.take(2))
        assertTrue(prompt.contains("deux mitigeurs lavabo"))
        assertTrue(prompt.contains("Mitigeur lavabo"))
        assertTrue(ExtractionLocale.promptSysteme().contains("tableau JSON"))
        assertTrue(ExtractionLocale.promptSysteme().contains("article_id"))
    }

    // ------------------------------------------------------------ grammaire

    @Test
    fun la_grammaire_definit_toutes_les_regles_qu_elle_utilise() {
        val grammaire = ExtractionLocale.GRAMMAIRE
        val definies = Regex("""(?m)^([a-z]+) ::=""").findAll(grammaire).map { it.groupValues[1] }.toSet()
        assertTrue(definies.containsAll(listOf("root", "ligne", "texte", "car", "unite", "nombre", "prix", "tva", "identifiant", "confiance", "sp")))
        // chaque clé attendue par le parseur figure dans la règle « ligne »
        for (cle in listOf("designation", "quantite", "unite", "prix_ht", "tva", "article_id", "confiance")) {
            assertTrue(cle, grammaire.contains("\\\"$cle\\\""))
        }
        // les répétitions sont bornées : un modèle qui boucle s'arrête
        assertTrue(grammaire.contains("{0,${ExtractionLocale.MAX_LIGNES - 1}}"))
        assertTrue(grammaire.contains("car{1,90}"))
    }

    // --------------------------------------------------------------- sortie

    @Test
    fun la_sortie_du_modele_est_relue_et_etiquetee_locale() {
        val sortie = """[{"designation":"Mitigeur lavabo","quantite":2,"unite":"u","prix_ht":85,"tva":10,"article_id":1,"confiance":0.9},""" +
            """{"designation":"Main d'œuvre","quantite":3,"unite":"h","prix_ht":null,"tva":null,"article_id":null,"confiance":0.5}]"""
        val lignes = ExtractionLocale.parse(sortie)
        assertEquals(2, lignes.size)
        assertEquals(ExtractionLocale.SOURCE, lignes[0].source)
        assertEquals(1L, lignes[0].articleId)
        assertEquals(2.0, lignes[0].ligne.quantite, 0.001)
        assertNull(lignes[1].prixUnitaireHTOuNull())
    }

    private fun com.trucdecomptable.devischantier.api.PropositionLigne.prixUnitaireHTOuNull() = ligne.prixUnitaireHT

    @Test
    fun un_identifiant_invente_par_le_modele_est_ignore_au_rattachement() {
        val proposition = ExtractionLocale.parse(
            """[{"designation":"Mitigeur lavabo","quantite":1,"unite":"u","prix_ht":null,"tva":null,"article_id":999,"confiance":0.8}]""",
        ).first()
        val rattachee = MoteurDictee.rattacher(proposition, articles)
        assertEquals(1L, rattachee.articleId)
        assertEquals(85.0, rattachee.ligne.prixUnitaireHT!!, 0.001)
        assertEquals(10.0, rattachee.ligne.tauxTva!!, 0.001)
    }

    @Test
    fun une_sortie_vide_ou_illisible_ne_plante_pas() {
        assertTrue(ExtractionLocale.parse("[]").isEmpty())
        assertTrue(ExtractionLocale.parse("").isEmpty())
        assertTrue(ExtractionLocale.parse("<think>je réfléchis</think>").isEmpty())
    }

    // ---------------------------------------------------------------- modèle

    @Test
    fun la_fiche_du_modele_est_coherente() {
        val m = ModeleLocal.LFM3_1_7B
        assertTrue(m.url.startsWith("https://huggingface.co/LiquidAI/LFM3-1.7B-GGUF/resolve/main/"))
        assertTrue(m.url.endsWith(m.fichier))
        assertTrue(m.fichier.endsWith(".gguf"))
        assertEquals(64, m.sha256.length)
        assertTrue(m.sha256.all { it in '0'..'9' || it in 'a'..'f' })
        assertEquals(1_049_615_680L, m.taille)
        assertEquals("1,05 Go", m.tailleLisible)
        assertTrue(m.licence.contains("LFM Open License"))
    }

    @Test
    fun l_en_tete_gguf_est_reconnu() {
        val v3 = byteArrayOf('G'.code.toByte(), 'G'.code.toByte(), 'U'.code.toByte(), 'F'.code.toByte(), 3, 0, 0, 0)
        assertTrue(ModeleLocal.enTeteValide(v3))
        val v2 = v3.copyOf().also { it[4] = 2 }
        assertTrue(ModeleLocal.enTeteValide(v2))
        val html = "<!DOCTYPE html>".toByteArray()
        assertFalse(ModeleLocal.enTeteValide(html))
        val version99 = v3.copyOf().also { it[4] = 99 }
        assertFalse(ModeleLocal.enTeteValide(version99))
        assertFalse(ModeleLocal.enTeteValide(byteArrayOf(1, 2)))
    }

    @Test
    fun la_reprise_de_telechargement_utilise_l_en_tete_range() {
        assertNull(TelechargementModele.enTeteReprise(0))
        assertEquals("bytes=123456-", TelechargementModele.enTeteReprise(123_456))
        assertTrue(TelechargementModele.messageHttp(403).contains("Importez", ignoreCase = true) ||
            TelechargementModele.messageHttp(403).contains("importez", ignoreCase = true))
        assertTrue(TelechargementModele.messageHttp(404).contains("404"))
    }

    @Test
    fun le_nombre_de_threads_ne_sature_pas_les_petits_coeurs() {
        assertEquals(4, ModeleLocal.nombreThreads(8))
        assertEquals(3, ModeleLocal.nombreThreads(4))
        assertEquals(2, ModeleLocal.nombreThreads(2))
        assertEquals(1, ModeleLocal.nombreThreads(1))
    }

    // ------------------------------------------------------------ fournisseurs

    @Test
    fun le_fournisseur_embarque_a_un_libelle_et_un_mode() {
        assertTrue(MoteurDictee.libelleFournisseur(MoteurDictee.Fournisseur.LOCAL).contains("téléphone"))
        assertEquals("IA LOCALE", MoteurDictee.Mode.IA_LOCALE)
        assertEquals("LOCAL", MoteurDictee.Fournisseur.LOCAL)
    }

    @Test
    fun un_service_compatible_openai_ne_recoit_jamais_un_identifiant_claude() {
        assertEquals("https://api.mistral.ai/v1/models", ClaudeClient.urlModeles("https://api.mistral.ai/v1/", compatibleOpenAi = true))
        assertEquals("https://api.anthropic.com/v1/models", ClaudeClient.urlModeles("https://api.anthropic.com", compatibleOpenAi = false))
        assertFalse(ClaudeClient.MODELE_DEFAUT_OPENAI.startsWith("claude"))
    }
}
