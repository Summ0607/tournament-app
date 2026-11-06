package com.summ0.tournamentscoringapp.engine

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID

data class RingPairingCode(val server: String, val event: String, val ringId: String,
    val day: String, val token: String)

fun parseRingPairingCode(text: String): RingPairingCode {
    require(text.length <= 2048) { "This is not a tournament ring QR code." }
    val json = JSONObject(text)
    require(json.optString("type") == "tournament-ring" && json.optInt("version") == 1) {
        "Scan a daily ring QR code displayed by Tournament Management."
    }
    val server = json.getString("server")
    val uri = URI(server)
    require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.rawUserInfo == null &&
        uri.rawQuery == null && uri.rawFragment == null && uri.rawPath.orEmpty() in listOf("", "/")) {
        "The ring QR code must use an HTTPS server address."
    }
    val event = json.getString("event")
    val ring = json.getString("ringId")
    val day = json.getString("day")
    val token = json.getString("token")
    require(event.isNotBlank() && ring.matches(Regex("[a-zA-Z0-9_-]+")) &&
        day.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) && token.matches(Regex("[a-f0-9]{64}"))) {
        "The ring QR code is incomplete."
    }
    return RingPairingCode(server.trimEnd('/'), event, ring, day, token)
}

object TabletAuthorization {
    private const val PREFS = "tablet_authorization"
    private data class Credential(val server: String, val ring: String, val token: String)
    @Volatile private var credential: Credential? = null

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val server = prefs.getString("server", "").orEmpty()
        val token = prefs.getString("credential", "").orEmpty()
        credential = if (server.isNotBlank() && token.matches(Regex("[a-f0-9]{64}")))
            Credential(server, prefs.getString("ring", "").orEmpty(), token) else null
    }

    fun server(): String? = credential?.server
    fun ringId(): String? = credential?.ring

    fun deviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString("device", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("device", it).apply()
        }
    }

    fun save(context: Context, server: String, response: JSONObject) {
        val token = response.getString("credential")
        val ring = response.getString("ringId")
        require(token.matches(Regex("[a-f0-9]{64}")) && ring.isNotBlank()) { "Invalid tablet authorization response." }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("server", server).putString("ring", ring).putString("credential", token).apply()
        credential = Credential(server, ring, token)
    }

    fun applyTo(connection: HttpURLConnection) {
        connection.instanceFollowRedirects = false
        val saved = credential ?: return
        val target = connection.url.toURI()
        val origin = URI(saved.server)
        if (target.scheme == "https" && target.host.equals(origin.host, ignoreCase = true) &&
            target.port == origin.port && target.rawUserInfo == null) {
            connection.setRequestProperty("Authorization", "Bearer ${saved.token}")
        }
    }
}
