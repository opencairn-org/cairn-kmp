import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("dev.zacsweers.metro")
    id("cairn.quality")
    id("cairn.licenses")
}

licenseReport {
    configurations = arrayOf("runtimeClasspath")
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    api(projects.app.shared)
    implementation(compose.desktop.currentOs)
    runtimeOnly(libs.kotlinx.coroutines.swing)
}

compose.desktop {
    application {
        mainClass = "org.opencairn.cairn.desktop.MainKt"
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
