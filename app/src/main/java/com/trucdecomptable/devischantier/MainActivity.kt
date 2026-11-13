package com.trucdecomptable.devischantier

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Euro
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trucdecomptable.devischantier.data.BaseLocale
import com.trucdecomptable.devischantier.data.Depots
import com.trucdecomptable.devischantier.data.Reglages
import com.trucdecomptable.devischantier.data.Statut
import com.trucdecomptable.devischantier.devis.OperationsDevis
import com.trucdecomptable.devischantier.ui.BoutonGeant
import com.trucdecomptable.devischantier.ui.DialogueConfirmation
import com.trucdecomptable.devischantier.ui.DevisTheme
import com.trucdecomptable.devischantier.ui.EcranAchats
import com.trucdecomptable.devischantier.ui.EcranArticles
import com.trucdecomptable.devischantier.ui.EcranClients
import com.trucdecomptable.devischantier.ui.EcranDevis
import com.trucdecomptable.devischantier.ui.EcranDetailDevis
import com.trucdecomptable.devischantier.ui.EcranDictee
import com.trucdecomptable.devischantier.ui.EcranOnboarding
import com.trucdecomptable.devischantier.ui.EcranReglages
import com.trucdecomptable.devischantier.ui.EcranAccueil
import com.trucdecomptable.devischantier.update.UpdateManager
import com.trucdecomptable.devischantier.util.Diag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Les écrans de l'application. L'accueil, les articles, les clients et les marges
 *  partagent la barre du bas ; le reste s'empile par-dessus. */
sealed interface Ecran {
    data object Devis : Ecran
    data object Articles : Ecran
    data object Clients : Ecran
    data object Marges : Ecran
    data object Reglages : Ecran
    data object Onboarding : Ecran
    data class Edition(val devisId: Long) : Ecran
    data class Detail(val devisId: Long, val relancer: Boolean = false) : Ecran
    data class Dictee(val devisId: Long?) : Ecran
}

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_DEVIS_ID = "devis_id"
        const val EXTRA_RELANCER = "relancer"
        const val EXTRA_TRAITEE = "intention_traitee"
    }

    private var intention by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intention = intent
        setContent {
            val contexte = LocalContext.current
            val reglages = remember { Reglages(contexte) }
            DevisTheme(modeChantier = reglages.modeChantier) {
                Application(reglages, intention)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        this.intention = intent
    }
}

