// Per-module quality gates: Spotless+ktlint (format), detekt 2 (lint), Kover (coverage).
import dev.detekt.gradle.Detekt

plugins {
    id("com.diffplug.spotless")
    id("dev.detekt")
    id("org.jetbrains.kotlinx.kover")
    id("com.autonomousapps.dependency-analysis")
}

spotless {
    kotlin {
        target("src/**/*.kt")
        targetExclude("**/build/**", "**/generated/**")
        ktlint(catalog.version("ktlint")).editorConfigOverride(
            mapOf(
                "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
                "max_line_length" to "120",
                "ktlint_compose" to "enabled",
            ),
        )
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint(catalog.version("ktlint"))
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    source.setFrom(
        files(
            "src/commonMain/kotlin",
            "src/commonTest/kotlin",
            "src/androidMain/kotlin",
            "src/desktopMain/kotlin",
            "src/iosMain/kotlin",
            "src/main/kotlin",
            "src/test/kotlin",
        ),
    )
}

tasks.withType<Detekt>().configureEach {
    reports {
        sarif.required.set(true)
        html.required.set(false)
    }
}
