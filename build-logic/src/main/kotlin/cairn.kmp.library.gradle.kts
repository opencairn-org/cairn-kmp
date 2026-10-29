// Every shared module: Android + desktop (JVM) + iOS targets, JDK 25 toolchain, warnings as errors.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("cairn.quality")
}

kotlin {
    jvmToolchain(25)

    androidLibrary {
        namespace = cairnNamespace
        compileSdk = catalog.version("android-compileSdk").toInt()
        minSdk = catalog.version("android-minSdk").toInt()
        // Packages Compose Multiplatform resources (strings, fonts) into the Android artifact.
        androidResources.enable = true
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }
    jvm("desktop")
    iosArm64()
    iosSimulatorArm64()

    compilerOptions {
        allWarningsAsErrors.set(true)
        freeCompilerArgs.addAll(
            "-Xexpect-actual-classes",
            "-opt-in=kotlin.time.ExperimentalTime",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
        )
    }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(catalog.library("kotlinx-coroutines-test"))
        }
    }
}
