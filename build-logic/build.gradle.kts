plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.android.gradlePlugin)
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.kotlin.composeCompilerPlugin)
    implementation(libs.kotlin.serializationPlugin)
    implementation(libs.spotless.gradlePlugin)

    testImplementation(libs.kotlin.test.junit)
}
