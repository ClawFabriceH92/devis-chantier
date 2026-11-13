package com.trucdecomptable.devischantier.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import com.trucdecomptable.devischantier.data.Client
import com.trucdecomptable.devischantier.data.Devis
import com.trucdecomptable.devischantier.data.Ligne
import com.trucdecomptable.devischantier.devis.Calculs
import com.trucdecomptable.devischantier.devis.Mentions
import com.trucdecomptable.devischantier.devis.Totaux
import com.trucdecomptable.devischantier.util.Diag
import com.trucdecomptable.devischantier.util.Formats
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Le PDF du devis, généré sur le téléphone (T-03) : logo, mentions, lignes et
 * signature, lisible imprimé en A4. Aucune bibliothèque externe, donc aucun
 * téléversement et un APK léger.
 */
object GenerateurPdf {

    private const val LARGEUR = 595
    private const val HAUTEUR = 842
    private const val MARGE = 36f
    /** Réserve en bas de page pour le pied de page. */
    private const val BAS = 40f
    private const val HAUTEUR_SIGNATURE = 130f

    private val dateFr = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)

    fun generer(
        context: Context,
        devis: Devis,
        client: Client?,
        lignes: List<Ligne>,
        entreprise: Entreprise,
        logo: Bitmap? = null,
        signature: Bitmap? = null,
        avecMarge: Boolean = false,
    ): File {
        val document = PdfDocument()
        val totaux = Calculs.totaux(lignes, devis.remiseGlobalePct)
        var numeroPage = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(LARGEUR, HAUTEUR, numeroPage).create())
        var toile = page.canvas
        var y = MARGE

        y = entete(toile, devis, entreprise, logo, y)
        y = blocParties(toile, devis, client, entreprise, y)
        // Ouvre une page de continuation et renvoie le nouvel y de départ.
        val nouvellePage: () -> Float = {
            piedDePage(toile, devis, numeroPage, entreprise)
            document.finishPage(page)
            numeroPage++
            page = document.startPage(PdfDocument.PageInfo.Builder(LARGEUR, HAUTEUR, numeroPage).create())
            toile = page.canvas
            enteteCompact(toile, devis, entreprise)
            MARGE + 26f
        }
        y = tableauLignes(toile, lignes, devis, totaux, entreprise, y, avecMarge, nouvellePage)

        // Chaque bloc annonce sa hauteur : s'il ne tient pas sur la page, il part
        // entier sur la suivante. Les mentions obligatoires ne sont jamais
        // tronquées, quel que soit le nombre de lignes du devis.
        if (y + hauteurTotaux(totaux, devis) > HAUTEUR - BAS) y = nouvellePage()
        y = blocTotaux(toile, totaux, devis, entreprise, y)
        y = blocMentions({ toile }, devis, y, nouvellePage)
        if (signature != null || devis.signeChezClient || devis.statut != "BROUILLON") {
            if (y + HAUTEUR_SIGNATURE > HAUTEUR - BAS) y = nouvellePage()
            blocSignature(toile, devis, entreprise, signature, y)
        }
        piedDePage(toile, devis, numeroPage, entreprise)
        document.finishPage(page)

        val dossier = File(context.filesDir, "devis").apply { mkdirs() }
        val fichier = File(dossier, "devis-${devis.numero}-${devis.indice}.pdf")
        FileOutputStream(fichier).use { document.writeTo(it) }
        document.close()
        Diag.record("pdf", "généré : ${fichier.name} (${fichier.length() / 1024} Ko)")
        return fichier
    }

    data class Entreprise(
        val nom: String,
        val adresse: String,
        val siret: String,
        val telephone: String,
        val email: String,
        val couleur: Int = Color.rgb(15, 61, 92),
    )

    // ------------------------------------------------------------------ blocs

    private fun entete(canvas: Canvas, devis: Devis, e: Entreprise, logo: Bitmap?, depart: Float): Float {
        var y = depart
        if (logo != null) {
            val hauteurLogo = 46
            val largeurLogo = (logo.width.toFloat() / logo.height * hauteurLogo).toInt().coerceAtMost(140)
            canvas.drawBitmap(
                logo,
                null,
                Rect(MARGE.toInt(), y.toInt(), MARGE.toInt() + largeurLogo, y.toInt() + hauteurLogo),
                Paint(Paint.FILTER_BITMAP_FLAG),
            )
        }
        val paint = Paint().apply { color = e.couleur; textSize = 15f; isAntiAlias = true }
        canvas.drawText(e.nom.ifBlank { "Mon entreprise" }, MARGE, y + 18f, paint)
        val petit = Paint().apply { color = Color.DKGRAY; textSize = 9f; isAntiAlias = true }
        canvas.drawText(e.adresse, MARGE, y + 32f, petit)
        canvas.drawText(
            listOf(e.telephone, e.email, e.siret.takeIf { it.isNotBlank() }?.let { "SIRET $it" })
                .filterNotNull()
                .filter { it.isNotBlank() }
                .joinToString(" · "),
            MARGE, y + 44f, petit,
        )

        val titre = Paint().apply {
            color = e.couleur; textSize = 20f; isAntiAlias = true; textAlign = Paint.Align.RIGHT
            isFakeBoldText = true
        }
        canvas.drawText("DEVIS", LARGEUR - MARGE, y + 20f, titre)
        val info = Paint().apply { color = Color.DKGRAY; textSize = 9.5f; textAlign = Paint.Align.RIGHT; isAntiAlias = true }
        canvas.drawText(devis.numero + " · indice " + devis.indice, LARGEUR - MARGE, y + 34f, info)
        canvas.drawText(dateFr.format(Date(devis.dateDevis)), LARGEUR - MARGE, y + 46f, info)
        y += 58f
        canvas.drawRect(MARGE, y, LARGEUR - MARGE, y + 1.6f, Paint().apply { color = e.couleur })
        return y + 16f
    }

    private fun enteteCompact(canvas: Canvas, devis: Devis, e: Entreprise) {
        val paint = Paint().apply { color = e.couleur; textSize = 10f; isAntiAlias = true }
        canvas.drawText("${e.nom} · devis ${devis.numero} (suite)", MARGE, MARGE, paint)
    }

    private fun blocParties(
        canvas: Canvas,
        devis: Devis,
        client: Client?,
        entreprise: Entreprise,
        depart: Float,
    ): Float {
        var y = depart
        val gras = Paint().apply { textSize = 10f; isAntiAlias = true; isFakeBoldText = true; color = Color.BLACK }
        val normal = Paint().apply { textSize = 9.5f; isAntiAlias = true; color = Color.DKGRAY }
        canvas.drawText("CLIENT", MARGE, y, gras)
        canvas.drawText(devis.clientNom.ifBlank { client?.nom ?: "—" }, MARGE, y + 13f, gras)
        val adresse = devis.adresseChantier ?: client?.adresse
        var ligneY = y + 26f
        if (!adresse.isNullOrBlank()) {
            for (morceau in decouper(adresse, 9.5f, normal, 200f)) {
                canvas.drawText(morceau, MARGE, ligneY, normal); ligneY += 12f
            }
        }
        client?.siret?.takeIf { it.isNotBlank() }?.let {
            canvas.drawText("SIRET $it", MARGE, ligneY, normal); ligneY += 12f
        }
        client?.tvaIntra?.takeIf { it.isNotBlank() }?.let {
            canvas.drawText(it, MARGE, ligneY, normal); ligneY += 12f
        }
        if (devis.objet.isNotBlank()) {
            canvas.drawText("OBJET", MARGE + 260f, y, gras)
            var objetY = y + 13f
            for (morceau in decouper(devis.objet, 9.5f, normal, 250f)) {
                canvas.drawText(morceau, MARGE + 260f, objetY, normal); objetY += 12f
            }
            ligneY = maxOf(ligneY, objetY)
        }
        if (devis.adresseChantier != null && client?.adresse != null) {
            canvas.drawText("Chantier : ${devis.adresseChantier}", MARGE, ligneY, normal); ligneY += 12f
        }
        return maxOf(ligneY, y + 40f) + 10f
    }

    private fun tableauLignes(
        canvas: Canvas,
        lignes: List<Ligne>,
        devis: Devis,
        totaux: Totaux,
        entreprise: Entreprise,
        depart: Float,
        avecMarge: Boolean,
        nouvellePage: () -> Float,
    ): Float {
        var y = depart
        val entete = Paint().apply { color = entreprise.couleur; isAntiAlias = true; textSize = 9f; isFakeBoldText = true }
        val texte = Paint().apply { isAntiAlias = true; textSize = 9.5f; color = Color.BLACK }
        val droite = Paint().apply { isAntiAlias = true; textSize = 9.5f; color = Color.BLACK; textAlign = Paint.Align.RIGHT }
        val fond = Paint().apply { color = Color.rgb(240, 243, 246) }

        fun bandeau() {
            canvas.drawRect(MARGE, y, LARGEUR - MARGE, y + 16f, fond)
            canvas.drawText("Désignation", MARGE + 4f, y + 11f, entete)
            canvas.drawText("Qté", MARGE + 300f, y + 11f, entete)
            canvas.drawText("U", MARGE + 345f, y + 11f, entete)
            canvas.drawText("P.U. HT", MARGE + 420f, y + 11f, entete)
            canvas.drawText("Total HT", LARGEUR - MARGE - 4f, y + 11f, entete)
            y += 22f
        }
        bandeau()

        var sectionCourante: String? = null
        for (ligne in lignes) {
            val section = ligne.section?.takeIf { it.isNotBlank() }
            if (section != null && section != sectionCourante) {
                sectionCourante = section
                if (y > HAUTEUR - 140f) {
                    y = nouvellePage()
                    bandeau()
                }
                canvas.drawRect(MARGE, y, LARGEUR - MARGE, y + 14f, Paint().apply { color = Color.rgb(228, 118, 27) })
                canvas.drawText(
                    section.uppercase(Locale.FRANCE),
                    MARGE + 4f,
                    y + 10.5f,
                    Paint().apply { color = Color.WHITE; textSize = 9f; isFakeBoldText = true; isAntiAlias = true },
                )
                y += 18f
            }
            val morceaux = decouper(ligne.designation, 9.5f, texte, 280f)
            val hauteurLigne = 13f * morceaux.size
            if (y + hauteurLigne > HAUTEUR - 120f) {
                y = nouvellePage()
                bandeau()
            }
            morceaux.forEachIndexed { index, morceau ->
                canvas.drawText(morceau, MARGE + 4f, y + 10f + index * 12.5f, texte)
            }
            val totalLigne = Calculs.ligne(ligne, devis.remiseGlobalePct)
            canvas.drawText(Formats.nombre(ligne.quantite), MARGE + 300f, y + 10f, texte)
            canvas.drawText(ligne.unite, MARGE + 345f, y + 10f, texte)
            canvas.drawText(Formats.euros(ligne.prixUnitaireHT), MARGE + 480f, y + 10f, droite)
            canvas.drawText(Formats.euros(totalLigne.ht), LARGEUR - MARGE - 4f, y + 10f, droite)
            y += hauteurLigne + 6f
            if (avecMarge && ligne.coutAchatUnitaire > 0) {
                val marge = Calculs.marge(totalLigne.ht, ligne.quantite * ligne.coutAchatUnitaire)
                canvas.drawText(
                    "interne : coût ${Formats.euros(ligne.quantite * ligne.coutAchatUnitaire)} · " +
                        "marge ${Formats.euros(marge.margeBrute)} (${Formats.pourcent(marge.tauxMarge)})",
                    MARGE + 4f, y + 6f,
                    Paint().apply { color = Color.rgb(120, 120, 120); textSize = 7.5f; isAntiAlias = true },
                )
                y += 10f
            }
            y += 2f
        }
        return y + 8f
    }

    private fun blocTotaux(
        canvas: Canvas,
        totaux: Totaux,
        devis: Devis,
        entreprise: Entreprise,
        depart: Float,
    ): Float {
        var y = depart
        val normal = Paint().apply { textSize = 10f; isAntiAlias = true; textAlign = Paint.Align.RIGHT; color = Color.BLACK }
        val gras = Paint().apply {
            textSize = 12f; isAntiAlias = true; textAlign = Paint.Align.RIGHT; color = entreprise.couleur
            isFakeBoldText = true
        }
        val x = LARGEUR - MARGE - 4f
        if (totaux.totalTVA > 0) {
            totaux.tvaParTaux.filter { it.value > 0 }.forEach { (taux, montant) ->
                canvas.drawText("TVA ${Formats.nombre(taux)} % : ${Formats.euros(montant)}", x, y, normal)
                y += 14f
            }
        }
        if (devis.remiseGlobalePct > 0) {
            canvas.drawText("Remise ${Formats.nombre(devis.remiseGlobalePct)} % appliquée", x, y, normal)
            y += 14f
        }
        canvas.drawText("Total HT : ${Formats.euros(totaux.totalHT)}", x, y, normal); y += 14f
        if (totaux.totalTVA > 0) {
            canvas.drawText("Total TVA : ${Formats.euros(totaux.totalTVA)}", x, y, normal); y += 14f
        }
        canvas.drawText("Total TTC : ${Formats.euros(totaux.totalTTC)}", x, y + 4f, gras); y += 20f
        if (devis.acomptePct > 0) {
            canvas.drawText(
                "Acompte ${Formats.nombre(devis.acomptePct)} % : " +
                    Formats.euros(Calculs.montantAcompte(totaux.totalTTC, devis.acomptePct)),
                x, y, normal,
            )
            y += 14f
        }
        return y + 10f
    }

    fun hauteurTotaux(totaux: Totaux, devis: Devis): Float {
        var h = 0f
        if (totaux.totalTVA > 0) h += 14f * totaux.tvaParTaux.count { it.value > 0 } + 14f
        if (devis.remiseGlobalePct > 0) h += 14f
        h += 14f + 20f
        if (devis.acomptePct > 0) h += 14f
        return h + 10f
    }

    /** Les mentions, ligne à ligne, avec saut de page quand la place manque : rien n'est perdu. */
    private fun blocMentions(toile: () -> Canvas, devis: Devis, depart: Float, nouvellePage: () -> Float): Float {
        var y = depart
        val petit = Paint().apply { textSize = 8.2f; isAntiAlias = true; color = Color.DKGRAY }
        val texte = devis.mentionsTexte.ifBlank { "—" }
        for (ligne in texte.split("\n")) {
            if (ligne.isBlank()) { y += 4f; continue }
            for (morceau in decouper(ligne, 8.2f, petit, LARGEUR - 2 * MARGE)) {
                if (y + 10.5f > HAUTEUR - BAS) y = nouvellePage()
                // la toile a pu changer de page : on dessine sur la toile courante
                toile().drawText(morceau, MARGE, y, petit)
                y += 10.5f
            }
        }
        return y + 8f
    }

    private fun blocSignature(
        canvas: Canvas,
        devis: Devis,
        entreprise: Entreprise,
        signature: Bitmap?,
        depart: Float,
    ): Float {
        var y = maxOf(depart, HAUTEUR - 170f)
        val gras = Paint().apply { textSize = 10f; isAntiAlias = true; isFakeBoldText = true; color = entreprise.couleur }
        canvas.drawText("Bon pour accord", MARGE, y, gras)
        val normal = Paint().apply { textSize = 9f; isAntiAlias = true; color = Color.DKGRAY }
        canvas.drawText("Date : ${dateFr.format(Date(devis.dateAcceptation ?: System.currentTimeMillis()))}", MARGE, y + 14f, normal)
        canvas.drawText("Signature du client :", MARGE, y + 30f, normal)
        canvas.drawRect(MARGE, y + 36f, MARGE + 200f, y + 96f, Paint().apply {
            style = Paint.Style.STROKE; color = Color.LTGRAY; strokeWidth = 1f
        })
        if (signature != null) {
            canvas.drawBitmap(
                signature,
                null,
                Rect(MARGE.toInt() + 4, y.toInt() + 40, MARGE.toInt() + 196, y.toInt() + 92),
                Paint(Paint.FILTER_BITMAP_FLAG),
            )
        }
        if (devis.mentionTauxReduit) {
            canvas.drawText(
                "Le client atteste que les conditions du taux réduit de TVA sont remplies.",
                MARGE, y + 112f, normal,
            )
        }
        return y + 130f
    }

    private fun piedDePage(canvas: Canvas, devis: Devis, page: Int, entreprise: Entreprise) {
        val paint = Paint().apply { textSize = 7.5f; color = Color.GRAY; isAntiAlias = true }
        canvas.drawText(
            "${entreprise.nom} · devis ${devis.numero} · page $page",
            MARGE, HAUTEUR - 20f, paint,
        )
    }

    // ---------------------------------------------------------------- helpers

    fun decouper(texte: String, taille: Float, paint: Paint, largeurMax: Float): List<String> {
        val p = TextPaint(paint)
        p.textSize = taille
        val mots = texte.split(" ")
        val lignes = mutableListOf<String>()
        var courante = StringBuilder()
        for (mot in mots) {
            val essai = if (courante.isEmpty()) mot else "$courante $mot"
            if (p.measureText(essai) <= largeurMax) {
                courante = StringBuilder(essai)
            } else {
                if (courante.isNotEmpty()) lignes.add(courante.toString())
                courante = StringBuilder(mot)
            }
        }
        if (courante.isNotEmpty()) lignes.add(courante.toString())
        return lignes.ifEmpty { listOf("") }
    }

    fun chargerBitmap(chemin: String?): Bitmap? = chemin
        ?.takeIf { it.isNotBlank() }
        ?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
}
