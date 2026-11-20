plugins {
    id("sdhelper.android-compose")
}

dependencies {
    implementation(project(":ui:design-api"))
    implementation(project(":core:model"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    testImplementation(libs.kotlin.test.junit)
}
