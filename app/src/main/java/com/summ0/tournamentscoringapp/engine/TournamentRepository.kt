package com.summ0.tournamentscoringapp.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

internal const val DEFAULT_SERVER_HOSTNAME = "HomePC-Sum2"
internal const val DEFAULT_SERVER_PORT = 3000
internal const val DEFAULT_SERVER_BASE_URL = "http://" + DEFAULT_SERVER_HOSTNAME + ":" + DEFAULT_SERVER_PORT
internal const val SERVER_CONNECTION_PREFS_NAME = "server_connection_preferences"
internal const val SERVER_CONNECTION_MODE_KEY = "server_connection_mode"
internal const val SERVER_CONNECTION_LAST_DNS_NAME_KEY = "server_connection_last_dns_name"
internal const val SERVER_CONNECTION_LAST_ADDRESS_KEY = "server_connection_last_address"
internal const val SERVER_CONNECTION_LAST_IP_KEY = "server_connection_last_ip"

fun parseServerConnectionMode(modeName: String): ServerConnectionMode = try {
    ServerConnectionMode.valueOf(modeName)
} catch (_: IllegalArgumentException) {
    ServerConnectionMode.DNS
}

fun loadServerConnectionConfig(context: Context): ServerConnectionConfig {
    val preferences = context.getSharedPreferences(SERVER_CONNECTION_PREFS_NAME, Context.MODE_PRIVATE)
    val mode = parseServerConnectionMode(
        preferences.getString(SERVER_CONNECTION_MODE_KEY, ServerConnectionMode.DNS.name).orEmpty()
    )
    val lastDnsName = preferences.getString(SERVER_CONNECTION_LAST_DNS_NAME_KEY, DEFAULT_SERVER_HOSTNAME).orEmpty().trim()
    val lastServerAddress = preferences.getString(SERVER_CONNECTION_LAST_ADDRESS_KEY, null)
        ?: preferences.getString(SERVER_CONNECTION_LAST_IP_KEY, "")
        ?: ""
    return ServerConnectionConfig(
        mode = mode,
        lastDnsName = lastDnsName.ifBlank { DEFAULT_SERVER_HOSTNAME },
        lastServerAddress = lastServerAddress.trim()
    )
}

fun persistServerConnectionConfig(context: Context, config: ServerConnectionConfig) {
    context.getSharedPreferences(SERVER_CONNECTION_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(SERVER_CONNECTION_MODE_KEY, config.mode.name)
        .putString(SERVER_CONNECTION_LAST_DNS_NAME_KEY, config.lastDnsName.ifBlank { DEFAULT_SERVER_HOSTNAME })
        .putString(SERVER_CONNECTION_LAST_ADDRESS_KEY, config.lastServerAddress.trim())
        .putString(SERVER_CONNECTION_LAST_IP_KEY, config.lastServerAddress.trim())
        .apply()
}

suspend fun fetchJsonObject(
    urlString: String,
    method: String = "GET",
    jsonBody: JSONObject? = null
): JsonFetchResult = withContext(Dispatchers.IO) {
    try {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        if (jsonBody != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(jsonBody.toString())
            }
        }
        connection.connect()

        val responseCode = connection.responseCode
        if (responseCode != HttpURLConnection.HTTP_OK) {
            val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            JsonFetchResult(errorMessage = if (errorBody.isNotBlank()) {
                "Server returned HTTP $responseCode: $errorBody"
            } else {
                "Server returned HTTP $responseCode"
            })
        } else {
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            JsonFetchResult(root = JSONObject(body))
        }
    } catch (exception: Exception) {
        JsonFetchResult(errorMessage = exception.message ?: "Unknown network error")
    }
}

suspend fun fetchRemoteGroup(urlString: String): RemoteGroupFetchResult {
    val jsonResult = fetchJsonObject(urlString)
    val root = jsonResult.root ?: return RemoteGroupFetchResult(errorMessage = jsonResult.errorMessage)
    return try {
        RemoteGroupFetchResult(group = parseRemoteGroup(root))
    } catch (exception: Exception) {
        RemoteGroupFetchResult(errorMessage = exception.message ?: "Unable to parse group response")
    }
}

