package com.trucdecomptable.devischantier.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trucdecomptable.devischantier.api.EntrepriseResultat
import com.trucdecomptable.devischantier.api.Etablissement
import com.trucdecomptable.devischantier.api.RechercheEntreprises
import com.trucdecomptable.devischantier.data.Client
import com.trucdecomptable.devischantier.data.Dao
import com.trucdecomptable.devischantier.data.Reglages
import com.trucdecomptable.devischantier.util.Diag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Le carnet clients (§3.5) : saisie manuelle, et remplissage automatique depuis
 * l'Annuaire des Entreprises de l'État pour un client professionnel.
 */
@Composable
fun EcranClients(
    dao: Dao,
    reglages: Reglages,
) {
    val portee = rememberCoroutineScope()
    val clients by dao.clients().collectAsStateWithLifecycle(initialValue = emptyList())
    var recherche by remember { mutableStateOf("") }
    var rechercheApi by remember { mutableStateOf("") }
    var resultats by remember { mutableStateOf<List<EntrepriseResultat>>(emptyList()) }
    var chargement by remember { mutableStateOf(false) }
    var erreur by remember { mutableStateOf("") }
    var clientEnEdition by remember { mutableStateOf<Client?>(null) }
    var resultatChoisi by remember { mutableStateOf<EntrepriseResultat?>(null) }

    val filtres = clients.filter {
        recherche.isBlank() || it.nom.contains(recherche, ignoreCase = true) ||
            it.siren.orEmpty().contains(recherche)
    }

    LaunchedEffect(rechercheApi) {
        if (rechercheApi.trim().length < 3) {
            resultats = emptyList()
            erreur = ""
            return@LaunchedEffect
        }
        delay(450)
        chargement = true
        val reponse = RechercheEntreprises.rechercher(rechercheApi)
        chargement = false
        resultats = reponse.getOrElse {
            erreur = it.message ?: "Recherche indisponible"
            emptyList()
        }
        erreur = if (resultats.isEmpty() && erreur.isBlank() && rechercheApi.trim().length >= 3) {
            "Aucune entreprise trouvée. La saisie manuelle reste possible."
        } else {
            erreur
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Clients", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            IconButton(
                onClick = { clientEnEdition = Client(nom = "", professionnel = false) },
                modifier = Modifier.size(56.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = "Nouveau client", modifier = Modifier.size(30.dp))
            }
        }
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            ChampTexte("Rechercher dans mes clients", recherche, onValeur = { recherche = it })
            ChampTexte(
                "Entreprise : nom, SIREN ou SIRET",
                rechercheApi,
                onValeur = { rechercheApi = it },
                aide = "Remplit la fiche depuis l'Annuaire des Entreprises (API de l'État, sans clé).",
            )
            if (chargement) Text("Recherche en cours…", style = MaterialTheme.typography.bodyMedium)
            if (erreur.isNotBlank()) {
                BlocAlerte(erreur)
                Spacer(Modifier.height(6.dp))
            }
            if (resultats.isNotEmpty()) {
                CarteBloc(titre = "Résultats de l'Annuaire") {
                    LazyColumn(Modifier.heightIn(max = 240.dp)) {
                        items(resultats) { r ->
                            BoutonSecondaire(
                                texte = r.nom + (if (r.cessee) " · radiée" else "") + " · ${r.siren}",
                                onClick = { resultatChoisi = r },
                                modifier = Modifier.padding(vertical = 2.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (filtres.isEmpty()) {
                Text(
                    "Aucun client enregistré. Cherchez une entreprise ci-dessus ou créez un particulier.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Couleurs.gris,
                )
            }
            LazyColumn(Modifier.weight(1f)) {
                items(filtres) { c ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 68.dp)
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp),
                        ) {
                            Text(c.nom, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                listOfNotNull(
                                    if (c.professionnel) "professionnel" else "particulier",
                                    c.siren?.let { "SIREN $it" },
                                    c.adresse,
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Couleurs.gris,
                            )
                        }
                        BoutonSecondaire(
                            "Ouvrir",
                            onClick = { clientEnEdition = c },
                            modifier = Modifier.size(width = 110.dp, height = 56.dp),
                        )
                    }
                }
                item { Spacer(Modifier.height(90.dp)) }
            }
        }
    }

    resultatChoisi?.let { resultat ->
        DialogueEntreprise(
            resultat = resultat,
            onChoisir = { etablissement ->
                val client = RechercheEntreprises.versClient(resultat, etablissement)
                portee.launch(Dispatchers.IO) { dao.enregistrerClient(client) }
                resultatChoisi = null
                rechercheApi = ""
                resultats = emptyList()
            },
            onSaisieManuelle = {
                clientEnEdition = Client(
                    nom = resultat.nom,
                    professionnel = true,
                    siren = resultat.siren,
                    adresse = resultat.adresse,
                )
                resultatChoisi = null
            },
            onFermer = { resultatChoisi = null },
        )
    }

    clientEnEdition?.let { client ->
        DialogueClient(
            client = client,
            onEnregistrer = { c ->
                portee.launch(Dispatchers.IO) { dao.enregistrerClient(c) }
                clientEnEdition = null
            },
            onSupprimer = if (client.id != 0L) {
                {
                    portee.launch(Dispatchers.IO) { dao.supprimerClient(client.id) }
                    clientEnEdition = null
                }
            } else null,
            onActualiser = {
                val siren = client.siren
                if (!siren.isNullOrBlank()) {
                    portee.launch(Dispatchers.IO) {
                        val reponse = RechercheEntreprises.rechercher(siren)
                        val premier = reponse.getOrNull()?.firstOrNull()
                        if (premier != null) {
                            val frais = RechercheEntreprises.versClient(premier).copy(
                                id = client.id,
                                adresseChantier = client.adresseChantier,
                                email = client.email,
                                telephone = client.telephone,
                            )
                            dao.enregistrerClient(frais)
                            withContext(Dispatchers.Main) { clientEnEdition = frais }
                        }
                    }
                }
            },
            onFermer = { clientEnEdition = null },
        )
    }
}

@Composable
private fun DialogueEntreprise(
    resultat: EntrepriseResultat,
    onChoisir: (Etablissement?) -> Unit,
    onSaisieManuelle: () -> Unit,
    onFermer: () -> Unit,
) {
    var etablissement by remember { mutableStateOf<Etablissement?>(resultat.etablissements.firstOrNull()) }
    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text(resultat.nom, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (resultat.cessee) {
                    BlocAlerte("Cette entreprise est signalée cessée ou radiée.", Couleurs.rouge)
                    Spacer(Modifier.height(8.dp))
                }
                LigneValeur("SIREN", resultat.siren)
                resultat.siret?.let { LigneValeur("SIRET", it) }
                resultat.formeJuridique?.let { LigneValeur("Forme", it) }
                resultat.naf?.let { LigneValeur("NAF", it) }
                resultat.tvaIntra?.let { LigneValeur("TVA intracom.", it) }
                if (resultat.dirigeants.isNotEmpty()) {
                    LigneValeur("Dirigeants", RechercheEntreprises.formaterDirigeants(resultat.dirigeants))
                }
                Spacer(Modifier.height(10.dp))
                Text("Établissement retenu", style = MaterialTheme.typography.titleMedium)
                resultat.etablissements.forEach { e ->
                    BoutonSecondaire(
                        texte = (if (e.siret == resultat.siret) "Siège · " else "") +
                            RechercheEntreprises.resumeEtablissement(e),
                        onClick = { etablissement = e },
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                BoutonSecondaire("Saisir la fiche à la main", onClick = onSaisieManuelle)
            }
        },
        confirmButton = {
            BoutonGeant("Utiliser cette fiche", onClick = { onChoisir(etablissement) }, hauteur = 56.dp)
        },
        dismissButton = { TextButton(onClick = onFermer) { Text("Annuler") } },
    )
}

@Composable
private fun DialogueClient(
    client: Client,
    onEnregistrer: (Client) -> Unit,
    onSupprimer: (() -> Unit)?,
    onActualiser: () -> Unit,
    onFermer: () -> Unit,
) {
    var nom by remember { mutableStateOf(client.nom) }
    var adresse by remember { mutableStateOf(client.adresse.orEmpty()) }
    var chantier by remember { mutableStateOf(client.adresseChantier.orEmpty()) }
    var email by remember { mutableStateOf(client.email.orEmpty()) }
    var telephone by remember { mutableStateOf(client.telephone.orEmpty()) }
    var siren by remember { mutableStateOf(client.siren.orEmpty()) }
    var professionnel by remember { mutableStateOf(client.professionnel) }

    AlertDialog(
        onDismissRequest = onFermer,
        title = {
            Text(
                if (client.id == 0L) "Nouveau client" else "Fiche client",
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ChampTexte("Nom", nom, onValeur = { nom = it })
                ChampTexte("Adresse de facturation", adresse, onValeur = { adresse = it }, lignes = 2)
                ChampTexte("Adresse du chantier", chantier, onValeur = { chantier = it }, lignes = 2)
                ChampTexte("Email", email, onValeur = { email = it })
                ChampTexte("Téléphone", telephone, onValeur = { telephone = it }, clavier = KeyboardType.Phone)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = professionnel,
                        onCheckedChange = { professionnel = it },
                        modifier = Modifier.size(48.dp),
                    )
                    Text("Client professionnel")
                }
                if (professionnel) {
                    ChampTexte("SIREN", siren, onValeur = { siren = it }, clavier = KeyboardType.Number)
                    RechercheEntreprises.tvaIntracommunautaire(siren)?.let {
                        Text("TVA intracommunautaire : $it", style = MaterialTheme.typography.bodyMedium)
                    }
                    BoutonSecondaire("Actualiser depuis l'Annuaire", onClick = onActualiser)
                }
                if (client.radie) {
                    BlocAlerte("Entreprise signalée radiée ou cessée.", Couleurs.rouge)
                }
                if (onSupprimer != null) {
                    Spacer(Modifier.height(8.dp))
                    BoutonSecondaire("Supprimer ce client", onClick = onSupprimer)
                }
            }
        },
        confirmButton = {
            BoutonGeant(
                "Enregistrer",
                onClick = {
                    onEnregistrer(
                        client.copy(
                            nom = nom,
                            adresse = adresse.ifBlank { null },
                            adresseChantier = chantier.ifBlank { null },
                            email = email.ifBlank { null },
                            telephone = telephone.ifBlank { null },
                            siren = siren.ifBlank { null },
                            professionnel = professionnel,
                            majLe = System.currentTimeMillis(),
                        ),
                    )
                },
                hauteur = 56.dp,
            )
        },
        dismissButton = { TextButton(onClick = onFermer) { Text("Annuler") } },
    )
}
