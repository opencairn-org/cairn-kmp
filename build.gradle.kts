// Root build: conventions live in build-logic/, modules apply them by id.

plugins {
    id("cairn.root")
    alias(libs.plugins.dependency.analysis)
}

dependencyAnalysis {
    // Compose Multiplatform and KMP source sets confuse the "declare transitively used" advice,
    // so only unused and misconfigured declarations fail; the rest is reported.
    issues {
        all {
            onAny { severity("warn") }
            onUnusedDependencies {
                severity("fail")
                exclude("org.jetbrains.compose.desktop:desktop-jvm-linux-x64", "org.jetbrains.compose.hot-reload:hot-reload-runtime-api")
                exclude(
                    "org.jetbrains.compose.foundation:foundation",
                    "org.jetbrains.compose.material3:material3",
                    "org.jetbrains.compose.material:material-icons-extended",
                    "org.jetbrains.compose.runtime:runtime",
                    "org.jetbrains.compose.ui:ui",
                    "org.jetbrains.compose.components:components-resources",
                )
            }
            onIncorrectConfiguration {
                severity("fail")
                // Added by the Metro plugin; its scope is not ours to declare.
                exclude("dev.zacsweers.metro:runtime")
            }
        }
    }
}

// Coverage floor over every module; `make cover` passes COVER_MIN as cairn.coverMin.
// Kover merges only the projects listed here; every module that applies cairn.quality is in.
dependencies {
    subprojects.filter { it.buildFile.exists() && it.path != ":app:android" }.forEach { kover(it) }
}

kover {
    reports {
        filters {
            excludes {
                // UI and wiring are covered by screenshot/UI tests, not line coverage.
                annotatedBy("androidx.compose.runtime.Composable")
                packages("org.opencairn.cairn.app", "org.opencairn.cairn.android", "org.opencairn.cairn.desktop")
                packages("org.opencairn.cairn.ui.design.resources", "org.opencairn.cairn.ui.design.theme")
                classes("*ComposableSingletons*", "*\$\$Metro*", "*_Factory*", "*Graph*")
            }
        }
        total {
            verify {
                rule {
                    minBound((project.findProperty("cairn.coverMin") as String?)?.toInt() ?: 60)
                }
            }
        }
    }
}
