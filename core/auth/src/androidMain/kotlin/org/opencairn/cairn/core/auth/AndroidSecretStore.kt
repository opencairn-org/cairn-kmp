package org.opencairn.cairn.core.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Values are AES-GCM encrypted with a non-exportable AndroidKeyStore key and stored as
 * base64(iv || ciphertext) in private SharedPreferences. No deprecated Jetpack Security.
 */
class AndroidSecretStore(
    context: Context,
) : SecretStore {
    private val prefs = context.applicationContext.getSharedPreferences("cairn.secrets", Context.MODE_PRIVATE)

    override suspend fun get(key: String): String? =
        withContext(Dispatchers.IO) {
            val raw = prefs.getString(key, null) ?: return@withContext null
            runCatching {
                val bytes = Base64.decode(raw, Base64.NO_WRAP)
                val iv = bytes.copyOfRange(0, IV_BYTES)
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
                String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
            }.getOrNull()
        }

    override suspend fun put(
        key: String,
        value: String,
    ) = withContext(Dispatchers.IO) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val packed = cipher.iv + ciphertext
        prefs.edit().putString(key, Base64.encodeToString(packed, Base64.NO_WRAP)).commit()
        Unit
    }

    override suspend fun remove(key: String) =
        withContext(Dispatchers.IO) {
            prefs.edit().remove(key).commit()
            Unit
        }

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec
                .Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "org.opencairn.cairn.secrets"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
