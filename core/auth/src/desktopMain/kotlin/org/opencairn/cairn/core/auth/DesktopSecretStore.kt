package org.opencairn.cairn.core.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.util.Properties

/**
 * Secrets in a 0600 properties file inside the app-data directory; same protection level as
 * most CLI tools' credential files. OS keyring integration is a planned hardening step.
 */
class DesktopSecretStore(
    dataDir: File,
) : SecretStore {
    private val file = File(dataDir, "secrets.properties")
    private val mutex = Mutex()

    override suspend fun get(key: String): String? =
        mutex.withLock {
            withContext(Dispatchers.IO) { load().getProperty(key) }
        }

    override suspend fun put(
        key: String,
        value: String,
    ) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val p = load()
            p.setProperty(key, value)
            save(p)
        }
    }

    override suspend fun remove(key: String) =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val p = load()
                p.remove(key)
                save(p)
            }
        }

    private fun load(): Properties =
        Properties().apply {
            if (file.exists()) file.inputStream().use { load(it) }
        }

    private fun save(p: Properties) {
        file.parentFile?.mkdirs()
        if (!file.exists()) file.createNewFile()
        runCatching {
            Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString("rw-------"))
        }
        file.outputStream().use { p.store(it, null) }
    }
}

/** Per-user application data directory following each OS's convention. */
object DesktopPaths {
    val dataDir: File by lazy {
        val os = System.getProperty("os.name").lowercase()
        val home = System.getProperty("user.home")
        when {
            os.contains("win") -> File(System.getenv("APPDATA") ?: "$home\\AppData\\Roaming", "cairn")
            os.contains("mac") -> File(home, "Library/Application Support/cairn")
            else -> File(System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() } ?: "$home/.local/share", "cairn")
        }
    }
}
