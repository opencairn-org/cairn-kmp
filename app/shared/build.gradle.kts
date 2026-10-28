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
            api(projects.ui.design)
            implementation(projects.feature.login)
            implementation(libs.navigation3.ui)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.viewmodel.navigation3)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
