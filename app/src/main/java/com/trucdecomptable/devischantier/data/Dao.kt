package com.trucdecomptable.devischantier.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface Dao {

    // ---- familles / articles ----
    @Query("SELECT * FROM famille ORDER BY nom")
    fun familles(): Flow<List<Famille>>

    @Query("SELECT * FROM famille ORDER BY nom")
    suspend fun famillesListe(): List<Famille>

    @Upsert
    suspend fun enregistrerFamille(famille: Famille): Long

    @Query("DELETE FROM famille WHERE id = :id")
    suspend fun supprimerFamille(id: Long)

    @Query("SELECT * FROM article ORDER BY favori DESC, usages DESC, libelle")
    fun articles(): Flow<List<Article>>

    @Query("SELECT * FROM article ORDER BY favori DESC, usages DESC, libelle")
    suspend fun articlesListe(): List<Article>

    @Upsert
    suspend fun enregistrerArticle(article: Article): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enregistrerArticles(articles: List<Article>)

    @Query("DELETE FROM article WHERE id = :id")
    suspend fun supprimerArticle(id: Long)

    @Query("UPDATE article SET usages = usages + 1 WHERE id = :id")
    suspend fun comptabiliserUsage(id: Long)

    // ---- clients ----
    @Query("SELECT * FROM client ORDER BY nom")
    fun clients(): Flow<List<Client>>

    @Query("SELECT * FROM client ORDER BY nom")
    suspend fun clientsListe(): List<Client>

    @Query("SELECT * FROM client WHERE id = :id")
    suspend fun client(id: Long): Client?

    @Upsert
    suspend fun enregistrerClient(client: Client): Long

    @Query("DELETE FROM client WHERE id = :id")
    suspend fun supprimerClient(id: Long)

    // ---- devis ----
    @Query("SELECT * FROM devis ORDER BY dateDevis DESC, id DESC")
    fun devis(): Flow<List<Devis>>

    @Query("SELECT * FROM devis ORDER BY dateDevis DESC, id DESC")
    suspend fun devisListe(): List<Devis>

    @Query("SELECT * FROM devis WHERE id = :id")
    suspend fun devis(id: Long): Devis?

    @Query("SELECT * FROM devis WHERE id = :id")
    fun devisFlux(id: Long): Flow<Devis?>

    @Upsert
    suspend fun enregistrerDevis(devis: Devis): Long

    @Query("DELETE FROM devis WHERE id = :id")
    suspend fun supprimerDevis(id: Long)

    @Query("SELECT COUNT(*) FROM devis WHERE numero LIKE :prefixe || '%'")
    suspend fun compterNumeros(prefixe: String): Int

    @Query("SELECT numero FROM devis WHERE numero LIKE :prefixe || '%'")
    suspend fun numeros(prefixe: String): List<String>

    @Query("SELECT indice FROM devis WHERE numero = :numero")
    suspend fun indices(numero: String): List<String>

    /**
     * Numérotation et insertion dans la même transaction : deux créations
     * simultanées (double appui, raccourci relancé) ne peuvent plus recevoir le
     * même numéro.
     */
    @Transaction
    suspend fun creerDevisNumerote(devis: Devis, prefixe: String, annee: Int): Long {
        val numero = com.trucdecomptable.devischantier.devis.Calculs.numeroSuivant(
            prefixe, annee, numeros("${prefixe.trim().ifBlank { "D" }}-$annee-"),
        )
        return enregistrerDevis(devis.copy(numero = numero))
    }

    // ---- lignes ----
    @Query("SELECT * FROM ligne WHERE devisId = :devisId ORDER BY position, id")
    fun lignes(devisId: Long): Flow<List<Ligne>>

    @Query("SELECT * FROM ligne WHERE devisId = :devisId ORDER BY position, id")
    suspend fun lignesListe(devisId: Long): List<Ligne>

    @Upsert
    suspend fun enregistrerLigne(ligne: Ligne): Long

    @Query("DELETE FROM ligne WHERE id = :id")
    suspend fun supprimerLigne(id: Long)

    @Query("DELETE FROM ligne WHERE devisId = :devisId")
    suspend fun supprimerLignes(devisId: Long)

    // ---- achats et marges ----
    @Query("SELECT * FROM achat ORDER BY libelle")
    fun achats(): Flow<List<Achat>>

    @Query("SELECT * FROM achat ORDER BY libelle")
    suspend fun achatsListe(): List<Achat>

    @Upsert
    suspend fun enregistrerAchat(achat: Achat): Long

    @Query("DELETE FROM achat WHERE id = :id")
    suspend fun supprimerAchat(id: Long)

    @Query("SELECT * FROM prix_achat WHERE achatId = :achatId ORDER BY date DESC")
    fun historique(achatId: Long): Flow<List<PrixAchat>>

    @Upsert
    suspend fun enregistrerPrix(prix: PrixAchat)

    @Query("SELECT * FROM lien_achat WHERE articleId = :articleId")
    suspend fun liens(articleId: Long): List<LienAchat>

    @Query("SELECT * FROM lien_achat")
    fun tousLesLiens(): Flow<List<LienAchat>>

    @Upsert
    suspend fun enregistrerLien(lien: LienAchat)

    @Query("DELETE FROM lien_achat WHERE articleId = :articleId AND achatId = :achatId")
    suspend fun supprimerLien(articleId: Long, achatId: Long)

    // ---- journal ----
    @Query("SELECT * FROM journal WHERE devisId = :devisId ORDER BY date DESC, id DESC")
    fun journal(devisId: Long): Flow<List<Evenement>>

    @Insert
    suspend fun ajouterEvenement(evenement: Evenement): Long

    /** Remplace tout le contenu du devis brouillon : une seule transaction. */
    @Transaction
    suspend fun remplacerLignes(devisId: Long, lignes: List<Ligne>) {
        supprimerLignes(devisId)
        lignes.forEach { enregistrerLigne(it.copy(id = 0, devisId = devisId)) }
    }
}
