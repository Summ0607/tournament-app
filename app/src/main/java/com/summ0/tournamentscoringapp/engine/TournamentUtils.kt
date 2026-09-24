package com.summ0.tournamentscoringapp.engine

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.summ0.tournamentscoringapp.BuildConfig
import java.io.File
import java.net.URL
import java.util.Locale

fun isValidIpv4Host(host: String): Boolean {
    val parts = host.split(".")
    if (parts.size != 4) return false
    return parts.all { part ->
        val number = part.toIntOrNull() ?: return@all false
        number in 0..255 && part == number.toString()
    }
}

fun normalizeDnsServerBaseUrl(serverAddressInput: String): String? {
    val trimmedInput = serverAddressInput.trim()
    if (trimmedInput.isEmpty()) return null
    val host = if (trimmedInput.startsWith("http://", ignoreCase = true) || trimmedInput.startsWith("https://", ignoreCase = true)) {
        try {
            URL(trimmedInput).host.trim()
        } catch (_: Exception) {
            ""
        }
    } else {
        trimmedInput
    }
    return if (host.isBlank()) null else "http://$host:${DEFAULT_SERVER_PORT}"
}

fun normalizeIpServerBaseUrl(serverAddressInput: String): String? {
    val trimmedInput = serverAddressInput.trim()
    if (trimmedInput.isEmpty()) return null
    val withProtocol = if (
        trimmedInput.startsWith("http://", ignoreCase = true) ||
        trimmedInput.startsWith("https://", ignoreCase = true)
    ) {
        trimmedInput
    } else {
        "http://$trimmedInput"
    }
    return try {
        val parsed = URL(withProtocol)
        val host = parsed.host.trim()
        if (!isValidIpv4Host(host)) {
            null
        } else {
            "http://$host:${DEFAULT_SERVER_PORT}"
        }
    } catch (_: Exception) {
        null
    }
}

fun buildServerBaseUrl(mode: ServerConnectionMode, serverAddressInput: String): String? = when (mode) {
    ServerConnectionMode.DNS -> normalizeDnsServerBaseUrl(serverAddressInput) ?: DEFAULT_SERVER_BASE_URL
    ServerConnectionMode.IP -> normalizeIpServerBaseUrl(serverAddressInput)
}

fun serverAddressInputFromServerBaseUrl(serverBaseUrl: String): String? {
    return try {
        val parsed = URL(serverBaseUrl)
        val host = parsed.host.trim()
        if (host.isBlank()) null else host
    } catch (_: Exception) {
        null
    }
}

fun deriveServerBaseUrl(urlString: String): String = try {
    val url = URL(urlString)
    val portPart = if (url.port >= 0) ":${url.port}" else ""
    "${url.protocol}://${url.host}$portPart"
} catch (_: Exception) {
    DEFAULT_SERVER_BASE_URL
}

fun deviceLabel(): String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

fun appVersionLabel(): String = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

internal fun screenPhase(screen: CompetitionScreen): String = when (screen) {
    CompetitionScreen.CHECK_IN -> "check-in"
    CompetitionScreen.WEAPONS_SCORING -> "weapons"
    CompetitionScreen.HYUNGS_SCORING -> "hyungs"
    CompetitionScreen.SPARRING_BRACKET -> "sparring"
    CompetitionScreen.OVERALL_AWARDS -> "awards"
}

fun IntRange.displayLabel(): String {
    return if (isEmpty()) "0-0" else "${first}-${last}"
}

fun parseRingGridCoordinates(ringLabel: String): Pair<String, Int>? {
    val match = Regex("^([A-Za-z]+)(\\d+)$").matchEntire(ringLabel.trim()) ?: return null
    val letter = match.groupValues[1].uppercase(Locale.US)
    val number = match.groupValues[2].toIntOrNull() ?: return null
    return letter to number
}

fun centimeters(value: Float): Dp = (value * 160f / 2.54f).dp

fun installApk(context: Context, apkFile: File) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        apkFile
    )
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
