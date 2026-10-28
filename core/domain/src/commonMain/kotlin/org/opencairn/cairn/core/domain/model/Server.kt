package org.opencairn.cairn.core.domain.model

/** A cairn-core instance the app knows about, keyed by its normalized base URL. */
data class Server(
    val baseUrl: String,
    val version: String?,
    val federationEnabled: Boolean,
    val oauthEnabled: Boolean,
) {
    val host: String get() = baseUrl.removePrefix("https://").removePrefix("http://").trimEnd('/')
}

/** Which account is active: server + user. One database file per account. */
data class AccountKey(
    val serverBaseUrl: String,
    val userId: String,
) {
    val storageId: String get() = (serverBaseUrl + "|" + userId).hashCode().toUInt().toString(16)
}
