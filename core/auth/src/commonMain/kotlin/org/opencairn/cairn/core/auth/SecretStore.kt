package org.opencairn.cairn.core.auth

/**
 * Small-value secret storage backed by the platform's keystore
 * (Keychain on iOS, AndroidKeyStore-encrypted preferences on Android, a 0600 file on desktop).
 */
interface SecretStore {
    suspend fun get(key: String): String?

    suspend fun put(
        key: String,
        value: String,
    )

    suspend fun remove(key: String)
}

/** For tests and previews. */
class InMemorySecretStore : SecretStore {
    private val map = mutableMapOf<String, String>()

    override suspend fun get(key: String): String? = map[key]

    override suspend fun put(
        key: String,
        value: String,
    ) {
        map[key] = value
    }

    override suspend fun remove(key: String) {
        map.remove(key)
    }
}
