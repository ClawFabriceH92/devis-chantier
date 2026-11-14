package com.trucdecomptable.devischantier

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.trucdecomptable.devischantier.data.Article
import com.trucdecomptable.devischantier.data.BaseLocale
import com.trucdecomptable.devischantier.data.Client
import com.trucdecomptable.devischantier.data.Devis
import com.trucdecomptable.devischantier.data.Ligne
import com.trucdecomptable.devischantier.data.Reglages
import com.trucdecomptable.devischantier.data.Statut
import com.trucdecomptable.devischantier.ui.DevisTheme
import com.trucdecomptable.devischantier.ui.EcranAccueil
import com.trucdecomptable.devischantier.ui.EcranDetailDevis
import com.trucdecomptable.devischantier.ui.EcranDevis
import com.trucdecomptable.devischantier.ui.EcranDictee
import com.trucdecomptable.devischantier.ui.EcranOnboarding
import com.trucdecomptable.devischantier.ui.EcranReglages
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Maquettes : chaque écran principal est rendu en JVM (Robolectric, format
 * téléphone 411 × 891 dp) et enregistré en PNG dans app/build/captures. Sert à
 * juger l'ergonomie et les couleurs sans appareil. Ignoré sauf si la variable
 * d'environnement CAPTURES_ECRANS vaut 1 : la CI n'en a pas besoin.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Application de base : DevisApplication démarre WorkManager et la boucle de mise à jour, inutiles ici.
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class CapturesEcransTest {

    @get:Rule
    val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val contexte get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var base: BaseLocale
    private lateinit var reglages: Reglages

    private val devisExemples = listOf(
        Devis(id = 1, numero = "D-2026-0012", statut = Statut.BROUILLON, clientNom = "Mme Martin", objet = "Remplacement chauffe-eau", totalTTC = 1450.0, totalHT = 1318.18, dateDevis = System.currentTimeMillis() - 86_400_000L),
        Devis(id = 2, numero = "D-2026-0011", statut = Statut.ENVOYE, clientNom = "SCI Les Tilleuls", objet = "Salle de bain complète, 2e étage", totalTTC = 8420.5, totalHT = 7655.0, dateDevis = System.currentTimeMillis() - 9 * 86_400_000L, dateEnvoi = System.currentTimeMillis() - 8 * 86_400_000L, validiteJours = 30),
        Devis(id = 3, numero = "D-2026-0010", statut = Statut.ACCEPTE, clientNom = "M. Dupont", objet = "Mitigeur + siphon lavabo", totalTTC = 236.5, totalHT = 215.0, dateDevis = System.currentTimeMillis() - 15 * 86_400_000L, dateAcceptation = System.currentTimeMillis() - 3 * 86_400_000L),
        Devis(id = 4, numero = "D-2026-0009", statut = Statut.ENVOYE, clientNom = "Boulangerie Petit", objet = "Dépannage fuite", totalTTC = 180.0, totalHT = 150.0, dateDevis = System.currentTimeMillis() - 26 * 86_400_000L, dateEnvoi = System.currentTimeMillis() - 25 * 86_400_000L),
        Devis(id = 5, numero = "D-2026-0008", statut = Statut.FACTURE, clientNom = "M. et Mme Leroy", objet = "Pose WC suspendu", totalTTC = 640.0, totalHT = 581.82, dateDevis = System.currentTimeMillis() - 40 * 86_400_000L),
        Devis(id = 6, numero = "D-2026-0007", statut = Statut.REFUSE, clientNom = "Garage Moderne", objet = "Chaufferie", totalTTC = 12400.0, totalHT = 10333.0, dateDevis = System.currentTimeMillis() - 50 * 86_400_000L),
    )

    @Before
    fun preparer() {
        assumeTrue("captures désactivées (CAPTURES_ECRANS≠1)", System.getenv("CAPTURES_ECRANS") == "1")
        base = Room.inMemoryDatabaseBuilder(contexte, BaseLocale::class.java).allowMainThreadQueries().build()
        reglages = Reglages(contexte)
        reglages.entrepriseNom = "Durand Plomberie"
        reglages.siren = "123456789"
        reglages.adresseEntreprise = "12 rue des Artisans, 92600 Asnières-sur-Seine"
        reglages.assureurNom = "AXA"
        reglages.assureurCoordonnees = "Contrat 1234567, 75008 Paris"
        reglages.mediateur = "CM2C, 14 rue Saint-Jean 75017 Paris"
        reglages.onboardingFait = true
        runBlocking {
            val dao = base.dao()
            dao.enregistrerClient(Client(id = 1, nom = "Mme Martin", adresse = "4 rue des Lilas, 92600 Asnières", email = "martin@example.org"))
            dao.enregistrerArticle(Article(id = 1, libelle = "Mitigeur lavabo", unite = "u", prixHT = 85.0, tauxTva = 10.0))
            dao.enregistrerArticle(Article(id = 2, libelle = "Main d'œuvre plomberie", unite = "h", prixHT = 48.0, tauxTva = 10.0))
            devisExemples.forEach { dao.enregistrerDevis(it.copy(clientId = 1)) }
            dao.enregistrerLigne(Ligne(devisId = 1, designation = "Chauffe-eau électrique 200 L", unite = "u", quantite = 1.0, prixUnitaireHT = 690.0, tauxTva = 10.0, position = 0))
            dao.enregistrerLigne(Ligne(devisId = 1, designation = "Dépose de l'ancien et évacuation", unite = "forfait", quantite = 1.0, prixUnitaireHT = 120.0, tauxTva = 10.0, position = 1))
            dao.enregistrerLigne(Ligne(devisId = 1, designation = "Main d'œuvre plomberie", unite = "h", quantite = 4.0, prixUnitaireHT = 48.0, tauxTva = 10.0, position = 2))
            dao.enregistrerLigne(Ligne(devisId = 2, designation = "Receveur de douche extra-plat 90x120", unite = "u", quantite = 1.0, prixUnitaireHT = 420.0, tauxTva = 10.0, position = 0, section = "Douche"))
        }
    }

    /**
     * Dessine la vue racine de l'activité dans un bitmap : sous Robolectric,
     * captureToImage() attend un rafraîchissement matériel qui n'arrive jamais,
     * alors que View.draw() fonctionne avec le rendu natif.
     */
    private fun capturer(nom: String, contenu: @Composable () -> Unit) {
        compose.setContent { DevisTheme { contenu() } }
        compose.waitForIdle()
        // Les écrans lisent Room par des Flow : on laisse la première émission arriver.
        repeat(3) { Thread.sleep(400); compose.waitForIdle() }
        var bitmap: Bitmap? = null
        compose.activityRule.scenario.onActivity { activite ->
            val vue = activite.window.decorView
            val largeur = if (vue.width > 0) vue.width else 1233
            val hauteur = if (vue.height > 0) vue.height else 2673
            vue.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(largeur, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(hauteur, android.view.View.MeasureSpec.EXACTLY),
            )
            vue.layout(0, 0, largeur, hauteur)
            val image = Bitmap.createBitmap(largeur, hauteur, Bitmap.Config.ARGB_8888)
            vue.draw(android.graphics.Canvas(image))
            bitmap = image
        }
        compose.waitForIdle()
        val dossier = File("build/captures").apply { mkdirs() }
        File(dossier, "$nom.png").outputStream().use { bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun accueil() = capturer("01-accueil") {
        EcranAccueil(devisExemples, reglages.entrepriseNom, {}, {}, {}, {}, { _, _ -> })
    }

    @Test fun accueil_vide() = capturer("02-accueil-vide") {
        EcranAccueil(emptyList(), "", {}, {}, {}, {}, { _, _ -> })
    }

    @Test fun onboarding() = capturer("03-onboarding") {
        EcranOnboarding(contexte, base.dao(), Reglages(contexte).also { it.entrepriseNom = "" }, {})
    }

    @Test fun edition_devis() = capturer("04-edition-devis") {
        EcranDevis(contexte, base.dao(), reglages, 1L, emptyList(), emptyList(), {}, {}, {})
    }

    @Test fun detail_devis() = capturer("05-detail-devis") {
        EcranDetailDevis(contexte, base.dao(), reglages, 2L, emptyList(), false, {}, {}, {})
    }

    @Test fun dictee() = capturer("06-dictee") {
        EcranDictee(contexte, base.dao(), reglages, null, {}, {})
    }

    @Test fun reglages() = capturer("07-reglages") {
        EcranReglages(contexte, base.dao(), reglages, {})
    }
}
