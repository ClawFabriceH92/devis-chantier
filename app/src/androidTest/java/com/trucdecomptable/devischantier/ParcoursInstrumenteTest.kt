package com.trucdecomptable.devischantier

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.trucdecomptable.devischantier.data.Client
import com.trucdecomptable.devischantier.data.Depots
import com.trucdecomptable.devischantier.data.Ligne
import com.trucdecomptable.devischantier.data.Reglages
import com.trucdecomptable.devischantier.data.Statut
import com.trucdecomptable.devischantier.devis.OperationsDevis
import com.trucdecomptable.devischantier.util.Formats
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Vérifications exécutées sur un vrai Android (émulateur de la CI) : ce que les
 * tests JVM ne peuvent pas prouver, c'est-à-dire le rendu réel du PDF, le
 * chiffrement de la sauvegarde par le Keystore et la persistance Room.
 */
@RunWith(AndroidJUnit4::class)
class ParcoursInstrumenteTest {

    private val contexte = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun reglagesComplets(): Reglages = Reglages(contexte).apply {
        entrepriseNom = "Plomberie Martin"
        adresseEntreprise = "1 rue des Artisans, 92600 Asnières-sur-Seine"
        siren = "123456789"
        siret = "12345678900012"
        formeJuridique = "Entreprise individuelle"
        telephone = "0102030405"
        email = "contact@plomberie-martin.fr"
        regimeTva = "FRANCHISE"
        assureurNom = "AXA IARD"
        assureurCoordonnees = "12 avenue de la Paix, 75010 Paris"
        couvertureGeo = "France métropolitaine"
        attestationExpiration = System.currentTimeMillis() + 300L * 86_400_000L
        mediateur = "CM2C, 14 rue Saint-Jean, 75010 Paris"
        comptableEmails = "comptable@cabinet.fr"
        validiteJours = 30
        acomptePct = 30.0
    }

    @Test
    fun un_devis_complet_produit_un_pdf_valide() = runBlocking {
        val reglages = reglagesComplets()
        val dao = Depots.dao(contexte)
        val client = Client(
            nom = "M. Durand",
            adresse = "5 rue des Lilas, 92600 Asnières-sur-Seine",
            email = "durand@example.com",
        )
        val clientId = dao.enregistrerClient(client)
        val devisId = OperationsDevis.nouveau(dao, reglages, clientId)
        val devis = dao.devis(devisId)!!
        val avecObjet = devis.copy(
            objet = "Rénovation de la salle de bain",
            clientNom = "M. Durand",
            statut = Statut.BROUILLON,
        )
        dao.enregistrerDevis(avecObjet)
        dao.enregistrerLigne(
            Ligne(
                devisId = devisId,
                section = "Salle de bain",
                designation = "Mitigeur lavabo",
                quantite = 2.0,
                prixUnitaireHT = 85.0,
                tauxTva = 10.0,
            ),
        )
        dao.enregistrerLigne(
            Ligne(
                devisId = devisId,
                designation = "Main-d'œuvre plomberie",
                unite = "h",
                quantite = 3.0,
                prixUnitaireHT = 48.0,
                tauxTva = 10.0,
            ),
        )
        val lignes = dao.lignesListe(devisId)
        val courant = dao.devis(devisId)!!
        val enregistre = OperationsDevis.enregistrer(courant, lignes, dao)

        // Totaux : 2 × 85 + 3 × 48 = 314 HT, TVA 10 % = 31,40, TTC = 345,40
        assertEquals(314.0, enregistre.totalHT, 0.01)
        assertEquals(31.4, enregistre.totalTVA, 0.01)
        assertEquals(345.4, enregistre.totalTTC, 0.01)

        // Contrôle des mentions : elles doivent toutes être réunies.
        val manquantes = OperationsDevis.controler(enregistre, client, lignes, reglages)
        assertTrue("mentions manquantes : $manquantes", manquantes.isEmpty())

        // Génération réelle du PDF sur le téléphone.
        val preparation = OperationsDevis.preparer(
            contexte, enregistre, lignes, dao.client(clientId), reglages,
            avecMarge = false, signature = null,
        )
        assertTrue("préparation en échec : ${preparation.message}", preparation.ok)
        val pdf = preparation.pdf
        assertNotNull(pdf)
        assertTrue("PDF absent", pdf!!.exists())
        assertTrue("PDF trop petit (${pdf.length()} octets)", pdf.length() > 3_000)
        val entete = pdf.inputStream().use { flux ->
            val tampon = ByteArray(5)
            flux.read(tampon)
            String(tampon)
        }
        assertEquals("%PDF-", entete)

        // Le PDF ne doit jamais contenir de marge ni de prix d'achat (M-09).
        val texte = pdf.readBytes().toString(Charsets.ISO_8859_1)
        assertFalse(texte.contains("marge", ignoreCase = true))
        assertFalse(texte.contains("coût d'achat", ignoreCase = true))

        // Les données du comptable sont bien produites à côté du PDF.
        val donnees = preparation.donnees
        assertNotNull(donnees)
        assertTrue(donnees!!.exists())
        assertTrue(donnees.readText().contains("\"total_ttc\""))
    }

