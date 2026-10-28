package pl.dakil.appanalyser.ui.components.filesystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlin.math.floor
import pl.dakil.appanalyser.R

/** One pixel per module, quiet zone included — the caller scales it to whatever size it draws at. */
private fun encodeQr(text: String): BitMatrix = QRCodeWriter().encode(
    text,
    BarcodeFormat.QR_CODE,
    0,
    0,
    mapOf(
        EncodeHintType.MARGIN to QUIET_ZONE_MODULES,
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
    ),
)

private const val QUIET_ZONE_MODULES = 4

/**
 * [text] as a scannable QR code. Deliberately fixed black-on-white rather than themed: many
 * scanners cannot read an inverted (light-on-dark) code, which is what the dark scheme would give.
 */
@Composable
fun QrCode(text: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) { encodeQr(text) }
    val description = stringResource(R.string.share_qr_description)
    Canvas(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .semantics {
                contentDescription = description
                role = Role.Image
            }
    ) {
        drawRect(Color.White)
        // Whole pixels per module keep the edges crisp; the leftover is split around the code.
        val module = floor(minOf(size.width, size.height) / matrix.width).coerceAtLeast(1f)
        val origin = Offset(
            (size.width - module * matrix.width) / 2,
            (size.height - module * matrix.height) / 2,
        )
        val cell = Size(module, module)
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix[x, y]) {
                    drawRect(Color.Black, Offset(origin.x + x * module, origin.y + y * module), cell)
                }
            }
        }
    }
}
