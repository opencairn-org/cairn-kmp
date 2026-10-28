plugins {
    id("cairn.kmp.library")
    id("app.cash.sqldelight")
}

sqldelight {
    databases {
        create("CairnDb") {
            packageName.set("org.opencairn.cairn.core.database")
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/schema"))
            verifyMigrations.set(true)
        }
    }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.sqldelight.runtime)
            api(libs.sqldelight.coroutines)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android)
        }
        getByName("desktopMain").dependencies {
            implementation(libs.sqldelight.jvm)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native)
        }
        getByName("desktopTest").dependencies {
            implementation(libs.sqldelight.jvm)
        }
    }
}