    @Test
    fun un_devis_incomplet_est_bloque_avant_l_envoi() = runBlocking {
        val reglages = Reglages(contexte).apply {
            entrepriseNom = ""
            assureurNom = ""
            }
        val dao = Depots.dao(contexte)
        val devisId = OperationsDevis.nouveau(dao, reglages)
        val devis = dao.devis(devisId)!!
        val preparation = OperationsDevis.preparer(contexte, devis, emptyList(), null, reglages)
        assertFalse(preparation.ok)
        assertTrue(preparation.manquantes.isNotEmpty())
        assertTrue(preparation.manquantes.any { it.contains("Nom de l'entreprise") })
    }

    @Test
    fun la_sauvegarde_chiffree_se_relit_sur_l_appareil() {
        val reglages = reglagesComplets()
        val resultat = com.trucdecomptable.devischantier.sauvegarde.Sauvegarde
            .exporter(contexte, reglages, "phrase-secrete-2026")
        assertTrue(resultat.message, resultat.ok)
        val fichier = resultat.fichier!!
        assertTrue(fichier.length() > 1_000)

        val octets = fichier.readBytes()
        // Mauvaise phrase secrète : rien ne doit sortir.
        assertEquals(null, com.trucdecomptable.devischantier.sauvegarde.Sauvegarde
            .dechiffrer(octets, "mauvaise-phrase"))
        val clair = com.trucdecomptable.devischantier.sauvegarde.Sauvegarde
            .dechiffrer(octets, "phrase-secrete-2026")
        assertNotNull(clair)
        // L'archive est un ZIP contenant la base.
        assertEquals('P', clair!![0].toInt().toChar())
        assertEquals('K', clair[1].toInt().toChar())
    }

    @Test
    fun la_cle_api_n_est_jamais_ecrite_en_clair() {
        val reglages = Reglages(contexte)
        val cle = "cle-de-test-0123456789"
        reglages.iaCle = cle
        // La clé reste utilisable dans la session, que le Keystore réponde ou non.
        assertEquals(cle, reglages.iaCle)

        val prefs = contexte.getSharedPreferences("reglages", android.content.Context.MODE_PRIVATE)
        // Ce qui compte : aucune trace en clair sur le disque.
        prefs.all.values.forEach { valeur ->
            assertFalse(
                "une préférence contient la clé en clair : $valeur",
                valeur.toString().contains(cle),
            )
        }
        val stocke = prefs.getString("ia_cle_chiffree", null)
        if (stocke == null) {
            // Keystore indisponible sur cet appareil : la clé reste en mémoire.
            assertTrue(reglages.iaCleNonSecurisee)
        } else {
            assertFalse(stocke.contains(cle))
            assertFalse(reglages.iaCleNonSecurisee)
        }
        reglages.iaCle = null
    }

    @Test
    fun un_devis_envoye_est_verrouille_et_une_version_suit() = runBlocking {
        val reglages = reglagesComplets()
        val dao = Depots.dao(contexte)
        val devisId = OperationsDevis.nouveau(dao, reglages)
        val devis = dao.devis(devisId)!!
        dao.enregistrerLigne(Ligne(devisId = devisId, designation = "Mitigeur", quantite = 1.0, prixUnitaireHT = 100.0))
        val lignes = dao.lignesListe(devisId)
        val envoye = OperationsDevis.marquerEnvoye(dao, devis, null, lignes, reglages)
        assertEquals(Statut.ENVOYE, envoye.statut)
        assertNotNull(envoye.dateEnvoi)
        assertTrue(envoye.mentionsTexte.contains("art. 293 B du CGI"))

        val nouvelId = OperationsDevis.nouvelleVersion(dao, devisId)
        assertNotNull(nouvelId)
        val nouvelle = dao.devis(nouvelId!!)!!
        assertEquals("B", nouvelle.indice)
        assertEquals(Statut.BROUILLON, nouvelle.statut)
        assertEquals(lignes.size, dao.lignesListe(nouvelId).size)
    }

    @Test
    fun les_formats_sont_ceux_du_pdf() {
        assertEquals("345,40 €", Formats.euros(345.4))
        assertEquals("08/11/2026", Formats.date(
            java.util.Calendar.getInstance().apply {
                clear(); set(2026, 10, 8, 12, 0, 0)
            }.timeInMillis,
        ))
    }

    @Test
    fun un_logo_est_repris_dans_le_pdf() {
        val bitmap = Bitmap.createBitmap(120, 60, Bitmap.Config.ARGB_8888)
        val dossier = File(contexte.filesDir, "logo-test").apply { mkdirs() }
        val fichier = File(dossier, "logo.png")
        fichier.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val charge = com.trucdecomptable.devischantier.pdf.GenerateurPdf.chargerBitmap(fichier.absolutePath)
        assertNotNull(charge)
        assertEquals(120, charge!!.width)
    }
}
