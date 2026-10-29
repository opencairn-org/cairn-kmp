plugins {
    id("cairn.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.domain)
            api(projects.core.common)
            implementation(projects.core.network)
            implementation(projects.core.database)
            implementation(projects.core.auth)
        }
        commonTest.dependencies {
            implementation(libs.turbine)
            implementation(libs.ktor.client.mock)
        }
        getByName("desktopTest").dependencies {
            implementation(libs.sqldelight.jvm)
        }
    }
}