@Composable
private fun Application(reglages: Reglages, intention: Intent?) {
    val contexte = LocalContext.current
    val portee = rememberCoroutineScope()
    val dao = remember { Depots.dao(contexte) }

    var ecran by remember {
        mutableStateOf<Ecran>(
            if (!reglages.onboardingFait) Ecran.Onboarding else Ecran.Devis,
        )
    }

    val devis by dao.devis().collectAsStateWithLifecycle(initialValue = emptyList())
    val clients by dao.clients().collectAsStateWithLifecycle(initialValue = emptyList())
    val articles by dao.articles().collectAsStateWithLifecycle(initialValue = emptyList())

    // Raccourcis de l'écran d'accueil du téléphone (devischantier://dictee, //nouveau)
    // et notification d'un rappel (EXTRA_DEVIS_ID). Une rotation recrée
    // l'activité avec la même intention : elle est marquée traitée sur l'objet
    // lui-même, sinon le raccourci « nouveau devis » créerait un brouillon à
    // chaque rotation. Une nouvelle notification arrive dans un nouvel objet.
    LaunchedEffect(intention) {
        val source = intention ?: return@LaunchedEffect
        if (source.getBooleanExtra(MainActivity.EXTRA_TRAITEE, false)) return@LaunchedEffect
        source.putExtra(MainActivity.EXTRA_TRAITEE, true)
        val devisId = source.getLongExtra(MainActivity.EXTRA_DEVIS_ID, 0L).takeIf { it > 0 }
        when {
            source.data?.host == "dictee" -> ecran = Ecran.Dictee(null)
            source.data?.host == "nouveau" -> {
                val id = withContext(Dispatchers.IO) { OperationsDevis.nouveau(dao, reglages) }
                ecran = Ecran.Edition(id)
            }
            devisId != null -> ecran = Ecran.Detail(
                devisId,
                relancer = source.getBooleanExtra(MainActivity.EXTRA_RELANCER, false),
            )
        }
    }

    // Les notifications Android 13+ sont demandées au premier lancement utile,
    // avec l'explication qui figure dans les réglages (N-01, §5.4).
    val permissionNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { Diag.record("notifications", if (it) "accordées" else "refusées") }
    LaunchedEffect(reglages.onboardingFait) {
        if (reglages.onboardingFait && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Le bouton retour d'Android ramène à l'accueil au lieu de fermer
    // l'application : un artisan qui sort des réglages ne doit pas se
    // retrouver sur le launcher du téléphone.
    BackHandler(enabled = ecran != Ecran.Devis && ecran != Ecran.Onboarding) {
        ecran = when (val courant = ecran) {
            is Ecran.Dictee -> courant.devisId?.let { Ecran.Edition(it) } ?: Ecran.Devis
            else -> Ecran.Devis
        }
    }

    val etatMaj by UpdateManager.state.collectAsStateWithLifecycle()
    var suppressionDemandee by remember { mutableStateOf<com.trucdecomptable.devischantier.data.Devis?>(null) }

    Scaffold(
        bottomBar = {
            val ongletActif = when (ecran) {
                Ecran.Devis -> 0
                Ecran.Articles -> 1
                Ecran.Clients -> 2
                Ecran.Marges -> 3
                else -> -1
            }
            if (ongletActif >= 0) {
                NavigationBar {
                    NavigationBarItem(
                        selected = ongletActif == 0,
                        onClick = { ecran = Ecran.Devis },
                        icon = { Icon(Icons.Default.Description, contentDescription = null) },
                        label = { Text("Devis") },
                    )
                    NavigationBarItem(
                        selected = ongletActif == 1,
                        onClick = { ecran = Ecran.Articles },
                        icon = { Icon(Icons.Default.Inventory2, contentDescription = null) },
                        label = { Text("Articles") },
                    )
                    NavigationBarItem(
                        selected = ongletActif == 2,
                        onClick = { ecran = Ecran.Clients },
                        icon = { Icon(Icons.Default.Group, contentDescription = null) },
                        label = { Text("Clients") },
                    )
                    NavigationBarItem(
                        selected = ongletActif == 3,
                        onClick = { ecran = Ecran.Marges },
                        icon = { Icon(Icons.Default.Euro, contentDescription = null) },
                        label = { Text("Marges") },
                    )
                }
            }
        },
    ) { marges ->
        Box(Modifier.fillMaxSize().padding(marges)) {
            when (val courant = ecran) {
                Ecran.Onboarding -> EcranOnboarding(
                    contexte = contexte,
                    dao = dao,
                    reglages = reglages,
                    onTermine = { ecran = Ecran.Devis },
                )

                Ecran.Devis -> EcranAccueil(
                    devis = devis,
                    nomEntreprise = reglages.entrepriseNom,
                    onOuvrir = { ecran = Ecran.Detail(it) },
                    onNouveau = {
                        portee.launch(Dispatchers.IO) {
                            val id = OperationsDevis.nouveau(dao, reglages)
                            withContext(Dispatchers.Main) { ecran = Ecran.Edition(id) }
                        }
                    },
                    onDictee = { ecran = Ecran.Dictee(null) },
                    onReglages = { ecran = Ecran.Reglages },
                    onAction = { devisCible, action ->
                        when (action) {
                            "DUPLIQUER" -> portee.launch(Dispatchers.IO) {
                                val id = OperationsDevis.dupliquer(dao, reglages, devisCible.id)
                                withContext(Dispatchers.Main) { if (id != null) ecran = Ecran.Edition(id) }
                            }
                            "VERSION" -> portee.launch(Dispatchers.IO) {
                                val id = OperationsDevis.nouvelleVersion(dao, devisCible.id)
                                withContext(Dispatchers.Main) { if (id != null) ecran = Ecran.Edition(id) }
                            }
                            "RELANCER" -> ecran = Ecran.Detail(devisCible.id, relancer = true)
                            "ACCEPTER" -> portee.launch(Dispatchers.IO) {
                                OperationsDevis.marquerAccepte(dao, devisCible)
                            }
                            "TRANSMETTRE" -> ecran = Ecran.Detail(devisCible.id)
                            "FACTURE" -> portee.launch(Dispatchers.IO) {
                                OperationsDevis.marquerFacture(dao, devisCible)
                            }
                            "SUPPRIMER" -> suppressionDemandee = devisCible
                        }
                    },
                )

                Ecran.Articles -> EcranArticles(contexte, dao, reglages)
                Ecran.Clients -> EcranClients(dao, reglages)
                Ecran.Marges -> EcranAchats(contexte, dao, reglages, onRetour = { ecran = Ecran.Devis })

                Ecran.Reglages -> EcranReglages(
                    contexte = contexte,
                    dao = dao,
                    reglages = reglages,
                    onRetour = { ecran = Ecran.Devis },
                )

                is Ecran.Edition -> EcranDevis(
                    contexte = contexte,
                    dao = dao,
                    reglages = reglages,
                    devisId = courant.devisId,
                    clients = clients,
                    articles = articles,
                    onRetour = { ecran = Ecran.Devis },
                    onDictee = { ecran = Ecran.Dictee(courant.devisId) },
                    onDetail = { ecran = Ecran.Detail(courant.devisId) },
                )

                is Ecran.Detail -> EcranDetailDevis(
                    contexte = contexte,
                    dao = dao,
                    reglages = reglages,
                    devisId = courant.devisId,
                    clients = clients,
                    relancerDemande = courant.relancer,
                    onRetour = { ecran = Ecran.Devis },
                    onModifier = { ecran = Ecran.Edition(courant.devisId) },
                    onSupprime = { ecran = Ecran.Devis },
                )

                is Ecran.Dictee -> EcranDictee(
                    contexte = contexte,
                    dao = dao,
                    reglages = reglages,
                    devisId = courant.devisId,
                    onTermine = { id -> ecran = Ecran.Edition(id) },
                    onRetour = {
                        ecran = courant.devisId?.let { Ecran.Edition(it) } ?: Ecran.Devis
                    },
                )
            }
        }
    }

    suppressionDemandee?.let { cible ->
        DialogueConfirmation(
            titre = "Supprimer ce brouillon ?",
            texte = "Le devis ${cible.numero} sera supprimé définitivement.",
            libelleConfirmer = "Supprimer",
            onConfirmer = {
                portee.launch(Dispatchers.IO) {
                    dao.supprimerLignes(cible.id)
                    dao.supprimerDevis(cible.id)
                }
            },
            onAnnuler = { suppressionDemandee = null },
        )
    }

    DialogueMiseAJour(
        etat = etatMaj,
        onTelecharger = { UpdateManager.download(contexte) },
        onInstaller = {
            if (UpdateManager.canInstall(contexte)) UpdateManager.install(contexte) else UpdateManager.openInstallSettings(contexte)
        },
        onAutoriser = { UpdateManager.openInstallSettings(contexte) },
        onPlusTard = {
            val disponible = etatMaj as? UpdateManager.State.Available
            if (disponible != null) {
                // « Plus tard » survit au redémarrage : la version est mémorisée.
                reglages.versionIgnoree = disponible.version
                UpdateManager.ignorerVersion(disponible.version)
            } else {
                UpdateManager.dismiss()
            }
        },
    )
}

/** La mise à jour doit se voir, pas seulement exister dans les réglages. */
@Composable
private fun DialogueMiseAJour(
    etat: UpdateManager.State,
    onTelecharger: () -> Unit,
    onInstaller: () -> Unit,
    onAutoriser: () -> Unit,
    onPlusTard: () -> Unit,
) {
    val instantane = etat
    when (instantane) {
        is UpdateManager.State.Available -> AlertDialog(
            onDismissRequest = onPlusTard,
            title = { Text("Mise à jour ${instantane.version} disponible") },
            text = {
                Text(
                    instantane.notes.ifBlank {
                        "Une nouvelle version de Devis Chantier est prête à être téléchargée."
                    },
                )
            },
            confirmButton = { BoutonGeant("Télécharger", onClick = onTelecharger) },
            dismissButton = { TextButton(onClick = onPlusTard) { Text("Plus tard") } },
        )

        is UpdateManager.State.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Téléchargement de la mise à jour") },
            text = { Text("${(instantane.progress * 100).toInt()} %") },
            confirmButton = { TextButton(onClick = onPlusTard) { Text("Masquer") } },
        )

        is UpdateManager.State.ReadyToInstall -> AlertDialog(
            onDismissRequest = onPlusTard,
            title = { Text("Version ${instantane.version} prête") },
            text = { Text("L'installation ouvre l'écran Android habituel : un appui, puis confirmation.") },
            confirmButton = { BoutonGeant("Installer", onClick = onInstaller) },
            dismissButton = { TextButton(onClick = onPlusTard) { Text("Plus tard") } },
        )

        is UpdateManager.State.Failed -> AlertDialog(
            onDismissRequest = onPlusTard,
            title = { Text("Mise à jour impossible") },
            text = { Text(instantane.detail ?: "Vérification impossible pour le moment.") },
            confirmButton = { TextButton(onClick = onPlusTard) { Text("Fermer") } },
        )

        else -> Unit
    }
}
