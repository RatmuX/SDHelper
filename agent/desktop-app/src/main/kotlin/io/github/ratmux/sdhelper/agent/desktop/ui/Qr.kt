package io.github.ratmux.sdhelper.agent.desktop.ui

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.awt.image.BufferedImage

object Qr {
    private const val QUIET_ZONE = 2

    fun matrix(text: String): BitMatrix =
        QRCodeWriter().encode(
            text,
            BarcodeFormat.QR_CODE,
            0,
            0,
            mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to QUIET_ZONE),
        )

    /** Black-and-white image with each QR module scaled to [scale] pixels. */
    fun image(
        text: String,
        scale: Int,
    ): BufferedImage {
        val bits = matrix(text)
        val image = BufferedImage(bits.width * scale, bits.height * scale, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                image.setRGB(x, y, if (bits.get(x / scale, y / scale)) BLACK else WHITE)
            }
        }
        return image
    }

    /** Terminal rendering with half-block characters; two QR rows per text line. */
    fun ascii(text: String): String {
        val bits = matrix(text)
        return buildString {
            for (y in 0 until bits.height step 2) {
                for (x in 0 until bits.width) {
                    val top = bits.get(x, y)
                    val bottom = y + 1 < bits.height && bits.get(x, y + 1)
                    append(
                        when {
                            top && bottom -> '█'
                            top -> '▀'
                            bottom -> '▄'
                            else -> ' '
                        },
                    )
                }
                append('\n')
            }
        }
    }

    private const val BLACK = 0x000000
    private const val WHITE = 0xFFFFFF
}
