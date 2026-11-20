plugins {
    id("sdhelper.jvm-library")
    application
}

dependencies {
    implementation(project(":agent:core"))
    implementation(project(":agent:server"))
    implementation(project(":agent:platform-windows"))
    implementation(libs.zxing.core)
    runtimeOnly(libs.slf4j.simple)

    testImplementation(libs.kotlinx.coroutines.test)
}

application {
    mainClass.set("io.github.ratmux.sdhelper.agent.desktop.MainKt")
    // Netty loads a native transport; allow it explicitly instead of printing a JDK warning.
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}
