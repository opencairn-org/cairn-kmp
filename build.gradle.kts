// Root build: conventions live in build-logic/, modules apply them by id.

plugins {
    id("cairn.root")
    alias(libs.plugins.dependency.analysis)
}

dependencyAnalysis {
    // The plugin's advice for Kotlin Multiplatform source sets is unreliable (api/implementation
    // across targets, expect/actual, Compose accessors), so buildHealth reports and never fails;
    // the report is reviewed when dependencies change.
    issues {
        all {
            onAny { severity("warn") }
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
                // Generated code: Wire messages/stubs, SQLDelight queries and schema, Compose resources.
                packages("cairn.v1", "cairn.worker.v1")
                classes("*.generated.resources.*", "*.database.*Queries", "*.database.CairnDb*")
                classes("*.database.Account", "*.database.Server", "*.database.*.CairnDbImpl*", "*.database.*Impl")
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
