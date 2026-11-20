plugins {
    id("sdhelper.android-library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
