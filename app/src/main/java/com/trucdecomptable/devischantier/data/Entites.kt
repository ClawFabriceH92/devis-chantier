package com.trucdecomptable.devischantier.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Statuts d'un devis, dans l'ordre du cycle de vie (§2). */
object Statut {
    const val BROUILLON = "BROUILLON"
    const val ENVOYE = "ENVOYE"
    const val ACCEPTE = "ACCEPTE"
    const val TRANSMIS = "TRANSMIS"
    const val FACTURE = "FACTURE"
    const val REFUSE = "REFUSE"
    const val EXPIRE = "EXPIRE"

    val tous = listOf(BROUILLON, ENVOYE, ACCEPTE, TRANSMIS, FACTURE, REFUSE, EXPIRE)

    fun libelle(statut: String): String = when (statut) {
        BROUILLON -> "Brouillon"
        ENVOYE -> "Envoyé au client"
        ACCEPTE -> "Accepté"
        TRANSMIS -> "Transmis au comptable"
        FACTURE -> "Facturé"
        REFUSE -> "Refusé"
        EXPIRE -> "Expiré"
        else -> statut
    }

    /** Un devis envoyé n'est plus modifiable : on repart d'une nouvelle version (§F-15). */
    fun verrouille(statut: String): Boolean = statut != BROUILLON
}

/** Familles et sous-familles du catalogue (§F-20). */
@Entity(tableName = "famille")
data class Famille(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nom: String,
    val parentId: Long? = null,
    val metier: String? = null,
)

/** Article du catalogue de vente. Le prix est figé dans chaque ligne de devis (F-25). */
@Entity(tableName = "article", indices = [Index("familleId")])
data class Article(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val familleId: Long? = null,
    val libelle: String,
    val reference: String? = null,
    val unite: String = "u",
    val prixHT: Double = 0.0,
    val tauxTva: Double = 20.0,
    val nature: String = "fourniture",
    val synonymes: String = "",
    val favori: Boolean = false,
    val usages: Int = 0,
)

/** Client particulier ou professionnel (fiche remplie depuis l'Annuaire des Entreprises). */
@Entity(tableName = "client")
data class Client(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nom: String,
    val professionnel: Boolean = false,
    val siren: String? = null,
    val siret: String? = null,
    val formeJuridique: String? = null,
    val naf: String? = null,
    val tvaIntra: String? = null,
    val adresse: String? = null,
    val email: String? = null,
    val telephone: String? = null,
    val adresseChantier: String? = null,
    val radie: Boolean = false,
    val majLe: Long = System.currentTimeMillis(),
)

/** Le devis. Les mentions légales sont figées au moment de l'envoi (F-15, §6). */
@Entity(tableName = "devis")
data class Devis(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val numero: String,
    val indice: String = "A",
    val statut: String = Statut.BROUILLON,
    val clientId: Long? = null,
    val clientNom: String = "",
    val clientAdresse: String? = null,
    val clientSiren: String? = null,
    val objet: String = "",
    val dateDevis: Long = System.currentTimeMillis(),
    val validiteJours: Int = 30,
    val adresseChantier: String? = null,
    val remiseGlobalePct: Double = 0.0,
    val acomptePct: Double = 0.0,
    val totalHT: Double = 0.0,
    val totalTVA: Double = 0.0,
    val totalTTC: Double = 0.0,
    val montantAcompte: Double = 0.0,
    val coutAchat: Double = 0.0,
    val signeChezClient: Boolean = false,
    val mentionTauxReduit: Boolean = false,
    val tauxReduit: Double = 10.0,
    val urgence: Boolean = false,
    val dateEnvoi: Long? = null,
    val dateAcceptation: Long? = null,
    val dateTransmission: Long? = null,
    val dateRefus: Long? = null,
    val dateFacturation: Long? = null,
    val signatureChemin: String? = null,
    val devisParentId: Long? = null,
    val note: String = "",
    val mentionsTexte: String = "",
    val transmisComptable: Boolean = false,
) {
    val dateLimite: Long get() = dateDevis + validiteJours * 86_400_000L
}

/** Ligne de devis, éventuellement rattachée à une section (« Salle de bain »). */
@Entity(tableName = "ligne", indices = [Index("devisId")])
data class Ligne(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val devisId: Long = 0,
    val section: String? = null,
    val designation: String = "",
    val unite: String = "u",
    val quantite: Double = 1.0,
    val prixUnitaireHT: Double = 0.0,
    val tauxTva: Double = 20.0,
    val remisePct: Double = 0.0,
    val articleId: Long? = null,
    val position: Int = 0,
    val coutAchatUnitaire: Double = 0.0,
)

/** Achat de l'artisan : distinct du catalogue de vente (§3.6, M-01). */
@Entity(tableName = "achat")
data class Achat(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val libelle: String,
    val famille: String? = null,
    val fournisseur: String? = null,
    val unite: String = "u",
    val prixHT: Double = 0.0,
    val datePrix: Long = System.currentTimeMillis(),
)

/** Historique des prix d'achat, pour signaler les hausses (M-06). */
@Entity(tableName = "prix_achat", indices = [Index("achatId")])
data class PrixAchat(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val achatId: Long,
    val prixHT: Double,
    val date: Long = System.currentTimeMillis(),
)

/** « Pose WC » = WC + raccords + évacuation : un article vendu relié à plusieurs achats (M-03). */
@Entity(tableName = "lien_achat", primaryKeys = ["articleId", "achatId"])
data class LienAchat(
    val articleId: Long,
    val achatId: Long,
    val quantite: Double = 1.0,
)

/** Journal des envois et des actions (E-13, T-06). */
@Entity(tableName = "journal", indices = [Index("devisId")])
data class Evenement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val devisId: Long,
    val date: Long = System.currentTimeMillis(),
    val type: String,
    val detail: String = "",
)
