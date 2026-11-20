plugins {
    id("cairn.kmp.compose")
    id("dev.zacsweers.metro")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "CairnShared"
            isStatic = true
        }
    }
    sourceSets {
        commonMain.dependencies {
            api(projects.core.common)
            api(projects.core.domain)
            api(projects.core.auth)
            api(projects.core.database)
            api(projects.core.network)
            api(projects.core.data)
            api(projects.ui.design)
            implementation(projects.feature.login)
            implementation(projects.feature.overview)
            implementation(projects.feature.activities)
            implementation(projects.feature.account)
            implementation(libs.navigation3.ui)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.viewmodel.navigation3)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.savedstate.compose)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor)
        }
        getByName("desktopTest").dependencies {
            // Screenshot tests render the screens off-screen with Compose's desktop test scene.
            implementation(libs.compose.ui.test)
            implementation(compose.desktop.currentOs)
            implementation(projects.ui.charts)
        }
    }
}

// Screenshot tests compare against the reference images under src/desktopTest/screenshots;
// `make screenshots` (-Pcairn.screenshots=record) rewrites them instead.
tasks.withType<Test>().configureEach {
    val mode = providers.gradleProperty("cairn.screenshots").getOrElse("verify")
    systemProperty("cairn.screenshots", mode)
    systemProperty("java.awt.headless", "true")
    inputs.dir(layout.projectDirectory.dir("src/desktopTest/screenshots")).withPathSensitivity(PathSensitivity.RELATIVE)
    if (mode == "record") outputs.upToDateWhen { false }
}
