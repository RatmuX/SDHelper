plugins {
    id("sdhelper.jvm-library")
}

dependencies {
    api(project(":core:protocol"))
    api(project(":core:plugin-api"))
}
