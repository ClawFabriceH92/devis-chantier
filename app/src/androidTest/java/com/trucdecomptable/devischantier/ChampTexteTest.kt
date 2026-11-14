package com.trucdecomptable.devischantier

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.trucdecomptable.devischantier.data.Reglages
import com.trucdecomptable.devischantier.ui.BoutonSecondaire
import com.trucdecomptable.devischantier.ui.ChampTexte
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Le champ de saisie relié aux préférences, tel que l'assistant de premier
 * lancement l'utilise : la frappe doit rester à l'écran et arriver dans la
 * préférence, et une valeur poussée de l'extérieur (fiche reprise de
 * l'Annuaire) doit s'afficher. En 1.0.x, chaque lettre tapée disparaissait.
 */
@RunWith(AndroidJUnit4::class)
class ChampTexteTest {

    @get:Rule
    val compose = createComposeRule()

    private val contexte: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun la_frappe_reste_affichee_et_arrive_dans_la_preference() {
        val reglages = Reglages(contexte)
        reglages.entrepriseNom = ""
        compose.setContent {
            // Comme dans EcranOnboarding : valeur lue dans la préférence, rien ne provoque de recomposition.
            ChampTexte("Dénomination", reglages.entrepriseNom, onValeur = { reglages.entrepriseNom = it })
        }
        compose.onNodeWithText("Dénomination").performClick()
        compose.onNodeWithText("Dénomination").performTextInput("Durand Plomberie")
        compose.waitForIdle()
        compose.onNodeWithText("Dénomination").assertTextContains("Durand Plomberie")
        assertEquals("Durand Plomberie", reglages.entrepriseNom)
    }

    @Test
    fun une_valeur_poussee_de_l_exterieur_remplace_le_texte_quand_le_champ_n_est_pas_en_saisie() {
        compose.setContent { ChampAvecRemplissage() }
        compose.onNodeWithText("Remplir").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Dénomination").assertTextContains("DURAND PLOMBERIE")
    }

    @Composable
    private fun ChampAvecRemplissage() {
        var valeur by remember { mutableStateOf("") }
        ChampTexte("Dénomination", valeur, onValeur = { valeur = it })
        BoutonSecondaire("Remplir", onClick = { valeur = "DURAND PLOMBERIE" })
    }
}