suspend fun fetchRingAssignment(
    urlString: String,
    method: String = "GET",
    jsonBody: JSONObject? = null
): RingAssignmentFetchResult {
    val jsonResult = fetchJsonObject(urlString, method = method, jsonBody = jsonBody)
    val root = jsonResult.root ?: return RingAssignmentFetchResult(errorMessage = jsonResult.errorMessage)
    return try {
        val ringId = root.optString("ringId", "").trim()
        val serverBaseUrl = root.optString("serverBaseUrl", "").trim().ifEmpty {
            deriveServerBaseUrl(urlString)
        }
        RingAssignmentFetchResult(
            assignment = RingAssignment(
                ringId = ringId,
                ringLabel = root.optString("ringLabel", ringId.ifBlank { "Ring" }),
                serverBaseUrl = serverBaseUrl,
                currentGroup = root.optJSONObject("currentGroup")?.let(::parseRemoteGroup),
                currentPhase = root.optString("currentPhase", "").trim(),
                phasePlan = root.opt("phasePlan").let { value ->
                    if (value == null || value == JSONObject.NULL) "" else value.toString()
                },
                queuedGroupIds = root.optStringList("queuedGroupIds"),
                completedGroupIds = root.optStringList("completedGroupIds")
            )
        )
    } catch (exception: Exception) {
        RingAssignmentFetchResult(errorMessage = exception.message ?: "Unable to parse ring response")
    }
}

suspend fun probeHttpOk(urlString: String): HttpProbeResult = withContext(Dispatchers.IO) {
    try {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.connect()
        val responseCode = connection.responseCode
        if (responseCode in 200..299) {
            HttpProbeResult(ok = true)
        } else {
            val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            HttpProbeResult(
                ok = false,
                errorMessage = if (errorBody.isNotBlank()) {
                    "Server returned HTTP $responseCode: $errorBody"
                } else {
                    "Server returned HTTP $responseCode"
                }
            )
        }
    } catch (exception: Exception) {
        HttpProbeResult(ok = false, errorMessage = exception.message ?: "Unknown network error")
    }
}

suspend fun fetchRingConfig(urlString: String, tabletLabel: String = ""): RingConfigFetchResult {
    val requestUrl = if (tabletLabel.isBlank()) {
        urlString
    } else {
        val separator = if (urlString.contains("?")) "&" else "?"
        "${urlString}${separator}tabletLabel=${URLEncoder.encode(tabletLabel, Charsets.UTF_8.name())}"
    }
    val jsonResult = fetchJsonObject(requestUrl)
    val root = jsonResult.root ?: return RingConfigFetchResult(errorMessage = jsonResult.errorMessage)
    return try {
        val rawRings = root.optJSONArray("allowedRings") ?: JSONArray()
        val rings = mutableListOf<RingOption>()
        for (index in 0 until rawRings.length()) {
            val ringJson = rawRings.getJSONObject(index)
            val ringId = ringJson.optString("ringId", "").trim()
            val ringLabel = ringJson.optString("ringLabel", ringId).trim()
            if (ringId.isNotEmpty() && ringLabel.isNotEmpty()) {
                rings += RingOption(
                    ringId = ringId,
                    ringLabel = ringLabel,
                    isAvailable = ringJson.optBoolean("isAvailable", true),
                    statusLabel = ringJson.optString(
                        "statusLabel",
                        if (ringJson.optBoolean("isInUse", false)) "In use" else "Available"
                    )
                )
            }
        }

        RingConfigFetchResult(
            serverBaseUrl = deriveServerBaseUrl(urlString),
            rings = rings
        )
    } catch (exception: Exception) {
        RingConfigFetchResult(errorMessage = exception.message ?: "Unable to parse ring configuration")
    }
}

suspend fun sendRingHeartbeat(
    serverBaseUrl: String,
    ringId: String,
    phase: String,
    tabletLabel: String,
    progress: HeartbeatProgressSnapshot
): JsonFetchResult {
    if (ringId.isBlank()) return JsonFetchResult()
    val heartbeatUrl = "${serverBaseUrl.trimEnd('/')}/api/rings/$ringId/heartbeat"
    val payload = JSONObject()
        .put("phase", phase)
        .put("tabletLabel", tabletLabel)
        .put("checkInCount", progress.completedCount)
        .put("checkInTotal", progress.totalCount)
        .put("phaseCompletedCount", progress.completedCount)
        .put("phaseTotalCount", progress.totalCount)
        .put("phaseProgress", progress.percent)
    return fetchJsonObject(heartbeatUrl, method = "POST", jsonBody = payload)
}

suspend fun fetchServerVersionCode(serverBaseUrl: String): Int {
    return try {
        val result = fetchJsonObject("${serverBaseUrl.trimEnd('/')}/api/version")
        result.root?.optInt("versionCode", 0) ?: 0
    } catch (_: Exception) {
        0
    }
}

data class DivisionResultSubmissionRingAssignment(
    val ringId: String,
    val currentGroupId: String? = null,
    val queuedGroupIds: List<String> = emptyList(),
    val completedGroupIds: List<String> = emptyList(),
    val rawJson: JSONObject = JSONObject()
)

