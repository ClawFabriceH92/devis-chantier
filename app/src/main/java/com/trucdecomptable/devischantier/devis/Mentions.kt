package com.trucdecomptable.devischantier.devis

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Ce que le devis doit porter pour être régulier (§6). Tout est calculé ici,
 * sans Android, pour que les mêmes textes partent à l'écran, dans le PDF et
 * dans l'export au comptable.
 *
 * Les textes évoluent : la mention de franchise change au 1er janvier 2027
 * (recodification de la TVA dans le CIBS, ordonnance n° 2026-671), l'ancienne
 * formulation restant admise jusqu'au 30 juin 2028. La date du devis décide.
 */
data class ContexteMentions(
    val entrepriseNom: String = "",
    val adresseEntreprise: String = "",
    val siren: String = "",
    val siret: String = "",
    val immatriculation: String = "",
    val formeJuridique: String = "",
    val telephone: String = "",
    val email: String = "",
    val clientNom: String = "",
    val clientAdresse: String? = null,
    val professionalClient: Boolean = false,
    val objet: String = "",
    val dateDevis: Long = System.currentTimeMillis(),
    val validiteJours: Int = 30,
    val devisGratuit: Boolean = true,
    val franchiseTva: Boolean = true,
    val mentionFranchisePersonnalisee: String = "",
    val assureurNom: String = "",
    val assureurCoordonnees: String = "",
    val couvertureGeo: String = "",
    val assuranceObligatoire: Boolean = true,
    val attestationExpiration: Long = 0L,
    val mentionTauxReduit: Boolean = false,
    val tauxReduit: Double = 10.0,
    val signeChezClient: Boolean = false,
    val mediateur: String = "",
    val conditionsGenerales: String = "",
    val acomptePct: Double = 0.0,
    val modalitesPaiement: String = "",
    val nombreLignes: Int = 0,
    val margeAffichee: Boolean = false,
)

object Mentions {

