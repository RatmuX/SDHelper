plugins {
    id("sdhelper.jvm-library")
    application
}

dependencies {
    implementation(project(":agent:core"))
    implementation(project(":agent:platform-windows"))
}

application {
    mainClass.set("io.github.ratmux.sdhelper.agent.desktop.MainKt")
}
