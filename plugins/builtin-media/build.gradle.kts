plugins {
    id("sdhelper.jvm-library")
}

dependencies {
    api(project(":agent:core"))
    api(project(":core:plugin-api"))

    testImplementation(libs.kotlinx.coroutines.test)
}
