package com.trucdecomptable.devischantier.devis

import android.content.Context
import android.graphics.Bitmap
import com.trucdecomptable.devischantier.data.Client
import com.trucdecomptable.devischantier.data.Dao
import com.trucdecomptable.devischantier.data.Devis
import com.trucdecomptable.devischantier.data.Evenement
import com.trucdecomptable.devischantier.data.Ligne
import com.trucdecomptable.devischantier.data.Reglages
import com.trucdecomptable.devischantier.data.Statut
import com.trucdecomptable.devischantier.pdf.GenerateurPdf
import com.trucdecomptable.devischantier.util.Diag
import com.trucdecomptable.devischantier.util.Formats
import java.io.File

/**
 * Le cycle de vie d'un devis, écrit une seule fois : création et numérotation,
 * calcul des totaux, verrouillage à l'envoi, nouvelle version, passage au
 * comptable. Les écrans n'appellent que ces fonctions.
 */
object OperationsDevis {

    data class Contenu(val devis: Devis, val lignes: List<Ligne>, val client: Client?)

    data class Preparation(
        val ok: Boolean,
        val pdf: File? = null,
        val donnees: File? = null,
        val manquantes: List<String> = emptyList(),
        val message: String = "",
    )

    suspend fun numeroSuivant(dao: Dao, reglages: Reglages): String {
        val annee = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        return Calculs.numeroSuivant(reglages.prefixeNumero, annee, dao.numeros("${reglages.prefixeNumero}-$annee-"))
    }

    /** Nouveau brouillon prêt à saisir. */
    suspend fun nouveau(dao: Dao, reglages: Reglages, clientId: Long? = null): Long {
        val client = clientId?.let { dao.client(it) }
        val devis = Devis(
            numero = numeroSuivant(dao, reglages),
            clientId = client?.id,
            clientNom = client?.nom.orEmpty(),
            clientAdresse = client?.adresse,
            clientSiren = client?.siren,
            dateDevis = System.currentTimeMillis(),
            validiteJours = reglages.validiteJours,
            acomptePct = reglages.acomptePct,
        )
        return dao.enregistrerDevis(devis)
    }

    /** Totaux recalculés et enregistrés : le devis reste la référence. */
    suspend fun enregistrer(devis: Devis, lignes: List<Ligne>, dao: Dao): Devis {
        val totaux = Calculs.totaux(lignes, devis.remiseGlobalePct)
        val maj = devis.copy(
            totalHT = totaux.totalHT,
            totalTVA = totaux.totalTVA,
            totalTTC = totaux.totalTTC,
            coutAchat = totaux.coutAchat,
            montantAcompte = Calculs.montantAcompte(totaux.totalTTC, devis.acomptePct),
        )
        dao.enregistrerDevis(maj)
        return maj
    }

    fun contexte(devis: Devis, client: Client?, reglages: Reglages, nombreLignes: Int): ContexteMentions =
        ContexteMentions(
            entrepriseNom = reglages.entrepriseNom,
            adresseEntreprise = reglages.adresseEntreprise,
            siren = reglages.siren,
            siret = reglages.siret,
            immatriculation = reglages.immatriculation,
            formeJuridique = reglages.formeJuridique,
            telephone = reglages.telephone,
            email = reglages.email,
            clientNom = devis.clientNom.ifBlank { client?.nom.orEmpty() },
            clientAdresse = devis.adresseChantier ?: client?.adresse,
            professionalClient = client?.professionnel ?: false,
            objet = devis.objet,
            dateDevis = devis.dateDevis,
            validiteJours = devis.validiteJours,
            devisGratuit = reglages.devisGratuit,
            franchiseTva = !reglages.assujetti,
            mentionFranchisePersonnalisee = reglages.mentionFranchise,
            assureurNom = reglages.assureurNom,
            assureurCoordonnees = reglages.assureurCoordonnees,
            couvertureGeo = reglages.couvertureGeo,
            attestationExpiration = reglages.attestationExpiration,
            mentionTauxReduit = devis.mentionTauxReduit,
            tauxReduit = devis.tauxReduit,
            signeChezClient = devis.signeChezClient,
            mediateur = reglages.mediateur,
            conditionsGenerales = reglages.conditionsGenerales,
            acomptePct = devis.acomptePct,
            modalitesPaiement = reglages.modalitesPaiement,
            nombreLignes = nombreLignes,
        )

