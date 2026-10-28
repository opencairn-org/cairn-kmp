// The Android application module.
import com.android.build.api.dsl.ApplicationExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("cairn.quality")
}

configure<ApplicationExtension> {
    namespace = "org.opencairn.cairn"
    compileSdk = catalog.version("android-compileSdk").toInt()
    defaultConfig {
        minSdk = catalog.version("android-minSdk").toInt()
        targetSdk = catalog.version("android-targetSdk").toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        allWarningsAsErrors.set(true)
    }
}
