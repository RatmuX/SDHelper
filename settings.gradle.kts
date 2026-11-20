pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "SDHelper"

include(
    ":core:model",
    ":core:protocol",
    ":core:plugin-api",
    ":core:common",
    ":ui:design-api",
    ":ui:design-m3",
    ":feature:home",
    ":feature:deck",
    ":feature:deck-editor",
    ":feature:connection",
    ":feature:settings",
    ":data:profiles",
    ":data:devices",
    ":data:connection",
    ":app:android",
    ":agent:core",
    ":agent:server",
    ":agent:platform-windows",
    ":agent:desktop-app",
)
