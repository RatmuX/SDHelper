plugins {
    id("sdhelper.android-application")
}

android {
    // Design languages are build flavors; see docs/decisions/ADR-0003-design-flavors.md.
    flavorDimensions += "design"
    productFlavors {
        create("m3") {
            dimension = "design"
        }
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)

    implementation(project(":core:model"))
    implementation(project(":ui:design-api"))
    implementation(project(":feature:home"))

    // The only place where a concrete design module is wired in.
    "m3Implementation"(project(":ui:design-m3"))
}
