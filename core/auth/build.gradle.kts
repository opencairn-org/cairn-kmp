plugins {
    id("cairn.kmp.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.common)
            api(projects.core.domain)
            api(projects.core.network)
            api(projects.core.database)
            api(libs.oidc.appsupport)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
        }
        androidMain.dependencies {
            implementation(libs.androidx.browser)
        }
        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
            implementation(libs.turbine)
        }
        getByName("desktopTest").dependencies {
            implementation(libs.sqldelight.jvm)
        }
    }
}
