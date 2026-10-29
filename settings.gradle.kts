pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "cairn"

include(
    ":core:common",
    ":core:domain",
    ":core:network",
    ":core:database",
    ":core:auth",
    ":core:data",
    ":ui:design",
    ":ui:map",
    ":ui:charts",
    ":feature:login",
    ":feature:overview",
    ":feature:activities",
    ":app:shared",
    ":app:android",
    ":app:desktop",
)

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
