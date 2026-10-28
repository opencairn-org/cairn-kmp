plugins {
    id("cairn.kmp.library")
    id("com.squareup.wire")
}

// Wire generates the cairn.v1 messages and client stubs into commonMain from the vendored protos.
wire {
    sourcePath {
        srcDir("$rootDir/proto")
        include("cairn/v1/*.proto")
    }
    kotlin {
        rpcRole = "client"
        rpcCallStyle = "suspending"
        javaInterop = false
    }
}

kotlin {
    // Wire's generated code carries a few compiler warnings that are not ours to fix.
    compilerOptions {
        allWarningsAsErrors.set(false)
    }
    sourceSets {
        commonMain.dependencies {
            api(projects.core.common)
            api(libs.wire.runtime)
            api(libs.wire.grpc.client)
            api(libs.ktor.client.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.okio)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        getByName("desktopMain").dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
        }
    }
}
