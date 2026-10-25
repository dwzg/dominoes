package de.wtzg.domino.feature.online

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import de.wtzg.domino.data.local.localInviteFrom
import de.wtzg.domino.protocol.Limits

/** An invite link: `https://server/join/CODE` or `dominoes://join/CODE`. */
private val INVITE_LINK = Regex("""join/([A-Za-z0-9]+)/?(?:[?#].*)?$""")
private val BARE_CODE = Regex("""^[A-Za-z0-9]+$""")

/**
 * The game code in something scanned: an invite link, or just the code itself.
 * Null when it isn't a dominoes invite.
 */
fun inviteCodeFrom(scanned: String): String? {
    val text = scanned.trim()
    val code = INVITE_LINK.find(text)?.groupValues?.get(1) ?: text.takeIf { BARE_CODE.matches(it) }
    return code?.uppercase()?.takeIf { it.length == Limits.CODE_LENGTH }
}

/** The dark and light squares of a QR code for [text], row by row, without the quiet zone. */
fun qrModules(text: String): List<BooleanArray> {
    val matrix = Encoder.encode(text, ErrorCorrectionLevel.M, mapOf(EncodeHintType.CHARACTER_SET to "UTF-8")).matrix
    return List(matrix.height) { y -> BooleanArray(matrix.width) { x -> matrix.get(x, y).toInt() == 1 } }
}

/**
 * A QR code for [text]. Always dark on white with a white border, whatever the theme,
 * because that's what camera apps read most reliably.
 */
@Composable
fun QrCode(text: String, description: String, modifier: Modifier = Modifier, size: Dp = 184.dp) {
    val modules = remember(text) { qrModules(text) }
    Canvas(
        modifier
            .background(Color.White, RoundedCornerShape(12.dp))
            .padding(12.dp)
            .size(size)
            .semantics { contentDescription = description },
    ) {
        val cell = this.size.width / modules.size
        modules.forEachIndexed { y, row ->
            row.forEachIndexed { x, dark ->
                // A hair of overlap so no seams show between neighbouring squares.
                if (dark) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
            }
        }
    }
}

/**
 * Opens the Play services QR scanner (no camera permission needed). Calls [onCode] with the
 * game code from a scanned invite, or [onNotAnInvite] if the QR code holds something else.
 * Nothing happens if the scan is cancelled.
 */
fun scanInviteQr(
    context: Context,
    onCode: (String) -> Unit,
    onNotAnInvite: () -> Unit,
    onUnavailable: () -> Unit,
    /** The code is for a game hosted on a phone on the same Wi-Fi, not on the online server. */
    onLocalGame: () -> Unit = onNotAnInvite,
) = scanQr(
    context,
    onText = { text ->
        when {
            localInviteFrom(text) != null -> onLocalGame()
            else -> inviteCodeFrom(text)?.let(onCode) ?: onNotAnInvite()
        }
    },
    onUnavailable = onUnavailable,
)

/** Opens the Play services QR scanner and hands over whatever the code says. Cancelling does nothing. */
fun scanQr(context: Context, onText: (String) -> Unit, onUnavailable: () -> Unit) {
    val options = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .build()
    GmsBarcodeScanning.getClient(context, options).startScan()
        .addOnSuccessListener { barcode -> onText(barcode.rawValue.orEmpty()) }
        .addOnFailureListener { onUnavailable() }
}