    /** Contrôle des mentions avant tout envoi : la liste vide vaut feu vert. */
    fun controler(devis: Devis, client: Client?, lignes: List<Ligne>, reglages: Reglages): List<String> =
        Mentions.manquantes(contexte(devis, client, reglages, lignes.size))

    /** Prépare le PDF et les données du devis sans rien envoyer. */
    fun preparer(
        context: Context,
        devis: Devis,
        lignes: List<Ligne>,
        client: Client?,
        reglages: Reglages,
        avecMarge: Boolean = false,
        signature: Bitmap? = null,
    ): Preparation {
        val manquantes = controler(devis, client, lignes, reglages)
        if (manquantes.isNotEmpty()) {
            return Preparation(ok = false, manquantes = manquantes, message = "Mentions obligatoires incomplètes")
        }
        val ctx = contexte(devis, client, reglages, lignes.size)
        val mentions = Mentions.texteComplet(ctx)
        val entreprise = GenerateurPdf.Entreprise(
            nom = reglages.entrepriseNom,
            adresse = reglages.adresseEntreprise,
            siret = reglages.siret.ifBlank { reglages.siren },
            telephone = reglages.telephone,
            email = reglages.email,
        )
        val avecMentions = devis.copy(mentionsTexte = mentions)
        val pdf = GenerateurPdf.generer(
            context = context,
            devis = avecMentions,
            client = client,
            lignes = lignes,
            entreprise = entreprise,
            logo = GenerateurPdf.chargerBitmap(reglages.logoChemin),
            signature = signature,
            avecMarge = avecMarge,
        )
        val donnees = File(pdf.parentFile, "devis-${devis.numero}-${devis.indice}.json")
        donnees.writeText(
            com.trucdecomptable.devischantier.export.Exports.donneesComptable(
                avecMentions, client, lignes, inclureMarge = false,
            ),
        )
        return Preparation(true, pdf, donnees, message = "Document prêt")
    }

    /** Envoi au client : le devis est verrouillé et les mentions sont figées (F-15). */
    suspend fun marquerEnvoye(dao: Dao, devis: Devis, client: Client?, lignes: List<Ligne>, reglages: Reglages): Devis {
        val ctx = contexte(devis, client, reglages, lignes.size)
        val maj = devis.copy(
            statut = Statut.ENVOYE,
            dateEnvoi = System.currentTimeMillis(),
            mentionsTexte = Mentions.texteComplet(ctx),
        )
        dao.enregistrerDevis(maj)
        dao.ajouterEvenement(
            Evenement(devisId = devis.id, type = "ENVOI_CLIENT", detail = "indice ${devis.indice}"),
        )
        Diag.record("devis", "${devis.numero} envoyé au client")
        return maj
    }

    suspend fun marquerAccepte(dao: Dao, devis: Devis, signatureChemin: String? = null): Devis {
        val maj = devis.copy(
            statut = Statut.ACCEPTE,
            dateAcceptation = System.currentTimeMillis(),
            signatureChemin = signatureChemin ?: devis.signatureChemin,
        )
        dao.enregistrerDevis(maj)
        dao.ajouterEvenement(Evenement(devisId = devis.id, type = "ACCEPTATION", detail = "bon pour accord"))
        return maj
    }

    suspend fun marquerTransmis(dao: Dao, devis: Devis, destinataires: String): Devis {
        val maj = devis.copy(
            statut = Statut.TRANSMIS,
            dateTransmission = System.currentTimeMillis(),
            transmisComptable = true,
        )
        dao.enregistrerDevis(maj)
        dao.ajouterEvenement(Evenement(devisId = devis.id, type = "TRANSMISSION", detail = destinataires))
        return maj
    }

