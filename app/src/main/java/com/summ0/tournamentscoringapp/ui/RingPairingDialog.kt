package com.summ0.tournamentscoringapp

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.summ0.tournamentscoringapp.engine.*
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
internal fun RingPairingDialog(
    expectedRingId: String?,
    expectedEvent: String?,
    onDismiss: () -> Unit,
    onAuthorized: (String, String) -> Unit
) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("Scan the daily QR code for your ring.") }
    var pending by remember { mutableStateOf<RingPairingCode?>(null) }
    var replacement by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    fun pair(code: RingPairingCode, replace: Boolean) {
        scope.launch {
            busy = true
            replacement = false
            status = "Authorizing Ring ${code.ringId}..."
            try {
                val body = JSONObject().put("event", code.event).put("ringId", code.ringId)
                    .put("day", code.day).put("token", code.token)
                    .put("deviceId", TabletAuthorization.deviceId(context))
                    .put("tabletLabel", android.os.Build.MODEL.take(100).ifBlank { "Tablet" })
                    .put("replace", replace)
                val response = fetchJsonObject("${code.server}/api/tablet/pair", "POST", body, authorize = false)
                val result = response.root
                if (result != null) {
                    TabletAuthorization.save(context, code.server, result)
                    onAuthorized(code.server, result.getString("ringId"))
                } else {
                    status = response.errorMessage ?: "Unable to authorize tablet."
                    replacement = response.httpStatus == 409
                }
            } catch (error: Exception) {
                status = error.message ?: "Unable to save tablet authorization."
            } finally { busy = false }
        }
    }

    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents == null) {
            status = "Scan cancelled. Allow camera access to scan a ring QR code."
        } else {
            try {
                val code = parseRingPairingCode(result.contents)
                require(expectedRingId.isNullOrBlank() || code.ringId == expectedRingId) {
                    "A division is loaded for another ring. Scan that ring's code to preserve the current work."
                }
                require(expectedEvent.isNullOrBlank() || code.event == expectedEvent) {
                    "A division is loaded for another tournament. Finish that division before switching tournaments."
                }
                pending = code
                pair(code, false)
            } catch (error: Exception) {
                status = error.message ?: "Invalid ring QR code."
            }
        }
    }
    fun scan() {
        replacement = false
        scanner.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .setPrompt("Scan today's ring QR code").setBeepEnabled(false).setOrientationLocked(false))
    }
    LaunchedEffect(Unit) { scan() }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Daily ring authorization") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { Text(status) } },
        confirmButton = {
            if (replacement) {
                TextButton(enabled = !busy, onClick = { pending?.let { pair(it, true) } }) {
                    Text("Replace previous tablet")
                }
            } else {
                TextButton(enabled = !busy, onClick = { scan() }) { Text("Scan QR code") }
            }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } }
    )
}
