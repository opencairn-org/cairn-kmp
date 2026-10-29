plugins {
    id("cairn.kmp.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.domain)
            implementation(projects.core.common)
            implementation(projects.ui.design)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.qrose)
        }
        commonTest.dependencies {
            implementation(libs.turbine)
        }
    }
}