data class DivisionResultSubmissionAcceptedResponse(
    val ok: Boolean,
    val status: String,
    val serverRecordId: String,
    val submissionId: String,
    val receivedAt: String,
    val ringAssignment: DivisionResultSubmissionRingAssignment?,
    val rawJson: JSONObject
)

data class DivisionResultSubmissionRejectedResponse(
    val statusCode: Int,
    val errors: List<String>,
    val rawBody: String
)

data class DivisionResultSubmissionNetworkFailure(
    val message: String,
    val statusCode: Int? = null
)

sealed interface DivisionResultSubmissionResult {
    data class Accepted(val response: DivisionResultSubmissionAcceptedResponse) : DivisionResultSubmissionResult
    data class Rejected(val response: DivisionResultSubmissionRejectedResponse) : DivisionResultSubmissionResult
    data class NetworkFailure(val failure: DivisionResultSubmissionNetworkFailure) : DivisionResultSubmissionResult
}

suspend fun submitDivisionResult(
    serverBaseUrl: String,
    ringId: String,
    packetJson: JSONObject
): DivisionResultSubmissionResult {
    return submitDivisionResult(serverBaseUrl, ringId, packetJson.toString())
}

suspend fun submitDivisionResult(
    serverBaseUrl: String,
    ringId: String,
    packetJsonText: String
): DivisionResultSubmissionResult = withContext(Dispatchers.IO) {
    val urlString = "${serverBaseUrl.trimEnd('/')}/api/rings/$ringId/complete"
    try {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(packetJsonText)
        }
        connection.connect()

        val responseCode = connection.responseCode
        val responseBody = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()
            ?.use { it.readText() }
            .orEmpty()

        when {
            responseCode in 200..299 -> {
                val root = JSONObject(responseBody.ifBlank { "{}" })
                DivisionResultSubmissionResult.Accepted(
                    DivisionResultSubmissionAcceptedResponse(
                        ok = root.optBoolean("ok", true),
                        status = root.optString("status", "accepted"),
                        serverRecordId = root.optString("serverRecordId", ""),
                        submissionId = root.optString("submissionId", JSONObject(packetJsonText).optString("submissionId", "")),
                        receivedAt = root.optString("receivedAt", ""),
                        ringAssignment = root.optJSONObject("ringAssignment")?.let { ring ->
                            DivisionResultSubmissionRingAssignment(
                                ringId = ring.optString("ringId", ringId),
                                currentGroupId = ring.optString("currentGroupId", "").trim().ifBlank { null },
                                queuedGroupIds = ring.optJSONArray("queuedGroupIds")?.let { array ->
                                    buildList {
                                        for (index in 0 until array.length()) {
                                            val value = array.optString(index, "").trim()
                                            if (value.isNotEmpty()) add(value)
                                        }
                                    }
                                } ?: emptyList(),
                                completedGroupIds = ring.optJSONArray("completedGroupIds")?.let { array ->
                                    buildList {
                                        for (index in 0 until array.length()) {
                                            val value = array.optString(index, "").trim()
                                            if (value.isNotEmpty()) add(value)
                                        }
                                    }
                                } ?: emptyList(),
                                rawJson = ring
                            )
                        },
                        rawJson = root
                    )
                )
            }
            responseCode in 400..499 -> {
                val root = runCatching { JSONObject(responseBody) }.getOrNull()
                val errors = when {
                    root == null -> listOf(responseBody.ifBlank { "Server returned HTTP $responseCode" })
                    root.optJSONArray("errors") != null -> {
                        val array = root.optJSONArray("errors")!!
                        buildList {
                            for (index in 0 until array.length()) {
                                val value = array.optString(index, "").trim()
                                if (value.isNotEmpty()) add(value)
                            }
                        }
                    }
                    root.optString("message", "").isNotBlank() -> listOf(root.optString("message"))
                    root.optString("error", "").isNotBlank() -> listOf(root.optString("error"))
                    else -> listOf(root.toString())
                }
                DivisionResultSubmissionResult.Rejected(
                    DivisionResultSubmissionRejectedResponse(
                        statusCode = responseCode,
                        errors = errors,
                        rawBody = responseBody
                    )
                )
            }
            else -> {
                DivisionResultSubmissionResult.NetworkFailure(
                    DivisionResultSubmissionNetworkFailure(
                        message = if (responseBody.isNotBlank()) {
                            "Server returned HTTP $responseCode: $responseBody"
                        } else {
                            "Server returned HTTP $responseCode"
                        },
                        statusCode = responseCode
                    )
                )
            }
        }
    } catch (exception: Exception) {
        DivisionResultSubmissionResult.NetworkFailure(
            DivisionResultSubmissionNetworkFailure(
                message = exception.message ?: "Unknown network error"
            )
        )
    }
}
