plugins {
    id("cairn.android.application")
    id("dev.zacsweers.metro")
    id("cairn.licenses")
    alias(libs.plugins.cyclonedx)
}

licenseReport {
    configurations = arrayOf("githubReleaseRuntimeClasspath")
}

android {
    defaultConfig {
        applicationId = "org.opencairn.cairn"
        versionCode = (project.findProperty("cairn.versionCode") as String?)?.toInt() ?: 1
        versionName = (project.findProperty("cairn.versionName") as String?) ?: "0.1.0-dev"
        // Custom-scheme redirect for the OAuth browser flow (kotlin-multiplatform-oidc's HandleRedirectActivity).
        addManifestPlaceholders(mapOf("oidcRedirectScheme" to "org.opencairn.cairn"))
    }
    // Distribution channel baked in so the update check knows where the build came from.
    flavorDimensions += "distribution"
    productFlavors {
        create("github") {
            dimension = "distribution"
            buildConfigField("String", "DISTRIBUTION", "\"github\"")
        }
        create("play") {
            dimension = "distribution"
            buildConfigField("String", "DISTRIBUTION", "\"play\"")
        }
    }
    signingConfigs {
        // CI passes the keystore through environment variables; local release builds stay unsigned.
        create("release") {
            val path = System.getenv("CAIRN_KEYSTORE_PATH")
            if (path != null) {
                storeFile = file(path)
                storePassword = System.getenv("CAIRN_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("CAIRN_KEY_ALIAS")
                keyPassword = System.getenv("CAIRN_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (System.getenv("CAIRN_KEYSTORE_PATH") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }
    packaging {
        resources.excludes += setOf("META-INF/{AL2.0,LGPL2.1}", "META-INF/versions/9/OSGI-INF/MANIFEST.MF")
    }
}

dependencies {
    implementation(projects.app.shared)
    implementation(projects.core.auth)
    implementation(projects.core.database)
    implementation(libs.oidc.appsupport)
    implementation(libs.androidx.activity.compose)
    runtimeOnly(libs.kotlinx.coroutines.android)
    // MapLibre Native render backend; OpenGL works in the emulator, Vulkan does not.
    runtimeOnly(libs.maplibre.runtime.android)
}
