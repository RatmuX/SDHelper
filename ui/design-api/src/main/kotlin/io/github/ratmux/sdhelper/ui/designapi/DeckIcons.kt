package io.github.ratmux.sdhelper.ui.designapi

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource

/**
 * The built-in icon set, addressed by the key an action carries (for example `mic_off`). The icons are vector
 * drawables, so the set does not depend on any design library; every design language draws them with its own colors.
 */
object DeckIconSet {
    /** Keys of the icons the set knows. Anything else resolves to a generic icon. */
    val keys: Set<String> =
        setOf(
            "volume_up",
            "volume_down",
            "volume_off",
            "mic",
            "mic_off",
            "play_pause",
            "skip_next",
            "skip_previous",
            "apps",
            "touchpad",
            "keyboard",
            "computer",
            "general",
            "palette",
            "theme",
            "light",
            "dark",
            "settings",
            "list",
            "add",
            "edit",
            "check",
            "pages",
            "grid",
        )

    /**
     * The drawable for [key]. Toggle actions have two faces: while [toggled] is true `mic_off` shows the crossed
     * microphone and otherwise the open one; `volume_off` and `play_pause` work the same way.
     */
    @DrawableRes
    fun resolve(
        key: String?,
        toggled: Boolean?,
    ): Int =
        when (key) {
            "volume_up" -> R.drawable.ic_deck_volume_up
            "volume_down" -> R.drawable.ic_deck_volume_down
            "volume_off" -> if (toggled == true) R.drawable.ic_deck_volume_off else R.drawable.ic_deck_volume_up
            "mic", "mic_off" -> if (toggled == true) R.drawable.ic_deck_mic_off else R.drawable.ic_deck_mic
            "play_pause" -> if (toggled == true) R.drawable.ic_deck_pause else R.drawable.ic_deck_play_arrow
            "skip_next" -> R.drawable.ic_deck_skip_next
            "skip_previous" -> R.drawable.ic_deck_skip_previous
            "apps" -> R.drawable.ic_deck_apps
            "touchpad" -> R.drawable.ic_deck_touchpad
            "keyboard" -> R.drawable.ic_deck_keyboard
            "computer" -> R.drawable.ic_deck_computer
            "general" -> R.drawable.ic_deck_general
            "palette" -> R.drawable.ic_deck_palette
            "theme" -> R.drawable.ic_deck_theme
            "light" -> R.drawable.ic_deck_light
            "dark" -> R.drawable.ic_deck_dark
            "settings" -> R.drawable.ic_deck_settings
            "list" -> R.drawable.ic_deck_list
            "add" -> R.drawable.ic_deck_add
            "edit" -> R.drawable.ic_deck_edit
            "check" -> R.drawable.ic_deck_check
            "pages" -> R.drawable.ic_deck_pages
            "grid" -> R.drawable.ic_deck_grid
            else -> R.drawable.ic_deck_apps
        }
}

/** Draws the icon for [key] in [tint]. Decorative: the button around it carries the label. */
@Composable
fun DeckIcon(
    key: String?,
    toggled: Boolean?,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(DeckIconSet.resolve(key, toggled)),
        contentDescription = null,
        modifier = modifier,
        colorFilter = ColorFilter.tint(tint),
    )
}