    suspend fun marquerFacture(dao: Dao, devis: Devis): Devis {
        val maj = devis.copy(statut = Statut.FACTURE, dateFacturation = System.currentTimeMillis())
        dao.enregistrerDevis(maj)
        dao.ajouterEvenement(Evenement(devisId = devis.id, type = "FACTURE", detail = "marqué par l'artisan"))
        return maj
    }

    suspend fun marquerRelance(dao: Dao, devis: Devis, canal: String) {
        dao.ajouterEvenement(Evenement(devisId = devis.id, type = "RELANCE", detail = canal))
    }

    suspend fun marquerRefuse(dao: Dao, devis: Devis) {
        dao.enregistrerDevis(devis.copy(statut = Statut.REFUSE, dateRefus = System.currentTimeMillis()))
        dao.ajouterEvenement(Evenement(devisId = devis.id, type = "REFUS", detail = "marqué refusé"))
    }

    /** Duplication : un brouillon neuf, même contenu, sans les dates d'envoi. */
    suspend fun dupliquer(dao: Dao, reglages: Reglages, devisId: Long): Long? {
        val devis = dao.devis(devisId) ?: return null
        val lignes = dao.lignesListe(devisId)
        val copie = devis.copy(
            id = 0,
            numero = numeroSuivant(dao, reglages),
            indice = "A",
            statut = Statut.BROUILLON,
            dateDevis = System.currentTimeMillis(),
            dateEnvoi = null,
            dateAcceptation = null,
            dateTransmission = null,
            dateRefus = null,
            dateFacturation = null,
            signatureChemin = null,
            transmisComptable = false,
            devisParentId = devis.id,
        )
        val nouvelId = dao.enregistrerDevis(copie)
        lignes.forEach { dao.enregistrerLigne(it.copy(id = 0, devisId = nouvelId)) }
        return nouvelId
    }

    /** Nouvelle version B, C… d'un devis envoyé, pour garder la trace (F-15). */
    suspend fun nouvelleVersion(dao: Dao, devisId: Long): Long? {
        val devis = dao.devis(devisId) ?: return null
        val lignes = dao.lignesListe(devisId)
        val copie = devis.copy(
            id = 0,
            indice = Calculs.indiceSuivant(devis.indice),
            statut = Statut.BROUILLON,
            dateDevis = System.currentTimeMillis(),
            dateEnvoi = null,
            dateAcceptation = null,
            dateTransmission = null,
            dateRefus = null,
            dateFacturation = null,
            signatureChemin = null,
            transmisComptable = false,
            devisParentId = devis.devisParentId ?: devis.id,
        )
        val nouvelId = dao.enregistrerDevis(copie)
        lignes.forEach { dao.enregistrerLigne(it.copy(id = 0, devisId = nouvelId)) }
        dao.ajouterEvenement(Evenement(devisId = devis.id, type = "VERSION", detail = "indice ${copie.indice}"))
        return nouvelId
    }

    /** Vérification « mode pro » après enregistrement : ce qui cloche, en clair. */
    fun verifier(devis: Devis, lignes: List<Ligne>, client: Client?): List<String> {
        val alertes = mutableListOf<String>()
        val totaux = Calculs.totaux(lignes, devis.remiseGlobalePct)
        if (lignes.isEmpty()) alertes.add("Aucune ligne : le devis ne peut pas être envoyé.")
        if (lignes.any { it.quantite <= 0 }) alertes.add("Une ligne a une quantité nulle.")
        if (lignes.any { it.prixUnitaireHT < 0 }) alertes.add("Une ligne a un prix négatif.")
        if (lignes.any { Calculs.sousLeCout(it) }) alertes.add("Une ligne est vendue sous son prix d'achat.")
        if (client?.radie == true) alertes.add("Le client est signalé comme radié ou cessé.")
        if (devis.validiteJours <= 0) alertes.add("Durée de validité absente.")
        if (totaux.totalTTC <= 0) alertes.add("Total TTC nul.")
        return alertes
    }

    fun resumeMontants(devis: Devis): String =
        "${Formats.euros(devis.totalHT)} HT · ${Formats.euros(devis.totalTTC)} TTC"
}
