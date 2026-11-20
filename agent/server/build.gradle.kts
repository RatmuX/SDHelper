plugins {
    id("sdhelper.jvm-library")
}

dependencies {
    api(project(":agent:core"))
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.network.tls.certificates)
    implementation(libs.jmdns)

    testImplementation(project(":plugins:builtin-system-audio"))
    testImplementation(project(":plugins:builtin-media"))
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.slf4j.simple)
}
