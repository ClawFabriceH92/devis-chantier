package com.trucdecomptable.devischantier.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.trucdecomptable.devischantier.util.Diag
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * La clé API de l'artisan (S-02) : chiffrée en AES-GCM par une clé créée dans
 * le Keystore Android, qui ne sort jamais du téléphone. Format stocké :
 * base64(iv) + ":" + base64(chiffré).
 */
object Coffre {

    private const val ALIAS = "devischantier_cle_api"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val LONGUEUR_TAG = 128

    private fun cle(): SecretKey {
        val magasin = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (magasin.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generateur = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generateur.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generateur.generateKey()
    }

    fun chiffrer(texte: String): String? = runCatching {
        val chiffreur = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, cle()) }
        val donnees = chiffreur.doFinal(texte.toByteArray(Charsets.UTF_8))
        val iv = Base64.encodeToString(chiffreur.iv, Base64.NO_WRAP)
        val corps = Base64.encodeToString(donnees, Base64.NO_WRAP)
        "$iv:$corps"
    }.onFailure { Diag.record("coffre/chiffrement", it) }.getOrNull()

    fun dechiffrer(valeur: String): String? = runCatching {
        val (ivEncode, corps) = valeur.split(":", limit = 2).let { it[0] to it[1] }
        val dechiffreur = Cipher.getInstance(TRANSFORMATION).apply {
            init(
                Cipher.DECRYPT_MODE,
                cle(),
                GCMParameterSpec(LONGUEUR_TAG, Base64.decode(ivEncode, Base64.NO_WRAP)),
            )
        }
        String(dechiffreur.doFinal(Base64.decode(corps, Base64.NO_WRAP)), Charsets.UTF_8)
    }.onFailure { Diag.record("coffre/dechiffrement", it) }.getOrNull()

    /** Affichage masqué : « sk-ant-…f3a2 » (V-32). */
    fun masquer(cle: String): String =
        if (cle.length <= 8) "••••" else "${cle.take(6)}…${cle.takeLast(4)}"
}
