plugins {
    id("sdhelper.android-compose")
}

dependencies {
    implementation(project(":ui:design-api"))
    implementation(libs.compose.material3)
}
