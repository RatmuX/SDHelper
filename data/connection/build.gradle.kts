plugins {
    id("sdhelper.android-library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:protocol"))
    implementation(libs.okhttp)
    implementation(libs.koin.android)

    // The phone side is tested against the real agent over TLS (see ConnectionIntegrationTest).
    testImplementation(project(":agent:server"))
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.slf4j.simple)
}
