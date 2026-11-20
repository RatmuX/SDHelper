package io.github.ratmux.sdhelper.core.model

/** Ids of the actions that ship with the agent. A test checks that every one of them really exists. */
object BuiltinActions {
    const val VOLUME_UP = "builtin.system-audio/volume-up"
    const val VOLUME_DOWN = "builtin.system-audio/volume-down"
    const val MUTE_OUTPUT = "builtin.system-audio/mute-output"
    const val MUTE_MICROPHONE = "builtin.system-audio/mute-microphone"
    const val PLAY_PAUSE = "builtin.media/play-pause"
    const val NEXT = "builtin.media/next"
    const val PREVIOUS = "builtin.media/previous"
}

/** A ready-made layout the person can add from the settings. Texts are `language code -> text`, falling back to `en`. */
class LayoutPreset(
    val id: String,
    val title: Map<String, String>,
    val description: Map<String, String>,
    private val pages: List<PresetPage>,
) {
    class PresetPage(
        val title: Map<String, String>,
        val actions: List<String>,
    )

    /** The action ids this preset places, in order; used by tests and by the editor's "missing action" hint. */
    val actionIds: List<String> get() = pages.flatMap { it.actions }

    fun title(language: String): String = title[language] ?: title.getValue("en")

    fun description(language: String): String = description[language] ?: description.getValue("en")

    /** A new profile with fresh ids, named and labelled in [language]. Buttons fill each page from the first cell. */
    fun create(
        language: String,
        grid: GridSize = GridSize.DEFAULT,
        newId: () -> String = LayoutIds::random,
    ): DeckProfile =
        DeckProfile(
            id = newId(),
            name = title(language),
            grid = grid,
            pages =
                pages.map { page ->
                    val slots = LayoutOps.emptySlots(grid).toMutableList()
                    page.actions.take(grid.capacity).forEachIndexed { i, id -> slots[i] = DeckSlot.Action(id) }
                    DeckPage(newId(), page.title[language] ?: page.title.getValue("en"), slots)
                },
        )
}

/**
 * The starter layouts. They use only the actions that exist today; when Discord, OBS and others arrive the presets
 * grow with them. The first page is meant for application controls, the second for the system (the owner's design).
 */
object LayoutPresets {
    val EVERYDAY =
        LayoutPreset(
            id = "everyday",
            title = mapOf("en" to "Everyday", "tr" to "Günlük"),
            description =
                mapOf(
                    "en" to "Media controls on the first page, volume and microphone on the second.",
                    "tr" to "İlk sayfada medya kontrolleri, ikinci sayfada ses ve mikrofon.",
                ),
            pages =
                listOf(
                    LayoutPreset.PresetPage(
                        mapOf("en" to "Apps and media", "tr" to "Uygulamalar ve medya"),
                        listOf(BuiltinActions.PREVIOUS, BuiltinActions.PLAY_PAUSE, BuiltinActions.NEXT),
                    ),
                    LayoutPreset.PresetPage(
                        mapOf("en" to "System", "tr" to "Sistem"),
                        listOf(
                            BuiltinActions.VOLUME_DOWN,
                            BuiltinActions.VOLUME_UP,
                            BuiltinActions.MUTE_OUTPUT,
                            BuiltinActions.MUTE_MICROPHONE,
                        ),
                    ),
                ),
        )

    val STREAMING =
        LayoutPreset(
            id = "streaming",
            title = mapOf("en" to "Streaming", "tr" to "Yayın"),
            description =
                mapOf(
                    "en" to
                        "Microphone and speaker mute first, so you can cut sound in a second; audio and music on page two.",
                    "tr" to
                        "Mikrofon ve hoparlör sustur en başta, sesi bir saniyede kesebilesin; ses ve müzik ikinci sayfada.",
                ),
            pages =
                listOf(
                    LayoutPreset.PresetPage(
                        mapOf("en" to "Live", "tr" to "Canlı"),
                        listOf(
                            BuiltinActions.MUTE_MICROPHONE,
                            BuiltinActions.MUTE_OUTPUT,
                            BuiltinActions.PLAY_PAUSE,
                            BuiltinActions.NEXT,
                        ),
                    ),
                    LayoutPreset.PresetPage(
                        mapOf("en" to "Audio", "tr" to "Ses"),
                        listOf(BuiltinActions.VOLUME_DOWN, BuiltinActions.VOLUME_UP, BuiltinActions.PREVIOUS),
                    ),
                ),
        )

    val GAMING =
        LayoutPreset(
            id = "gaming",
            title = mapOf("en" to "Gaming", "tr" to "Oyun"),
            description =
                mapOf(
                    "en" to "Volume and mutes in reach on the first page, the music player on the second.",
                    "tr" to "İlk sayfada ses ve sustur tuşları, ikinci sayfada müzik oynatıcı.",
                ),
            pages =
                listOf(
                    LayoutPreset.PresetPage(
                        mapOf("en" to "Game", "tr" to "Oyun"),
                        listOf(
                            BuiltinActions.VOLUME_DOWN,
                            BuiltinActions.VOLUME_UP,
                            BuiltinActions.MUTE_OUTPUT,
                            BuiltinActions.MUTE_MICROPHONE,
                        ),
                    ),
                    LayoutPreset.PresetPage(
                        mapOf("en" to "Music", "tr" to "Müzik"),
                        listOf(BuiltinActions.PREVIOUS, BuiltinActions.PLAY_PAUSE, BuiltinActions.NEXT),
                    ),
                ),
        )

    val all: List<LayoutPreset> = listOf(EVERYDAY, STREAMING, GAMING)

    fun byId(id: String): LayoutPreset? = all.firstOrNull { it.id == id }
}
