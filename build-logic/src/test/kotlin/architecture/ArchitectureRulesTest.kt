package architecture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArchitectureRulesTest {
    private fun module(
        path: String,
        project: Set<String> = emptySet(),
        external: Set<String> = emptySet(),
    ) = ModuleInfo(path, project, external)

    @Test
    fun `valid graph has no violations`() {
        val modules =
            listOf(
                module(":core:model"),
                module(":ui:design-api", setOf(":core:model")),
                module(":ui:design-m3", setOf(":ui:design-api"), setOf("androidx.compose.material3:material3")),
                module(":feature:home", setOf(":ui:design-api", ":core:model")),
                module(":app:android", setOf(":feature:home", ":ui:design-m3")),
            )
        assertEquals(emptyList(), ArchitectureRules.checkDependencies(modules))
    }

    @Test
    fun `feature must not depend on a concrete design module`() {
        val violations =
            ArchitectureRules.checkDependencies(
                listOf(module(":feature:home", setOf(":ui:design-m3"))),
            )
        assertTrue(violations.single().contains(":feature:home must not depend on :ui:design-m3"))
    }

    @Test
    fun `feature must not depend on material3 or miuix artifacts`() {
        val violations =
            ArchitectureRules.checkDependencies(
                listOf(
                    module(
                        ":feature:home",
                        external = setOf("androidx.compose.material3:material3", "top.yukonga.miuix.kmp:miuix-ui"),
                    ),
                ),
            )
        assertEquals(2, violations.size)
    }

    @Test
    fun `features must not depend on each other`() {
        val violations =
            ArchitectureRules.checkDependencies(
                listOf(module(":feature:deck", setOf(":feature:home"))),
            )
        assertEquals(1, violations.size)
    }

    @Test
    fun `agent core must not depend on a platform module`() {
        val violations =
            ArchitectureRules.checkDependencies(
                listOf(module(":agent:core", setOf(":agent:platform-windows"))),
            )
        assertEquals(1, violations.size)
    }

    @Test
    fun `core must not depend on non-core modules`() {
        val violations =
            ArchitectureRules.checkDependencies(
                listOf(module(":core:model", setOf(":ui:design-api"))),
            )
        assertEquals(1, violations.size)
    }

    @Test
    fun `cycles are detected`() {
        val violations =
            ArchitectureRules.checkDependencies(
                listOf(
                    module(":core:model", setOf(":core:protocol")),
                    module(":core:protocol", setOf(":core:model")),
                ),
            )
        assertTrue(violations.any { it.startsWith("dependency cycle") })
    }

    @Test
    fun `forbidden imports are reported outside design modules`() {
        val imports =
            listOf(
                "import androidx.compose.material3.Text",
                "import top.yukonga.miuix.kmp.basic.Text",
                "import androidx.compose.ui.Modifier",
            )
        assertEquals(2, ArchitectureRules.checkImports(":feature:home", imports).size)
        assertEquals(emptyList(), ArchitectureRules.checkImports(":ui:design-m3", imports))
        assertEquals(emptyList(), ArchitectureRules.checkImports(":app:android", imports))
    }
}
