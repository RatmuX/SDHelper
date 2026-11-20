package io.github.ratmux.sdhelper.agent.desktop.ui

import io.github.ratmux.sdhelper.agent.core.PairedDevice
import io.github.ratmux.sdhelper.agent.core.PairingApproval
import io.github.ratmux.sdhelper.agent.core.PairingApprovalRequest
import io.github.ratmux.sdhelper.agent.core.platform.PlatformServices
import io.github.ratmux.sdhelper.agent.desktop.AgentRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.suspendCancellableCoroutine
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.DefaultListModel
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.coroutines.resume

/**
 * Temporary desktop interface built on Swing and AWT (docs/decisions/ADR-0009): pairing QR code and PIN, the list
 * of paired phones, the approval dialog and a tray icon where the platform has one. It sits behind [AgentRuntime],
 * so it can be replaced without touching the agent logic.
 */
class SwingUi private constructor(
    private val scope: CoroutineScope,
) : PairingApproval {
    private lateinit var runtime: AgentRuntime
    private val frame = JFrame(Messages["window.title"])
    private val qrLabel = JLabel().apply { horizontalAlignment = SwingConstants.CENTER }
    private val pinLabel = JLabel().apply { font = font.deriveFont(Font.BOLD, PIN_FONT_SIZE) }
    private val expiryLabel = JLabel()
    private val addresses = JComboBox<String>()
    private val devices = DefaultListModel<PairedDevice>()

    override suspend fun approve(request: PairingApprovalRequest): Boolean =
        suspendCancellableCoroutine { continuation ->
            SwingUtilities.invokeLater {
                frame.isVisible = true
                val answer =
                    JOptionPane.showConfirmDialog(
                        frame,
                        Messages["approve.message", request.deviceName, request.shortCode],
                        Messages["approve.title"],
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE,
                    )
                continuation.resume(answer == JOptionPane.YES_OPTION)
            }
        }

    private fun show() {
        addresses.removeAllItems()
        runtime.addresses().ifEmpty { listOf("127.0.0.1") }.forEach(addresses::addItem)
        addresses.addActionListener { refreshOffer(keepOffer = true) }

        val newCode = JButton(Messages["pairing.new"]).apply { addActionListener { refreshOffer(keepOffer = false) } }
        val left =
            JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                border = BorderFactory.createEmptyBorder(PAD, PAD, PAD, PAD)
                add(JLabel(Messages["pairing.qr.hint"]))
                add(qrLabel)
                add(JLabel(Messages["pairing.pin.hint"]))
                add(row(JLabel(Messages["pairing.address"] + ":"), addresses))
                add(row(JLabel(Messages["pairing.pin"] + ":"), pinLabel))
                add(expiryLabel)
                add(newCode)
            }

        val list = JList(devices).apply { cellRenderer = DeviceRenderer() }
        val revoke =
            JButton(Messages["devices.revoke"]).apply {
                addActionListener { list.selectedValue?.let(::confirmRevoke) }
            }
        val right =
            JPanel(BorderLayout(0, GAP)).apply {
                border = BorderFactory.createEmptyBorder(PAD, 0, PAD, PAD)
                preferredSize = Dimension(RIGHT_WIDTH, 0)
                add(JLabel(Messages["devices.title"]), BorderLayout.NORTH)
                add(JScrollPane(list), BorderLayout.CENTER)
                add(revoke, BorderLayout.SOUTH)
            }

        frame.contentPane.add(left, BorderLayout.CENTER)
        frame.contentPane.add(right, BorderLayout.EAST)
        frame.pack()
        frame.setLocationRelativeTo(null)

        // Pairing is possible only while the window shows the offer: hiding the window withdraws it.
        frame.addComponentListener(
            object : java.awt.event.ComponentAdapter() {
                override fun componentShown(e: java.awt.event.ComponentEvent?) = refreshOffer(keepOffer = false)

                override fun componentHidden(e: java.awt.event.ComponentEvent?) = runtime.pairing.cancelOffer()
            },
        )
        installTray()
        runtime.registry.devices
            .onEach { current ->
                SwingUtilities.invokeLater {
                    devices.clear()
                    current.forEach(devices::addElement)
                }
            }.launchIn(scope)
        Timer(TICK_MILLIS) { tick() }.start()
        frame.isVisible = true
    }

    private fun row(vararg parts: java.awt.Component) =
        JPanel(FlowLayout(FlowLayout.LEFT)).apply { parts.forEach(::add) }

    private fun confirmRevoke(device: PairedDevice) {
        val answer =
            JOptionPane.showConfirmDialog(
                frame,
                Messages["devices.revoke.confirm", device.name],
                Messages["devices.revoke"],
                JOptionPane.YES_NO_OPTION,
            )
        if (answer == JOptionPane.YES_OPTION) runtime.registry.revoke(device.deviceId)
    }

    /** Shows the active offer; creates a new one when [keepOffer] is false or none is valid. */
    private fun refreshOffer(keepOffer: Boolean) {
        val host = addresses.selectedItem as? String ?: return
        var offer = runtime.pairing.offer.value
        if (!keepOffer || offer == null || System.currentTimeMillis() > offer.expiresAtMillis) {
            offer = runtime.pairing.newOffer()
        }
        qrLabel.icon = ImageIcon(Qr.image(runtime.pairingUri(host, offer).toUriString(), QR_SCALE))
        pinLabel.text = offer.pin
        tick()
        frame.pack()
    }

    private fun tick() {
        if (!frame.isVisible) return
        val offer = runtime.pairing.offer.value
        if (offer == null || System.currentTimeMillis() > offer.expiresAtMillis) {
            refreshOffer(keepOffer = false)
            return
        }
        val seconds = ((offer.expiresAtMillis - System.currentTimeMillis()) / MILLIS).coerceAtLeast(0)
        expiryLabel.text =
            Messages["pairing.expires", "%d:%02d".format(seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE)]
    }

    private fun installTray() {
        if (!SystemTray.isSupported()) {
            frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
            return
        }
        frame.defaultCloseOperation = JFrame.HIDE_ON_CLOSE
        val menu =
            java.awt.PopupMenu().apply {
                add(java.awt.MenuItem(Messages["tray.open"]).apply { addActionListener { frame.isVisible = true } })
                add(java.awt.MenuItem(Messages["tray.quit"]).apply { addActionListener { System.exit(0) } })
            }
        val icon =
            TrayIcon(trayImage(), Messages["tray.tooltip"], menu).apply {
                isImageAutoSize = true
                addActionListener { frame.isVisible = true }
            }
        runCatching { SystemTray.getSystemTray().add(icon) }
            .onFailure { frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE }
    }

    private fun trayImage(): BufferedImage =
        BufferedImage(TRAY_SIZE, TRAY_SIZE, BufferedImage.TYPE_INT_ARGB).also { image ->
            val g = image.createGraphics()
            g.color = Color(TRAY_COLOR)
            g.fillRoundRect(0, 0, TRAY_SIZE, TRAY_SIZE, TRAY_ARC, TRAY_ARC)
            g.dispose()
        }

    private class DeviceRenderer : javax.swing.DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ) = super.getListCellRendererComponent(list, (value as? PairedDevice)?.name, index, isSelected, cellHasFocus)
    }

    companion object {
        private const val PAD = 16
        private const val GAP = 8
        private const val RIGHT_WIDTH = 240
        private const val QR_SCALE = 6
        private const val PIN_FONT_SIZE = 28f
        private const val TICK_MILLIS = 1000
        private const val MILLIS = 1000L
        private const val SECONDS_PER_MINUTE = 60
        private const val TRAY_SIZE = 32
        private const val TRAY_ARC = 8
        private const val TRAY_COLOR = 0x3F51B5

        fun launch(
            port: Int,
            platform: PlatformServices,
            configDirectory: Path,
        ) {
            val ui = SwingUi(CoroutineScope(SupervisorJob() + Dispatchers.Default))
            ui.runtime =
                AgentRuntime(approval = ui, platform = platform, configDirectory = configDirectory, port = port)
            ui.runtime.start()
            Runtime.getRuntime().addShutdownHook(Thread { ui.runtime.stop() })
            SwingUtilities.invokeLater { ui.show() }
        }
    }
}
