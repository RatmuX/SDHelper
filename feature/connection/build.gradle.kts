plugins {
    id("sdhelper.android-feature")
}

dependencies {
    implementation(project(":core:protocol"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.zxing.android.embedded)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
