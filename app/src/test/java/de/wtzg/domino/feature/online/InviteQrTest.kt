package de.wtzg.domino.feature.online

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InviteQrTest {
    @Test
    fun readsTheCodeFromInviteLinks() {
        assertEquals("ABC234", inviteCodeFrom("https://dominoes.example.com/join/ABC234"))
        assertEquals("ABC234", inviteCodeFrom("http://192.168.1.20:8080/join/abc234/"))
        assertEquals("ABC234", inviteCodeFrom("dominoes://join/ABC234"))
        assertEquals("ABC234", inviteCodeFrom("https://dominoes.example.com/join/ABC234?ref=qr"))
    }

    @Test
    fun acceptsABareCode() {
        assertEquals("XYZ789", inviteCodeFrom("  xyz789 \n"))
    }

    @Test
    fun rejectsAnythingElse() {
        assertNull(inviteCodeFrom("https://example.com/menu"))
        assertNull(inviteCodeFrom("https://dominoes.example.com/join/TOOLONGCODE"))
        assertNull(inviteCodeFrom("ABC"))
        assertNull(inviteCodeFrom("WIFI:S:home;T:WPA;P:secret;;"))
        assertNull(inviteCodeFrom(""))
    }

    @Test
    fun drawnQrCodeScansBackToTheLink() {
        val link = "https://dominoes.example.com/join/ABC234"
        val modules = qrModules(link)
        // Square, and a real QR size (21, 25, 29, ... modules).
        assertEquals(modules.size, modules[0].size)
        assertEquals(0, (modules.size - 17) % 4)

        // Render it like the screen does, with a quiet zone, and read it back with ZXing.
        val scale = 4
        val quiet = 4
        val side = (modules.size + quiet * 2) * scale
        val pixels = IntArray(side * side) { 0xFFFFFFFF.toInt() }
        modules.forEachIndexed { y, row ->
            row.forEachIndexed { x, dark ->
                if (dark) {
                    for (dy in 0 until scale) for (dx in 0 until scale) {
                        pixels[((y + quiet) * scale + dy) * side + (x + quiet) * scale + dx] = 0xFF000000.toInt()
                    }
                }
            }
        }
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(side, side, pixels)))
        assertEquals(link, QRCodeReader().decode(bitmap).text)
        assertEquals("ABC234", inviteCodeFrom(QRCodeReader().decode(bitmap).text))
    }
}
