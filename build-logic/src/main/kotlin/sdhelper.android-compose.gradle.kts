plugins {
    id("sdhelper.android-library")
    id("org.jetbrains.kotlin.plugin.compose")
}

val libs = versionCatalogs.named("libs")

dependencies {
    "implementation"(platform(libs.findLibrary("compose-bom").get()))
    "implementation"(libs.findLibrary("compose-runtime").get())
    "implementation"(libs.findLibrary("compose-ui").get())
    "implementation"(libs.findLibrary("compose-foundation").get())
}
