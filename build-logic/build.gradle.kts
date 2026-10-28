plugins {
    `kotlin-dsl`
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    implementation(libs.plugin.kotlin)
    implementation(libs.plugin.kotlin.compose)
    implementation(libs.plugin.kotlin.serialization)
    implementation(libs.plugin.android)
    implementation(libs.plugin.compose)
    implementation(libs.plugin.metro)
    implementation(libs.plugin.sqldelight)
    implementation(libs.plugin.wire)
    implementation(libs.plugin.spotless)
    implementation(libs.plugin.detekt)
    implementation(libs.plugin.kover)
    implementation(libs.plugin.license.report)
    implementation(libs.plugin.dependency.analysis)
    // Lets precompiled script plugins read the catalog (`libs.…`).
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}
