package org.opencairn.cairn.android

import android.app.Application
import dev.zacsweers.metro.createGraphFactory
import org.opencairn.cairn.BuildConfig
import org.opencairn.cairn.app.di.AppGraph
import org.opencairn.cairn.app.di.PlatformContext
import org.opencairn.cairn.core.auth.AndroidSecretStore
import org.opencairn.cairn.core.auth.AuthFlow
import org.opencairn.cairn.core.auth.AuthPlatform
import org.opencairn.cairn.core.auth.OidcLibraryAuthFlow
import org.opencairn.cairn.core.auth.SecretStore
import org.opencairn.cairn.core.database.DatabaseDriverFactory
import org.publicvalue.multiplatform.oidc.appsupport.AndroidCodeAuthFlowFactory

class CairnApplication : Application() {
    /** One factory per process; activities register with it in onCreate (see MainActivity). */
    val authFlowFactory = AndroidCodeAuthFlowFactory(useWebView = false)

    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = createGraphFactory<AppGraph.Factory>().create(AndroidPlatformContext(this))
    }
}

class AndroidPlatformContext(
    private val app: CairnApplication,
) : PlatformContext {
    override val appVersion: String = BuildConfig.VERSION_NAME
    override val databaseDriverFactory = DatabaseDriverFactory(app)
    override val authPlatform: AuthPlatform =
        object : AuthPlatform {
            override val redirectUri: String = "org.opencairn.cairn:/oauth/callback"
            override val authFlow: AuthFlow = OidcLibraryAuthFlow(app.authFlowFactory)
            override val secretStore: SecretStore = AndroidSecretStore(app)
        }
}
