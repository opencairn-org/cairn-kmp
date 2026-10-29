// Root-only conventions: formatting of build scripts and config files, aggregated coverage.
plugins {
    id("com.diffplug.spotless")
    id("org.jetbrains.kotlinx.kover")
}

val ktlintVersion = catalog.version("ktlint")

spotless {
    kotlinGradle {
        target("*.gradle.kts", "build-logic/**/*.gradle.kts", "build-logic/src/**/*.kt")
        targetExclude("**/build/**")
        ktlint(ktlintVersion)
    }
    format("misc") {
        target(
            "*.md",
            "docs/**/*.md",
            "*.yml",
            "*.yaml",
            ".github/**/*.yml",
            "config/**/*.yml",
            "*.toml",
            "gradle/*.toml",
        )
        targetExclude("**/build/**", "**/.gradle/**")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

// Reproducible resolution: every configuration is locked; `make update-locks` rewrites the lock state.
allprojects {
    dependencyLocking {
        lockAllConfigurations()
    }
    // Tool classpaths pinned by their plugins (AGP's lint tool, Kotlin's Swift export) never ship
    // and are not ours to fix, so they stay out of the lock state and the osv-scanner results.
    configurations
        .matching {
            it.name.startsWith(
                "androidLintTool",
            ) || it.name.startsWith("swiftExport")
        }.configureEach {
            resolutionStrategy.deactivateDependencyLocking()
        }
    // `./gradlew resolveAndLockAll --write-locks` (make update-locks) resolves every resolvable
    // configuration so the lock state covers all of them, per project.
    tasks.register("resolveAndLockAll") {
        notCompatibleWithConfigurationCache("resolves configurations at execution time")
        doFirst {
            require(gradle.startParameter.isWriteDependencyLocks) { "run with --write-locks" }
        }
        doLast {
            configurations.filter { it.isCanBeResolved }.forEach { c ->
                runCatching { c.resolve() }.onFailure { logger.info("skipping ${c.name}: ${it.message}") }
            }
        }
    }
}