    private val dateFr = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)

    // Bornes en heure locale : un devis daté du 30 juin 2028 doit encore
    // accepter l'ancienne mention, quel que soit le fuseau du téléphone.
    private val BASCULE_CIBS = debutDeJour(2027, 1, 1)
    private val FIN_TOLERANCE_ANCIENNE = debutDeJour(2028, 7, 1)

    private fun debutDeJour(annee: Int, mois: Int, jour: Int): Long = Calendar.getInstance().apply {
        clear()
        set(annee, mois - 1, jour, 0, 0, 0)
    }.timeInMillis

    /** Mention de TVA à porter selon la date du devis. */
    fun mentionFranchise(dateDevis: Long, personnalisee: String = ""): String {
        if (personnalisee.isNotBlank()) return personnalisee
        return when {
            dateDevis < BASCULE_CIBS -> "TVA non applicable, art. 293 B du CGI"
            dateDevis < FIN_TOLERANCE_ANCIENNE ->
                "TVA non applicable, art. L. 233-3 du CIBS (ancienne mention admise : art. 293 B du CGI)"
            else -> "TVA non applicable, art. L. 233-3 du CIBS"
        }
    }

    /** Bloc assurance : il figure sur chaque devis d'un artisan du bâtiment. */
    fun blocAssurance(ctx: ContexteMentions): String? {
        if (!ctx.assuranceObligatoire) return null
        if (ctx.assureurNom.isBlank() || ctx.assureurCoordonnees.isBlank()) return null
        val couverture = ctx.couvertureGeo.ifBlank { "France métropolitaine" }
        return "Assurance professionnelle : ${ctx.assureurNom} — ${ctx.assureurCoordonnees} · " +
            "couverture : $couverture"
    }

    fun attestationPerimee(ctx: ContexteMentions, maintenant: Long = System.currentTimeMillis()): Boolean =
        ctx.attestationExpiration in 1..maintenant

    /** Mention de taux réduit (10 % ou 5,5 %) : elle remplace le Cerfa depuis 2025. */
    fun mentionTauxReduit(ctx: ContexteMentions): String? {
        if (!ctx.mentionTauxReduit) return null
        val taux = com.trucdecomptable.devischantier.util.Formats.nombre(ctx.tauxReduit)
        return "TVA à $taux % appliquée aux travaux d'amélioration, de transformation, " +
            "d'aménagement et d'entretien d'un local d'habitation achevé depuis plus de deux ans. " +
            "Le client atteste que les conditions de ce taux sont remplies ; " +
            "une copie de cette mention est conservée en comptabilité."
    }

    /**
     * Les mentions indispensables qui manquent. Tant que la liste n'est pas
     * vide, l'envoi est bloqué (§6).
     */
    fun manquantes(ctx: ContexteMentions): List<String> {
        val manques = mutableListOf<String>()
        if (ctx.entrepriseNom.isBlank()) manques.add("Nom de l'entreprise (roue dentée)")
        if (ctx.adresseEntreprise.isBlank()) manques.add("Adresse de l'entreprise")
        if (ctx.siren.isBlank() && ctx.immatriculation.isBlank()) {
            manques.add("SIREN ou numéro d'immatriculation de l'entreprise")
        }
        if (ctx.clientNom.isBlank()) manques.add("Client du devis")
        if (ctx.objet.isBlank()) manques.add("Objet du devis")
        if (ctx.validiteJours <= 0) manques.add("Durée de validité du devis")
        if (ctx.nombreLignes <= 0) manques.add("Au moins une ligne chiffrée")
        if (ctx.assuranceObligatoire) {
            if (ctx.assureurNom.isBlank()) manques.add("Assureur (assurance professionnelle)")
            if (ctx.assureurCoordonnees.isBlank()) manques.add("Coordonnées de l'assureur")
            if (ctx.couvertureGeo.isBlank()) manques.add("Couverture géographique de l'assurance")
        }
        if (!ctx.professionalClient && ctx.mediateur.isBlank()) {
            manques.add("Médiateur de la consommation (client particulier)")
        }
        if (ctx.assuranceObligatoire && attestationPerimee(ctx)) {
            manques.add("Attestation d'assurance à jour (elle est périmée)")
        }
        return manques
    }

    fun conforme(ctx: ContexteMentions): Boolean = manquantes(ctx).isEmpty()

    /** Le texte complet, figé dans le devis au moment de l'envoi (F-15). */
    fun texteComplet(ctx: ContexteMentions): String {
        val lignes = mutableListOf<String>()
        val identite = listOfNotNull(
            ctx.entrepriseNom.takeIf { it.isNotBlank() },
            ctx.formeJuridique.takeIf { it.isNotBlank() },
            ctx.adresseEntreprise.takeIf { it.isNotBlank() },
            ctx.telephone.takeIf { it.isNotBlank() }?.let { "Tél. $it" },
            ctx.email.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
        if (identite.isNotBlank()) lignes.add(identite)

        val immatriculation = listOfNotNull(
            ctx.siren.takeIf { it.isNotBlank() }?.let { "SIREN $it" },
            ctx.siret.takeIf { it.isNotBlank() }?.let { "SIRET $it" },
            ctx.immatriculation.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
        if (immatriculation.isNotBlank()) lignes.add(immatriculation)

        lignes.add(
            "Devis ${if (ctx.devisGratuit) "gratuit" else "payant"} établi le " +
                dateFr.format(Date(ctx.dateDevis)) + ", valable ${ctx.validiteJours} jours",
        )
        if (ctx.franchiseTva) lignes.add(mentionFranchise(ctx.dateDevis, ctx.mentionFranchisePersonnalisee))
        mentionTauxReduit(ctx)?.let { lignes.add(it) }
        blocAssurance(ctx)?.let { lignes.add(it) }
        if (ctx.acomptePct > 0.0) {
            lignes.add(
                "Acompte de ${String.format(Locale.FRANCE, "%.0f", ctx.acomptePct)} % à la commande" +
                    ctx.modalitesPaiement.takeIf { it.isNotBlank() }?.let { " ; $it" }.orEmpty(),
            )
        } else if (ctx.modalitesPaiement.isNotBlank()) {
            lignes.add("Modalités de paiement : ${ctx.modalitesPaiement}")
        }
        if (ctx.signeChezClient) {
            lignes.add(
                "Devis signé hors établissement : le client dispose d'un délai de rétractation de 14 jours " +
                    "(art. L. 221-18 du code de la consommation). Aucun paiement n'est encaissé avant 7 jours, " +
                    "sauf travaux d'entretien ou de réparation urgents demandés par le client.",
            )
        }
        if (!ctx.professionalClient && ctx.mediateur.isNotBlank()) {
            lignes.add("Médiateur de la consommation : ${ctx.mediateur}")
        }
        if (ctx.conditionsGenerales.isNotBlank()) lignes.add(ctx.conditionsGenerales)
        if (ctx.margeAffichee) {
            // La marge ne doit jamais apparaître sur un document client (M-09).
            lignes.removeAll { it.contains("marge", ignoreCase = true) }
        }
        return lignes.joinToString("\n")
    }

    fun contexteDepuis(
        entrepriseNom: String,
        adresseEntreprise: String,
        siren: String,
        siret: String,
        immatriculation: String,
        formeJuridique: String,
        telephone: String,
        email: String,
        clientNom: String,
        clientAdresse: String?,
        professionalClient: Boolean,
        objet: String,
        dateDevis: Long,
        validiteJours: Int,
        devisGratuit: Boolean,
        franchiseTva: Boolean,
        mentionFranchisePersonnalisee: String,
        assureurNom: String,
        assureurCoordonnees: String,
        couvertureGeo: String,
        attestationExpiration: Long,
        mentionTauxReduit: Boolean,
        tauxReduit: Double,
        signeChezClient: Boolean,
        mediateur: String,
        conditionsGenerales: String,
        acomptePct: Double,
        modalitesPaiement: String,
        nombreLignes: Int,
    ): ContexteMentions = ContexteMentions(
        entrepriseNom = entrepriseNom,
        adresseEntreprise = adresseEntreprise,
        siren = siren,
        siret = siret,
        immatriculation = immatriculation,
        formeJuridique = formeJuridique,
        telephone = telephone,
        email = email,
        clientNom = clientNom,
        clientAdresse = clientAdresse,
        professionalClient = professionalClient,
        objet = objet,
        dateDevis = dateDevis,
        validiteJours = validiteJours,
        devisGratuit = devisGratuit,
        franchiseTva = franchiseTva,
        mentionFranchisePersonnalisee = mentionFranchisePersonnalisee,
        assureurNom = assureurNom,
        assureurCoordonnees = assureurCoordonnees,
        couvertureGeo = couvertureGeo,
        attestationExpiration = attestationExpiration,
        mentionTauxReduit = mentionTauxReduit,
        tauxReduit = tauxReduit,
        signeChezClient = signeChezClient,
        mediateur = mediateur,
        conditionsGenerales = conditionsGenerales,
        acomptePct = acomptePct,
        modalitesPaiement = modalitesPaiement,
        nombreLignes = nombreLignes,
    )
}
