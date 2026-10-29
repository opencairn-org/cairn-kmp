plugins {
    id("cairn.kmp.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.domain)
            implementation(projects.ui.design)
            api(libs.maplibre.compose)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
