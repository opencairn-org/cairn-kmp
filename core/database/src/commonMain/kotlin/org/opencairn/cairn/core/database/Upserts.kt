package org.opencairn.cairn.core.database

/** Insert-or-update helpers (SQLite 3.18 on Android 8 has no UPSERT clause). */
fun CairnDb.upsertServer(
    baseUrl: String,
    version: String?,
    federation: Boolean,
    oauth: Boolean,
    clientId: String?,
    now: Long,
) = transaction {
    serversQueries.insertOrIgnore(baseUrl, version, if (federation) 1 else 0, if (oauth) 1 else 0, clientId, now)
    serversQueries.updateInfo(version, if (federation) 1 else 0, if (oauth) 1 else 0, now, baseUrl)
    if (clientId != null) serversQueries.setClientId(clientId, baseUrl)
}

@Suppress("LongParameterList")
fun CairnDb.upsertAccount(
    serverBaseUrl: String,
    userId: String,
    authKind: String,
    username: String,
    email: String,
    displayName: String,
    avatarUrl: String?,
    role: String,
    units: String,
    dateFormat: String,
    timeFormat: String,
    locale: String,
    timezone: String,
    permissions: String,
    createdAt: Long?,
    now: Long,
    active: Boolean,
) = transaction {
    accountsQueries.insertOrIgnore(
        serverBaseUrl,
        userId,
        authKind,
        username,
        email,
        displayName,
        avatarUrl,
        role,
        units,
        dateFormat,
        timeFormat,
        locale,
        timezone,
        permissions,
        createdAt,
        now,
    )
    accountsQueries.updateUser(
        authKind,
        username,
        email,
        displayName,
        avatarUrl,
        role,
        units,
        dateFormat,
        timeFormat,
        locale,
        timezone,
        permissions,
        createdAt,
        now,
        serverBaseUrl,
        userId,
    )
    if (active) {
        accountsQueries.clearActive()
        accountsQueries.setActive(serverBaseUrl, userId)
    }
}
