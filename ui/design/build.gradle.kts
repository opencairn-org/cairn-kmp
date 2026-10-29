plugins {
    id("cairn.kmp.compose")
}

compose.resources {
    publicResClass = true
    packageOfResClass = "org.opencairn.cairn.ui.design.resources"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.domain)
            api(projects.core.common)
            api(libs.kotlinx.datetime)
        }
    }
}
