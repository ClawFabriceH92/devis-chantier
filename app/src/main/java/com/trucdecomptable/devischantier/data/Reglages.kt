package com.trucdecomptable.devischantier.data

import android.content.Context
import android.content.SharedPreferences
import com.trucdecomptable.devischantier.util.Diag
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tous les paramétrages de la roue dentée (§3.4) et de l'assistant de premier
 * lancement. Un seul fichier de préférences, relu par les écrans.
 */
class Reglages(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("reglages", Context.MODE_PRIVATE)

    // ---- assistant de premier lancement ----
    var onboardingFait: Boolean
        get() = prefs.getBoolean("onboarding_fait", false)
        set(v) = prefs.edit().putBoolean("onboarding_fait", v).apply()

    // ---- entreprise (F-30) ----
    var entrepriseNom: String
        get() = prefs.getString("entreprise_nom", "").orEmpty()
        set(v) = prefs.edit().putString("entreprise_nom", v).apply()
    var siren: String
        get() = prefs.getString("siren", "").orEmpty()
        set(v) = prefs.edit().putString("siren", v).apply()
    var siret: String
        get() = prefs.getString("siret", "").orEmpty()
        set(v) = prefs.edit().putString("siret", v).apply()
    var formeJuridique: String
        get() = prefs.getString("forme_juridique", "").orEmpty()
        set(v) = prefs.edit().putString("forme_juridique", v).apply()
    var adresseEntreprise: String
        get() = prefs.getString("adresse_entreprise", "").orEmpty()
        set(v) = prefs.edit().putString("adresse_entreprise", v).apply()
    var codeNaf: String
        get() = prefs.getString("code_naf", "").orEmpty()
        set(v) = prefs.edit().putString("code_naf", v).apply()
    var tvaIntra: String
        get() = prefs.getString("tva_intra", "").orEmpty()
        set(v) = prefs.edit().putString("tva_intra", v).apply()
    var telephone: String
        get() = prefs.getString("telephone", "").orEmpty()
        set(v) = prefs.edit().putString("telephone", v).apply()
    var email: String
        get() = prefs.getString("email", "").orEmpty()
        set(v) = prefs.edit().putString("email", v).apply()
    var immatriculation: String
        get() = prefs.getString("immatriculation", "").orEmpty()
        set(v) = prefs.edit().putString("immatriculation", v).apply()

    // ---- logo (F-30 bis) ----
    var logoChemin: String?
        get() = prefs.getString("logo_chemin", null)
        set(v) = prefs.edit().putString("logo_chemin", v).apply()

    // ---- TVA (F-31) ----
    var regimeTva: String
        get() = prefs.getString("regime_tva", "FRANCHISE").orEmpty()
        set(v) = prefs.edit().putString("regime_tva", v).apply()
    var mentionFranchise: String
        get() = prefs.getString(
            "mention_franchise",
            "TVA non applicable, art. 293 B du CGI",
        ).orEmpty()
        set(v) = prefs.edit().putString("mention_franchise", v).apply()

    val assujetti: Boolean get() = regimeTva == "ASSUJETTI"

    // ---- assurance (F-32) ----
    var assureurNom: String
        get() = prefs.getString("assureur_nom", "").orEmpty()
        set(v) = prefs.edit().putString("assureur_nom", v).apply()
    var assureurCoordonnees: String
        get() = prefs.getString("assureur_coordonnees", "").orEmpty()
        set(v) = prefs.edit().putString("assureur_coordonnees", v).apply()
    var couvertureGeo: String
        get() = prefs.getString("couverture_geo", "France métropolitaine").orEmpty()
        set(v) = prefs.edit().putString("couverture_geo", v).apply()
    var attestationChemin: String?
        get() = prefs.getString("attestation_chemin", null)
        set(v) = prefs.edit().putString("attestation_chemin", v).apply()
    var attestationExpiration: Long
        get() = prefs.getLong("attestation_expiration", 0L)
        set(v) = prefs.edit().putLong("attestation_expiration", v).apply()

    // ---- destinataire administratif (F-33) ----
    var comptableEmails: String
        get() = prefs.getString("comptable_emails", "").orEmpty()
        set(v) = prefs.edit().putString("comptable_emails", v).apply()
    var copieArtisan: Boolean
        get() = prefs.getBoolean("copie_artisan", true)
        set(v) = prefs.edit().putBoolean("copie_artisan", v).apply()
    var declencheurTransmission: String
        get() = prefs.getString("declencheur_transmission", Declencheur.ACCEPTATION).orEmpty()
        set(v) = prefs.edit().putString("declencheur_transmission", v).apply()

    // ---- conditions par défaut (F-34) ----
    var validiteJours: Int
        get() = prefs.getInt("validite_jours", 30)
        set(v) = prefs.edit().putInt("validite_jours", v).apply()
    var acomptePct: Double
        get() = prefs.getFloat("acompte_pct", 30f).toDouble()
        set(v) = prefs.edit().putFloat("acompte_pct", v.toFloat()).apply()
    var modalitesPaiement: String
        get() = prefs.getString("modalites_paiement", "30 % à la commande, solde à la fin des travaux").orEmpty()
        set(v) = prefs.edit().putString("modalites_paiement", v).apply()
    var tauxHoraire: Double
        get() = prefs.getFloat("taux_horaire", 45f).toDouble()
        set(v) = prefs.edit().putFloat("taux_horaire", v.toFloat()).apply()
    var fraisDeplacement: Double
        get() = prefs.getFloat("frais_deplacement", 0f).toDouble()
        set(v) = prefs.edit().putFloat("frais_deplacement", v.toFloat()).apply()
    var devisGratuit: Boolean
        get() = prefs.getBoolean("devis_gratuit", true)
        set(v) = prefs.edit().putBoolean("devis_gratuit", v).apply()
    var mediateur: String
        get() = prefs.getString("mediateur", "").orEmpty()
        set(v) = prefs.edit().putString("mediateur", v).apply()
    var conditionsGenerales: String
        get() = prefs.getString("conditions_generales", "").orEmpty()
        set(v) = prefs.edit().putString("conditions_generales", v).apply()

    // ---- numérotation (F-13) ----
    var prefixeNumero: String
        get() = prefs.getString("prefixe_numero", "D").orEmpty()
        set(v) = prefs.edit().putString("prefixe_numero", v).apply()

    // ---- modèles d'emails (F-35) ----
    var modeleClientObjet: String
        get() = prefs.getString("modele_client_objet", "Votre devis {numero} - {entreprise}").orEmpty()
        set(v) = prefs.edit().putString("modele_client_objet", v).apply()
    var modeleClientCorps: String
        get() = prefs.getString(
            "modele_client_corps",
            "Bonjour,\n\nVeuillez trouver ci-joint mon devis {numero} d'un montant de {ttc} TTC " +
                "pour {objet}.\nIl est valable jusqu'au {limite}.\n\n" +
                "Je reste à votre disposition pour toute question.\n\nCordialement,\n{entreprise}",
        ).orEmpty()
        set(v) = prefs.edit().putString("modele_client_corps", v).apply()
    var modeleComptableObjet: String
        get() = prefs.getString("modele_comptable_objet", "Devis {numero} accepté - {client}").orEmpty()
        set(v) = prefs.edit().putString("modele_comptable_objet", v).apply()
    var modeleComptableCorps: String
        get() = prefs.getString(
            "modele_comptable_corps",
            "Bonjour,\n\nLe devis {numero} de {client} a été accepté le {date}.\n" +
                "Montant : {ht} HT / {ttc} TTC.\n\n" +
                "Vous trouverez ci-joint le devis signé et les données pour la facturation.\n\nCordialement,\n{entreprise}",
        ).orEmpty()
        set(v) = prefs.edit().putString("modele_comptable_corps", v).apply()

    // ---- intelligence artificielle (F-37, §4.4) ----
    /**
     * LOCAL (modèle embarqué sur le téléphone, défaut), CLAUDE ou AUTRE (service
     * compatible OpenAI). Les installations antérieures à la 1.1 n'avaient que
     * les deux derniers : une clé déjà enregistrée garde son fournisseur.
     */
    var iaFournisseur: String
        get() = prefs.getString("ia_fournisseur", null)
            ?: if (prefs.contains("ia_cle_chiffree")) "CLAUDE" else "LOCAL"
        set(v) = prefs.edit().putString("ia_fournisseur", v).apply()
    /** L'artisan a accepté de télécharger le modèle (1 Go) sur un réseau facturé. */
    var iaLocalDonneesMobiles: Boolean
        get() = prefs.getBoolean("ia_local_donnees_mobiles", false)
        set(v) = prefs.edit().putBoolean("ia_local_donnees_mobiles", v).apply()
    var iaModele: String
        get() = prefs.getString("ia_modele", "").orEmpty()
        set(v) = prefs.edit().putString("ia_modele", v).apply()
    /** Niveau de qualité demandé (QUALITE, EQUILIBRE, ECONOMIE). */
    var iaNiveau: String
        get() = prefs.getString("ia_niveau", "QUALITE").orEmpty()
        set(v) = prefs.edit().putString("ia_niveau", v).apply()
    var iaBaseUrl: String
        get() = prefs.getString("ia_base_url", "").orEmpty()
        set(v) = prefs.edit().putString("ia_base_url", v).apply()
    var iaDictees: Int
        get() = prefs.getInt("ia_dictees", 0)
        set(v) = prefs.edit().putInt("ia_dictees", v).apply()
    var iaConsentement: Boolean
        get() = prefs.getBoolean("ia_consentement", false)
        set(v) = prefs.edit().putBoolean("ia_consentement", v).apply()

    /**
     * Clé API : chiffrée par la clé matérielle Android, jamais en clair (S-02).
     * Si le Keystore du téléphone est indisponible, la clé n'est pas écrite et
     * n'est gardée qu'en mémoire pour la session : l'écran des réglages le dit.
     */
    var iaCle: String?
        get() = prefs.getString("ia_cle_chiffree", null)?.let { Coffre.dechiffrer(it) }
            ?: Coffre.enMemoire
        set(v) {
            val clair = v?.takeIf { it.isNotBlank() }
            Coffre.enMemoire = clair
            val chiffre = clair?.let { Coffre.chiffrer(it) }
            prefs.edit().putString("ia_cle_chiffree", chiffre).apply()
            iaCleNonSecurisee = clair != null && chiffre == null
        }

    /** Vrai quand la clé n'a pas pu être chiffrée par le Keystore du téléphone. */
    var iaCleNonSecurisee: Boolean
        get() = prefs.getBoolean("ia_cle_non_securisee", false)
        set(v) = prefs.edit().putBoolean("ia_cle_non_securisee", v).apply()

    // ---- rappels (F-38, §5.4) ----
    var notificationsActives: Boolean
        get() = prefs.getBoolean("notifications_actives", true)
        set(v) = prefs.edit().putBoolean("notifications_actives", v).apply()
    var rappelJoursAvant: Int
        get() = prefs.getInt("rappel_jours_avant", 3)
        set(v) = prefs.edit().putInt("rappel_jours_avant", v).apply()
    var relanceJoursApres: Int
        get() = prefs.getInt("relance_jours_apres", 7)
        set(v) = prefs.edit().putInt("relance_jours_apres", v).apply()
    var heureDebut: Int
        get() = prefs.getInt("heure_debut", 8)
        set(v) = prefs.edit().putInt("heure_debut", v).apply()
    var heureFin: Int
        get() = prefs.getInt("heure_fin", 20)
        set(v) = prefs.edit().putInt("heure_fin", v).apply()
    var dimancheAutorise: Boolean
        get() = prefs.getBoolean("dimanche_autorise", false)
        set(v) = prefs.edit().putBoolean("dimanche_autorise", v).apply()
    var rappelAssuranceJoursAvant: Int
        get() = prefs.getInt("rappel_assurance_jours", 45)
        set(v) = prefs.edit().putInt("rappel_assurance_jours", v).apply()

    // ---- marge (§3.6) ----
    var seuilMarge: Double
        get() = prefs.getFloat("seuil_marge", 20f).toDouble()
        set(v) = prefs.edit().putFloat("seuil_marge", v.toFloat()).apply()

    // ---- sauvegarde (§8.2, S-03) ----
    var sauvegardeAuto: Boolean
        get() = prefs.getBoolean("sauvegarde_auto", false)
        set(v) = prefs.edit().putBoolean("sauvegarde_auto", v).apply()
    var sauvegardeDossier: String?
        get() = prefs.getString("sauvegarde_dossier", null)
        set(v) = prefs.edit().putString("sauvegarde_dossier", v).apply()
    var sauvegardeDerniere: Long
        get() = prefs.getLong("sauvegarde_derniere", 0L)
        set(v) = prefs.edit().putLong("sauvegarde_derniere", v).apply()
    var conservationMois: Int
        get() = prefs.getInt("conservation_mois", 120)
        set(v) = prefs.edit().putInt("conservation_mois", v).apply()

    /**
     * Phrase secrète des sauvegardes. Conservée ici, chiffrée par le Keystore,
     * pour que la sauvegarde automatique tourne sans rien demander. Elle est
     * indispensable pour restaurer sur un nouveau téléphone : l'artisan reçoit
     * un avertissement clair quand il la choisit.
     */
    var motDePasseSauvegarde: String?
        get() = prefs.getString("sauvegarde_mdp_chiffre", null)?.let { Coffre.dechiffrer(it) }
        set(v) = prefs.edit()
            .putString("sauvegarde_mdp_chiffre", v?.takeIf { it.isNotBlank() }?.let { Coffre.chiffrer(it) })
            .apply()

    // ---- apparence mode chantier (U-08) ----
    var modeChantier: Boolean
        get() = prefs.getBoolean("mode_chantier", false)
        set(v) = prefs.edit().putBoolean("mode_chantier", v).apply()

    // ---- mise à jour ----
    var versionIgnoree: String
        get() = prefs.getString("version_ignoree", "").orEmpty()
        set(v) = prefs.edit().putString("version_ignoree", v).apply()

    /**
     * Les réglages tels qu'ils partent dans la sauvegarde : tout sauf les
     * secrets chiffrés par le Keystore (inutilisables sur un autre téléphone)
     * et l'état propre à l'appareil.
     */
    fun exporter(): Map<String, Any?> =
        prefs.all.filterKeys { it !in CLES_PRIVEES }

    fun importer(valeurs: Map<String, Any?>) {
        val edition = prefs.edit()
        for ((cle, valeur) in valeurs) {
            if (cle in CLES_PRIVEES) continue
            when (valeur) {
                is Boolean -> edition.putBoolean(cle, valeur)
                is Int -> edition.putInt(cle, valeur)
                is Long -> edition.putLong(cle, valeur)
                is Float -> edition.putFloat(cle, valeur)
                is Double -> edition.putFloat(cle, valeur.toFloat())
                is String -> edition.putString(cle, valeur)
                null -> edition.remove(cle)
                else -> edition.putString(cle, valeur.toString())
            }
        }
        edition.apply()
    }

    fun resumeEntreprise(): String = listOf(entrepriseNom, adresseEntreprise)
        .filter { it.isNotBlank() }
        .joinToString(" · ")

    fun destinatairesComptable(): List<String> = comptableEmails
        .split(',', ';', ' ')
        .map { it.trim() }
        .filter { it.contains("@") }

    fun journaliser(action: String) {
        val heure = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date())
        Diag.record("reglages", "$action ($heure)")
    }
}

/** Clés qui ne quittent jamais le téléphone. */
val CLES_PRIVEES = setOf(
    "ia_cle_chiffree", "ia_cle_non_securisee", "sauvegarde_mdp_chiffre",
    "sauvegarde_dossier", "sauvegarde_derniere", "version_ignoree",
    "logo_chemin", "attestation_chemin",
)

object Declencheur {
    const val ACCEPTATION = "ACCEPTATION"
    const val ENVOI = "ENVOI"
}
