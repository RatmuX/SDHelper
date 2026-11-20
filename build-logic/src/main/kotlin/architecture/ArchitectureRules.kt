package architecture

/** A Gradle module reduced to what the architecture rules need. */
data class ModuleInfo(
    val path: String,
    val projectDependencies: Set<String>,
    val externalDependencies: Set<String>,
)

/**
 * Pure architecture rules, independent of Gradle so they can be unit-tested.
 * The rules mirror docs/ARCHITECTURE.md section 3 and 8.
 */
object ArchitectureRules {
    private val forbiddenExternalPrefixes = listOf("androidx.compose.material3:", "top.yukonga.miuix")
    private val forbiddenImportPrefixes = listOf("androidx.compose.material3", "top.yukonga.miuix")

    /** Modules that may use a concrete design library. */
    private fun mayUseConcreteDesign(path: String) =
        path == ":ui:design-m3" || path == ":ui:design-miuix" || path.startsWith(":app:")

    fun checkDependencies(modules: List<ModuleInfo>): List<String> {
        val violations = mutableListOf<String>()
        for (module in modules) {
            for (dep in module.projectDependencies) {
                if (!isAllowed(module.path, dep)) {
                    violations += "${module.path} must not depend on $dep"
                }
            }
            if (!mayUseConcreteDesign(module.path)) {
                for (ext in module.externalDependencies) {
                    if (forbiddenExternalPrefixes.any { ext.startsWith(it) }) {
                        violations += "${module.path} must not depend on design library $ext"
                    }
                }
            }
        }
        violations += findCycles(modules)
        return violations
    }

    fun checkImports(
        modulePath: String,
        imports: List<String>,
    ): List<String> {
        if (mayUseConcreteDesign(modulePath)) return emptyList()
        return imports
            .filter { line -> forbiddenImportPrefixes.any { line.startsWith("import $it") } }
            .map { "$modulePath imports a concrete design library: $it" }
    }

    private fun isAllowed(
        from: String,
        to: String,
    ): Boolean =
        when {
            from.startsWith(":core:") -> {
                to.startsWith(":core:")
            }

            from == ":ui:design-api" -> {
                to.startsWith(":core:")
            }

            from.startsWith(":ui:design-") -> {
                to == ":ui:design-api" || to.startsWith(":core:")
            }

            from.startsWith(":feature:") -> {
                to == ":ui:design-api" || to.startsWith(":core:") || to.startsWith(":data:")
            }

            from.startsWith(":data:") -> {
                to.startsWith(":core:")
            }

            from == ":agent:core" -> {
                to.startsWith(":core:")
            }

            from.startsWith(":agent:platform-") -> {
                to == ":agent:core" || to.startsWith(":core:")
            }

            from.startsWith(":plugins:builtin-") -> {
                to == ":agent:core" || to.startsWith(":core:")
            }

            from.startsWith(":agent:") -> {
                !to.startsWith(":app:") && !to.startsWith(":feature:") && !to.startsWith(":ui:")
            }

            from.startsWith(":app:") -> {
                !to.startsWith(":agent:")
            }

            else -> {
                true
            }
        }

    private fun findCycles(modules: List<ModuleInfo>): List<String> {
        val graph = modules.associate { it.path to it.projectDependencies }
        val visiting = mutableSetOf<String>()
        val done = mutableSetOf<String>()
        val found = mutableListOf<String>()

        fun visit(
            node: String,
            trail: List<String>,
        ) {
            if (node in done) return
            if (!visiting.add(node)) {
                found += "dependency cycle: ${(trail + node).joinToString(" -> ")}"
                return
            }
            graph[node].orEmpty().forEach { visit(it, trail + node) }
            visiting.remove(node)
            done.add(node)
        }
        graph.keys.forEach { visit(it, emptyList()) }
        return found
    }
}
