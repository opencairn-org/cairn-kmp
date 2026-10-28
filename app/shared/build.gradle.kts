plugins {
    id("cairn.kmp.compose")
    id("dev.zacsweers.metro")
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
            api(projects.ui.design)
        }
    }
}
