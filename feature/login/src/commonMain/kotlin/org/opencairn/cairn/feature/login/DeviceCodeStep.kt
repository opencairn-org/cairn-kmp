package org.opencairn.cairn.feature.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import org.jetbrains.compose.resources.stringResource
import org.opencairn.cairn.core.domain.model.DeviceSignIn
import org.opencairn.cairn.ui.design.resources.Res
import org.opencairn.cairn.ui.design.resources.device_code_cancel
import org.opencairn.cairn.ui.design.resources.device_code_open
import org.opencairn.cairn.ui.design.resources.device_code_title
import org.opencairn.cairn.ui.design.resources.device_code_waiting

/** The waiting side of a device sign-in: the code to type elsewhere, a QR code of the link, and cancel. */
@Composable
internal fun ColumnScope.DeviceCodeStep(
    device: DeviceSignIn,
    onCancel: () -> Unit,
) {
    Text(
        stringResource(Res.string.device_code_title),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        device.userCode,
        style = MaterialTheme.typography.displayMedium.copy(fontFamily = FontFamily.Monospace, letterSpacing = 4.sp),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    // The QR code carries the verification link with the code filled in; white quiet zone for scanners.
    Image(
        painter = rememberQrCodePainter(device.verificationUriComplete),
        contentDescription = device.verificationUriComplete,
        modifier =
            Modifier
                .size(QR_SIZE)
                .align(Alignment.CenterHorizontally)
                .background(Color.White, RoundedCornerShape(8.dp))
                .padding(12.dp),
    )
    Text(
        stringResource(Res.string.device_code_open, device.verificationUri),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(
        modifier = Modifier.align(Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Text(stringResource(Res.string.device_code_waiting), style = MaterialTheme.typography.bodyMedium)
    }
    TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.CenterHorizontally)) {
        Text(stringResource(Res.string.device_code_cancel))
    }
}

private val QR_SIZE = 200.dp
