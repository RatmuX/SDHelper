import architecture.ModuleInfo
import architecture.VerifyDependenciesTask
import architecture.VerifyImportsTask

plugins {
    base
    id("com.diffplug.spotless")
}

val ktlintVersion =
    versionCatalogs
        .named("libs")
        .findVersion("ktlint")
        .get()
        .requiredVersion

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**")
        ktlint(ktlintVersion)
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint(ktlintVersion)
    }
}

// --- Architecture enforcement (docs/ARCHITECTURE.md, section 8) ---

val verifyDependencies =
    tasks.register<VerifyDependenciesTask>("verifyDependencies") {
        group = "verification"
        description = "Checks module dependency direction, design-library isolation and cycles."
    }

subprojects {
    val verifyImports =
        tasks.register<VerifyImportsTask>("verifyImports") {
            group = "verification"
            description = "Rejects imports of concrete design libraries outside design modules."
            modulePath.set(project.path)
            sources.from(fileTree("src") { include("**/*.kt") })
        }
    verifyDependencies.configure { dependsOn(verifyImports) }
}

gradle.projectsEvaluated {
    val declarable = { name: String ->
        !name.contains("test", ignoreCase = true) &&
            !name.contains("lint", ignoreCase = true) &&
            !name.contains("kotlinCompilerPlugin", ignoreCase = true)
    }
    val infos =
        subprojects.map { sub ->
            val projectDeps = mutableSetOf<String>()
            val externalDeps = mutableSetOf<String>()
            sub.configurations
                .filter { it.isCanBeDeclared && !it.isCanBeResolved && declarable(it.name) }
                .flatMap { it.dependencies }
                .forEach { dep ->
                    when (dep) {
                        is ProjectDependency -> projectDeps += dep.path
                        else -> externalDeps += "${dep.group.orEmpty()}:${dep.name}"
                    }
                }
            VerifyDependenciesTask.encode(ModuleInfo(sub.path, projectDeps, externalDeps))
        }
    verifyDependencies.configure { modules.set(infos) }
}

tasks.named("check") {
    dependsOn(verifyDependencies)
}
