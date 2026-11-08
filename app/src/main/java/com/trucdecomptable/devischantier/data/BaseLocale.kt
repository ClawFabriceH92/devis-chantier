package com.trucdecomptable.devischantier.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Famille::class,
        Article::class,
        Client::class,
        Devis::class,
        Ligne::class,
        Achat::class,
        PrixAchat::class,
        LienAchat::class,
        Evenement::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class BaseLocale : RoomDatabase() {

    abstract fun dao(): Dao

    companion object {
        const val NOM_FICHIER = "devis-chantier.db"

        @Volatile
        private var instance: BaseLocale? = null

        fun obtenir(context: Context): BaseLocale =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    BaseLocale::class.java,
                    NOM_FICHIER,
                )
                    // Jamais fallbackToDestructiveMigration sur une app qui contient
                    // les devis de l'artisan : une mise à jour effacerait tout.
                    .build()
                    .also { instance = it }
            }

        /** Ferme la base avant de remplacer ses fichiers (restauration). */
        fun fermer() = synchronized(this) {
            instance?.close()
            instance = null
        }
    }
}

object Depots {
    fun dao(context: Context): Dao = BaseLocale.obtenir(context).dao()
}
