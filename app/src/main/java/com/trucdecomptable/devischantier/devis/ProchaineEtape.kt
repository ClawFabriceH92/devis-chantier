package com.trucdecomptable.devischantier.devis

import com.trucdecomptable.devischantier.data.Devis
import com.trucdecomptable.devischantier.data.Statut
import com.trucdecomptable.devischantier.util.Formats

/**
 * Ce que l'artisan doit faire maintenant avec un devis, en une phrase et un
 * bouton. Calculé ici, sans Android, pour que l'écran de détail et les tests
 * disent la même chose.
 */
data class ProchaineEtape(
    val titre: String,
    val texte: String,
    val libelleBouton: String,
    val action: String,
) {
    companion object {
        const val MODIFIER = "MODIFIER"
        const val ENVOYER = "ENVOYER"
        const val SIGNER = "SIGNER"
        const val RELANCER = "RELANCER"
        const val TRANSMETTRE = "TRANSMETTRE"
        const val FACTURER = "FACTURER"
        const val VERSION = "VERSION"
        const val APERCU = "APERCU"

        private const val JOUR = 86_400_000L

        fun pour(
            devis: Devis,
            nombreLignes: Int,
            relanceJoursApres: Int = 7,
            maintenant: Long = System.currentTimeMillis(),
        ): ProchaineEtape = when (devis.statut) {
            Statut.BROUILLON -> if (nombreLignes == 0) {
                ProchaineEtape(
                    "Brouillon vide",
                    "Ajoutez des lignes au devis, à la main ou en les dictant, puis choisissez le client.",
                    "Modifier le brouillon", MODIFIER,
                )
            } else {
                ProchaineEtape(
                    "Brouillon prêt à partir",
                    "Vérifiez les lignes et le client, puis envoyez le devis. Sur place, vous pouvez aussi le faire signer directement.",
                    "Envoyer au client", ENVOYER,
                )
            }

            Statut.ENVOYE -> {
                val depuis = (maintenant - (devis.dateEnvoi ?: devis.dateDevis)) / JOUR
                val restant = (devis.dateLimite - maintenant) / JOUR
                when {
                    restant < 0 -> ProchaineEtape(
                        "Validité dépassée",
                        "Le devis n'est plus valable depuis le ${Formats.date(devis.dateLimite)}. Relancez le client ou repartez d'une nouvelle version.",
                        "Relancer le client", RELANCER,
                    )
                    depuis >= relanceJoursApres -> ProchaineEtape(
                        "Sans réponse depuis $depuis jours",
                        "Un appel ou un message suffit souvent : relancez le client avant la fin de validité (${Formats.date(devis.dateLimite)}).",
                        "Relancer le client", RELANCER,
                    )
                    else -> ProchaineEtape(
                        "En attente du client",
                        "Envoyé le ${Formats.date(devis.dateEnvoi ?: devis.dateDevis)}, valable jusqu'au ${Formats.date(devis.dateLimite)}. " +
                            "Dès l'accord, faites signer le bon pour accord.",
                        "Faire signer sur place", SIGNER,
                    )
                }
            }

            Statut.ACCEPTE -> ProchaineEtape(
                "Accepté, à transmettre",
                "Le client a accepté${devis.dateAcceptation?.let { " le ${Formats.date(it)}" }.orEmpty()}. " +
                    "Transmettez le devis au comptable ou à l'assistante : c'est elle ou lui qui établit la facture.",
                "Transmettre au comptable", TRANSMETTRE,
            )

            Statut.TRANSMIS -> ProchaineEtape(
                "Transmis pour facturation",
                "Transmis${devis.dateTransmission?.let { " le ${Formats.date(it)}" }.orEmpty()}. " +
                    "Quand la facture est émise, marquez le devis facturé pour le sortir du suivi.",
                "Marquer facturé", FACTURER,
            )

            Statut.FACTURE -> ProchaineEtape(
                "Terminé",
                "Devis facturé : plus rien à faire. Pour un chantier semblable, dupliquez-le depuis l'accueil.",
                "Aperçu du PDF", APERCU,
            )

            Statut.REFUSE -> ProchaineEtape(
                "Refusé",
                "Le client a décliné. Vous pouvez repartir d'une nouvelle version pour une autre proposition.",
                "Créer une nouvelle version", VERSION,
            )

            Statut.EXPIRE -> ProchaineEtape(
                "Validité dépassée",
                "Le devis a expiré le ${Formats.date(devis.dateLimite)}. Relancez le client, ou créez une nouvelle version avec des prix à jour.",
                "Créer une nouvelle version", VERSION,
            )

            else -> ProchaineEtape("Devis", "", "Aperçu du PDF", APERCU)
        }
    }
}
