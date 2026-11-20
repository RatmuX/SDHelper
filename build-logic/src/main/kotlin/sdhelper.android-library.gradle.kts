import com.android.build.api.dsl.LibraryExtension

plugins {
    id("com.android.library")
}

extensions.configure<LibraryExtension> {
    namespace = "io.github.ratmux.sdhelper" +
        project.path.replace(":", ".").replace("-", "")
    compileSdk {
        version =
            release(37) {
                minorApiLevel = 1
            }
    }
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
