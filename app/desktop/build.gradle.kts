import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("dev.zacsweers.metro")
    id("cairn.quality")
    id("cairn.licenses")
    alias(libs.plugins.cyclonedx)
    alias(libs.plugins.conveyor)
}

licenseReport {
    configurations = arrayOf("runtimeClasspath")
}

// Conveyor and the Compose packager read the project version; CI passes the tag via -Pcairn.versionName.
version = (project.findProperty("cairn.versionName") as String?)?.substringBefore('-') ?: "0.1.0"

kotlin {
    jvmToolchain(25)
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    api(projects.app.shared)
    implementation(projects.core.auth)
    implementation(projects.core.database)
    implementation(libs.oidc.appsupport)
    implementation(compose.desktop.currentOs)
    runtimeOnly(libs.kotlinx.coroutines.swing)
    // MapLibre Native render backends, one per desktop platform the packages target.
    runtimeOnly(libs.maplibre.runtime.linux.x64)
    runtimeOnly(libs.maplibre.runtime.windows.x64)
    runtimeOnly(libs.maplibre.runtime.macos.arm64)
    implementation(libs.maplibre.compose)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}

compose.desktop {
    application {
        mainClass = "org.opencairn.cairn.desktop.MainKt"
        // MapLibre's native bindings use the FFM API.
        jvmArgs += "--enable-native-access=ALL-UNNAMED"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "cairn"
            packageVersion = (project.findProperty("cairn.versionName") as String?)?.substringBefore('-') ?: "0.1.0"
            description = "Cairn: your activities, on every device"
            vendor = "The Cairn Authors"
            licenseFile.set(rootProject.file("LICENSE"))
        }
    }
}
