package io.github.ratmux.sdhelper.agent.platform.windows

import io.github.ratmux.sdhelper.agent.core.platform.AudioDevice
import io.github.ratmux.sdhelper.agent.core.platform.AudioState
import io.github.ratmux.sdhelper.agent.core.platform.InputEndpoint
import io.github.ratmux.sdhelper.agent.core.platform.MediaCommand
import io.github.ratmux.sdhelper.agent.core.platform.MediaState
import io.github.ratmux.sdhelper.agent.core.platform.MediaStatus
import io.github.ratmux.sdhelper.agent.core.platform.OutputEndpoint
import io.github.ratmux.sdhelper.agent.core.platform.PlatformException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WindowsControllersTest {
    private val audioJson = """{"output":{"volume":42,"muted":false},"input":{"muted":true}}"""
    private val audioState = AudioState(OutputEndpoint(42, false), InputEndpoint(true))

    @Test
    fun `audio state starts unavailable, is read from the helper, then follows notifications`() =
        runTest(UnconfinedTestDispatcher()) {
            val helper = FakeHelperRpc { method, _ -> if (method == "audio.get") json(audioJson) else json("{}") }
            val audio = WindowsAudioController(helper, backgroundScope)

            assertEquals(audioState, audio.state.first())

            helper.emit("audio.changed", json("""{"output":{"volume":80,"muted":true},"input":null}"""))
            assertEquals(AudioState(OutputEndpoint(80, true), null), audio.state.first())
        }

    @Test
    fun `volume and mute are sent in the helper's vocabulary and update the state at once`() =
        runTest(UnconfinedTestDispatcher()) {
            val helper =
                FakeHelperRpc { method, _ ->
                    when (method) {
                        "audio.get" -> json(audioJson)
                        "audio.setVolume" -> json("""{"output":{"volume":47,"muted":false},"input":{"muted":true}}""")
                        else -> json("""{"output":{"volume":47,"muted":false},"input":{"muted":false}}""")
                    }
                }
            val audio = WindowsAudioController(helper, backgroundScope)

            audio.changeVolume(+5)
            assertEquals(
                47,
                audio.state
                    .first()
                    .output
                    ?.volumePercent,
            )
            assertEquals(
                5,
                helper.calls
                    .last { it.first == "audio.setVolume" }
                    .second
                    .int("delta"),
            )

            audio.setMuted(AudioDevice.INPUT, null)
            audio.setMuted(AudioDevice.OUTPUT, true)
            audio.setMuted(AudioDevice.OUTPUT, false)
            val sent =
                helper.calls.filter { it.first == "audio.setMute" }.map { (_, p) ->
                    p["device"].toString() +
                        p["mode"]
                }
            assertEquals(listOf("\"input\"\"toggle\"", "\"output\"\"on\"", "\"output\"\"off\""), sent)
        }

    @Test
    fun `an unavailable device is reported with a readable message`() =
        runTest(UnconfinedTestDispatcher()) {
            val helper =
                FakeHelperRpc { method, _ ->
                    if (method ==
                        "audio.get"
                    ) {
                        json(audioJson)
                    } else {
                        throw FakeRpcError(RpcException.UNAVAILABLE, "no microphone")
                    }
                }
            val audio = WindowsAudioController(helper, backgroundScope)
            val error = assertFailsWith<PlatformException> { audio.setMuted(AudioDevice.INPUT, null) }
            assertEquals("No microphone", error.message)
        }

    @Test
    fun `other helper failures do not leak details`() =
        runTest(UnconfinedTestDispatcher()) {
            val helper =
                FakeHelperRpc { method, _ ->
                    if (method ==
                        "audio.get"
                    ) {
                        json(audioJson)
                    } else {
                        throw FakeRpcError(-32000, "HRESULT 0x8007000E in C:\\Windows\\x")
                    }
                }
            val audio = WindowsAudioController(helper, backgroundScope)
            assertEquals(
                "The system could not do that",
                assertFailsWith<PlatformException> { audio.changeVolume(5) }.message,
            )
        }

    @Test
    fun `a helper that is not running is reported`() =
        runTest(UnconfinedTestDispatcher()) {
            val helper =
                FakeHelperRpc { method, _ ->
                    if (method ==
                        "audio.get"
                    ) {
                        json(audioJson)
                    } else {
                        throw HelperClosedException()
                    }
                }
            val audio = WindowsAudioController(helper, backgroundScope)
            assertEquals(
                "The Windows helper is not running",
                assertFailsWith<PlatformException> { audio.changeVolume(5) }.message,
            )
        }

    @Test
    fun `when the helper dies the state becomes unavailable and recovers by itself`() =
        runTest(UnconfinedTestDispatcher()) {
            var healthy = true
            val helper = FakeHelperRpc { _, _ -> if (healthy) json(audioJson) else throw HelperClosedException() }
            val audio = WindowsAudioController(helper, backgroundScope)
            assertEquals(audioState, audio.state.first())

            healthy = false
            helper.emit(HELPER_CLOSED)
            assertEquals(AudioState(null, null), audio.state.first())

            advanceTimeBy(3_000) // still failing: keeps trying with growing pauses
            healthy = true
            advanceTimeBy(40_000)
            assertEquals(audioState, audio.state.first())
        }

    @Test
    fun `media statuses and commands`() =
        runTest(UnconfinedTestDispatcher()) {
            val helper =
                FakeHelperRpc { method, params ->
                    when (method) {
                        "media.get" -> {
                            json("""{"available":true,"status":"paused","title":"Song","artist":"Band"}""")
                        }

                        else -> {
                            json(
                                """{"available":true,"status":"playing","title":"${params["command"]?.jsonPrimitive?.content}","artist":null}""",
                            )
                        }
                    }
                }
            val media = WindowsMediaController(helper, backgroundScope)

            assertEquals(MediaState(true, MediaStatus.PAUSED, "Song", "Band"), media.state.first())

            media.send(MediaCommand.PLAY_PAUSE)
            media.send(MediaCommand.NEXT)
            media.send(MediaCommand.PREVIOUS)

            val commands = helper.calls.filter { it.first == "media.command" }.map { it.second["command"].toString() }
            assertEquals(listOf("\"playPause\"", "\"next\"", "\"previous\""), commands)
            assertEquals(MediaStatus.PLAYING, media.state.first().status)

            helper.emit("media.changed", json("""{"available":false,"status":"none"}"""))
            assertEquals(MediaState(false, MediaStatus.NONE), media.state.first())
        }

    @Test
    fun `an unknown media status reads as none and a missing player is reported`() =
        runTest(UnconfinedTestDispatcher()) {
            val helper =
                FakeHelperRpc { method, _ ->
                    if (method ==
                        "media.get"
                    ) {
                        json("""{"available":true,"status":"someday"}""")
                    } else {
                        throw FakeRpcError(RpcException.UNAVAILABLE, "no media player is running")
                    }
                }
            val media = WindowsMediaController(helper, backgroundScope)
            assertEquals(MediaStatus.NONE, media.state.first().status)
            assertEquals(
                "No media player is running",
                assertFailsWith<PlatformException> { media.send(MediaCommand.NEXT) }.message,
            )
        }
}
