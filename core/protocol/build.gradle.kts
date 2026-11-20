plugins {
    id("sdhelper.jvm-library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

dependencies {
    api(project(":core:model"))
    api(libs.kotlinx.serialization.json)
}
