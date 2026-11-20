package io.github.ratmux.sdhelper.agent.desktop.ui

import java.text.MessageFormat
import java.util.ResourceBundle

/** Localized texts of the agent window (English and Turkish, chosen by the system locale). */
object Messages {
    private val bundle: ResourceBundle = ResourceBundle.getBundle("agent_ui")

    operator fun get(
        key: String,
        vararg args: Any,
    ): String {
        val pattern = bundle.getString(key)
        return if (args.isEmpty()) pattern else MessageFormat.format(pattern, *args)
    }
}
