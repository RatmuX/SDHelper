package architecture

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/** Checks the module dependency graph against [ArchitectureRules]. */
abstract class VerifyDependenciesTask : DefaultTask() {
    /** Encoded modules: `path|projectDep,projectDep|group:name,group:name`. */
    @get:Input
    abstract val modules: ListProperty<String>

    @TaskAction
    fun verify() {
        val infos = modules.get().map(::decode)
        val violations = ArchitectureRules.checkDependencies(infos)
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Architecture violations (see docs/ARCHITECTURE.md):\n" + violations.joinToString("\n") { " - $it" },
            )
        }
    }

    companion object {
        fun encode(info: ModuleInfo) =
            "${info.path}|${info.projectDependencies.sorted().joinToString(",")}|" +
                info.externalDependencies.sorted().joinToString(",")

        private fun decode(text: String): ModuleInfo {
            val parts = text.split("|")

            fun set(value: String) = value.split(",").filter { it.isNotEmpty() }.toSet()
            return ModuleInfo(parts[0], set(parts[1]), set(parts[2]))
        }
    }
}

/** Rejects imports of concrete design libraries in modules that must stay design-agnostic. */
abstract class VerifyImportsTask : DefaultTask() {
    @get:Input
    abstract val modulePath: Property<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @TaskAction
    fun verify() {
        val violations =
            sources.files.flatMap { file ->
                val imports = file.readLines().map { it.trim() }.filter { it.startsWith("import ") }
                ArchitectureRules.checkImports(modulePath.get(), imports).map { "${file.name}: $it" }
            }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Forbidden imports (see docs/ARCHITECTURE.md):\n" + violations.joinToString("\n") { " - $it" },
            )
        }
    }
}
