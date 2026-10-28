package org.opencairn.cairn.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.opencairn.cairn.app.CairnApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as CairnApplication
        app.authFlowFactory.registerActivity(this)
        setContent { CairnApp(app.graph) }
    }
}
