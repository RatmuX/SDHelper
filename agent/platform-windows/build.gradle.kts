plugins {
    id("sdhelper.jvm-library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

dependencies {
    api(project(":agent:core"))

    testImplementation(libs.kotlinx.coroutines.test)
}
