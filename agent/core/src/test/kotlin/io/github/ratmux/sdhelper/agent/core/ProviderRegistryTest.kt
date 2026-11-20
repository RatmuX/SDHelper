package io.github.ratmux.sdhelper.agent.core

import io.github.ratmux.sdhelper.core.pluginapi.ActionDescriptor
import io.github.ratmux.sdhelper.core.pluginapi.ActionState
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ProviderRegistryTest {
    @Test
    fun `actions are published as provider slash action`() {
        val registry = ProviderRegistry(listOf(FakeProvider("a"), FakeProvider("b")))

        assertEquals(setOf("a/go", "a/stop", "b/go", "b/stop"), registry.catalog().map { it.id }.toSet())
        assertEquals("go", assertNotNull(registry.find("b/go")).localId)
        assertNull(registry.find("go"))
        assertNull(registry.find("c/go"))
    }

    @Test
    fun `duplicate provider ids or action ids are programming errors`() {
        assertFailsWith<IllegalArgumentException> { ProviderRegistry(listOf(FakeProvider("a"), FakeProvider("a"))) }
        val twice = listOf(ActionDescriptor("x", mapOf("en" to "X")), ActionDescriptor("x", mapOf("en" to "X")))
        assertFailsWith<IllegalArgumentException> { ProviderRegistry(listOf(FakeProvider("a", twice))) }
    }

    @Test
    fun `states of several providers are merged under qualified ids`() =
        runTest(UnconfinedTestDispatcher()) {
            val a = FakeProvider("a")
            val b = FakeProvider("b")
            val states = ProviderRegistry(listOf(a, b)).states(backgroundScope)

            a.state.value = mapOf("go" to ActionState(toggled = true))
            b.state.value = mapOf("stop" to ActionState(available = false), "unknown" to ActionState())

            assertEquals(setOf("a/go", "b/stop"), states.value.keys)
            assertEquals(true, states.value["a/go"]?.toggled)
        }

    @Test
    fun `an empty registry is valid`() =
        runTest(UnconfinedTestDispatcher()) {
            val registry = ProviderRegistry(emptyList())
            assertEquals(emptyList(), registry.catalog())
            assertEquals(emptyMap(), registry.states(backgroundScope).value)
        }
}
