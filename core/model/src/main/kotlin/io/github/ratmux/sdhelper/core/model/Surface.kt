package io.github.ratmux.sdhelper.core.model

/**
 * A top-level area of the app that the home menu can list (control panel, editor, later keyboard/touchpad).
 * See docs/ARCHITECTURE.md, "Surfaces".
 */
data class Surface(
    val id: String,
    val requiredCapabilities: Set<String> = emptySet(),
)

/** Ids of the surfaces that exist in v1. */
object SurfaceIds {
    const val DECK = "deck"
    const val DECK_EDITOR = "deck-editor"
}
