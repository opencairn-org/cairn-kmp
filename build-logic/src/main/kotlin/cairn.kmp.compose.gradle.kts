// Shared modules with Compose UI.
import org.jetbrains.compose.ComposeExtension

plugins {
    id("cairn.kmp.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

val compose = extensions.getByType<ComposeExtension>().dependencies

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            api(compose.components.resources)
            implementation(compose.materialIconsExtended)
        }
    }
}
