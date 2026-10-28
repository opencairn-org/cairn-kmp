package org.opencairn.cairn.desktop

import org.opencairn.cairn.app.di.PlatformContext
import org.opencairn.cairn.core.auth.AuthFlow
import org.opencairn.cairn.core.auth.AuthPlatform
import org.opencairn.cairn.core.auth.DesktopPaths
import org.opencairn.cairn.core.auth.DesktopSecretStore
import org.opencairn.cairn.core.auth.OidcLibraryAuthFlow
import org.opencairn.cairn.core.auth.SecretStore
import org.opencairn.cairn.core.database.DatabaseDriverFactory
import org.publicvalue.multiplatform.oidc.ExperimentalOpenIdConnect
import org.publicvalue.multiplatform.oidc.appsupport.JvmCodeAuthFlowFactory
import org.publicvalue.multiplatform.oidc.appsupport.webserver.SimpleKtorWebserver

/** Loopback port for the OAuth redirect; fixed because cairn-core matches redirect URIs exactly. */
private const val LOOPBACK_PORT = 8765

@OptIn(ExperimentalOpenIdConnect::class)
object DesktopPlatformContext : PlatformContext {
    override val appVersion: String = System.getProperty("cairn.version") ?: "0.1.0-dev"
    override val databaseDriverFactory = DatabaseDriverFactory(DesktopPaths.dataDir)
    override val authPlatform: AuthPlatform =
        object : AuthPlatform {
            override val redirectUri: String = "http://127.0.0.1:$LOOPBACK_PORT/callback"
            override val authFlow: AuthFlow =
                OidcLibraryAuthFlow(
                    JvmCodeAuthFlowFactory(webserverProvider = { SimpleKtorWebserver(port = LOOPBACK_PORT) }),
                )
            override val secretStore: SecretStore = DesktopSecretStore(DesktopPaths.dataDir)
        }
}
