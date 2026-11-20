plugins {
    id("sdhelper.android-application")
    id("org.jetbrains.kotlin.plugin.serialization")
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
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)

    implementation(project(":core:model"))
    implementation(project(":ui:design-api"))
    implementation(project(":feature:home"))
    implementation(project(":feature:connection"))
    implementation(project(":feature:deck"))
    implementation(project(":data:devices"))
    implementation(project(":data:connection"))

    // The only place where a concrete design module is wired in.
    "m3Implementation"(project(":ui:design-m3"))

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.koin.test)
}
