package com.trucdecomptable.devischantier.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** Money, quantities and dates, all formatted the French way. */
object Formats {

    private val dateFr = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)
    private val dateHeureFr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)

    fun euros(montant: Double, decimales: Boolean = true): String {
        val arrondi = (montant * 100).roundToLong() / 100.0
        val s = if (decimales) String.format(Locale.FRANCE, "%,.2f", arrondi)
        else String.format(Locale.FRANCE, "%,.0f", arrondi)
        // Les JDK récents insèrent une espace fine insécable : on garde une
        // espace normale, identique à l'écran, dans le PDF et dans les CSV.
        return s.replace("\u202F", " ").replace("\u00A0", " ") + " €"
    }

    fun nombre(valeur: Double): String =
        if (abs(valeur - valeur.roundToLong()) < 0.0001) valeur.roundToLong().toString()
        else String.format(Locale.FRANCE, "%.2f", valeur)

    fun pourcent(valeur: Double): String =
        String.format(Locale.FRANCE, "%.1f %%", valeur).replace("\u202F", " ").replace("\u00A0", " ")

    fun date(millis: Long?): String = if (millis == null || millis <= 0L) "—" else dateFr.format(Date(millis))

    fun dateHeure(millis: Long?): String =
        if (millis == null || millis <= 0L) "—" else dateHeureFr.format(Date(millis))

    fun aujourdHui(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun plusDeJours(millis: Long, jours: Int): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = millis
        cal.add(Calendar.DAY_OF_YEAR, jours)
        return cal.timeInMillis
    }

    fun joursEntre(debut: Long, fin: Long): Long {
        val a = Calendar.getInstance().apply {
            timeInMillis = debut
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val b = Calendar.getInstance().apply {
            timeInMillis = fin
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return ((b.timeInMillis - a.timeInMillis) / 86_400_000L)
    }

    /** "1 234,56", "1234.56", "1 234,56 €", "85 € HT" -> 1234.56 */
    fun montant(texte: String): Double? {
        val nettoye = texte
            .replace("\u00A0", "")
            .replace(" ", "")
            .replace("€", "")
            .replace("EUR", "", ignoreCase = true)
            .replace(",", ".")
            .trim()
        return nettoye.toDoubleOrNull()
    }

    /** "2", "deux", "2,5" -> 2.0 / 2.5 */
    fun quantite(texte: String): Double? {
        val t = texte.trim().lowercase(Locale.FRANCE)
        t.toDoubleOrNull()?.let { return it }
        t.replace(",", ".").toDoubleOrNull()?.let { return it }
        return NombresEnLettres.valeur(t)
    }

    fun arrondi2(valeur: Double): Double = (valeur * 100).roundToLong() / 100.0
}

/** "deux", "trois heures", "une" ... -> the number, for dictated quantities. */
object NombresEnLettres {

    private val simples = mapOf(
        "zero" to 0, "un" to 1, "une" to 1, "deux" to 2, "trois" to 3, "quatre" to 4,
        "cinq" to 5, "six" to 6, "sept" to 7, "huit" to 8, "neuf" to 9, "dix" to 10,
        "onze" to 11, "douze" to 12, "treize" to 13, "quatorze" to 14, "quinze" to 15,
        "seize" to 16, "vingt" to 20, "trente" to 30, "quarante" to 40, "cinquante" to 50,
        "soixante" to 60, "cent" to 100,
    )

    fun valeur(texte: String): Double? {
        val mots = texte.split("-", " ").filter { it.isNotBlank() }
        if (mots.isEmpty()) return null
        var total = 0.0
        var courant = 0.0
        for (mot in mots) {
            if (mot == "et") continue
            val v = simples[mot]
            if (v == null) {
                // « soixante-dix », « quatre-vingt-dix » : deux dizaines collées
                val colle = mots.filter { simples.containsKey(it) }
                if (colle.size == 2) return colle.sumOf { (simples[it] ?: 0).toDouble() }
                return null
            }
            when {
                v == 100 -> courant = if (courant == 0.0) 100.0 else courant * 100
                else -> courant += v
            }
        }
        total += courant
        return if (total > 0) total else null
    }
}
