import com.android.build.api.dsl.ApplicationExtension

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

extensions.configure<ApplicationExtension> {
    namespace = "io.github.ratmux.sdhelper"
    compileSdk {
        version =
            release(37) {
                minorApiLevel = 1
            }
    }
    defaultConfig {
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
