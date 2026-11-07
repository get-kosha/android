@file:UseSerializers(RoundedLong::class)

package app.getkosha.network

import kotlinx.serialization.UseSerializers
import app.getkosha.model.RoundedLong

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import app.getkosha.model.KoshaJson
import kotlinx.serialization.Serializable
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** A signed-in session: which server, and the tokens for it. */
@Serializable
data class Tokens(
    /** The server's origin, such as https://getkosha.app. */
    val server: String,
    val clientId: String,
    val access: String,
    val refresh: String,
    /** Unix seconds when [access] expires. */
    val expiresAt: Long,
)

/**
 * Keeps the session encrypted with an AES-GCM key that never leaves the Android Keystore. Nothing here is
 * backed up (data_extraction_rules.xml), so a restored phone signs in again.
 */
open class TokenStore(context: Context?) {
    // Null only in unit tests, which override read, write and clear.
    private val prefs by lazy { context!!.getSharedPreferences("kosha.session", Context.MODE_PRIVATE) }

    @Volatile private var cached: Tokens? = null

    open fun read(): Tokens? = cached ?: prefs.getString(KEY, null)?.let { stored ->
        runCatching { KoshaJson.decodeFromString(Tokens.serializer(), decrypt(stored)) }.getOrNull()
    }?.also { cached = it }

    /** Stores the new tokens before anyone uses them, so a rotated refresh token is never lost. */
    open fun write(tokens: Tokens) {
        prefs.edit().putString(KEY, encrypt(KoshaJson.encodeToString(Tokens.serializer(), tokens))).commit()
        cached = tokens
    }

    open fun clear() {
        prefs.edit().remove(KEY).commit()
        cached = null
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(AES)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val out = cipher.iv + cipher.doFinal(plain.toByteArray())
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val bytes = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(AES)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12))
        return String(cipher.doFinal(bytes, 12, bytes.size - 12))
    }

    private companion object {
        const val KEY = "tokens"
        const val ALIAS = "kosha.tokens"
        const val AES = "AES/GCM/NoPadding"
    }
}
