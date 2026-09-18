package com.summ0.tournamentscoringapp

import android.content.res.Configuration
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas as GraphicsCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Bundle
import androidx.core.content.FileProvider
import java.io.File
import java.io.ByteArrayOutputStream
import androidx.activity.ComponentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.time.Instant
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.DialogProperties
import com.summ0.tournamentscoringapp.engine.CheckInStatus
import com.summ0.tournamentscoringapp.engine.CompetitionEntry
import com.summ0.tournamentscoringapp.engine.CompetitionRegistrationStatus
import com.summ0.tournamentscoringapp.engine.CompetitionType
import com.summ0.tournamentscoringapp.engine.Competitor
import com.summ0.tournamentscoringapp.engine.Division
import com.summ0.tournamentscoringapp.engine.HyungDiscipline
import com.summ0.tournamentscoringapp.engine.TournamentEngine
import androidx.compose.foundation.layout.height
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.summ0.tournamentscoringapp.ui.theme.TournamentScoringAppTheme
import java.util.Locale
import kotlin.math.abs
import kotlin.random.Random
import kotlinx.coroutines.delay

internal const val EXTRA_LAUNCH_SCREEN = "com.summ0.tournamentscoringapp.extra.LAUNCH_SCREEN"
internal const val LAUNCH_SCREEN_HYUNGS = "hyungs"
internal const val LAUNCH_SCREEN_WEAPONS = "weapons"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launchScreen = intent?.getStringExtra(EXTRA_LAUNCH_SCREEN)
        val initialScreen = launchScreen?.let(::launchInitialScreen) ?: CompetitionScreen.CHECK_IN

        enableEdgeToEdge()
        setContent {
            TournamentScoringAppTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CheckInScreen(
                        initialScreen = initialScreen,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

private fun launchInitialScreen(value: String): CompetitionScreen {
    return when (value) {
        LAUNCH_SCREEN_WEAPONS -> CompetitionScreen.WEAPONS_SCORING
        LAUNCH_SCREEN_HYUNGS -> CompetitionScreen.HYUNGS_SCORING
        else -> CompetitionScreen.CHECK_IN
    }
}

internal enum class CompetitionScreen {
    CHECK_IN,
    WEAPONS_SCORING,
    HYUNGS_SCORING,
    SPARRING_BRACKET,
    OVERALL_AWARDS
}

private data class PlacementFinalizeState(
    val labels: Map<String, String> = emptyMap(),
    val tieBreakDetails: Map<String, String> = emptyMap(),
    val message: String? = null
)

private data class PendingTieBreak(
    val discipline: com.summ0.tournamentscoringapp.engine.HyungDiscipline,
    val competitorIds: List<String>,
    val judgeCount: Int,
    val groupKey: String
)

private data class PlacementCandidate(
    val competitor: Competitor,
    val scores: List<Double>,
    val total: Double,
    val judgeCount: Int
)

private data class TieResolution(
    val orderedCandidates: List<PlacementCandidate>,
    val tieBreakDetails: Map<String, String>
)

private sealed interface PlacementFinalizeResult {
    data class Completed(
        val labels: Map<String, String>,
        val tieBreakDetails: Map<String, String>
    ) : PlacementFinalizeResult
    data class NeedsTieBreak(
        val pendingLabels: Map<String, String>,
        val request: PendingTieBreak
    ) : PlacementFinalizeResult

    data class Incomplete(val message: String) : PlacementFinalizeResult
}

private data class ActiveSparringBout(
    val roundIndex: Int,
    val boutIndex: Int,
    val bout: BracketSheetBout
)

private data class SparringBoutProgress(
    val bluePoints: Int = 0,
    val redPoints: Int = 0,
    val blueWarnings: Int = 0,
    val redWarnings: Int = 0,
    val elapsedSeconds: Int = 0,
    val winnerId: String? = null,
    val outcomeLabel: String = ""
)

private data class SparringBoutAssessment(
    val blueAdjustedScore: Int,
    val redAdjustedScore: Int,
    val blueDisqualified: Boolean,
    val redDisqualified: Boolean,
    val winner: Competitor?,
    val outcomeLabel: String
)

private data class RemoteCompetitor(
    val id: String,
    val name: String,
    val studio: String,
    val rank: String,
    val age: Int,
    val heightInInches: Int
) {
    fun toCompetitor(): Competitor = Competitor(
        id = id,
        name = name,
        studio = studio,
        rank = rank,
        rankLevel = rankLevelFor(rank.trim()),
        age = age,
        heightInInches = heightInInches,
        checkInStatus = CheckInStatus.REGISTERED,
        competitionEntries = emptyMap()
    )
}

private data class RemoteGroup(
    val groupId: String,
    val name: String,
    val ageRange: IntRange,
    val rankRange: IntRange,
    val rankRangeLabel: String,
    val matNumber: Int,
    val competitors: List<RemoteCompetitor>
)

private data class RemoteGroupFetchResult(
    val group: RemoteGroup? = null,
    val errorMessage: String? = null
)

private data class RingAssignment(
    val ringId: String,
    val ringLabel: String,
    val serverBaseUrl: String,
    val currentGroup: RemoteGroup?,
    val queuedGroupIds: List<String>,
    val completedGroupIds: List<String>
)

private data class RingAssignmentFetchResult(
    val assignment: RingAssignment? = null,
    val errorMessage: String? = null
)

private data class RingOption(
    val ringId: String,
    val ringLabel: String,
    val isAvailable: Boolean,
    val statusLabel: String
)

private data class RingConfigFetchResult(
    val serverBaseUrl: String? = null,
    val rings: List<RingOption> = emptyList(),
    val errorMessage: String? = null
)

private data class RingGridCell(
    val letter: String,
    val number: Int,
    val ring: RingOption?
)

private data class JsonFetchResult(
    val root: JSONObject? = null,
    val errorMessage: String? = null
)

private data class HttpProbeResult(
    val ok: Boolean,
    val errorMessage: String? = null
)

private const val DEFAULT_SERVER_HOSTNAME = "HomePC-Sum2"
private const val DEFAULT_SERVER_PORT = 3000
private const val DEFAULT_SERVER_BASE_URL = "http://" + DEFAULT_SERVER_HOSTNAME + ":" + DEFAULT_SERVER_PORT
private const val SERVER_CONNECTION_PREFS_NAME = "server_connection_preferences"
private const val SERVER_CONNECTION_MODE_KEY = "server_connection_mode"
private const val SERVER_CONNECTION_LAST_DNS_NAME_KEY = "server_connection_last_dns_name"
private const val SERVER_CONNECTION_LAST_ADDRESS_KEY = "server_connection_last_address"
private const val SERVER_CONNECTION_LAST_IP_KEY = "server_connection_last_ip"

private enum class ServerConnectionMode {
    DNS,
    IP
}

private data class ServerConnectionConfig(
    val mode: ServerConnectionMode,
    val lastDnsName: String,
    val lastServerAddress: String
)

private fun parseServerConnectionMode(modeName: String): ServerConnectionMode = try {
    ServerConnectionMode.valueOf(modeName)
} catch (_: IllegalArgumentException) {
    ServerConnectionMode.DNS
}

private fun loadServerConnectionConfig(context: Context): ServerConnectionConfig {
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

private fun persistServerConnectionConfig(context: Context, config: ServerConnectionConfig) {
    context.getSharedPreferences(SERVER_CONNECTION_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(SERVER_CONNECTION_MODE_KEY, config.mode.name)
        .putString(SERVER_CONNECTION_LAST_DNS_NAME_KEY, config.lastDnsName.ifBlank { DEFAULT_SERVER_HOSTNAME })
        .putString(SERVER_CONNECTION_LAST_ADDRESS_KEY, config.lastServerAddress.trim())
        .putString(SERVER_CONNECTION_LAST_IP_KEY, config.lastServerAddress.trim())
        .apply()
}

private fun isValidIpv4Host(host: String): Boolean {
    val parts = host.split(".")
    if (parts.size != 4) return false
    return parts.all { part ->
        val number = part.toIntOrNull() ?: return@all false
        number in 0..255 && part == number.toString()
    }
}

private fun normalizeDnsServerBaseUrl(serverAddressInput: String): String? {
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
    return if (host.isBlank()) null else "http://$host:$DEFAULT_SERVER_PORT"
}

private fun normalizeIpServerBaseUrl(serverAddressInput: String): String? {
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
            "http://$host:$DEFAULT_SERVER_PORT"
        }
    } catch (_: Exception) {
        null
    }
}

private fun buildServerBaseUrl(mode: ServerConnectionMode, serverAddressInput: String): String? = when (mode) {
    ServerConnectionMode.DNS -> normalizeDnsServerBaseUrl(serverAddressInput) ?: DEFAULT_SERVER_BASE_URL
    ServerConnectionMode.IP -> normalizeIpServerBaseUrl(serverAddressInput)
}

private fun serverAddressInputFromServerBaseUrl(serverBaseUrl: String): String? {
    return try {
        val parsed = URL(serverBaseUrl)
        val host = parsed.host.trim()
        if (host.isBlank()) null else host
    } catch (_: Exception) {
        null
    }
}

private data class GroupBanner(
    val groupId: String = "",
    val details: String = "",
    val statusText: String = ""
) {
    val isLoaded: Boolean get() = groupId.isNotEmpty()
    val displayId: String get() = when {
        groupId.isEmpty() && statusText.isNotEmpty() -> statusText
        groupId.startsWith("group-") -> "Group ${groupId.removePrefix("group-")}"
        groupId.isNotEmpty() -> groupId
        else -> "? No Group Loaded"
    }
}

private val groupBannerSaver = Saver<GroupBanner, String>(
    save = { banner ->
        JSONObject().apply {
            put("groupId", banner.groupId)
            put("details", banner.details)
            put("statusText", banner.statusText)
        }.toString()
    },
    restore = { saved ->
        val json = saved ?: "{}"
        val map = JSONObject(json)
        GroupBanner(
            groupId = map.optString("groupId", ""),
            details = map.optString("details", ""),
            statusText = map.optString("statusText", "")
        )
    }
)

private val competitionScreenSaver = Saver<CompetitionScreen, String>(
    save = { it.name },
    restore = { name ->
        try { CompetitionScreen.valueOf(name) } catch (_: IllegalArgumentException) { CompetitionScreen.CHECK_IN }
    }
)

private val competitorListSaver = Saver<List<Competitor>, String>(
    save = { competitors ->
        val root = JSONArray()
        competitors.forEach { competitor ->
            val item = JSONObject().apply {
                put("id", competitor.id)
                put("name", competitor.name)
                put("studio", competitor.studio)
                put("rank", competitor.rank)
                put("rankLevel", competitor.rankLevel)
                put("age", competitor.age)
                put("heightInInches", competitor.heightInInches)
                put("checkInStatus", competitor.checkInStatus.name)
                val entries = JSONArray()
                competitor.competitionEntries.forEach { (type, entry) ->
                    val pair = JSONArray().apply {
                        put(type.name)
                        put(entry.status.name)
                    }
                    entries.put(pair)
                }
                put("competitionEntries", entries)
            }
            root.put(item)
        }
        root.toString()
    },
    restore = { saved ->
        val root = JSONArray(saved ?: "[]")
        val parsed = mutableListOf<Competitor>()
        for (index in 0 until root.length()) {
            val item = root.getJSONObject(index)
            val competitionEntryMap = mutableMapOf<CompetitionType, CompetitionEntry>()
            val entries = item.optJSONArray("competitionEntries") ?: JSONArray()
            for (entryIndex in 0 until entries.length()) {
                val pair = entries.getJSONArray(entryIndex)
                val typeName = pair.optString(0, "")
                val statusName = pair.optString(1, "")
                val type = try {
                    CompetitionType.valueOf(typeName)
                } catch (_: IllegalArgumentException) {
                    continue
                }
                val status = try {
                    CompetitionRegistrationStatus.valueOf(statusName)
                } catch (_: IllegalArgumentException) {
                    CompetitionRegistrationStatus.REGISTERED
                }
                competitionEntryMap[type] = CompetitionEntry(type = type, status = status)
            }
            parsed += Competitor(
                id = item.optString("id", ""),
                name = item.optString("name", ""),
                studio = item.optString("studio", ""),
                rank = item.optString("rank", ""),
                rankLevel = item.optInt("rankLevel", 0),
                age = item.optInt("age", 0),
                heightInInches = item.optInt("heightInInches", 60),
                checkInStatus = try {
                    CheckInStatus.valueOf(item.optString("checkInStatus", CheckInStatus.REGISTERED.name))
                } catch (_: IllegalArgumentException) {
                    CheckInStatus.REGISTERED
                },
                competitionEntries = competitionEntryMap
            )
        }
        parsed
    }
)

private fun JSONObject.optNonBlank(vararg keys: String): String? {
    keys.forEach { key ->
        val value = optString(key, "").trim()
        if (value.isNotEmpty()) return value
    }
    return null
}

private data class ParsedRankRange(
    val range: IntRange,
    val label: String
)

private fun parseRankRange(root: JSONObject, rawCompetitors: JSONArray): ParsedRankRange {
    val rankRangeJson = root.optJSONObject("rankRange")
    val lowLabel = rankRangeJson?.optNonBlank("low", "min")
    val highLabel = rankRangeJson?.optNonBlank("high", "max")

    val competitorRankLevels = mutableListOf<Int>()
    for (index in 0 until rawCompetitors.length()) {
        val rankLevel = rankLevelFor(rawCompetitors.getJSONObject(index).optString("rank", ""))
        if (rankLevel != Int.MAX_VALUE) {
            competitorRankLevels += rankLevel
        }
    }

    val derivedRange = if (competitorRankLevels.isEmpty()) {
        0..0
    } else {
        competitorRankLevels.minOrNull()!!..competitorRankLevels.maxOrNull()!!
    }

    val parsedRange = if (lowLabel != null && highLabel != null) {
        val lowLevel = rankLevelFor(lowLabel)
        val highLevel = rankLevelFor(highLabel)
        if (lowLevel != Int.MAX_VALUE && highLevel != Int.MAX_VALUE) {
            minOf(lowLevel, highLevel)..maxOf(lowLevel, highLevel)
        } else {
            derivedRange
        }
    } else {
        derivedRange
    }

    val label = when {
        lowLabel != null && highLabel != null -> "$lowLabel - $highLabel"
        parsedRange.isEmpty() -> "0-0"
        else -> "${rankLabelForLevel(parsedRange.last)} - ${rankLabelForLevel(parsedRange.first)}"
    }

    return ParsedRankRange(parsedRange, label)
}

private fun JSONObject.optHeightInches(defaultValue: Int = 60): Int {
    val fromNumber = optInt("heightInInches", Int.MIN_VALUE)
    if (fromNumber != Int.MIN_VALUE && fromNumber > 0) return fromNumber

    val fromHeight = optString("height", "").trim().toIntOrNull()
    if (fromHeight != null && fromHeight > 0) return fromHeight

    return defaultValue
}

private fun JSONObject.optStringList(key: String): List<String> {
    val values = optJSONArray(key) ?: return emptyList()
    val result = mutableListOf<String>()
    for (index in 0 until values.length()) {
        val value = values.optString(index, "").trim()
        if (value.isNotEmpty()) {
            result += value
        }
    }
    return result
}

private fun deriveServerBaseUrl(urlString: String): String = try {
    val url = URL(urlString)
    val portPart = if (url.port >= 0) ":${url.port}" else ""
    "${url.protocol}://${url.host}$portPart"
} catch (_: Exception) {
    DEFAULT_SERVER_BASE_URL
}

private fun deviceLabel(): String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

private fun appVersionLabel(): String = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

private fun screenPhase(screen: CompetitionScreen): String = when (screen) {
    CompetitionScreen.CHECK_IN -> "check-in"
    CompetitionScreen.WEAPONS_SCORING -> "weapons"
    CompetitionScreen.HYUNGS_SCORING -> "hyungs"
    CompetitionScreen.SPARRING_BRACKET -> "sparring"
    CompetitionScreen.OVERALL_AWARDS -> "awards"
}

private fun parseRemoteGroup(root: JSONObject): RemoteGroup {
    val groupId = root.optString("groupId", "").ifBlank { "group-unknown" }
    val name = root.optString("name", "Group")
    val ageRangeJson = root.optJSONObject("ageRange")
    val ageRange = if (ageRangeJson != null) {
        ageRangeJson.optInt("min", 0)..ageRangeJson.optInt("max", 0)
    } else {
        0..0
    }
    val rawCompetitors = root.optJSONArray("competitors")
        ?: error("Response did not include a competitors array")
    val rankRange = parseRankRange(root, rawCompetitors)
    val matNumber = root.optInt("matNumber", 1)

    val competitors = mutableListOf<RemoteCompetitor>()
    for (index in 0 until rawCompetitors.length()) {
        val competitorJson = rawCompetitors.getJSONObject(index)
        val competitorName = competitorJson.optNonBlank("name", "fullName") ?: "Unknown"
        val competitorId = competitorJson.optNonBlank("id", "competitorId")
            ?: "${groupId}-$index-${competitorName.lowercase().replace(" ", "-")}"
        val studioName = competitorJson.optNonBlank("studio", "school") ?: "Unknown Studio"
        competitors += RemoteCompetitor(
            id = competitorId,
            name = competitorName,
            studio = studioName,
            rank = competitorJson.optString("rank", "10th Gup"),
            age = competitorJson.optInt("age", 18),
            heightInInches = competitorJson.optHeightInches()
        )
    }

    return RemoteGroup(
        groupId = groupId,
        name = name,
        ageRange = ageRange,
        rankRange = rankRange.range,
        rankRangeLabel = rankRange.label,
        matNumber = matNumber,
        competitors = competitors
    )
}

private fun IntRange.displayLabel(): String {
    return if (isEmpty()) "0-0" else "${first}-${last}"
}

private suspend fun fetchJsonObject(
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
            return@withContext JsonFetchResult(errorMessage = if (errorBody.isNotBlank()) {
                "Server returned HTTP $responseCode: $errorBody"
            } else {
                "Server returned HTTP $responseCode"
            })

        }

        val body = connection.inputStream.bufferedReader().use { it.readText() }
        JsonFetchResult(root = JSONObject(body))
    } catch (exception: Exception) {
        JsonFetchResult(errorMessage = exception.message ?: "Unknown network error")
    }
}

private suspend fun fetchRemoteGroup(urlString: String): RemoteGroupFetchResult {
    val jsonResult = fetchJsonObject(urlString)
    val root = jsonResult.root ?: return RemoteGroupFetchResult(errorMessage = jsonResult.errorMessage)
    return try {
        RemoteGroupFetchResult(group = parseRemoteGroup(root))
    } catch (exception: Exception) {
        RemoteGroupFetchResult(errorMessage = exception.message ?: "Unable to parse group response")
    }
}

private suspend fun fetchRingAssignment(
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
                queuedGroupIds = root.optStringList("queuedGroupIds"),
                completedGroupIds = root.optStringList("completedGroupIds")
            )
        )
    } catch (exception: Exception) {
        RingAssignmentFetchResult(errorMessage = exception.message ?: "Unable to parse ring response")
    }
}

private suspend fun probeHttpOk(urlString: String): HttpProbeResult = withContext(Dispatchers.IO) {
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

private suspend fun fetchRingConfig(urlString: String, tabletLabel: String = ""): RingConfigFetchResult {
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

private fun parseRingGridCoordinates(ringLabel: String): Pair<String, Int>? {
    val match = Regex("^([A-Za-z]+)(\\d+)$").matchEntire(ringLabel.trim()) ?: return null
    val letter = match.groupValues[1].uppercase(Locale.US)
    val number = match.groupValues[2].toIntOrNull() ?: return null
    return letter to number
}

private fun centimeters(value: Float): Dp = (value * 160f / 2.54f).dp

private suspend fun sendRingHeartbeat(
    serverBaseUrl: String,
    ringId: String,
    phase: String,
    tabletLabel: String
) : JsonFetchResult {
    if (ringId.isBlank()) return JsonFetchResult()
    val heartbeatUrl = "${serverBaseUrl.trimEnd('/')}/api/rings/$ringId/heartbeat"
    val payload = JSONObject()
        .put("phase", phase)
        .put("tabletLabel", tabletLabel)
    return fetchJsonObject(heartbeatUrl, method = "POST", jsonBody = payload)
}

private suspend fun fetchServerVersionCode(serverBaseUrl: String): Int {
    return try {
        val result = fetchJsonObject("${serverBaseUrl.trimEnd('/')}/api/version")
        result.root?.optInt("versionCode", 0) ?: 0
    } catch (e: Exception) {
        0
    }
}

private fun installApk(context: Context, apkFile: File) {
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

@Composable
internal fun CheckInScreen(
    initialScreen: CompetitionScreen = CompetitionScreen.CHECK_IN,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val activity = context.findActivity()
    val initialServerConnectionConfig = remember(appContext) { loadServerConnectionConfig(appContext) }
    val competitorsState = rememberSaveable(stateSaver = competitorListSaver) { mutableStateOf(emptyList<Competitor>()) }
    val competitors: List<Competitor> = competitorsState.value
    val checkedInCount = remember(competitors) { competitors.count { it.checkInStatus == CheckInStatus.CHECKED_IN } }
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var activeDivision by remember {
        mutableStateOf(
            Division(
                id = "",
                name = "",
                rankRange = 0..0,
                rankRangeLabel = "0-0"
            )
        )
    }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingCompetitorId by remember { mutableStateOf<String?>(null) }
    var lockedPhases by remember { mutableStateOf(setOf<CompetitionType>()) }
    var phaseEntrants by remember { mutableStateOf(mapOf<CompetitionType, Set<String>>()) }
    var useOnlyThreeJudges by rememberSaveable { mutableStateOf(false) }
    val currentScreenState = rememberSaveable(stateSaver = competitionScreenSaver) { mutableStateOf(initialScreen) }
    val currentScreen: CompetitionScreen = currentScreenState.value
    val controlBoardPhaseState = rememberSaveable { mutableStateOf("check-in") }
    val controlBoardPhase: String = controlBoardPhaseState.value
    val weaponsScoresByCompetitor = remember { mutableStateMapOf<String, List<String>>() }
    val hyungsScoresByCompetitor = remember { mutableStateMapOf<String, List<String>>() }
    var weaponsFinalizeState by remember { mutableStateOf(PlacementFinalizeState()) }
    var hyungsFinalizeState by remember { mutableStateOf(PlacementFinalizeState()) }
    val weaponsTieBreakScoresByCompetitor = remember { mutableStateMapOf<String, List<String>>() }
    val hyungsTieBreakScoresByCompetitor = remember { mutableStateMapOf<String, List<String>>() }
    val weaponsJudgeChoicesByGroup = remember { mutableStateMapOf<String, List<String>>() }
    val hyungsJudgeChoicesByGroup = remember { mutableStateMapOf<String, List<String>>() }
    var pendingTieBreak by remember { mutableStateOf<PendingTieBreak?>(null) }
    val sparringWinners = remember { mutableStateMapOf<Int, Map<Int, Competitor>>() }
    val sparringBoutProgress = remember { mutableStateMapOf<String, SparringBoutProgress>() }
    var activeSparringBout by remember { mutableStateOf<ActiveSparringBout?>(null) }
    val editingCompetitor = competitors.firstOrNull { it.id == editingCompetitorId }
    var remoteStatus by remember { mutableStateOf("Connect a ring to begin.") }
    var isLoadingGroup by remember { mutableStateOf(false) }
    var isCompletingGroup by remember { mutableStateOf(false) }
    var groupLoadRequested by remember { mutableStateOf(false) }
    var ringConnectionNeedsReconnect by rememberSaveable { mutableStateOf(false) }
    var serverConnectionModeName by rememberSaveable { mutableStateOf(initialServerConnectionConfig.mode.name) }
    var lastDnsName by rememberSaveable {
        mutableStateOf(initialServerConnectionConfig.lastDnsName.ifBlank { DEFAULT_SERVER_HOSTNAME })
    }
    var lastServerAddress by rememberSaveable {
        mutableStateOf(initialServerConnectionConfig.lastServerAddress)
    }
    val serverAddressForCurrentMode = if (parseServerConnectionMode(serverConnectionModeName) == ServerConnectionMode.DNS) {
        lastDnsName
    } else {
        lastServerAddress
    }
    var serverBaseUrl by rememberSaveable {
        mutableStateOf(
            buildServerBaseUrl(
                mode = initialServerConnectionConfig.mode,
                serverAddressInput = serverAddressForCurrentMode
            ) ?: DEFAULT_SERVER_BASE_URL
        )
    }
    var launchRingOptions by remember { mutableStateOf(emptyList<RingOption>()) }
    var selectedLaunchRingId by rememberSaveable { mutableStateOf("") }
    var isCheckingLaunchServer by remember { mutableStateOf(false) }
    var isRefreshingLaunchServer by remember { mutableStateOf(false) }
    var launchServerReachable by remember { mutableStateOf(false) }
    var launchServerStatus by remember { mutableStateOf("Checking server...") }
    var showServerConfigDialog by remember { mutableStateOf(false) }
    var currentRingId by rememberSaveable { mutableStateOf("") }
    var currentRingLabel by rememberSaveable { mutableStateOf("") }
    val currentGroupBannerState = rememberSaveable(stateSaver = groupBannerSaver) { mutableStateOf(GroupBanner()) }
    val currentGroupBanner: GroupBanner = currentGroupBannerState.value
    var showAssistanceDialog by remember { mutableStateOf(false) }
    val tabletLabel = remember { deviceLabel() }
    val screenScope = rememberCoroutineScope()
    var showUpdateDialog by remember { mutableStateOf(false) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var updateDownloadError by remember { mutableStateOf("") }
    var showExitConfirmationDialog by remember { mutableStateOf(false) }

    fun resetCompetitionState() {
        lockedPhases = emptySet()
        phaseEntrants = emptyMap()
        weaponsScoresByCompetitor.clear()
        hyungsScoresByCompetitor.clear()
        weaponsFinalizeState = PlacementFinalizeState()
        hyungsFinalizeState = PlacementFinalizeState()
        weaponsTieBreakScoresByCompetitor.clear()
        hyungsTieBreakScoresByCompetitor.clear()
        weaponsJudgeChoicesByGroup.clear()
        hyungsJudgeChoicesByGroup.clear()
        pendingTieBreak = null
        sparringWinners.clear()
        sparringBoutProgress.clear()
        activeSparringBout = null
        currentScreenState.value = CompetitionScreen.CHECK_IN
    }

    fun resetDivisionState() {
        activeDivision = Division(
            id = "",
            name = "",
            rankRange = 0..0,
            rankRangeLabel = "0-0"
        )
    }

    fun applyLoadedGroup(group: RemoteGroup) {
        resetCompetitionState()
        activeDivision = Division(
            id = group.groupId,
            name = group.name,
            rankRange = group.rankRange,
            rankRangeLabel = group.rankRangeLabel,
            ageRange = group.ageRange
        )
        competitorsState.value = group.competitors.map { it.toCompetitor() }
        currentGroupBannerState.value = GroupBanner(groupId = group.groupId, details = group.name)
        controlBoardPhaseState.value = "check-in"
    }

    fun clearLoadedGroup() {
        resetCompetitionState()
        resetDivisionState()
        competitorsState.value = emptyList()
        currentGroupBannerState.value = GroupBanner()
        controlBoardPhaseState.value = "check-in"
    }

    fun markUnassignedAssignment() {
        resetCompetitionState()
        resetDivisionState()
        competitorsState.value = emptyList()
        currentGroupBannerState.value = GroupBanner()
        controlBoardPhaseState.value = "check-in"
        remoteStatus = "Unassigned"
    }

    fun markPendingAssignment() {
        resetCompetitionState()
        resetDivisionState()
        competitorsState.value = emptyList()
        currentGroupBannerState.value = GroupBanner()
        controlBoardPhaseState.value = "check-in"
        remoteStatus = "Pending Assignment"
    }

    fun invalidateStaleRingSelection(message: String) {
        resetCompetitionState()
        resetDivisionState()
        competitorsState.value = emptyList()
        currentGroupBannerState.value = GroupBanner()
        controlBoardPhaseState.value = "check-in"
        currentRingId = ""
        currentRingLabel = ""
        selectedLaunchRingId = ""
        groupLoadRequested = false
        ringConnectionNeedsReconnect = false
        remoteStatus = message
        launchServerStatus = message
    }

    fun moveToScreen(nextScreen: CompetitionScreen, resetControlBoardPhase: Boolean = false) {
        currentScreenState.value = nextScreen
        if (nextScreen == CompetitionScreen.CHECK_IN) {
            if (resetControlBoardPhase) {
                controlBoardPhaseState.value = "check-in"
            }
        } else {
            controlBoardPhaseState.value = screenPhase(nextScreen)
        }
    }

    fun requestExitApp() {
        showExitConfirmationDialog = true
    }

    fun markRingConnectionHealthy() {
        ringConnectionNeedsReconnect = false
    }

    fun markRingConnectionNeedsReconnect(message: String) {
        ringConnectionNeedsReconnect = true
        remoteStatus = message
    }

    fun syncSelectedLaunchRing() {
        val ringIds = launchRingOptions.map { it.ringId }
        if (currentRingId.isNotBlank() && currentRingId in ringIds) {
            selectedLaunchRingId = currentRingId
        } else if (selectedLaunchRingId !in ringIds) {
            selectedLaunchRingId = ""
        }
    }

    fun refreshLaunchRingOptions() {
        if (isRefreshingLaunchServer) return
        screenScope.launch {
            isRefreshingLaunchServer = true
            try {
                val preferredMode = parseServerConnectionMode(serverConnectionModeName)
                val alternateMode = if (preferredMode == ServerConnectionMode.DNS) ServerConnectionMode.IP else ServerConnectionMode.DNS
                val candidateModes = listOf(preferredMode, alternateMode).distinct()
                val candidateResults = mutableListOf<String>()

                isCheckingLaunchServer = true
                launchServerReachable = false
                launchServerStatus = "Checking server..."

                for (mode in candidateModes) {
                    val serverAddress = if (mode == ServerConnectionMode.DNS) lastDnsName else lastServerAddress
                    val candidateBaseUrl = buildServerBaseUrl(mode, serverAddress)
                    if (candidateBaseUrl == null) {
                        candidateResults += if (mode == ServerConnectionMode.DNS) {
                            "DNS server name is invalid."
                        } else {
                            "IPv4 address is invalid."
                        }
                        continue
                    }

                    launchServerStatus = "Checking ${if (mode == ServerConnectionMode.DNS) "DNS" else "IP"} server..."
                    val healthResult = probeHttpOk("${candidateBaseUrl.trimEnd('/')}/api/health")
                    if (!healthResult.ok) {
                        candidateResults += healthResult.errorMessage ?: "Unable to reach server."
                        continue
                    }

                    val result = fetchRingConfig(
                        "${candidateBaseUrl.trimEnd('/')}/api/rings/config",
                        tabletLabel = tabletLabel
                    )
                    if (result.errorMessage == null) {
                        val newRingIds = result.rings.map { it.ringId }.toSet()
                        val currentRingOption = result.rings.firstOrNull { it.ringId == currentRingId }
                        val selectedRingOption = result.rings.firstOrNull { it.ringId == selectedLaunchRingId }
                        serverConnectionModeName = mode.name
                        if (mode == ServerConnectionMode.DNS) {
                            lastDnsName = serverAddress.ifBlank { DEFAULT_SERVER_HOSTNAME }
                        } else {
                            lastServerAddress = serverAddress.trim()
                        }
                        persistServerConnectionConfig(
                            appContext,
                            ServerConnectionConfig(
                                mode = mode,
                                lastDnsName = lastDnsName,
                                lastServerAddress = lastServerAddress
                            )
                        )
                        serverBaseUrl = result.serverBaseUrl ?: candidateBaseUrl
                        launchRingOptions = result.rings
                        launchServerReachable = true
                        launchServerStatus = when {
                            launchRingOptions.isEmpty() -> "Server reachable, but no rings are configured."
                            launchRingOptions.any { it.isAvailable } -> "Server reachable. Select a ring."
                            else -> "All rings are in use."
                        }
                        if (currentRingId.isNotBlank() && (currentRingOption == null || !currentRingOption.isAvailable)) {
                            invalidateStaleRingSelection("Ring $currentRingLabel is no longer available. Please choose again.")
                        } else {
                            if (selectedLaunchRingId.isNotBlank() && (selectedRingOption == null || !selectedRingOption.isAvailable)) {
                                selectedLaunchRingId = ""
                            }
                            syncSelectedLaunchRing()
                        }
                        return@launch
                    }

                    candidateResults += result.errorMessage ?: "Unable to reach server."
                }

                launchRingOptions = emptyList()
                selectedLaunchRingId = ""
                currentRingId = ""
                currentRingLabel = ""
                currentGroupBannerState.value = GroupBanner()
                launchServerReachable = false
                launchServerStatus = buildString {
                    append("Unable to reach server.")
                    if (candidateResults.isNotEmpty()) {
                        append(' ')
                        append(candidateResults.joinToString(" "))
                    }
                }.trim()
            } finally {
                isCheckingLaunchServer = false
                isRefreshingLaunchServer = false
            }
        }
    }

    fun applyRingContactResult(result: JsonFetchResult) {
        if (result.root != null) {
            markRingConnectionHealthy()
        } else {
            markRingConnectionNeedsReconnect(
                "Connection lost. Reconnect Ring to restore contact."
            )
        }
    }

    fun syncAssignmentFromServer(
        assignment: RingAssignment,
        preserveLoadedGroup: Boolean
    ) {
        serverBaseUrl = assignment.serverBaseUrl
        currentRingId = assignment.ringId
        currentRingLabel = assignment.ringLabel
        markRingConnectionHealthy()

        val nextGroup = assignment.currentGroup
        val currentGroupId = currentGroupBanner.groupId
        when {
            nextGroup != null && (!preserveLoadedGroup || !currentGroupBanner.isLoaded) -> {
                applyLoadedGroup(nextGroup)
            }
            nextGroup != null && currentGroupBanner.isLoaded && nextGroup.groupId != currentGroupId -> {
                applyLoadedGroup(nextGroup)
            }
            nextGroup == null && !preserveLoadedGroup -> {
                markPendingAssignment()
            }
            nextGroup == null && preserveLoadedGroup && !currentGroupBanner.isLoaded -> {
                markPendingAssignment()
            }
        }
    }

    fun connectSelectedRing(onSuccess: (() -> Unit)? = null) {
        screenScope.launch {
            if (!launchServerReachable) {
                remoteStatus = "Unable to reach server. Review the server settings."
                return@launch
            }
            val selectedRingId = selectedLaunchRingId.trim()
            if (selectedRingId.isBlank()) {
                remoteStatus = "Select a ring first."
                return@launch
            }
            val hadAssignedRing = currentRingId.isNotBlank()
            val preserveLoadedGroupOnReconnect = hadAssignedRing && currentGroupBanner.isLoaded
            val requestedBootstrapUrl = "${serverBaseUrl.trimEnd('/')}/api/rings/$selectedRingId/bootstrap"
            isLoadingGroup = true
            if (!preserveLoadedGroupOnReconnect) {
                groupLoadRequested = false
            }
            remoteStatus = "Connecting ring..."
            val encodedTabletLabel = URLEncoder.encode(tabletLabel, Charsets.UTF_8.name())
            val bootstrapWithTabletLabel = if (requestedBootstrapUrl.contains("?")) {
                "$requestedBootstrapUrl&tabletLabel=$encodedTabletLabel"
            } else {
                "$requestedBootstrapUrl?tabletLabel=$encodedTabletLabel"
            }
            val fetchResult = fetchRingAssignment(bootstrapWithTabletLabel)
            val assignment = fetchResult.assignment
            if (assignment != null) {
                selectedLaunchRingId = assignment.ringId
                syncAssignmentFromServer(
                    assignment = assignment,
                    preserveLoadedGroup = preserveLoadedGroupOnReconnect
                )
                remoteStatus = when {
                    preserveLoadedGroupOnReconnect -> "${assignment.ringLabel} reconnected."
                    currentGroupBanner.isLoaded -> "${assignment.ringLabel} connected."
                    else -> "${assignment.ringLabel} connected. Waiting for the next group."
                }
                applyRingContactResult(
                    sendRingHeartbeat(
                        serverBaseUrl = assignment.serverBaseUrl,
                        ringId = assignment.ringId,
                        phase = controlBoardPhase,
                        tabletLabel = tabletLabel
                    )
                )
                onSuccess?.invoke()
            } else {
                if (hadAssignedRing) {
                    markRingConnectionNeedsReconnect(
                        "Connection lost. Reconnect Ring to restore contact."
                    )
                    launchServerReachable = false
                    launchServerStatus = remoteStatus
                } else {
                    remoteStatus = "Unable to connect ring. ${fetchResult.errorMessage ?: ""}".trim()
                    launchServerStatus = remoteStatus
                    launchServerReachable = false
                }
            }
            isLoadingGroup = false
        }
    }

    fun reconnectCurrentRing() {
        if (currentRingId.isNotBlank()) {
            selectedLaunchRingId = currentRingId
            connectSelectedRing()
        } else {
            refreshLaunchRingOptions()
        }
    }

    fun completeCurrentGroup(
        divisionPacket: JSONObject? = null,
        snapshotBytes: ByteArray? = null,
        snapshotMimeType: String? = null
    ) {
        screenScope.launch {
            val ringId = currentRingId.trim()
            if (ringId.isEmpty()) {
                remoteStatus = "Assign a ring before requesting the next group."
                return@launch
            }

            isCompletingGroup = true
            remoteStatus = "Loading next group: building packet..."
            val completeUrl = "${serverBaseUrl.trimEnd('/')}/api/rings/$ringId/complete"
            remoteStatus = "Loading next group: preparing upload body..."
            val uploadBody = divisionPacket
            remoteStatus = "Loading next group: sending request..."
            val fetchResult = fetchRingAssignment(completeUrl, "POST", uploadBody)
            val assignment = fetchResult.assignment
            if (assignment != null) {
                remoteStatus = "Loading next group: saving results..."
                markRingConnectionHealthy()
                if (divisionPacket != null && snapshotBytes != null && snapshotMimeType != null) {
                    persistDivisionPacketReceipt(
                        context = appContext,
                        divisionPacket = divisionPacket,
                        snapshotBytes = snapshotBytes,
                        snapshotMimeType = snapshotMimeType
                    )
                }
                syncAssignmentFromServer(
                    assignment = assignment,
                    preserveLoadedGroup = false
                )
                remoteStatus = assignment.currentGroup?.let { nextGroup ->
                    if (nextGroup.competitors.isNotEmpty()) {
                        "${assignment.ringLabel} advanced to ${nextGroup.groupId}."
                    } else {
                        "${assignment.ringLabel} is waiting for the next group."
                    }
                } ?: "${assignment.ringLabel} is waiting for the next group."
                applyRingContactResult(
                    sendRingHeartbeat(
                        serverBaseUrl = assignment.serverBaseUrl,
                        ringId = assignment.ringId,
                        phase = controlBoardPhase,
                        tabletLabel = tabletLabel
                    )
                )
            } else {
                markRingConnectionNeedsReconnect(
                    "Connection lost. Reconnect Ring to restore contact."
                )
            }
            isCompletingGroup = false
        }
    }

    fun requestRingAssistance(type: String, label: String) {
        screenScope.launch {
            val ringId = currentRingId.trim()
            if (ringId.isEmpty()) {
                remoteStatus = "Assign a ring before requesting assistance."
                return@launch
            }
            val assistanceUrl = "${serverBaseUrl.trimEnd('/')}/api/rings/$ringId/assistance"
            val payload = JSONObject().put("type", type)
            val result = fetchJsonObject(assistanceUrl, method = "POST", jsonBody = payload)
            if (result.root != null) {
                markRingConnectionHealthy()
                remoteStatus = "$label request sent for ${currentRingLabel.ifBlank { ringId }}."
                showAssistanceDialog = false
            } else {
                markRingConnectionNeedsReconnect(
                    "Connection lost. Reconnect Ring to restore contact."
                )
            }
        }
    }

    fun clearRingAssistance() {
        screenScope.launch {
            val ringId = currentRingId.trim()
            if (ringId.isEmpty()) {
                remoteStatus = "Assign a ring before clearing assistance."
                return@launch
            }
            val clearUrl = "${serverBaseUrl.trimEnd('/')}/api/rings/$ringId/assistance/clear"
            val result = fetchJsonObject(clearUrl, method = "POST")
            if (result.root != null) {
                markRingConnectionHealthy()
                remoteStatus = "Assistance request cleared for ${currentRingLabel.ifBlank { ringId }}."
                showAssistanceDialog = false
            } else {
                markRingConnectionNeedsReconnect(
                    "Connection lost. Reconnect Ring to restore contact."
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshLaunchRingOptions()
    }

    LaunchedEffect(showServerConfigDialog, launchServerReachable) {
        while (!showServerConfigDialog && !launchServerReachable) {
            refreshLaunchRingOptions()
            delay(5_000L)
        }
    }

    // Heartbeat on screen/ring change
    LaunchedEffect(currentScreen, currentRingId, serverBaseUrl, currentGroupBanner.groupId, controlBoardPhase) {
        if (currentRingId.isNotBlank()) {
            val heartbeatResult = sendRingHeartbeat(
                serverBaseUrl = serverBaseUrl,
                ringId = currentRingId,
                phase = controlBoardPhase,
                tabletLabel = tabletLabel
            )
            applyRingContactResult(heartbeatResult)
        }
    }

    LaunchedEffect(currentRingId, serverBaseUrl, currentGroupBanner.isLoaded, groupLoadRequested) {
        if (currentRingId.isBlank() || !groupLoadRequested || currentGroupBanner.isLoaded) return@LaunchedEffect

        if (remoteStatus == "Connect a ring to begin.") {
            remoteStatus = "Unassigned"
        }

        while (currentRingId.isNotBlank() && !currentGroupBanner.isLoaded) {
            if (!isCompletingGroup) {
                completeCurrentGroup()
            }
            delay(15_000L)
        }
    }

    // Periodic heartbeat every 60 seconds while assigned to a ring
    LaunchedEffect(currentRingId, serverBaseUrl, controlBoardPhase) {
        if (currentRingId.isNotBlank()) {
            while (true) {
                delay(60_000L)
                val heartbeatResult = sendRingHeartbeat(
                    serverBaseUrl = serverBaseUrl,
                    ringId = currentRingId,
                    phase = controlBoardPhase,
                    tabletLabel = tabletLabel
                )
                if (currentRingId.isNotBlank()) {
                    applyRingContactResult(heartbeatResult)
                }
            }
        }
    }

    // Check for app updates when server URL is known
    LaunchedEffect(serverBaseUrl) {
        if (serverBaseUrl.isNotBlank()) {
            val serverVersionCode = fetchServerVersionCode(serverBaseUrl)
            if (serverVersionCode > BuildConfig.VERSION_CODE) {
                showUpdateDialog = true
            }
        }
    }

    BackHandler(enabled = currentScreen != CompetitionScreen.CHECK_IN || showAddDialog || showServerConfigDialog || showAssistanceDialog || editingCompetitor != null || activeSparringBout != null) {
        when {
            activeSparringBout != null -> activeSparringBout = null
            showAssistanceDialog -> showAssistanceDialog = false
            showServerConfigDialog -> showServerConfigDialog = false
            showAddDialog || editingCompetitor != null -> {
                showAddDialog = false
                editingCompetitorId = null
            }
            currentScreenState.value == CompetitionScreen.OVERALL_AWARDS -> moveToScreen(CompetitionScreen.CHECK_IN)
            currentScreenState.value == CompetitionScreen.SPARRING_BRACKET -> moveToScreen(CompetitionScreen.CHECK_IN)
            currentScreenState.value == CompetitionScreen.HYUNGS_SCORING -> moveToScreen(CompetitionScreen.CHECK_IN)
            currentScreenState.value == CompetitionScreen.WEAPONS_SCORING -> moveToScreen(CompetitionScreen.CHECK_IN)
            currentScreen != CompetitionScreen.CHECK_IN -> moveToScreen(CompetitionScreen.CHECK_IN)
        }
    }

    val weaponsSheetCompetitors = remember(competitors, lockedPhases, phaseEntrants) {
        val lockedWeaponsIds = phaseEntrants[CompetitionType.WEAPONS]
        val eligible = if (CompetitionType.WEAPONS in lockedPhases && lockedWeaponsIds != null) {
            competitors.filter { it.id in lockedWeaponsIds }
        } else {
            competitors.filter { TournamentEngine.isEligibleForCompetition(it, CompetitionType.WEAPONS) }
        }
        randomizeScoringSheetCompetitors(eligible, CompetitionType.WEAPONS)
    }
    val hyungsSheetCompetitors = remember(competitors, lockedPhases, phaseEntrants) {
        val lockedHyungsIds = phaseEntrants[CompetitionType.HYUNGS]
        val eligible = if (CompetitionType.HYUNGS in lockedPhases && lockedHyungsIds != null) {
            competitors.filter { it.id in lockedHyungsIds }
        } else {
            competitors.filter { TournamentEngine.isEligibleForCompetition(it, CompetitionType.HYUNGS) }
        }
        randomizeScoringSheetCompetitors(eligible, CompetitionType.HYUNGS)
    }
    val sparringCompetitors = remember(competitors, lockedPhases, phaseEntrants) {
        val lockedSparringIds = phaseEntrants[CompetitionType.SPARRING]
        if (CompetitionType.SPARRING in lockedPhases && lockedSparringIds != null) {
            competitors.filter { it.id in lockedSparringIds }
        } else {
            competitors.filter { TournamentEngine.isEligibleForCompetition(it, CompetitionType.SPARRING) }
        }
    }
    val judgeCount = if (useOnlyThreeJudges) 3 else 5
    val nextCompetitionButtonLabel = when {
        weaponsSheetCompetitors.isNotEmpty() -> "Open Weapons"
        hyungsSheetCompetitors.isNotEmpty() -> "Open Hyungs"
        else -> null
    }
    val entrantsSummaryText = "Entrants - Weapons: ${
        phaseEntrantCount(CompetitionType.WEAPONS, phaseEntrants, competitors)
    }${if (CompetitionType.WEAPONS in lockedPhases) " (Locked)" else ""} | " +
        "Hyungs: ${phaseEntrantCount(CompetitionType.HYUNGS, phaseEntrants, competitors)}" +
        "${if (CompetitionType.HYUNGS in lockedPhases) " (Locked)" else ""} | " +
        "Sparring: ${phaseEntrantCount(CompetitionType.SPARRING, phaseEntrants, competitors)}" +
        "${if (CompetitionType.SPARRING in lockedPhases) " (Locked)" else ""}"

    if (showExitConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showExitConfirmationDialog = false },
            title = { Text("Exit App", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to exit the program?") },
            confirmButton = {
                Button(
                    onClick = {
                        showExitConfirmationDialog = false
                        activity?.finishAffinity()
                    }
                ) {
                    Text("Exit")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmationDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (currentScreenState.value == CompetitionScreen.WEAPONS_SCORING) {
        WeaponsScoringScreen(
            groupBanner = currentGroupBanner,
            competitors = weaponsSheetCompetitors,
            scoresByCompetitor = weaponsScoresByCompetitor,
            placementState = weaponsFinalizeState,
            judgeCount = judgeCount,
            onBack = { moveToScreen(CompetitionScreen.CHECK_IN) },
            onNext = { moveToScreen(CompetitionScreen.HYUNGS_SCORING) },
            onExit = { requestExitApp() },
            onScoreChanged = { competitorId, judgeIndex, rawValue ->
                val sanitized = rawValue.filter { it.isDigit() || it == '.' }.take(4)
                val existing = weaponsScoresByCompetitor[competitorId] ?: List(5) { "" }
                val updated = existing.toMutableList()
                updated[judgeIndex] = sanitized
                weaponsScoresByCompetitor[competitorId] = updated
                weaponsFinalizeState = PlacementFinalizeState()
                weaponsTieBreakScoresByCompetitor.clear()
                weaponsJudgeChoicesByGroup.clear()
                if (pendingTieBreak?.discipline == com.summ0.tournamentscoringapp.engine.HyungDiscipline.WEAPONS) {
                    pendingTieBreak = null
                }

                if (CompetitionType.WEAPONS !in lockedPhases && calculateScoreTotal(updated) != null) {
                    lockedPhases = lockedPhases + CompetitionType.WEAPONS
                    phaseEntrants = phaseEntrants + (
                        CompetitionType.WEAPONS to entrantsForPhase(competitors, CompetitionType.WEAPONS)
                        )
                }
            },
            onFinalizePlacings = {
                when (
                    val result = finalizePlacements(
                        competitors = weaponsSheetCompetitors,
                        scoresByCompetitor = weaponsScoresByCompetitor,
                        discipline = com.summ0.tournamentscoringapp.engine.HyungDiscipline.WEAPONS,
                        tieBreakScoresByCompetitor = weaponsTieBreakScoresByCompetitor,
                        judgeChoicesByGroup = weaponsJudgeChoicesByGroup
                    )
                ) {
                    is PlacementFinalizeResult.Completed -> {
                        weaponsFinalizeState = PlacementFinalizeState(
                            labels = result.labels,
                            tieBreakDetails = result.tieBreakDetails,
                            message = "Placings finalized."
                        )
                        pendingTieBreak = null
                    }

                    is PlacementFinalizeResult.Incomplete -> {
                        weaponsFinalizeState = PlacementFinalizeState(message = result.message)
                    }

                    is PlacementFinalizeResult.NeedsTieBreak -> {
                        weaponsFinalizeState = PlacementFinalizeState(
                            labels = result.pendingLabels,
                            message = "Tie remains after adding back high and low. Second performance required."
                        )
                        pendingTieBreak = result.request
                    }
                }
            }
        )
        pendingTieBreak
            ?.takeIf { it.discipline == com.summ0.tournamentscoringapp.engine.HyungDiscipline.WEAPONS }
            ?.let { request ->
                TieBreakResolutionDialog(
                    competitors = weaponsSheetCompetitors.filter { it.id in request.competitorIds },
                    judgeCount = request.judgeCount,
                    scoreInputsByCompetitor = weaponsTieBreakScoresByCompetitor,
                    judgeSelections = weaponsJudgeChoicesByGroup[request.groupKey] ?: List(request.judgeCount) { "" },
                    onDismiss = { pendingTieBreak = null },
                    onScoreChanged = { competitorId, judgeIndex, value ->
                        val existing = weaponsTieBreakScoresByCompetitor[competitorId] ?: List(request.judgeCount) { "" }
                        val updated = existing.toMutableList()
                        updated[judgeIndex] = value.filter { it.isDigit() || it == '.' }.take(4)
                        weaponsTieBreakScoresByCompetitor[competitorId] = updated
                    },
                    onJudgeSelectionChanged = { judgeIndex, competitorId ->
                        val currentSelections = (weaponsJudgeChoicesByGroup[request.groupKey]
                            ?: List(request.judgeCount) { "" }).toMutableList()
                        currentSelections[judgeIndex] = competitorId
                        weaponsJudgeChoicesByGroup[request.groupKey] = currentSelections
                    },
                    onApply = {
                        when (
                            val result = finalizePlacements(
                                competitors = weaponsSheetCompetitors,
                                scoresByCompetitor = weaponsScoresByCompetitor,
                                discipline = com.summ0.tournamentscoringapp.engine.HyungDiscipline.WEAPONS,
                                tieBreakScoresByCompetitor = weaponsTieBreakScoresByCompetitor,
                                judgeChoicesByGroup = weaponsJudgeChoicesByGroup
                            )
                        ) {
                            is PlacementFinalizeResult.Completed -> {
                                weaponsFinalizeState = PlacementFinalizeState(
                                    labels = result.labels,
                                    tieBreakDetails = result.tieBreakDetails,
                                    message = "Placings finalized."
                                )
                                pendingTieBreak = null
                            }

                            is PlacementFinalizeResult.Incomplete -> {
                                weaponsFinalizeState = PlacementFinalizeState(message = result.message)
                            }

                            is PlacementFinalizeResult.NeedsTieBreak -> {
                                weaponsFinalizeState = PlacementFinalizeState(
                                    labels = result.pendingLabels,
                                    message = "Finish the second tie-break to finalize placings."
                                )
                                pendingTieBreak = result.request
                            }
                        }
                    }
                )
            }
        return
    }

    if (currentScreenState.value == CompetitionScreen.HYUNGS_SCORING) {
        HyungsScoringScreen(
            groupBanner = currentGroupBanner,
            competitors = hyungsSheetCompetitors,
            scoresByCompetitor = hyungsScoresByCompetitor,
            placementState = hyungsFinalizeState,
            judgeCount = judgeCount,
            onBack = { moveToScreen(CompetitionScreen.CHECK_IN) },
            onPrevious = { moveToScreen(CompetitionScreen.CHECK_IN) },
            onNext = { moveToScreen(CompetitionScreen.SPARRING_BRACKET) },
            onExit = { requestExitApp() },
            onScoreChanged = { competitorId, judgeIndex, rawValue ->
                val sanitized = rawValue.filter { it.isDigit() || it == '.' }.take(4)
                val existing = hyungsScoresByCompetitor[competitorId] ?: List(5) { "" }
                val updated = existing.toMutableList()
                updated[judgeIndex] = sanitized
                hyungsScoresByCompetitor[competitorId] = updated
                hyungsFinalizeState = PlacementFinalizeState()
                hyungsTieBreakScoresByCompetitor.clear()
                hyungsJudgeChoicesByGroup.clear()
                if (pendingTieBreak?.discipline == com.summ0.tournamentscoringapp.engine.HyungDiscipline.HYUNGS) {
                    pendingTieBreak = null
                }

                if (CompetitionType.HYUNGS !in lockedPhases && calculateScoreTotal(updated) != null) {
                    lockedPhases = lockedPhases + CompetitionType.HYUNGS
                    phaseEntrants = phaseEntrants + (
                        CompetitionType.HYUNGS to entrantsForPhase(competitors, CompetitionType.HYUNGS)
                        )
                }
            },
            onFinalizePlacings = {
                when (
                    val result = finalizePlacements(
                        competitors = hyungsSheetCompetitors,
                        scoresByCompetitor = hyungsScoresByCompetitor,
                        discipline = com.summ0.tournamentscoringapp.engine.HyungDiscipline.HYUNGS,
                        tieBreakScoresByCompetitor = hyungsTieBreakScoresByCompetitor,
                        judgeChoicesByGroup = hyungsJudgeChoicesByGroup
                    )
                ) {
                    is PlacementFinalizeResult.Completed -> {
                        hyungsFinalizeState = PlacementFinalizeState(
                            labels = result.labels,
                            tieBreakDetails = result.tieBreakDetails,
                            message = "Placings finalized."
                        )
                        pendingTieBreak = null
                    }

                    is PlacementFinalizeResult.Incomplete -> {
                        hyungsFinalizeState = PlacementFinalizeState(message = result.message)
                    }

                    is PlacementFinalizeResult.NeedsTieBreak -> {
                        hyungsFinalizeState = PlacementFinalizeState(
                            labels = result.pendingLabels,
                            message = "Tie remains after adding back high and low. Second performance required."
                        )
                        pendingTieBreak = result.request
                    }
                }
            }
        )
        pendingTieBreak
            ?.takeIf { it.discipline == com.summ0.tournamentscoringapp.engine.HyungDiscipline.HYUNGS }
            ?.let { request ->
                TieBreakResolutionDialog(
                    competitors = hyungsSheetCompetitors.filter { it.id in request.competitorIds },
                    judgeCount = request.judgeCount,
                    scoreInputsByCompetitor = hyungsTieBreakScoresByCompetitor,
                    judgeSelections = hyungsJudgeChoicesByGroup[request.groupKey] ?: List(request.judgeCount) { "" },
                    onDismiss = { pendingTieBreak = null },
                    onScoreChanged = { competitorId, judgeIndex, value ->
                        val existing = hyungsTieBreakScoresByCompetitor[competitorId] ?: List(request.judgeCount) { "" }
                        val updated = existing.toMutableList()
                        updated[judgeIndex] = value.filter { it.isDigit() || it == '.' }.take(4)
                        hyungsTieBreakScoresByCompetitor[competitorId] = updated
                    },
                    onJudgeSelectionChanged = { judgeIndex, competitorId ->
                        val currentSelections = (hyungsJudgeChoicesByGroup[request.groupKey]
                            ?: List(request.judgeCount) { "" }).toMutableList()
                        currentSelections[judgeIndex] = competitorId
                        hyungsJudgeChoicesByGroup[request.groupKey] = currentSelections
                    },
                    onApply = {
                        when (
                            val result = finalizePlacements(
                                competitors = hyungsSheetCompetitors,
                                scoresByCompetitor = hyungsScoresByCompetitor,
                                discipline = com.summ0.tournamentscoringapp.engine.HyungDiscipline.HYUNGS,
                                tieBreakScoresByCompetitor = hyungsTieBreakScoresByCompetitor,
                                judgeChoicesByGroup = hyungsJudgeChoicesByGroup
                            )
                        ) {
                            is PlacementFinalizeResult.Completed -> {
                                hyungsFinalizeState = PlacementFinalizeState(
                                    labels = result.labels,
                                    tieBreakDetails = result.tieBreakDetails,
                                    message = "Placings finalized."
                                )
                                pendingTieBreak = null
                            }

                            is PlacementFinalizeResult.Incomplete -> {
                                hyungsFinalizeState = PlacementFinalizeState(message = result.message)
                            }

                            is PlacementFinalizeResult.NeedsTieBreak -> {
                                hyungsFinalizeState = PlacementFinalizeState(
                                    labels = result.pendingLabels,
                                    message = "Finish the second tie-break to finalize placings."
                                )
                                pendingTieBreak = result.request
                            }
                        }
                    }
                )
            }
        return
    }

    if (currentScreenState.value == CompetitionScreen.SPARRING_BRACKET) {
        SparringBracketScreen(
            groupBanner = currentGroupBanner,
            competitors = sparringCompetitors,
            winners = sparringWinners,
            boutProgressByKey = sparringBoutProgress,
            onBack = { moveToScreen(CompetitionScreen.CHECK_IN) },
            onPrevious = { moveToScreen(CompetitionScreen.CHECK_IN) },
            onNext = { moveToScreen(CompetitionScreen.OVERALL_AWARDS) },
            onExit = { requestExitApp() },
            onOpenBout = { roundIndex, boutIndex, bout ->
                activeSparringBout = ActiveSparringBout(
                    roundIndex = roundIndex,
                    boutIndex = boutIndex,
                    bout = bout
                )
            }
        )
        activeSparringBout?.let { activeBout ->
            val boutKey = sparringBoutKey(activeBout.roundIndex, activeBout.boutIndex)
            SparringBoutDialog(
                bout = activeBout.bout,
                initialProgress = sparringBoutProgress[boutKey] ?: SparringBoutProgress(),
                onDismiss = { activeSparringBout = null },
                onSave = { progress ->
                    sparringBoutProgress[boutKey] = progress
                    val winner = activeBout.bout.blue.competitor?.takeIf { it.id == progress.winnerId }
                        ?: activeBout.bout.red.competitor?.takeIf { it.id == progress.winnerId }
                    if (winner != null) {
                        val currentRoundWinners = sparringWinners[activeBout.roundIndex] ?: emptyMap()
                        sparringWinners[activeBout.roundIndex] =
                            currentRoundWinners + (activeBout.boutIndex to winner)

                        if (CompetitionType.SPARRING !in lockedPhases) {
                            lockedPhases = lockedPhases + CompetitionType.SPARRING
                            phaseEntrants = phaseEntrants + (
                                CompetitionType.SPARRING to entrantsForPhase(competitors, CompetitionType.SPARRING)
                                )
                        }
                    }
                    activeSparringBout = null
                }
            )
        }
        return
    }

    if (currentScreenState.value == CompetitionScreen.OVERALL_AWARDS) {
        val round0Bouts = remember(sparringCompetitors) {
            if (sparringCompetitors.isEmpty()) {
                emptyList()
            } else {
                val seed = sparringCompetitors.sortedBy { it.id }.fold(0) { acc, competitor ->
                    31 * acc + competitor.id.hashCode()
                }
                separateStudioPairs(TournamentEngine.createFirstRoundSparringBouts(sparringCompetitors, seed))
            }
        }
        val overallAwards = buildOverallAwardsSummary(
            competitors = competitors,
            weaponsLabels = weaponsFinalizeState.labels,
            hyungsLabels = hyungsFinalizeState.labels,
            sparringWinners = sparringWinners,
            round0Bouts = round0Bouts
        )
        OverallAwardsScreen(
            groupBanner = currentGroupBanner,
            divisionId = activeDivision.id.ifBlank { currentGroupBanner.groupId },
            divisionType = activeDivision.name.ifBlank { "Division" },
            divisionCompetitors = competitors,
            groupSize = competitors.size,
            requiredJudgeCount = judgeCount,
            weaponsPlacements = overallAwards.weapons,
            hyungsPlacements = overallAwards.hyungs,
            sparringPlacements = overallAwards.sparring,
            weaponsCompetitors = weaponsSheetCompetitors,
            weaponsScoresByCompetitor = weaponsScoresByCompetitor,
            weaponsPlacementsByCompetitorId = weaponsFinalizeState.labels,
            weaponsTieBreakDetailsByCompetitorId = weaponsFinalizeState.tieBreakDetails,
            hyungsCompetitors = hyungsSheetCompetitors,
            hyungsScoresByCompetitor = hyungsScoresByCompetitor,
            hyungsPlacementsByCompetitorId = hyungsFinalizeState.labels,
            hyungsTieBreakDetailsByCompetitorId = hyungsFinalizeState.tieBreakDetails,
            sparringReviewLines = buildSparringReviewLines(
                round0Bouts = round0Bouts,
                winnersByRound = sparringWinners,
                boutProgressByKey = sparringBoutProgress
            ),
            onCompleteRound = { packet, snapshotBytes, snapshotMimeType ->
                completeCurrentGroup(packet, snapshotBytes, snapshotMimeType)
            },
            onBackToSparring = { moveToScreen(CompetitionScreen.SPARRING_BRACKET) },
            onExit = { requestExitApp() }
        )
        return
    }

    if (showAddDialog || editingCompetitor != null) {
        CompetitorFormDialog(
            division = activeDivision,
            initialCompetitor = editingCompetitor,
            onDismiss = {
                showAddDialog = false
                editingCompetitorId = null
            },
            onSaveCompetitor = { savedCompetitor ->
                if (editingCompetitor == null) {
                    competitorsState.value = competitors + savedCompetitor
                } else {
                    competitorsState.value = competitors.map { current ->
                        if (current.id == savedCompetitor.id) savedCompetitor else current
                    }
                }
                showAddDialog = false
                editingCompetitorId = null
            }
        )
    }

    if (showServerConfigDialog) {
        ServerConfigDialog(
            serverConnectionModeName = serverConnectionModeName,
            onServerConnectionModeChange = {
                serverConnectionModeName = it
                if (parseServerConnectionMode(it) == ServerConnectionMode.DNS && lastDnsName.isBlank()) {
                    lastDnsName = DEFAULT_SERVER_HOSTNAME
                }
            },
            serverAddress = serverAddressForCurrentMode,
            onServerAddressChange = {
                if (parseServerConnectionMode(serverConnectionModeName) == ServerConnectionMode.DNS) {
                    lastDnsName = it
                } else {
                    lastServerAddress = it
                }
            },
            status = launchServerStatus,
            isLoading = isCheckingLaunchServer,
            onRetry = {
                showServerConfigDialog = false
                refreshLaunchRingOptions()
            },
            onDismiss = { showServerConfigDialog = false }
        )
    }

    if (showAssistanceDialog) {
        AssistanceRequestDialog(
            ringLabel = currentRingLabel.ifBlank { currentRingId },
            isLoading = isLoadingGroup || isCompletingGroup,
            onRequestMedical = { requestRingAssistance("medical", "Medical assistance") },
            onRequestArbitrator = { requestRingAssistance("arbitrator", "Arbitrator") },
            onRequestGeneral = { requestRingAssistance("general", "General assistance") },
            onClearRequest = { clearRingAssistance() },
            onDismiss = { showAssistanceDialog = false }
        )
    }

    if (showUpdateDialog) {
        val updateContext = LocalContext.current
        AlertDialog(
            onDismissRequest = { if (!isDownloadingUpdate) showUpdateDialog = false },
            title = { Text("Update Available", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("A newer version of the app is available on the server.")
                    if (isDownloadingUpdate) Text("Downloading update...")
                    if (updateDownloadError.isNotEmpty()) {
                        Text(updateDownloadError, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isDownloadingUpdate = true
                        updateDownloadError = ""
                        screenScope.launch {
                            try {
                                val apkUrl = "${serverBaseUrl.trimEnd('/')}/download-app"
                                val dir = File(updateContext.cacheDir, "apk_downloads").also { it.mkdirs() }
                                val apkFile = File(dir, "update.apk")
                                withContext(Dispatchers.IO) {
                                    val conn = URL(apkUrl).openConnection() as HttpURLConnection
                                    conn.connect()
                                    conn.inputStream.use { input ->
                                        apkFile.outputStream().use { output -> input.copyTo(output) }
                                    }
                                    conn.disconnect()
                                }
                                installApk(updateContext, apkFile)
                                showUpdateDialog = false
                            } catch (e: Exception) {
                                updateDownloadError = "Download failed: ${e.message}"
                            } finally {
                                isDownloadingUpdate = false
                            }
                        }
                    },
                    enabled = !isDownloadingUpdate
                ) {
                    Text(if (isDownloadingUpdate) "Downloading..." else "Update Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDialog = false }, enabled = !isDownloadingUpdate) {
                    Text("Later")
                }
            }
        )
    }

    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val screenWidthDp = with(density) { windowInfo.containerSize.width.toDp().value }
    val isCompactScreen = screenWidthDp < 700
    val wideLandscapeCheckIn = isLandscape && screenWidthDp >= 760f
    val denseCheckInLayout = isCompactScreen || competitors.size >= 8
    val showLaunchScreen = currentScreenState.value == CompetitionScreen.CHECK_IN && !currentGroupBanner.isLoaded
    val connectionStateHeadline = when {
        currentRingId.isBlank() -> "Not assigned"
        ringConnectionNeedsReconnect -> "Connection lost"
        else -> "Connected"
    }
    val connectionStateBody = when {
        currentRingId.isBlank() -> "Connect a ring to begin."
        ringConnectionNeedsReconnect -> "Reconnect Ring to restore contact."
        currentGroupBanner.isLoaded -> ""
        else -> "Waiting for the next group."
    }
    val connectionActionLabel = if (ringConnectionNeedsReconnect && currentRingId.isNotBlank()) {
        "Reconnect Ring"
    } else {
        "Connect Ring"
    }
    val showConnectionAction = currentRingId.isBlank() || ringConnectionNeedsReconnect

    if (showLaunchScreen) {
        LaunchScreenPanel(
            currentRingLabel = currentRingLabel,
            currentRingId = currentRingId,
            status = if (currentRingId.isBlank()) launchServerStatus else remoteStatus,
            serverReachable = launchServerReachable,
            availableRings = launchRingOptions,
            selectedRingId = selectedLaunchRingId,
            isCompactScreen = isCompactScreen,
            onSelectRing = { selectedLaunchRingId = it },
            onConnectSelectedRing = { connectSelectedRing() },
            onEditServer = { showServerConfigDialog = true },
            onExit = { requestExitApp() }
        )
    } else {
        if (wideLandscapeCheckIn) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = if (screenWidthDp >= 1200f) 360.dp else 420.dp),
                modifier = modifier
                    .fillMaxSize()
                    .padding(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    CheckInHeaderCard(
                        currentGroupBanner = currentGroupBanner,
                        currentRingLabel = currentRingLabel,
                        connectionStateHeadline = connectionStateHeadline,
                        connectionStateBody = connectionStateBody,
                        checkedInCount = checkedInCount,
                        useOnlyThreeJudges = useOnlyThreeJudges,
                        onUseOnlyThreeJudgesChange = { useOnlyThreeJudges = it },
                        showAssistanceButton = currentRingId.isNotBlank(),
                        onAssistance = { showAssistanceDialog = true },
                        onExit = { requestExitApp() },
                        showConnectionAction = showConnectionAction,
                        connectionActionLabel = connectionActionLabel,
                        onConnectRing = { reconnectCurrentRing() },
                        nextCompetitionButtonLabel = nextCompetitionButtonLabel,
                        onOpenNextCompetition = {
                            if (weaponsSheetCompetitors.isNotEmpty()) {
                                moveToScreen(CompetitionScreen.WEAPONS_SCORING)
                            } else {
                                moveToScreen(CompetitionScreen.HYUNGS_SCORING)
                            }
                        },
                        entrantsSummaryText = entrantsSummaryText,
                        dense = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LandscapeCheckInHeaderRow(modifier = Modifier.fillMaxWidth())
                }
                items(competitors, key = { it.id }) { competitor ->
                    LandscapeCompetitorCheckInRow(
                        competitor = competitor,
                        sparringOptedOut = isSparringOptedOut(competitor),
                        canEditSparring = CompetitionType.SPARRING !in lockedPhases,
                        onToggleCheckIn = {
                            val nextStatus = when (competitor.checkInStatus) {
                                CheckInStatus.CHECKED_IN -> CheckInStatus.REGISTERED
                                CheckInStatus.REGISTERED,
                                CheckInStatus.NO_SHOW -> CheckInStatus.CHECKED_IN
                            }
                            competitorsState.value = competitors.map { current ->
                                if (current.id == competitor.id) {
                                    val updated = TournamentEngine.updateCompetitorCheckInStatus(current, nextStatus)
                                    if (nextStatus == CheckInStatus.CHECKED_IN) applyCheckInDefaults(updated) else updated
                                } else {
                                    current
                                }
                            }
                        },
                        onToggleSparringOptOut = {
                            if (CompetitionType.SPARRING !in lockedPhases) {
                                competitorsState.value = competitors.map { current ->
                                    if (current.id == competitor.id) {
                                        val nextEntryStatus = if (isSparringOptedOut(current)) {
                                            CompetitionRegistrationStatus.REGISTERED
                                        } else {
                                            CompetitionRegistrationStatus.SCRATCHED
                                        }
                                        TournamentEngine.updateCompetitionEntryStatus(
                                            competitor = current,
                                            competitionType = CompetitionType.SPARRING,
                                            status = nextEntryStatus
                                        )
                                    } else {
                                        current
                                    }
                                }
                            }
                        }
                    )
                }
            }
        } else if (isLandscape) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = if (denseCheckInLayout) 220.dp else 240.dp),
                modifier = modifier
                    .fillMaxSize()
                    .padding(12.dp),
                contentPadding = PaddingValues(bottom = if (denseCheckInLayout) 12.dp else 24.dp),
                horizontalArrangement = Arrangement.spacedBy(if (denseCheckInLayout) 8.dp else 10.dp),
                verticalArrangement = Arrangement.spacedBy(if (denseCheckInLayout) 8.dp else 10.dp)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    CheckInHeaderCard(
                        currentGroupBanner = currentGroupBanner,
                        currentRingLabel = currentRingLabel,
                        connectionStateHeadline = connectionStateHeadline,
                        connectionStateBody = connectionStateBody,
                        checkedInCount = checkedInCount,
                        useOnlyThreeJudges = useOnlyThreeJudges,
                        onUseOnlyThreeJudgesChange = { useOnlyThreeJudges = it },
                        showAssistanceButton = currentRingId.isNotBlank(),
                        onAssistance = { showAssistanceDialog = true },
                        onExit = { requestExitApp() },
                        showConnectionAction = showConnectionAction,
                        connectionActionLabel = connectionActionLabel,
                        onConnectRing = { reconnectCurrentRing() },
                        nextCompetitionButtonLabel = nextCompetitionButtonLabel,
                        onOpenNextCompetition = {
                            if (weaponsSheetCompetitors.isNotEmpty()) {
                                moveToScreen(CompetitionScreen.WEAPONS_SCORING)
                            } else {
                                moveToScreen(CompetitionScreen.HYUNGS_SCORING)
                            }
                        },
                        entrantsSummaryText = entrantsSummaryText,
                        dense = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    CheckInColumnHeaderRow(
                        dense = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                items(competitors, key = { it.id }) { competitor ->
                    CompetitorCheckInCard(
                        competitor = competitor,
                        dense = denseCheckInLayout,
                        onToggleCheckIn = {
                            val nextStatus = when (competitor.checkInStatus) {
                                CheckInStatus.CHECKED_IN -> CheckInStatus.REGISTERED
                                CheckInStatus.REGISTERED,
                                CheckInStatus.NO_SHOW -> CheckInStatus.CHECKED_IN
                            }
                            competitorsState.value = competitors.map { current ->
                                if (current.id == competitor.id) {
                                    val updated = TournamentEngine.updateCompetitorCheckInStatus(current, nextStatus)
                                    if (nextStatus == CheckInStatus.CHECKED_IN) applyCheckInDefaults(updated) else updated
                                } else {
                                    current
                                }
                            }
                        },
                        sparringOptedOut = isSparringOptedOut(competitor),
                            canEditSparring = CompetitionType.SPARRING !in lockedPhases,
                            onToggleSparringOptOut = {
                                if (CompetitionType.SPARRING !in lockedPhases) {
                                    competitorsState.value = competitors.map { current ->
                                    if (current.id == competitor.id) {
                                        val nextEntryStatus = if (isSparringOptedOut(current)) {
                                            CompetitionRegistrationStatus.REGISTERED
                                        } else {
                                            CompetitionRegistrationStatus.SCRATCHED
                                        }
                                        TournamentEngine.updateCompetitionEntryStatus(
                                            competitor = current,
                                            competitionType = CompetitionType.SPARRING,
                                            status = nextEntryStatus
                                        )
                                    } else {
                                        current
                                    }
                                }
                            }
                        }
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(12.dp),
                contentPadding = PaddingValues(bottom = if (denseCheckInLayout) 12.dp else 24.dp),
                verticalArrangement = Arrangement.spacedBy(if (denseCheckInLayout) 4.dp else 10.dp)
            ) {
                item {
                    CheckInHeaderCard(
                        currentGroupBanner = currentGroupBanner,
                        currentRingLabel = currentRingLabel,
                        connectionStateHeadline = connectionStateHeadline,
                        connectionStateBody = connectionStateBody,
                        checkedInCount = checkedInCount,
                        useOnlyThreeJudges = useOnlyThreeJudges,
                        onUseOnlyThreeJudgesChange = { useOnlyThreeJudges = it },
                        showAssistanceButton = currentRingId.isNotBlank(),
                        onAssistance = { showAssistanceDialog = true },
                        onExit = { requestExitApp() },
                        showConnectionAction = showConnectionAction,
                        connectionActionLabel = connectionActionLabel,
                        onConnectRing = { reconnectCurrentRing() },
                        nextCompetitionButtonLabel = nextCompetitionButtonLabel,
                        onOpenNextCompetition = {
                            if (weaponsSheetCompetitors.isNotEmpty()) {
                                moveToScreen(CompetitionScreen.WEAPONS_SCORING)
                            } else {
                                moveToScreen(CompetitionScreen.HYUNGS_SCORING)
                            }
                        },
                        entrantsSummaryText = entrantsSummaryText,
                        dense = denseCheckInLayout,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (denseCheckInLayout) {
                    item {
                        CheckInColumnHeaderRow(
                            dense = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                items(competitors, key = { it.id }) { competitor ->
                    CompactCompetitorCheckInRow(
                        competitor = competitor,
                        sparringOptedOut = isSparringOptedOut(competitor),
                        canEditSparring = CompetitionType.SPARRING !in lockedPhases,
                        dense = denseCheckInLayout,
                        onToggleCheckIn = {
                            val nextStatus = when (competitor.checkInStatus) {
                                CheckInStatus.CHECKED_IN -> CheckInStatus.REGISTERED
                                CheckInStatus.REGISTERED,
                                CheckInStatus.NO_SHOW -> CheckInStatus.CHECKED_IN
                            }
                            competitorsState.value = competitors.map { current ->
                                if (current.id == competitor.id) {
                                    val updated = TournamentEngine.updateCompetitorCheckInStatus(current, nextStatus)
                                    if (nextStatus == CheckInStatus.CHECKED_IN) applyCheckInDefaults(updated) else updated
                                } else {
                                    current
                                }
                            }
                        },
                        onToggleSparringOptOut = {
                            if (CompetitionType.SPARRING !in lockedPhases) {
                                competitorsState.value = competitors.map { current ->
                                    if (current.id == competitor.id) {
                                        val nextEntryStatus = if (isSparringOptedOut(current)) {
                                            CompetitionRegistrationStatus.REGISTERED
                                        } else {
                                            CompetitionRegistrationStatus.SCRATCHED
                                        }
                                        TournamentEngine.updateCompetitionEntryStatus(
                                            competitor = current,
                                            competitionType = CompetitionType.SPARRING,
                                            status = nextEntryStatus
                                        )
                                    } else {
                                        current
                                    }
                                }
                            }
                        },
                    )
                    if (!denseCheckInLayout) {
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
internal fun prepareCompetitorsForCheckIn(competitors: List<Competitor>): List<Competitor> {
    return competitors.map { competitor ->
        val standardCheckIn = when (competitor.checkInStatus) {
            CheckInStatus.CHECKED_IN -> TournamentEngine.updateCompetitorCheckInStatus(competitor, CheckInStatus.REGISTERED)
            CheckInStatus.REGISTERED,
            CheckInStatus.NO_SHOW -> competitor
        }
        CompetitionType.values().fold(standardCheckIn) { current, type ->
            TournamentEngine.updateCompetitionEntryStatus(
                competitor = current,
                competitionType = type,
                status = CompetitionRegistrationStatus.REGISTERED
            )
        }
    }
}

private fun randomizeScoringSheetCompetitors(
    competitors: List<Competitor>,
    competitionType: CompetitionType
): List<Competitor> {
    if (competitors.size < 2) return competitors
    val seedSource = competitors.map { it.id }.sorted().joinToString("|")
    val seed = (seedSource.hashCode().toLong() shl 8) xor competitionType.ordinal.toLong()
    return competitors.shuffled(Random(seed))
}

@Composable
private fun CheckInHeaderCard(
    currentGroupBanner: GroupBanner,
    currentRingLabel: String,
    connectionStateHeadline: String,
    connectionStateBody: String,
    checkedInCount: Int,
    useOnlyThreeJudges: Boolean,
    onUseOnlyThreeJudgesChange: (Boolean) -> Unit,
    showAssistanceButton: Boolean,
    onAssistance: () -> Unit,
    onExit: () -> Unit,
    showConnectionAction: Boolean,
    connectionActionLabel: String,
    onConnectRing: () -> Unit,
    nextCompetitionButtonLabel: String?,
    onOpenNextCompetition: () -> Unit,
    entrantsSummaryText: String,
    dense: Boolean,
    modifier: Modifier = Modifier
) {
    val cardPadding = if (dense) 10.dp else 12.dp
    val sectionGap = if (dense) 4.dp else 8.dp
    val actionPadding = if (dense) PaddingValues(horizontal = 12.dp, vertical = 6.dp) else ButtonDefaults.ContentPadding
    val actionTextSize = if (dense) 13.sp else 14.sp
    val headlineSize = if (dense) 18.sp else 22.sp
    val bodySize = if (dense) 12.sp else 14.sp
    val labelSize = if (dense) 14.sp else 16.sp

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors()
    ) {
        Column(modifier = Modifier.padding(cardPadding), verticalArrangement = Arrangement.spacedBy(sectionGap)) {
            Box(modifier = Modifier.fillMaxWidth()) {
                if (showAssistanceButton) {
                    Button(
                        onClick = onAssistance,
                        enabled = true,
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = actionPadding,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        Text("Assistance", fontSize = actionTextSize)
                    }
                }
                BannerTitleStack(
                    screenTitle = "Check-In",
                    group = currentGroupBanner,
                    compact = dense,
                    showVersion = !dense,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center)
                )
                Button(
                    onClick = onExit,
                    contentPadding = actionPadding,
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Text("Exit App", fontSize = actionTextSize)
                }
            }
            Text(
                text = connectionStateHeadline,
                fontWeight = FontWeight.Bold,
                fontSize = headlineSize
            )
            if (connectionStateBody.isNotBlank()) {
                Text(
                    text = connectionStateBody,
                    fontSize = bodySize
                )
            }
            Text(
                text = if (currentRingLabel.isNotBlank()) {
                    "Ring: $currentRingLabel"
                } else {
                    "Ring: Not Assigned"
                },
                fontWeight = FontWeight.SemiBold,
                fontSize = labelSize
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(if (dense) 8.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Checked In: $checkedInCount",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = labelSize
                )
                if (currentGroupBanner.isLoaded) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = useOnlyThreeJudges,
                            onCheckedChange = onUseOnlyThreeJudgesChange
                        )
                        Text("Use only 3 judges", fontSize = bodySize)
                    }
                }
            }
            Text(
                text = entrantsSummaryText,
                fontSize = bodySize
            )
            Row(
                modifier = if (dense) Modifier else Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(if (dense) 6.dp else 8.dp)
            ) {
                if (showConnectionAction) {
                    Button(
                        onClick = onConnectRing,
                        enabled = true,
                        contentPadding = actionPadding,
                        modifier = Modifier.height(if (dense) 34.dp else 40.dp)
                    ) {
                        Text(connectionActionLabel, fontSize = actionTextSize)
                    }
                }
                if (currentGroupBanner.isLoaded) {
                    if (nextCompetitionButtonLabel != null) {
                        Button(
                            onClick = onOpenNextCompetition,
                            enabled = true,
                            contentPadding = actionPadding,
                            modifier = Modifier.height(if (dense) 34.dp else 40.dp)
                        ) {
                            Text(nextCompetitionButtonLabel, fontSize = actionTextSize)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LaunchScreenPanel(
    currentRingLabel: String,
    currentRingId: String,
    status: String,
    serverReachable: Boolean,
    availableRings: List<RingOption>,
    selectedRingId: String,
    isCompactScreen: Boolean,
    onSelectRing: (String) -> Unit,
    onConnectSelectedRing: () -> Unit,
    onEditServer: () -> Unit,
    onExit: () -> Unit
) {
    val ringLabel = currentRingLabel.ifBlank { currentRingId.ifBlank { "Not assigned" } }
    val isAssigned = currentRingId.isNotBlank()
    val selectedRing = availableRings.firstOrNull { it.ringId == selectedRingId }
    val anyAvailableRings = availableRings.any { it.isAvailable }
    val outerPadding = if (isCompactScreen) 8.dp else 12.dp
    val cardPadding = if (isCompactScreen) 12.dp else 16.dp
    val titleSize = if (isCompactScreen) 24.sp else 28.sp
    val sectionSize = if (isCompactScreen) 18.sp else 20.sp
    val stepSpacing = if (isCompactScreen) 10.dp else 12.dp
    val scrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()
    val letters = availableRings.mapNotNull { parseRingGridCoordinates(it.ringLabel)?.first }
        .distinct()
        .sorted()
    val numbers = availableRings.mapNotNull { parseRingGridCoordinates(it.ringLabel)?.second }
        .distinct()
        .sorted()
    val ringByCoordinate = availableRings.mapNotNull { ring ->
        val coordinate = parseRingGridCoordinates(ring.ringLabel) ?: return@mapNotNull null
        (coordinate.first to coordinate.second) to ring
    }.toMap()
    val ringCellSize = centimeters(1f)
    val ringCellGap = centimeters(0.5f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(outerPadding)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(stepSpacing)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Tournament Scoring App",
                fontWeight = FontWeight.Bold,
                fontSize = titleSize,
                modifier = Modifier.align(Alignment.CenterStart)
            )
            Button(
                onClick = onExit,
                contentPadding = if (isCompactScreen) PaddingValues(horizontal = 12.dp, vertical = 8.dp) else ButtonDefaults.ContentPadding,
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Text("Exit App")
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(cardPadding),
                verticalArrangement = Arrangement.spacedBy(if (isCompactScreen) 10.dp else 12.dp)
            ) {
                Text(
                    text = when {
                        isAssigned -> "Connected"
                        serverReachable -> "Select Ring"
                        else -> "Server Status"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = sectionSize
                )
                Text(
                    text = when {
                        isAssigned -> "Ring: $ringLabel"
                        serverReachable && availableRings.isNotEmpty() && !anyAvailableRings -> "All rings are in use."
                        serverReachable && availableRings.isNotEmpty() -> "Choose one ring to connect."
                        else -> status.ifBlank { "Checking server..." }
                    },
                    fontSize = if (isCompactScreen) 15.sp else 16.sp
                )
                if (!serverReachable && status.startsWith("Unable")) {
                    Text(
                        text = status,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = if (isCompactScreen) 13.sp else 14.sp
                    )
                }
                if (!isAssigned && serverReachable && availableRings.isNotEmpty()) {
                    Text("Choose one ring to connect.", fontWeight = FontWeight.SemiBold)
                    Column(
                        modifier = Modifier.horizontalScroll(horizontalScrollState),
                        verticalArrangement = Arrangement.spacedBy(ringCellGap)
                    ) {
                        numbers.forEach { number ->
                            Row(horizontalArrangement = Arrangement.spacedBy(ringCellGap)) {
                                letters.forEach { letter ->
                                    val ring = ringByCoordinate[letter to number]
                                    if (ring == null) {
                                        Spacer(modifier = Modifier.size(ringCellSize))
                                    } else {
                                        val isSelected = ring.ringId == selectedRingId
                                        val isDisabled = !ring.isAvailable && !isSelected
                                        val backgroundColor = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.surface
                                        }
                                        val contentColor = if (isSelected) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else if (isDisabled) {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(ringCellSize)
                                                .background(backgroundColor, RoundedCornerShape(4.dp))
                                                .border(
                                                    width = 1.dp,
                                                    color = if (isDisabled) {
                                                        MaterialTheme.colorScheme.outlineVariant
                                                    } else {
                                                        MaterialTheme.colorScheme.outline
                                                    },
                                                    shape = RoundedCornerShape(4.dp)
                                                )
                                                .clickable(enabled = !isDisabled) { onSelectRing(ring.ringId) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = ring.ringLabel,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                textAlign = TextAlign.Center,
                                                color = contentColor,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Button(
                        onClick = onConnectSelectedRing,
                        enabled = selectedRing?.isAvailable == true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                    ) {
                        Text("Connect Ring")
                    }
                } else if (isAssigned) {
                    Text(
                        text = "Connected. Auto-load will keep checking for the next group.",
                        fontSize = if (isCompactScreen) 13.sp else 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!serverReachable) {
                    if (status.startsWith("Waiting for server") || status.startsWith("Unable")) {
                        Button(
                            onClick = onEditServer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                        ) {
                            Text("Update Server Config")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompetitorFormDialog(
    division: Division,
    initialCompetitor: Competitor?,
    onDismiss: () -> Unit,
    onSaveCompetitor: (Competitor) -> Unit
) {
    val isEditMode = initialCompetitor != null
    val canEditName = !isEditMode || isManualCompetitor(initialCompetitor)
    val validRanks = remember(division.rankRange) {
        allRankLabels().filter { rankLevelFor(it) in division.rankRange }
    }
    val requiresAgeInput = remember(division.ageRange) { division.ageRange.last < 18 }
    var name by rememberSaveable(initialCompetitor?.id) {
        mutableStateOf(initialCompetitor?.name ?: "")
    }
    var studio by rememberSaveable(initialCompetitor?.id) {
        mutableStateOf(initialCompetitor?.studio ?: "")
    }
    var rank by rememberSaveable(initialCompetitor?.id) {
        mutableStateOf(initialCompetitor?.rank ?: (validRanks.firstOrNull() ?: ""))
    }
    var ageText by rememberSaveable(initialCompetitor?.id, requiresAgeInput, division.ageRange.first) {
        mutableStateOf(
            initialCompetitor?.age?.toString()
                ?: if (requiresAgeInput) "" else division.ageRange.first.toString()
        )
    }
    var heightText by rememberSaveable(initialCompetitor?.id) {
        mutableStateOf(initialCompetitor?.heightInInches?.toString() ?: "60")
    }
    var message by rememberSaveable(initialCompetitor?.id) { mutableStateOf<String?>(null) }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .imePadding()
                .heightIn(max = 620.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(if (isEditMode) "Edit Competitor" else "Add Competitor")
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = capitalizeWords(it) },
                    label = { Text("Name") },
                    singleLine = true,
                    enabled = canEditName,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        keyboardType = KeyboardType.Text
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                if (!canEditName) {
                    Text("Name is locked for existing roster competitors.")
                }
                OutlinedTextField(
                    value = studio,
                    onValueChange = { studio = it },
                    label = { Text("Studio") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                DropdownSelector(
                    label = "Rank",
                    selectedValue = rank,
                    options = validRanks,
                    onSelect = { rank = it },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = ageText,
                    onValueChange = { ageText = it },
                    label = { Text(if (requiresAgeInput) "Age" else "Age (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = heightText,
                    onValueChange = { heightText = it },
                    label = { Text("Height (in)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (message != null) {
                    Text(message ?: "")
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        val parsedAge = ageText.toIntOrNull()
                        val resolvedAge = when {
                            parsedAge != null -> parsedAge
                            !requiresAgeInput -> initialCompetitor?.age ?: division.ageRange.first
                            else -> null
                        }
                        val height = heightText.toIntOrNull()

                        when {
                            name.isBlank() -> message = "Name is required."
                            studio.isBlank() -> message = "Studio is required."
                            validRanks.isEmpty() -> message = "No valid ranks available for this group."
                            !requiresAgeInput && ageText.isNotBlank() && parsedAge == null ->
                                message = "Age must be a whole number."
                            resolvedAge == null -> message = "Age is required for under-18 groups."
                            height == null -> message = "Height must be a whole number."
                            else -> {
                                val candidate = Competitor(
                                    id = initialCompetitor?.id ?: "manual-${System.currentTimeMillis()}",
                                    name = capitalizeWords(name.trim()),
                                    studio = studio.trim(),
                                    rank = rank.trim(),
                                    rankLevel = rankLevelFor(rank.trim()),
                                    age = resolvedAge,
                                    heightInInches = height,
                                    checkInStatus = initialCompetitor?.checkInStatus ?: CheckInStatus.CHECKED_IN,
                                    competitionEntries = initialCompetitor?.competitionEntries ?: emptyMap()
                                )
                                val validationMessage = validateCompetitorForGroup(division, candidate)
                                if (validationMessage != null) {
                                    message = validationMessage
                                } else {
                                    val readyCompetitor = if (isEditMode) {
                                        candidate
                                    } else {
                                        applyCheckInDefaults(candidate)
                                    }
                                    onSaveCompetitor(readyCompetitor)
                                }
                            }
                        }
                    }) {
                        Text(if (isEditMode) "Save" else "Add")
                    }
                }
            }
        }
    }
}

@Composable
private fun TieBreakResolutionDialog(
    competitors: List<Competitor>,
    judgeCount: Int,
    scoreInputsByCompetitor: Map<String, List<String>>,
    judgeSelections: List<String>,
    onDismiss: () -> Unit,
    onScoreChanged: (competitorId: String, judgeIndex: Int, value: String) -> Unit,
    onJudgeSelectionChanged: (judgeIndex: Int, competitorId: String) -> Unit,
    onApply: () -> Unit
) {
    val secondRoundCandidates = competitors.mapNotNull { competitor ->
        val inputs = scoreInputsByCompetitor[competitor.id] ?: return@mapNotNull null
        if (inputs.count { it.isNotBlank() } != judgeCount) return@mapNotNull null
        val total = calculateScoreTotal(inputs) ?: return@mapNotNull null
        PlacementCandidate(
            competitor = competitor,
            scores = totalsToScores(inputs),
            total = total,
            judgeCount = judgeCount
        )
    }.sortedWith(
        compareByDescending<PlacementCandidate> { it.total }
            .thenBy { it.competitor.name }
    )
    val secondRoundStillTied = secondRoundCandidates.size == competitors.size &&
        secondRoundCandidates.size > 1 &&
        secondRoundCandidates.drop(1).any { samePrimaryTieKey(secondRoundCandidates.first(), it) }
    val voteCounts = judgeSelections.filter { it.isNotBlank() }.groupingBy { it }.eachCount()
    val hasUniqueJudgeChoiceLeader = voteCounts.isNotEmpty() && voteCounts.values.count { it == voteCounts.values.maxOrNull() } == 1
    val canApply = if (secondRoundStillTied) {
        judgeSelections.size == judgeCount && judgeSelections.all { it.isNotBlank() } && hasUniqueJudgeChoiceLeader
    } else {
        secondRoundCandidates.size == competitors.size
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .imePadding()
                .heightIn(max = 720.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Second Tie-Break")
                Text("Repeat the same form and enter the second set of scores.")
                val horizontalState = rememberScrollState()
                Column(modifier = Modifier.horizontalScroll(horizontalState)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Competitor", modifier = Modifier.width(180.dp))
                        repeat(judgeCount) { judgeIndex ->
                            Text("J${judgeIndex + 1}", modifier = Modifier.width(72.dp))
                        }
                            Text("Total", modifier = Modifier.width(120.dp))
                    }
                    competitors.forEach { competitor ->
                        val inputs = scoreInputsByCompetitor[competitor.id] ?: List(judgeCount) { "" }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(competitor.name, modifier = Modifier.width(180.dp))
                            repeat(judgeCount) { judgeIndex ->
                                OutlinedTextField(
                                    value = inputs.getOrElse(judgeIndex) { "" },
                                    onValueChange = { onScoreChanged(competitor.id, judgeIndex, it) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.width(72.dp)
                                )
                            }
                            Text(
                                text = calculateScoreSummary(inputs),
                                modifier = Modifier.width(120.dp)
                            )
                        }
                    }
                }
                if (secondRoundStillTied) {
                    Text("Still tied after the second performance and high/low restoration. Record judge choice.")
                    repeat(judgeCount) { judgeIndex ->
                        JudgeChoiceSelector(
                            label = "Judge ${judgeIndex + 1}",
                            selectedCompetitorId = judgeSelections.getOrElse(judgeIndex) { "" },
                            competitors = competitors,
                            onSelect = { onJudgeSelectionChanged(judgeIndex, it) }
                        )
                    }
                    competitors.forEach { competitor ->
                        Text("${competitor.name}: ${voteCounts[competitor.id] ?: 0} votes")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onApply,
                        enabled = canApply
                    ) {
                        Text("Apply Tie-Break")
                    }
                }
            }
        }
    }
}

@Composable
private fun JudgeChoiceSelector(
    label: String,
    selectedCompetitorId: String,
    competitors: List<Competitor>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = competitors.firstOrNull { it.id == selectedCompetitorId }?.name.orEmpty()
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { expanded = true },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp)
        ) {
            Text("Pick")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            competitors.forEach { competitor ->
                DropdownMenuItem(
                    text = { Text(competitor.name) },
                    onClick = {
                        onSelect(competitor.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun WeaponsScoringScreen(
    groupBanner: GroupBanner,
    competitors: List<Competitor>,
    scoresByCompetitor: Map<String, List<String>>,
    placementState: PlacementFinalizeState,
    judgeCount: Int,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onExit: () -> Unit,
    onScoreChanged: (competitorId: String, judgeIndex: Int, value: String) -> Unit,
    onFinalizePlacings: () -> Unit
) {
    val verticalState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(verticalState)
            .imePadding()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onBack) {
                    Text("Back to Check-In")
                }
                Button(onClick = onNext) {
                    Text("Next: Hyungs")
                }
            }
            Button(
                onClick = onExit,
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Text("Exit App")
            }
        }
        ScoringSheetBanner(title = "Weapons", group = groupBanner)
        WeaponsScoringSheetCard(
            competitors = competitors,
            scoresByCompetitor = scoresByCompetitor,
            placementState = placementState,
            judgeCount = judgeCount,
            onFinalizePlacings = onFinalizePlacings,
            onScoreChanged = onScoreChanged
        )
    }
}

@Composable
private fun WeaponsScoringSheetCard(
    competitors: List<Competitor>,
    scoresByCompetitor: Map<String, List<String>>,
    placementState: PlacementFinalizeState,
    judgeCount: Int,
    onFinalizePlacings: () -> Unit,
    onScoreChanged: (competitorId: String, judgeIndex: Int, value: String) -> Unit
) {
    val rankForms = remember(competitors) {
        competitors.map { it.rank }
            .distinct()
            .sortedBy(::rankOrder)
            .associateWith { rank ->
                TournamentEngine.getAllowedHyungForms(rank, com.summ0.tournamentscoringapp.engine.HyungDiscipline.WEAPONS)
            }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onFinalizePlacings,
                    enabled = competitors.isNotEmpty()
                ) {
                    Text(if (placementState.labels.isEmpty()) "Finalize Placings" else "Recalculate Placings")
                }
            }
            if (rankForms.isEmpty()) {
                Text("No eligible competitors are currently available for weapons.")
            } else {
                Text("Eligible forms by rank:")
                rankForms.forEach { (rank, forms) ->
                    val formsLabel = if (forms.isEmpty()) "No weapons forms configured." else forms.joinToString(", ")
                    Text("$rank: $formsLabel")
                }
            }
            placementState.message?.let {
                Text(
                    text = it,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End
                )
            }

            val horizontalState = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(horizontalState),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ScoresHeaderRow(judgeCount)
                    competitors.forEach { competitor ->
                        val rowInputs = normalizedJudgeInputs(scoresByCompetitor[competitor.id], judgeCount)
                        val summary = buildScoreDisplayLabel(
                            baseLabel = calculateScoreSummary(rowInputs),
                            tieBreakDetail = placementState.tieBreakDetails[competitor.id]
                        )
                        WeaponScoreRow(
                            competitor = competitor,
                            inputs = rowInputs,
                            totalLabel = summary,
                            placementLabel = placementState.labels[competitor.id].orEmpty(),
                            judgeCount = judgeCount,
                            onScoreChanged = { judgeIndex, rawValue ->
                                onScoreChanged(competitor.id, judgeIndex, rawValue)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoresHeaderRow(judgeCount: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Competitor", modifier = Modifier.width(180.dp))
        Text("Rank", modifier = Modifier.width(90.dp))
        repeat(judgeCount) { judgeIndex ->
            Text("J${judgeIndex + 1}", modifier = Modifier.width(72.dp))
        }
        Text("Total", modifier = Modifier.width(170.dp))
        Text("Place", modifier = Modifier.width(120.dp))
    }
}

private fun normalizedJudgeInputs(inputs: List<String>?, judgeCount: Int): List<String> {
    val trimmed = inputs.orEmpty().take(judgeCount)
    return trimmed + List(judgeCount - trimmed.size) { "" }
}

@Composable
private fun WeaponScoreRow(
    competitor: Competitor,
    inputs: List<String>,
    totalLabel: String,
    placementLabel: String,
    judgeCount: Int,
    onScoreChanged: (judgeIndex: Int, value: String) -> Unit
) {
    val focusRequesters = remember(judgeCount) { List(judgeCount) { FocusRequester() } }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(competitor.name, modifier = Modifier.width(180.dp))
        Text(competitor.rank, modifier = Modifier.width(90.dp))
        repeat(judgeCount) { judgeIndex ->
            OutlinedTextField(
                value = inputs[judgeIndex],
                onValueChange = { rawValue ->
                    onScoreChanged(judgeIndex, rawValue)
                    if (
                        judgeIndex < judgeCount - 1 &&
                        rawValue.length >= 3 &&
                        rawValue.contains('.') &&
                        rawValue.lastOrNull()?.isDigit() == true
                    ) {
                        focusRequesters[judgeIndex + 1].requestFocus()
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .width(72.dp)
                    .focusRequester(focusRequesters[judgeIndex])
            )
        }
        Text(totalLabel, modifier = Modifier.width(170.dp))
        Text(placementLabel, modifier = Modifier.width(120.dp))
    }
}

@Composable
private fun HyungsScoringScreen(
    groupBanner: GroupBanner,
    competitors: List<Competitor>,
    scoresByCompetitor: Map<String, List<String>>,
    placementState: PlacementFinalizeState,
    judgeCount: Int,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onExit: () -> Unit,
    onScoreChanged: (competitorId: String, judgeIndex: Int, value: String) -> Unit,
    onFinalizePlacings: () -> Unit
) {
    val verticalState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(verticalState)
            .imePadding()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onBack) {
                    Text("Back to Check-In")
                }
                Button(onClick = onNext) {
                    Text("Next: Sparring")
                }
            }
            Button(
                onClick = onExit,
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Text("Exit App")
            }
        }
        ScoringSheetBanner(title = "Hyungs", group = groupBanner)
        HyungsScoringSheetCard(
            competitors = competitors,
            scoresByCompetitor = scoresByCompetitor,
            placementState = placementState,
            judgeCount = judgeCount,
            onFinalizePlacings = onFinalizePlacings,
            onScoreChanged = onScoreChanged
        )
    }
}

@Composable
private fun HyungsScoringSheetCard(
    competitors: List<Competitor>,
    scoresByCompetitor: Map<String, List<String>>,
    placementState: PlacementFinalizeState,
    judgeCount: Int,
    onFinalizePlacings: () -> Unit,
    onScoreChanged: (competitorId: String, judgeIndex: Int, value: String) -> Unit
) {
    val rankForms = remember(competitors) {
        competitors.map { it.rank }
            .distinct()
            .sortedBy(::rankOrder)
            .associateWith { rank ->
                TournamentEngine.getAllowedHyungForms(rank, com.summ0.tournamentscoringapp.engine.HyungDiscipline.HYUNGS)
            }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onFinalizePlacings,
                    enabled = competitors.isNotEmpty()
                ) {
                    Text(if (placementState.labels.isEmpty()) "Finalize Placings" else "Recalculate Placings")
                }
            }
            if (rankForms.isEmpty()) {
                Text("No eligible competitors are currently available for hyungs.")
            } else {
                Text("Eligible forms by rank:")
                rankForms.forEach { (rank, forms) ->
                    val formsLabel = if (forms.isEmpty()) "No hyung forms configured." else forms.joinToString(", ")
                    Text("$rank: $formsLabel")
                }
            }
            placementState.message?.let {
                Text(
                    text = it,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End
                )
            }
            val horizontalState = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(horizontalState),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ScoresHeaderRow(judgeCount)
                    competitors.forEach { competitor ->
                        val rowInputs = normalizedJudgeInputs(scoresByCompetitor[competitor.id], judgeCount)
                        val summary = buildScoreDisplayLabel(
                            baseLabel = calculateScoreSummary(rowInputs),
                            tieBreakDetail = placementState.tieBreakDetails[competitor.id]
                        )
                        HyungScoreRow(
                            competitor = competitor,
                            inputs = rowInputs,
                            totalLabel = summary,
                            placementLabel = placementState.labels[competitor.id].orEmpty(),
                            judgeCount = judgeCount,
                            onScoreChanged = { judgeIndex, rawValue ->
                                onScoreChanged(competitor.id, judgeIndex, rawValue)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HyungScoreRow(
    competitor: Competitor,
    inputs: List<String>,
    totalLabel: String,
    placementLabel: String,
    judgeCount: Int,
    onScoreChanged: (judgeIndex: Int, value: String) -> Unit
) {
    val focusRequesters = remember(judgeCount) { List(judgeCount) { FocusRequester() } }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(competitor.name, modifier = Modifier.width(180.dp))
        Text(competitor.rank, modifier = Modifier.width(90.dp))
        repeat(judgeCount) { judgeIndex ->
            OutlinedTextField(
                value = inputs[judgeIndex],
                onValueChange = { rawValue ->
                    onScoreChanged(judgeIndex, rawValue)
                    if (
                        judgeIndex < judgeCount - 1 &&
                        rawValue.length >= 3 &&
                        rawValue.contains('.') &&
                        rawValue.lastOrNull()?.isDigit() == true
                    ) {
                        focusRequesters[judgeIndex + 1].requestFocus()
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .width(72.dp)
                    .focusRequester(focusRequesters[judgeIndex])
            )
        }
        Text(totalLabel, modifier = Modifier.width(170.dp))
        Text(placementLabel, modifier = Modifier.width(120.dp))
    }
}

private fun finalizePlacements(
    competitors: List<Competitor>,
    scoresByCompetitor: Map<String, List<String>>,
    discipline: com.summ0.tournamentscoringapp.engine.HyungDiscipline,
    tieBreakScoresByCompetitor: Map<String, List<String>>,
    judgeChoicesByGroup: Map<String, List<String>>
): PlacementFinalizeResult {
    if (competitors.isEmpty()) {
        return PlacementFinalizeResult.Incomplete("No competitors available.")
    }

    val candidates = competitors.map { competitor ->
        val inputs = scoresByCompetitor[competitor.id]
            ?: return PlacementFinalizeResult.Incomplete("Enter scores for every competitor before finalizing.")
        val total = calculateScoreTotal(inputs)
            ?: return PlacementFinalizeResult.Incomplete("Each competitor needs 3 or 5 valid scores.")
        PlacementCandidate(
            competitor = competitor,
            scores = totalsToScores(inputs),
            total = total,
            judgeCount = totalsToScores(inputs).size
        )
    }.sortedWith(
        compareByDescending<PlacementCandidate> { it.total }
            .thenBy { it.competitor.name }
    )

    val rankedEntries = mutableListOf<Pair<PlacementCandidate, String?>>()
    val templateLabels = listOf("1st Place", "2nd Place", "Co-3rd Place", "Co-3rd Place")
    var index = 0
    while (index < candidates.size && rankedEntries.size < templateLabels.size) {
        val current = candidates[index]
        val tieGroup = mutableListOf(current)
        index++
        while (index < candidates.size && samePrimaryTieKey(current, candidates[index])) {
            tieGroup += candidates[index]
            index++
        }

        if (tieGroup.size == 1) {
            rankedEntries += current to null
            continue
        }

        val groupKey = tieGroupKey(tieGroup)
        when (
            val tieResolution = resolveTieGroup(
                tieGroup = tieGroup,
                tieBreakScoresByCompetitor = tieBreakScoresByCompetitor,
                judgeChoices = judgeChoicesByGroup[groupKey] ?: emptyList()
            )
        ) {
            null -> {
                return PlacementFinalizeResult.NeedsTieBreak(
                    pendingLabels = tieGroup.associate { it.competitor.id to "Tie Pending" },
                    request = PendingTieBreak(
                        discipline = discipline,
                        competitorIds = tieGroup.map { it.competitor.id },
                        judgeCount = current.judgeCount,
                        groupKey = groupKey
                    )
                )
            }

            else -> tieResolution.orderedCandidates.forEach { candidate ->
                rankedEntries += candidate to tieResolution.tieBreakDetails[candidate.competitor.id]
            }
        }
    }

    val labels = rankedEntries.take(templateLabels.size).mapIndexedNotNull { awardIndex, (candidate, _) ->
        candidate.competitor.id to templateLabels[awardIndex]
    }.toMap()
    val tieBreakDetails = rankedEntries.take(templateLabels.size).mapNotNull { (candidate, detail) ->
        detail?.takeIf { it.isNotBlank() }?.let { candidate.competitor.id to it }
    }.toMap()

    return PlacementFinalizeResult.Completed(labels, tieBreakDetails)
}

private fun totalsToScores(scoreInputs: List<String>): List<Double> {
    return scoreInputs
        .filter { it.isNotBlank() }
        .mapNotNull { it.toDoubleOrNull() }
}

private fun samePrimaryTieKey(first: PlacementCandidate, second: PlacementCandidate): Boolean {
    return first.total == second.total
}

private fun tieGroupKey(candidates: List<PlacementCandidate>): String {
    return candidates.map { it.competitor.id }.sorted().joinToString("|")
}

private fun resolveTieGroup(
    tieGroup: List<PlacementCandidate>,
    tieBreakScoresByCompetitor: Map<String, List<String>>,
    judgeChoices: List<String>
): TieResolution? {
    resolveTieByRestoringHighLow(tieGroup)?.let { return it }

    val secondRoundCandidates = tieGroup.map { candidate ->
        val scoreInputs = tieBreakScoresByCompetitor[candidate.competitor.id] ?: return null
        val scoreCount = scoreInputs.count { it.isNotBlank() }
        if (scoreCount != candidate.judgeCount) return null
        val total = calculateScoreTotal(scoreInputs) ?: return null
        PlacementCandidate(
            competitor = candidate.competitor,
            scores = totalsToScores(scoreInputs),
            total = total,
            judgeCount = candidate.judgeCount
        )
    }.sortedWith(
        compareByDescending<PlacementCandidate> { it.total }
            .thenBy { it.competitor.name }
    )

    val topCandidate = secondRoundCandidates.firstOrNull() ?: return null
    val stillTied = secondRoundCandidates.count { samePrimaryTieKey(topCandidate, it) } > 1
    if (!stillTied) {
        return TieResolution(
            orderedCandidates = secondRoundCandidates,
            tieBreakDetails = secondRoundCandidates.associate { candidate ->
                candidate.competitor.id to formatOneDecimal(candidate.total)
            }
        )
    }

    resolveTieByRestoringHighLow(secondRoundCandidates)?.let { return it }

    if (judgeChoices.size != topCandidate.judgeCount || judgeChoices.any { it.isBlank() }) {
        return null
    }

    val voteCounts = judgeChoices.groupingBy { it }.eachCount()
    val orderedByVotes = secondRoundCandidates.sortedWith(
        compareByDescending<PlacementCandidate> { voteCounts[it.competitor.id] ?: 0 }
            .thenByDescending { it.total }
            .thenBy { it.competitor.name }
    )
    val leadingVoteCount = voteCounts[orderedByVotes.first().competitor.id] ?: 0
    if (voteCounts.values.count { it == leadingVoteCount } > 1) {
        return null
    }

    return TieResolution(
        orderedCandidates = orderedByVotes,
        tieBreakDetails = orderedByVotes.associate { candidate ->
            candidate.competitor.id to "JC ${voteCounts[candidate.competitor.id] ?: 0}-${topCandidate.judgeCount - (voteCounts[candidate.competitor.id] ?: 0)}"
        }
    )
}

private fun resolveTieByRestoringHighLow(candidates: List<PlacementCandidate>): TieResolution? {
    if (candidates.isEmpty() || candidates.any { it.judgeCount != 5 }) return null

    val orderedByFullFive = candidates.sortedWith(
        compareByDescending<PlacementCandidate> { it.scores.sum() }
            .thenBy { it.competitor.name }
    )
    val hasRemainingTie = orderedByFullFive
        .zipWithNext()
        .any { (left, right) -> left.scores.sum() == right.scores.sum() }
    if (hasRemainingTie) return null

    return TieResolution(
        orderedCandidates = orderedByFullFive,
        tieBreakDetails = orderedByFullFive.associate { candidate ->
            candidate.competitor.id to "H/L ${formatOneDecimal(candidate.scores.sum())}"
        }
    )
}

@Composable
private fun SparringBracketScreen(
    groupBanner: GroupBanner,
    competitors: List<Competitor>,
    winners: Map<Int, Map<Int, Competitor>>,
    boutProgressByKey: Map<String, SparringBoutProgress>,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onExit: () -> Unit,
    onOpenBout: (roundIndex: Int, boutIndex: Int, bout: BracketSheetBout) -> Unit
) {
    val round0Bouts = remember(competitors) {
        if (competitors.isEmpty()) emptyList()
        else {
            val seed = competitors.sortedBy { it.id }.fold(0) { acc, c -> 31 * acc + c.id.hashCode() }
            separateStudioPairs(TournamentEngine.createFirstRoundSparringBouts(competitors, seed))
        }
    }
    // Do not cache with `remember` � winners is a SnapshotStateMap whose reference
    // never changes, so remember(winners) would never recompute after a bout is saved.
    val bracketRounds = buildBracketSheetRounds(round0Bouts, winners)
    val byeCount = remember(round0Bouts) { round0Bouts.count { it.isBye } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onBack) {
                    Text("Back to Check-In")
                }
                Button(onClick = onNext) {
                    Text("Next: Awards & Signatures")
                }
            }
            Button(
                onClick = onExit,
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Text("Exit App")
            }
        }
        ScoringSheetBanner(title = "Sparring", group = groupBanner)
        if (competitors.isEmpty()) {
            Text("No eligible sparring competitors.")
        } else {
            if (byeCount > 0) {
                Text("$byeCount bye-out${if (byeCount == 1) "" else "s"} placed directly into Round 2.")
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    bracketRounds.forEachIndexed { roundIndex, round ->
                        BracketRoundColumn(
                            roundIndex = roundIndex,
                            round = round,
                            totalRounds = bracketRounds.size,
                            baseSlotCount = bracketRounds.firstOrNull()?.slots?.size ?: 0,
                            roundWinners = winners[roundIndex] ?: emptyMap(),
                            boutProgressByKey = boutProgressByKey,
                            onOpenBout = { boutIndex, bout ->
                                onOpenBout(roundIndex, boutIndex, bout)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistanceRequestDialog(
    ringLabel: String,
    isLoading: Boolean,
    onRequestMedical: () -> Unit,
    onRequestArbitrator: () -> Unit,
    onRequestGeneral: () -> Unit,
    onClearRequest: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text("Request Assistance", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Ring: ${ringLabel.ifBlank { "Not Assigned" }}")
                Button(
                    onClick = onRequestMedical,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Medical Assistance")
                }
                Button(
                    onClick = onRequestArbitrator,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Request Arbitrator")
                }
                Button(
                    onClick = onRequestGeneral,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("General Assistance")
                }
                OutlinedButton(
                    onClick = onClearRequest,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Clear Assistance Request")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun ServerConfigDialog(
    serverConnectionModeName: String,
    onServerConnectionModeChange: (String) -> Unit,
    serverAddress: String,
    onServerAddressChange: (String) -> Unit,
    status: String,
    isLoading: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val serverConnectionMode = parseServerConnectionMode(serverConnectionModeName)
    val configuration = LocalConfiguration.current
    val compactLayout = configuration.screenWidthDp < 1000 || configuration.screenHeightDp < 700
    val veryCompactLayout = configuration.screenWidthDp < 760 || configuration.screenHeightDp < 520
    val dialogWidthFraction = if (compactLayout) 0.82f else 0.74f
    val dialogMaxHeight = if (compactLayout) 420.dp else 460.dp
    val outerPadding = if (compactLayout) 10.dp else 20.dp
    val sectionGap = if (compactLayout) 8.dp else 14.dp
    val scrollState = rememberScrollState()

    Dialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth(dialogWidthFraction)
                .heightIn(max = dialogMaxHeight)
        ) {
            Column(
                modifier = Modifier
                    .padding(outerPadding)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(sectionGap)
            ) {
                Text(
                    text = "Server Configuration",
                    fontWeight = FontWeight.Bold,
                    fontSize = if (compactLayout) 16.sp else 18.sp
                )
                Text(
                    text = "Update DNS or IP, then retry the server connection.",
                    fontSize = if (compactLayout) 12.sp else 14.sp
                )

                Text("Server", fontWeight = FontWeight.SemiBold)
                if (veryCompactLayout) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = { onServerConnectionModeChange(ServerConnectionMode.DNS.name) },
                            enabled = !isLoading,
                            modifier = Modifier.fillMaxWidth(),
                            colors = if (serverConnectionMode == ServerConnectionMode.DNS) {
                                ButtonDefaults.buttonColors()
                            } else {
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        ) { Text("DNS") }
                        Button(
                            onClick = { onServerConnectionModeChange(ServerConnectionMode.IP.name) },
                            enabled = !isLoading,
                            modifier = Modifier.fillMaxWidth(),
                            colors = if (serverConnectionMode == ServerConnectionMode.IP) {
                                ButtonDefaults.buttonColors()
                            } else {
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        ) { Text("IP") }
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { onServerConnectionModeChange(ServerConnectionMode.DNS.name) },
                            enabled = !isLoading,
                            modifier = Modifier.weight(1f),
                            colors = if (serverConnectionMode == ServerConnectionMode.DNS) {
                                ButtonDefaults.buttonColors()
                            } else {
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        ) { Text("DNS") }
                        Button(
                            onClick = { onServerConnectionModeChange(ServerConnectionMode.IP.name) },
                            enabled = !isLoading,
                            modifier = Modifier.weight(1f),
                            colors = if (serverConnectionMode == ServerConnectionMode.IP) {
                                ButtonDefaults.buttonColors()
                            } else {
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        ) { Text("IP") }
                    }
                }
                OutlinedTextField(
                    value = serverAddress,
                    onValueChange = onServerAddressChange,
                    label = {
                        Text(
                            if (serverConnectionMode == ServerConnectionMode.DNS) {
                                "Server name"
                            } else {
                                "IPv4 address"
                            }
                        )
                    },
                    placeholder = {
                        Text(
                            if (serverConnectionMode == ServerConnectionMode.DNS) {
                                "HomePC-Sum2"
                            } else {
                                "192.168.2.182"
                            }
                        )
                    },
                    singleLine = true,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Port 3000 is automatic.",
                    fontSize = 12.sp
                )

                if (status.isNotBlank()) {
                    Text(
                        text = status,
                        color = if (status.startsWith("Unable")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        fontSize = if (compactLayout) 12.sp else 14.sp
                    )
                }
                Button(
                    onClick = onRetry,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isLoading) "Checking..." else "Save & Retry")
                }
                TextButton(onClick = onDismiss, enabled = !isLoading) {
                    Text("Close")
                }
            }
        }
    }
}

private fun resultsRootDirectory(context: Context): File {
    return File(context.filesDir, "TournamentScoringApp/Results")
}

private fun completedDivisionPacketFiles(context: Context): List<File> {
    val resultsDir = resultsRootDirectory(context)
    val files = resultsDir.listFiles() ?: return emptyList()
    return files
        .filter { it.isFile && it.extension.equals("json", ignoreCase = true) && it.name != "standings.json" }
        .sortedByDescending { it.lastModified() }
}

private fun packetDivisionId(packet: JSONObject): String {
    return packet.optString("divisionId", packet.optString("groupId", "division-unknown"))
        .ifBlank { packet.optString("groupId", "division-unknown").ifBlank { "division-unknown" } }
}

private fun categoryBandForAge(age: Int): String {
    return when {
        age < 18 -> "Youth"
        age <= 35 -> "Adult"
        else -> "Senior"
    }
}

private fun categoryBandForRank(rank: String): String {
    return if (rankLevelFor(rank) > 0) "Gup" else "Black Belt"
}

private fun championshipCategoryForCompetitor(age: Int, rank: String): String {
    return "${categoryBandForAge(age)} ${categoryBandForRank(rank)}"
}

private fun jsonArrayContains(array: JSONArray, value: String): Boolean {
    for (index in 0 until array.length()) {
        if (array.optString(index, "") == value) return true
    }
    return false
}

private fun rebuildChampionshipStandings(context: Context) {
    val resultsDir = resultsRootDirectory(context)
    resultsDir.mkdirs()
    val standingsByCategory = linkedMapOf<String, MutableMap<String, JSONObject>>()

    completedDivisionPacketFiles(context).forEach { packetFile ->
        val packet = runCatching { JSONObject(packetFile.readText()) }.getOrNull() ?: return@forEach
        val divisionId = packetDivisionId(packet)
        val rosterById = linkedMapOf<String, JSONObject>()

        packet.optJSONObject("fullDivisionDetails")
            ?.optJSONArray("competitors")
            ?.let { competitors ->
                for (index in 0 until competitors.length()) {
                    val rosterEntry = competitors.optJSONObject(index) ?: continue
                    val competitor = rosterEntry.optJSONObject("competitor") ?: rosterEntry
                    val competitorId = competitor.optString("id", "").ifBlank {
                        competitor.optString("competitorId", "")
                    }
                    if (competitorId.isNotBlank()) {
                        rosterById[competitorId] = competitor
                    }
                }
            }

        packet.optJSONObject("winnerSummary")?.let { summary ->
            listOf("weapons", "hyungs", "sparring").forEach { key ->
                val placements = summary.optJSONArray(key) ?: JSONArray()
                for (index in 0 until placements.length()) {
                    val placement = placements.optJSONObject(index) ?: continue
                    val competitorJson = placement.optJSONObject("competitor") ?: continue
                    val competitorId = competitorJson.optString("id", "").ifBlank {
                        competitorJson.optString("competitorId", "")
                    }
                    if (competitorId.isBlank()) continue
                    val roster = rosterById[competitorId] ?: competitorJson
                    val age = roster.optInt("age", competitorJson.optInt("age", 0))
                    val rank = roster.optString("rank", competitorJson.optString("rank", ""))
                    val category = championshipCategoryForCompetitor(age, rank)
                    val points = placement.optInt("points", pointsForPlacement(placement.optString("place", "")))
                    val competitorName = roster.optString("name", competitorJson.optString("name", competitorId))
                    val categoryMap = standingsByCategory.getOrPut(category) { linkedMapOf() }
                    val existing = categoryMap[competitorId] ?: JSONObject().apply {
                        put("competitorId", competitorId)
                        put("name", competitorName)
                        put("points", 0)
                        put("divisionIds", JSONArray())
                    }
                    existing.put("name", competitorName)
                    existing.put("points", existing.optInt("points", 0) + points)
                    val divisionIds = existing.optJSONArray("divisionIds") ?: JSONArray().also {
                        existing.put("divisionIds", it)
                    }
                    if (!jsonArrayContains(divisionIds, divisionId)) {
                        divisionIds.put(divisionId)
                    }
                    categoryMap[competitorId] = existing
                }
            }
        }
    }

    val standings = JSONObject().apply {
        put("updatedAt", Instant.now().toString())
        put(
            "categories",
            JSONObject().apply {
                standingsByCategory.forEach { (category, competitors) ->
                    put(
                        category,
                        JSONObject().apply {
                            put(
                                "competitors",
                                JSONArray().apply {
                                    competitors.values
                                        .sortedWith(
                                            compareByDescending<JSONObject> { it.optInt("points", 0) }
                                                .thenBy { it.optString("name", "") }
                                        )
                                        .forEach { competitor ->
                                            put(competitor)
                                        }
                                }
                            )
                        }
                    )
                }
            }
        )
    }

    resultsDir.resolve("standings.json").writeText(standings.toString(2))
}

private fun persistDivisionPacketReceipt(
    context: Context,
    divisionPacket: JSONObject,
    snapshotBytes: ByteArray,
    snapshotMimeType: String
) {
    val resultsDir = resultsRootDirectory(context).apply { mkdirs() }
    val divisionId = packetDivisionId(divisionPacket)
    resultsDir.resolve("$divisionId.json").writeText(divisionPacket.toString(2))
    val attachmentFile = if (snapshotMimeType == "application/pdf") {
        resultsDir.resolve("$divisionId.pdf")
    } else {
        resultsDir.resolve("$divisionId.png")
    }
    attachmentFile.writeBytes(snapshotBytes)
    rebuildChampionshipStandings(context)
}

private fun loadPacketSummary(packetFile: File): String {
    val packet = runCatching { JSONObject(packetFile.readText()) }.getOrNull()
        ?: return packetFile.readText()
    val divisionId = packetDivisionId(packet)
    val winnerSummary = packet.optJSONObject("winnerSummary") ?: JSONObject()
    return JSONObject().apply {
        put("divisionId", divisionId)
        put("groupId", packet.optString("groupId", divisionId))
        put("divisionType", packet.optString("divisionType", "Division"))
        put("winnerSummary", winnerSummary)
        put("signatureBlock", packet.optJSONObject("signatureBlock") ?: JSONObject())
        put("updatedAt", packet.optString("completedAt", packet.optString("generatedAt", "")))
    }.toString(2)
}

private fun loadStandingsSummary(context: Context): String {
    val standingsFile = resultsRootDirectory(context).resolve("standings.json")
    if (!standingsFile.exists()) return "No championship standings yet."
    val standings = runCatching { JSONObject(standingsFile.readText()) }.getOrNull()
        ?: return standingsFile.readText()
    val categories = standings.optJSONObject("categories") ?: return "No championship standings yet."
    val lines = mutableListOf<String>()
    lines += "Updated: ${standings.optString("updatedAt", "")}"
    categories.keys().forEach { category ->
        lines += ""
        lines += category
        val entries = categories.optJSONObject(category)?.optJSONArray("competitors") ?: JSONArray()
        for (index in 0 until entries.length()) {
            val competitor = entries.optJSONObject(index) ?: continue
            lines += "  ${competitor.optString("name", "Unknown")} - ${competitor.optInt("points", 0)} pts"
        }
    }
    return lines.joinToString("\n")
}

private fun openPdf(context: Context, pdfFile: File) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        pdfFile
    )
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

@Composable
private fun CompletedDivisionsViewerDialog(
    context: Context,
    onDismiss: () -> Unit
) {
    val packetFiles = completedDivisionPacketFiles(context)
    var selectedDivisionId by remember(packetFiles) {
        mutableStateOf(packetFiles.firstOrNull()?.nameWithoutExtension.orEmpty())
    }
    val selectedPacketFile = packetFiles.firstOrNull { it.nameWithoutExtension == selectedDivisionId }
    val selectedSummary = selectedPacketFile?.let(::loadPacketSummary).orEmpty()
    val standingsSummary = loadStandingsSummary(context)
    val pdfFile = selectedPacketFile?.let { resultsRootDirectory(context).resolve("${it.nameWithoutExtension}.pdf") }
    val imageFile = selectedPacketFile?.let { resultsRootDirectory(context).resolve("${it.nameWithoutExtension}.png") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Completed Divisions", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                if (packetFiles.isEmpty()) {
                    Text("No completed divisions found.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(packetFiles, key = { it.name }) { packetFile ->
                            TextButton(onClick = { selectedDivisionId = packetFile.nameWithoutExtension }) {
                                Text(packetFile.nameWithoutExtension)
                            }
                        }
                    }
                }

                HorizontalDivider()

                Text("JSON Summary", fontWeight = FontWeight.SemiBold)
                Text(
                    text = if (selectedSummary.isNotBlank()) selectedSummary else "Select a division to view its summary.",
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )

                HorizontalDivider()

                Text("Championship Standings", fontWeight = FontWeight.SemiBold)
                Text(
                    text = standingsSummary,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )

                val attachment = when {
                    pdfFile != null && pdfFile.exists() -> pdfFile
                    imageFile != null && imageFile.exists() -> imageFile
                    else -> null
                }
                if (attachment != null) {
                    Text("Attachment: ${attachment.absolutePath}")
                    Button(onClick = {
                        if (attachment.extension.equals("png", ignoreCase = true)) {
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                attachment
                            )
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "image/png")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } else {
                            openPdf(context, attachment)
                        }
                    }) {
                        Text(if (attachment.extension.equals("png", ignoreCase = true)) "Open Image" else "Open PDF")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
private fun BannerTitleStack(
    screenTitle: String,
    group: GroupBanner,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    showVersion: Boolean = true
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        val screenWidthDp = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp().value }
        val titleSize = when {
            compact && screenWidthDp < 600 -> 20.sp
            compact && screenWidthDp < 840 -> 24.sp
            compact -> 26.sp
            screenWidthDp < 600 -> 24.sp
            screenWidthDp < 840 -> 30.sp
            else -> 34.sp
        }
        val groupSize = when {
            compact && screenWidthDp < 600 -> 18.sp
            compact && screenWidthDp < 840 -> 22.sp
            compact -> 24.sp
            screenWidthDp < 600 -> 22.sp
            screenWidthDp < 840 -> 28.sp
            else -> 32.sp
        }
        val detailsSize = when {
            compact && screenWidthDp < 600 -> 16.sp
            compact && screenWidthDp < 840 -> 20.sp
            compact -> 22.sp
            screenWidthDp < 600 -> 20.sp
            screenWidthDp < 840 -> 26.sp
            else -> 30.sp
        }
        Text(
            text = screenTitle,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            fontSize = titleSize
        )
        Text(
            text = group.displayId,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold,
            fontSize = groupSize
        )
        if (group.isLoaded && group.details.isNotEmpty()) {
            Text(
                text = group.details,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Normal,
                fontSize = detailsSize
            )
        }
        if (showVersion) {
            Text(
                text = appVersionLabel(),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium,
                fontSize = if (screenWidthDp < 600) 11.sp else 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ScoringSheetBanner(title: String, group: GroupBanner) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors()
    ) {
        BannerTitleStack(
            screenTitle = title,
            group = group,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        )
    }
}

private data class OverallAwardsSummary(
    val weapons: List<Pair<String, Competitor?>>, 
    val hyungs: List<Pair<String, Competitor?>>, 
    val sparring: List<Pair<String, Competitor?>>
)

internal fun formatSummaryPlacementLine(label: String, competitor: Competitor?): String {
    val competitorText = competitor?.let { "${it.name} - ${it.rank} - ${it.studio}" } ?: "N/A"
    return "$label: $competitorText"
}

private val signatureFontFamily = FontFamily.Cursive

private data class SignatureEntry(
    val name: String = "",
    val dan: String = ""
)

@Composable
private fun OverallAwardsScreen(
    groupBanner: GroupBanner,
    divisionId: String,
    divisionType: String,
    divisionCompetitors: List<Competitor>,
    groupSize: Int,
    requiredJudgeCount: Int,
    weaponsPlacements: List<Pair<String, Competitor?>>, 
    hyungsPlacements: List<Pair<String, Competitor?>>, 
    sparringPlacements: List<Pair<String, Competitor?>>, 
    weaponsCompetitors: List<Competitor>,
    weaponsScoresByCompetitor: Map<String, List<String>>,
    weaponsPlacementsByCompetitorId: Map<String, String>,
    weaponsTieBreakDetailsByCompetitorId: Map<String, String>,
    hyungsCompetitors: List<Competitor>,
    hyungsScoresByCompetitor: Map<String, List<String>>,
    hyungsPlacementsByCompetitorId: Map<String, String>,
    hyungsTieBreakDetailsByCompetitorId: Map<String, String>,
    sparringReviewLines: List<String>,
    onCompleteRound: (JSONObject, ByteArray, String) -> Unit,
    onBackToSparring: () -> Unit,
    onExit: () -> Unit
) {
    var judgeSignatures by remember { mutableStateOf(List(5) { SignatureEntry() }) }
    var scorekeeperSignature by remember { mutableStateOf(SignatureEntry()) }
    var timekeeperSignature by remember { mutableStateOf(SignatureEntry()) }
    var onlyOneScorekeeperToday by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val requiredScorekeeperCount = if (onlyOneScorekeeperToday) 1 else 2
    val allSignaturesComplete = judgeSignatures
        .take(requiredJudgeCount)
        .all { it.name.isNotBlank() && it.dan.isNotBlank() } &&
        listOf(scorekeeperSignature, timekeeperSignature)
            .take(requiredScorekeeperCount)
            .all { it.name.isNotBlank() && it.dan.isNotBlank() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .imePadding()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    val completedAt = Instant.now().toString()
                    val uploadPacket = buildDivisionPacketJson(
                        groupId = groupBanner.groupId.ifBlank { divisionId },
                        divisionId = divisionId,
                        divisionType = divisionType,
                        competitors = divisionCompetitors,
                        weaponsPlacements = weaponsPlacements,
                        hyungsPlacements = hyungsPlacements,
                        sparringPlacements = sparringPlacements,
                        weaponsScoresByCompetitor = weaponsScoresByCompetitor,
                        hyungsScoresByCompetitor = hyungsScoresByCompetitor,
                        weaponsPlacementsByCompetitorId = weaponsPlacementsByCompetitorId,
                        hyungsPlacementsByCompetitorId = hyungsPlacementsByCompetitorId,
                        weaponsTieBreakDetailsByCompetitorId = weaponsTieBreakDetailsByCompetitorId,
                        hyungsTieBreakDetailsByCompetitorId = hyungsTieBreakDetailsByCompetitorId,
                        judgeSignatures = judgeSignatures.take(requiredJudgeCount),
                        scorekeeperSignature = scorekeeperSignature,
                        timekeeperSignature = timekeeperSignature,
                        onlyOneScorekeeperToday = onlyOneScorekeeperToday,
                        completedAt = completedAt
                    )
                    val snapshot = buildSummarySheetSnapshot(
                        groupBanner = groupBanner,
                        divisionId = divisionId,
                        divisionType = divisionType,
                        divisionCompetitors = divisionCompetitors,
                        groupSize = groupSize,
                        weaponsPlacements = weaponsPlacements,
                        hyungsPlacements = hyungsPlacements,
                        sparringPlacements = sparringPlacements,
                        weaponsCompetitors = weaponsCompetitors,
                        weaponsScoresByCompetitor = weaponsScoresByCompetitor,
                        weaponsPlacementsByCompetitorId = weaponsPlacementsByCompetitorId,
                        weaponsTieBreakDetailsByCompetitorId = weaponsTieBreakDetailsByCompetitorId,
                        hyungsCompetitors = hyungsCompetitors,
                        hyungsScoresByCompetitor = hyungsScoresByCompetitor,
                        hyungsPlacementsByCompetitorId = hyungsPlacementsByCompetitorId,
                        hyungsTieBreakDetailsByCompetitorId = hyungsTieBreakDetailsByCompetitorId,
                        sparringReviewLines = sparringReviewLines,
                        judgeSignatures = judgeSignatures.take(requiredJudgeCount),
                        scorekeeperSignature = scorekeeperSignature,
                        timekeeperSignature = timekeeperSignature,
                        onlyOneScorekeeperToday = onlyOneScorekeeperToday,
                        completedAt = completedAt
                    )
                    onCompleteRound(uploadPacket, snapshot.first, snapshot.second)
                },
                enabled = allSignaturesComplete,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Text("Complete Round")
            }
            Button(
                onClick = onExit,
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Text("Exit App")
            }
        }

        ScoringSheetBanner(title = "Results", group = groupBanner)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Competitors: $groupSize")

                Text("Weapons", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                weaponsPlacements.forEach { (label, competitor) ->
                    Text(formatSummaryPlacementLine(label, competitor))
                }

                Text("Hyungs", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                hyungsPlacements.forEach { (label, competitor) ->
                    Text(formatSummaryPlacementLine(label, competitor))
                }

                Text("Sparring", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                sparringPlacements.forEach { (label, competitor) ->
                    Text(formatSummaryPlacementLine(label, competitor))
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Judge Review: Scores & Results", fontWeight = FontWeight.Bold, fontSize = 18.sp)

                Text("Weapons", fontWeight = FontWeight.SemiBold)
                if (weaponsCompetitors.isEmpty()) {
                    Text("No weapons competitors.")
                } else {
                    weaponsCompetitors.forEach { competitor ->
                        val inputs = weaponsScoresByCompetitor[competitor.id] ?: List(5) { "" }
                        val judges = inputs.mapIndexedNotNull { index, value ->
                            value.takeIf { it.isNotBlank() }?.let { "J${index + 1}=$it" }
                        }.joinToString(", ")
                        val total = buildScoreDisplayLabel(
                            baseLabel = calculateScoreSummary(inputs),
                            tieBreakDetail = weaponsTieBreakDetailsByCompetitorId[competitor.id]
                        ).ifBlank { "N/A" }
                        val place = weaponsPlacementsByCompetitorId[competitor.id] ?: "N/A"
                        Text("${competitor.name} (${competitor.rank}) | ${if (judges.isBlank()) "No scores" else judges} | Total $total | $place")
                    }
                }

                Text("Hyungs", fontWeight = FontWeight.SemiBold)
                if (hyungsCompetitors.isEmpty()) {
                    Text("No hyungs competitors.")
                } else {
                    hyungsCompetitors.forEach { competitor ->
                        val inputs = hyungsScoresByCompetitor[competitor.id] ?: List(5) { "" }
                        val judges = inputs.mapIndexedNotNull { index, value ->
                            value.takeIf { it.isNotBlank() }?.let { "J${index + 1}=$it" }
                        }.joinToString(", ")
                        val total = buildScoreDisplayLabel(
                            baseLabel = calculateScoreSummary(inputs),
                            tieBreakDetail = hyungsTieBreakDetailsByCompetitorId[competitor.id]
                        ).ifBlank { "N/A" }
                        val place = hyungsPlacementsByCompetitorId[competitor.id] ?: "N/A"
                        Text("${competitor.name} (${competitor.rank}) | ${if (judges.isBlank()) "No scores" else judges} | Total $total | $place")
                    }
                }

                Text("Sparring", fontWeight = FontWeight.SemiBold)
                if (sparringReviewLines.isEmpty()) {
                    Text("No sparring bouts to review.")
                } else {
                    sparringReviewLines.forEach { line ->
                        Text(line)
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Judges / Staff Signatures")
                judgeSignatures.forEachIndexed { index, entry ->
                    val judgeIsActive = index < requiredJudgeCount
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = entry.name,
                            onValueChange = { updated ->
                                judgeSignatures = judgeSignatures.toMutableList().also {
                                    it[index] = SignatureEntry(name = capitalizeWords(updated), dan = it[index].dan)
                                }
                            },
                            label = { Text("Judge ${index + 1} Name") },
                            singleLine = true,
                            enabled = judgeIsActive,
                            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = signatureFontFamily, fontSize = 28.sp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = entry.dan,
                            onValueChange = { updated ->
                                judgeSignatures = judgeSignatures.toMutableList().also {
                                    it[index] = SignatureEntry(name = it[index].name, dan = updated)
                                }
                            },
                            label = { Text("Rank / #") },
                            singleLine = true,
                            enabled = judgeIsActive,
                            modifier = Modifier.width(180.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = onlyOneScorekeeperToday,
                        onCheckedChange = { onlyOneScorekeeperToday = it }
                    )
                    Text("Only one ScoreKeeper/TimeKeeper today")
                }

                SignatureEntryField(
                    label = "Scorekeeper",
                    value = scorekeeperSignature,
                    enabled = true,
                    onNameChanged = { scorekeeperSignature = scorekeeperSignature.copy(name = capitalizeWords(it)) },
                    onDanChanged = { scorekeeperSignature = scorekeeperSignature.copy(dan = it) }
                )
                SignatureEntryField(
                    label = "Timekeeper",
                    value = timekeeperSignature,
                    enabled = !onlyOneScorekeeperToday,
                    onNameChanged = { timekeeperSignature = timekeeperSignature.copy(name = capitalizeWords(it)) },
                    onDanChanged = { timekeeperSignature = timekeeperSignature.copy(dan = it) }
                )
            }
        }
        Spacer(modifier = Modifier.height(120.dp))
    }
}

@Composable
private fun SignatureEntryField(
    label: String,
    value: SignatureEntry,
    enabled: Boolean = true,
    onNameChanged: (String) -> Unit,
    onDanChanged: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = value.name,
            onValueChange = { onNameChanged(capitalizeWords(it)) },
            label = { Text(label) },
            singleLine = true,
            enabled = enabled,
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = signatureFontFamily, fontSize = 28.sp),
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = value.dan,
            onValueChange = onDanChanged,
            label = { Text("Rank / #") },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.width(180.dp)
        )
    }
}

private fun SignatureEntry.toJson(): JSONObject {
    return JSONObject().apply {
        put("name", name)
        put("dan", dan)
    }
}

private fun Competitor.toPacketJson(): JSONObject {
    return JSONObject().apply {
        put("id", id)
        put("name", name)
        put("studio", studio)
        put("rank", rank)
        put("rankLevel", rankLevel)
        put("age", age)
        put("heightInInches", heightInInches)
        put("checkInStatus", checkInStatus.name)
        put("competitionEntries", JSONArray().apply {
            competitionEntries.forEach { (type, entry) ->
                put(JSONObject().apply {
                    put("type", type.name)
                    put("status", entry.status.name)
                })
            }
        })
    }
}

private fun pointsForPlacement(placeLabel: String): Int {
    return when {
        placeLabel.startsWith("1st") -> 10
        placeLabel.startsWith("2nd") -> 8
        placeLabel.contains("3rd") -> 5
        else -> 0
    }
}

private fun buildWinnerSummaryArray(placements: List<Pair<String, Competitor?>>): JSONArray {
    return JSONArray().apply {
        placements.forEach { (placeLabel, competitor) ->
            put(
                JSONObject().apply {
                    put("place", placeLabel)
                    put("points", pointsForPlacement(placeLabel))
                    if (competitor != null) {
                        put("competitor", competitor.toPacketJson())
                    }
                }
            )
        }
    }
}

private fun buildDivisionPacketJson(
    groupId: String,
    divisionId: String,
    divisionType: String,
    competitors: List<Competitor>,
    weaponsPlacements: List<Pair<String, Competitor?>>,
    hyungsPlacements: List<Pair<String, Competitor?>>,
    sparringPlacements: List<Pair<String, Competitor?>>,
    weaponsScoresByCompetitor: Map<String, List<String>>,
    hyungsScoresByCompetitor: Map<String, List<String>>,
    weaponsPlacementsByCompetitorId: Map<String, String>,
    hyungsPlacementsByCompetitorId: Map<String, String>,
    weaponsTieBreakDetailsByCompetitorId: Map<String, String>,
    hyungsTieBreakDetailsByCompetitorId: Map<String, String>,
    judgeSignatures: List<SignatureEntry>,
    scorekeeperSignature: SignatureEntry,
    timekeeperSignature: SignatureEntry,
    onlyOneScorekeeperToday: Boolean,
    completedAt: String
): JSONObject {
    val activeJudges = judgeSignatures.filter { it.name.isNotBlank() || it.dan.isNotBlank() }
    val centerJudgeIndex = if (activeJudges.isEmpty()) 0 else activeJudges.size / 2
    val centerJudge = activeJudges.getOrNull(centerJudgeIndex)
    val cornerJudges = JSONArray().apply {
        activeJudges.forEachIndexed { index, signature ->
            if (index != centerJudgeIndex) {
                put(signature.toJson())
            }
        }
    }

    return JSONObject().apply {
        put("groupId", groupId)
        put("divisionId", divisionId)
        put("divisionType", divisionType)
        put("generatedAt", completedAt)
        put("completedAt", completedAt)
        put(
            "winnerSummary",
            JSONObject().apply {
                put("weapons", buildWinnerSummaryArray(weaponsPlacements))
                put("hyungs", buildWinnerSummaryArray(hyungsPlacements))
                put("sparring", buildWinnerSummaryArray(sparringPlacements))
            }
        )
        put(
            "fullDivisionDetails",
            JSONObject().apply {
                put("timestamps", JSONObject().apply {
                    put("generatedAt", completedAt)
                    put("completedAt", completedAt)
                })
                put(
                    "judgeNames",
                    JSONObject().apply {
                        put("centerJudge", centerJudge?.toJson())
                        put("cornerJudges", cornerJudges)
                        put("operator", JSONObject().apply {
                            put("scorekeeper", scorekeeperSignature.toJson())
                            put("timekeeper", timekeeperSignature.toJson())
                            put("singleOperator", onlyOneScorekeeperToday)
                        })
                    }
                )
                put(
                    "competitors",
                    JSONArray().apply {
                        competitors.forEach { competitor ->
                            put(
                                JSONObject().apply {
                                    put("competitor", competitor.toPacketJson())
                                    put("rawScores", JSONObject().apply {
                                        put(
                                            "weapons",
                                            JSONArray().apply {
                                                (weaponsScoresByCompetitor[competitor.id] ?: emptyList()).forEach { score ->
                                                    put(score)
                                                }
                                            }
                                        )
                                        put(
                                            "hyungs",
                                            JSONArray().apply {
                                                (hyungsScoresByCompetitor[competitor.id] ?: emptyList()).forEach { score ->
                                                    put(score)
                                                }
                                            }
                                        )
                                    })
                                    put("placements", JSONObject().apply {
                                        put("weapons", weaponsPlacementsByCompetitorId[competitor.id].orEmpty())
                                        put("hyungs", hyungsPlacementsByCompetitorId[competitor.id].orEmpty())
                                    })
                                    put("tieBreakDetails", JSONObject().apply {
                                        put("weapons", weaponsTieBreakDetailsByCompetitorId[competitor.id].orEmpty())
                                        put("hyungs", hyungsTieBreakDetailsByCompetitorId[competitor.id].orEmpty())
                                    })
                                }
                            )
                        }
                    }
                )
            }
        )
        put(
            "signatureBlock",
            JSONObject().apply {
                put("centerJudge", centerJudge?.toJson())
                put("cornerJudges", cornerJudges)
                put(
                    "operator",
                    JSONObject().apply {
                        put("scorekeeper", scorekeeperSignature.toJson())
                        put("timekeeper", timekeeperSignature.toJson())
                        put("singleOperator", onlyOneScorekeeperToday)
                    }
                )
            }
        )
    }
}

private fun buildSummarySheetSnapshot(
    groupBanner: GroupBanner,
    divisionId: String,
    divisionType: String,
    divisionCompetitors: List<Competitor>,
    groupSize: Int,
    weaponsPlacements: List<Pair<String, Competitor?>>,
    hyungsPlacements: List<Pair<String, Competitor?>>,
    sparringPlacements: List<Pair<String, Competitor?>>,
    weaponsCompetitors: List<Competitor>,
    weaponsScoresByCompetitor: Map<String, List<String>>,
    weaponsPlacementsByCompetitorId: Map<String, String>,
    weaponsTieBreakDetailsByCompetitorId: Map<String, String>,
    hyungsCompetitors: List<Competitor>,
    hyungsScoresByCompetitor: Map<String, List<String>>,
    hyungsPlacementsByCompetitorId: Map<String, String>,
    hyungsTieBreakDetailsByCompetitorId: Map<String, String>,
    sparringReviewLines: List<String>,
    judgeSignatures: List<SignatureEntry>,
    scorekeeperSignature: SignatureEntry,
    timekeeperSignature: SignatureEntry,
    onlyOneScorekeeperToday: Boolean,
    completedAt: String
): Pair<ByteArray, String> {
    val lines = buildSummarySheetLines(
        groupBanner = groupBanner,
        divisionId = divisionId,
        divisionType = divisionType,
        divisionCompetitors = divisionCompetitors,
        groupSize = groupSize,
        weaponsPlacements = weaponsPlacements,
        hyungsPlacements = hyungsPlacements,
        sparringPlacements = sparringPlacements,
        weaponsCompetitors = weaponsCompetitors,
        weaponsScoresByCompetitor = weaponsScoresByCompetitor,
        weaponsPlacementsByCompetitorId = weaponsPlacementsByCompetitorId,
        weaponsTieBreakDetailsByCompetitorId = weaponsTieBreakDetailsByCompetitorId,
        hyungsCompetitors = hyungsCompetitors,
        hyungsScoresByCompetitor = hyungsScoresByCompetitor,
        hyungsPlacementsByCompetitorId = hyungsPlacementsByCompetitorId,
        hyungsTieBreakDetailsByCompetitorId = hyungsTieBreakDetailsByCompetitorId,
        sparringReviewLines = sparringReviewLines,
        judgeSignatures = judgeSignatures,
        scorekeeperSignature = scorekeeperSignature,
        timekeeperSignature = timekeeperSignature,
        onlyOneScorekeeperToday = onlyOneScorekeeperToday,
        completedAt = completedAt
    )

    return try {
        buildSummarySheetPdfBytes(lines) to "application/pdf"
    } catch (_: Exception) {
        buildSummarySheetImageBytes(lines) to "image/png"
    }
}

private fun buildSummarySheetLines(
    groupBanner: GroupBanner,
    divisionId: String,
    divisionType: String,
    divisionCompetitors: List<Competitor>,
    groupSize: Int,
    weaponsPlacements: List<Pair<String, Competitor?>>,
    hyungsPlacements: List<Pair<String, Competitor?>>,
    sparringPlacements: List<Pair<String, Competitor?>>,
    weaponsCompetitors: List<Competitor>,
    weaponsScoresByCompetitor: Map<String, List<String>>,
    weaponsPlacementsByCompetitorId: Map<String, String>,
    weaponsTieBreakDetailsByCompetitorId: Map<String, String>,
    hyungsCompetitors: List<Competitor>,
    hyungsScoresByCompetitor: Map<String, List<String>>,
    hyungsPlacementsByCompetitorId: Map<String, String>,
    hyungsTieBreakDetailsByCompetitorId: Map<String, String>,
    sparringReviewLines: List<String>,
    judgeSignatures: List<SignatureEntry>,
    scorekeeperSignature: SignatureEntry,
    timekeeperSignature: SignatureEntry,
    onlyOneScorekeeperToday: Boolean,
    completedAt: String
): List<String> {
    fun formatScorePairs(scores: List<String>): String {
        if (scores.isEmpty()) return "No scores"
        return scores.mapIndexed { index, value ->
            "J${index + 1}=${value.ifBlank { "-" }}"
        }.joinToString(", ")
    }

    val lines = mutableListOf<String>()
    lines += "Division Packet Summary"
    lines += "Group: ${groupBanner.groupId.ifBlank { divisionId }} | Division: $divisionId | Type: $divisionType"
    lines += "Completed: $completedAt"
    lines += "Group Size: $groupSize"
    lines += ""
    lines += "Winner Summary"
    listOf(
        "Weapons" to weaponsPlacements,
        "Hyungs" to hyungsPlacements,
        "Sparring" to sparringPlacements
    ).forEach { (label, placements) ->
        lines += label
        placements.forEach { (placeLabel, competitor) ->
            val points = pointsForPlacement(placeLabel)
            val competitorLabel = competitor?.let { "${it.name} (${it.rank})" } ?: "N/A"
            lines += "  $placeLabel ($points pts): $competitorLabel"
        }
    }
    lines += ""
    lines += "Division Details"
    divisionCompetitors.forEach { competitor ->
        val weaponsScores = formatScorePairs(weaponsScoresByCompetitor[competitor.id] ?: emptyList())
        val hyungsScores = formatScorePairs(hyungsScoresByCompetitor[competitor.id] ?: emptyList())
        lines += "${competitor.name} (${competitor.rank})"
        lines += "  Weapons: $weaponsScores | Place: ${weaponsPlacementsByCompetitorId[competitor.id].orEmpty().ifBlank { "N/A" }}"
        lines += "  Hyungs: $hyungsScores | Place: ${hyungsPlacementsByCompetitorId[competitor.id].orEmpty().ifBlank { "N/A" }}"
        val weaponsDetail = weaponsTieBreakDetailsByCompetitorId[competitor.id].orEmpty()
        val hyungsDetail = hyungsTieBreakDetailsByCompetitorId[competitor.id].orEmpty()
        if (weaponsDetail.isNotBlank() || hyungsDetail.isNotBlank()) {
            lines += "  Tie Breaks: weapons=${weaponsDetail.ifBlank { "N/A" }}, hyungs=${hyungsDetail.ifBlank { "N/A" }}"
        }
    }
    lines += ""
    lines += "Judges"
    judgeSignatures.forEachIndexed { index, signature ->
        lines += "  Judge ${index + 1}: ${signature.name} ${signature.dan}".trimEnd()
    }
    lines += "  Operator: ${scorekeeperSignature.name}/${timekeeperSignature.name}${if (onlyOneScorekeeperToday) " (single)" else ""}"
    lines += ""
    lines += "Sparring Review"
    if (sparringReviewLines.isEmpty()) {
        lines += "  No sparring review lines."
    } else {
        lines += sparringReviewLines.map { "  $it" }
    }
    return lines
}

private fun wrapSnapshotText(text: String, paint: Paint, maxWidth: Float): List<String> {
    if (text.isBlank()) return listOf("")
    val wrapped = mutableListOf<String>()
    var remaining = text
    while (remaining.isNotEmpty()) {
        val count = paint.breakText(remaining, true, maxWidth, null)
        if (count <= 0) {
            wrapped += remaining
            break
        }
        var chunk = remaining.substring(0, count)
        if (count < remaining.length) {
            val breakIndex = chunk.lastIndexOf(' ')
            if (breakIndex > 0) {
                chunk = chunk.substring(0, breakIndex)
            }
        }
        wrapped += chunk.trimEnd()
        remaining = remaining.substring(chunk.length).trimStart()
    }
    return wrapped
}

private fun expandedSnapshotLines(lines: List<String>, paint: Paint, maxWidth: Float): List<String> {
    return buildList {
        lines.forEach { line ->
            val wrapped = wrapSnapshotText(line, paint, maxWidth)
            if (wrapped.isEmpty()) {
                add("")
            } else {
                addAll(wrapped)
            }
        }
    }
}

private fun buildSummarySheetPdfBytes(lines: List<String>): ByteArray {
    val pageWidth = 1240
    val pageHeight = 1754
    val margin = 56f
    val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.BLACK
        textSize = 28f
        typeface = Typeface.MONOSPACE
    }
    val titlePaint = Paint(bodyPaint).apply {
        textSize = 34f
        typeface = Typeface.DEFAULT_BOLD
    }
    val expandedLines = expandedSnapshotLines(lines, bodyPaint, pageWidth - (margin * 2))
    val lineHeight = 34f
    val pdf = PdfDocument()
    var pageNumber = 1
    var page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
    var canvas = page.canvas
    var y = margin
    var titleDrawn = false

    fun startNewPage() {
        pdf.finishPage(page)
        pageNumber += 1
        page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        canvas = page.canvas
        y = margin
        titleDrawn = false
    }

    fun drawLine(text: String, paint: Paint) {
        if (y > pageHeight - margin) {
            startNewPage()
        }
        canvas.drawText(text, margin, y, paint)
        y += lineHeight
    }

    expandedLines.forEachIndexed { index, line ->
        if (index == 0 && !titleDrawn) {
            drawLine(line, titlePaint)
            y += 10f
            titleDrawn = true
        } else {
            drawLine(line, bodyPaint)
        }
    }

    pdf.finishPage(page)
    val output = ByteArrayOutputStream()
    pdf.writeTo(output)
    pdf.close()
    return output.toByteArray()
}

private fun buildSummarySheetImageBytes(lines: List<String>): ByteArray {
    val width = 1240
    val margin = 56f
    val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.BLACK
        textSize = 28f
        typeface = Typeface.MONOSPACE
    }
    val titlePaint = Paint(bodyPaint).apply {
        textSize = 34f
        typeface = Typeface.DEFAULT_BOLD
    }
    val expandedLines = expandedSnapshotLines(lines, bodyPaint, width - (margin * 2))
    val lineHeight = 34f
    val height = maxOf(1600, ((expandedLines.size + 3) * lineHeight).toInt() + 120)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = GraphicsCanvas(bitmap)
    canvas.drawColor(AndroidColor.WHITE)
    var y = margin
    expandedLines.forEachIndexed { index, line ->
        val paint = if (index == 0) titlePaint else bodyPaint
        canvas.drawText(line, margin, y, paint)
        y += lineHeight
    }
    val output = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
    return output.toByteArray()
}

private fun buildOverallAwardsSummary(
    competitors: List<Competitor>,
    weaponsLabels: Map<String, String>,
    hyungsLabels: Map<String, String>,
    sparringWinners: Map<Int, Map<Int, Competitor>>,
    round0Bouts: List<com.summ0.tournamentscoringapp.engine.SparringBout>
): OverallAwardsSummary {
    val placeLabels = listOf("1st Place", "2nd Place", "Co-3rd Place", "Co-3rd Place")
    val weapons = orderedSummaryPlacements(competitors, weaponsLabels, placeLabels)
    val hyungs = orderedSummaryPlacements(competitors, hyungsLabels, placeLabels)

    val sparring = buildSparringAwardPlacements(round0Bouts, sparringWinners)
    return OverallAwardsSummary(weapons = weapons, hyungs = hyungs, sparring = sparring)
}

internal fun orderedSummaryPlacements(
    competitors: List<Competitor>,
    placementsByCompetitorId: Map<String, String>,
    placeLabels: List<String>
): List<Pair<String, Competitor?>> {
    val usedCompetitorIds = mutableSetOf<String>()
    return placeLabels.map { label ->
        val placementEntry = placementsByCompetitorId.entries.firstOrNull { entry ->
            entry.value == label && entry.key !in usedCompetitorIds
        }
        if (placementEntry != null) {
            usedCompetitorIds += placementEntry.key
        }
        label to placementEntry?.let { entry ->
            competitors.firstOrNull { it.id == entry.key }
        }
    }
}

internal fun buildSparringAwardPlacements(
    round0Bouts: List<com.summ0.tournamentscoringapp.engine.SparringBout>,
    sparringWinners: Map<Int, Map<Int, Competitor>>
): List<Pair<String, Competitor?>> {
    if (round0Bouts.isEmpty()) {
        return emptySparringAwardPlacements()
    }

    val rounds = mutableListOf<List<com.summ0.tournamentscoringapp.engine.SparringBout>>()
    var currentRound = round0Bouts.toList()
    var roundIndex = 0
    while (true) {
        rounds += currentRound
        if (currentRound.size <= 1) break

        val nextRound = mutableListOf<com.summ0.tournamentscoringapp.engine.SparringBout>()
        var boutIndex = 0
        while (boutIndex < currentRound.size) {
            val leftBout = currentRound[boutIndex]
            val leftWinner = automaticBoutWinner(leftBout)
                ?: sparringWinners[roundIndex]?.get(boutIndex)

            val rightBout = currentRound.getOrNull(boutIndex + 1)
            val rightWinner = when {
                rightBout == null -> null
                else -> automaticBoutWinner(rightBout)
                    ?: sparringWinners[roundIndex]?.get(boutIndex + 1)
            }

            if (leftWinner != null && rightWinner != null) {
                nextRound += com.summ0.tournamentscoringapp.engine.SparringBout(
                    boutNumber = 0,
                    competitorA = leftWinner,
                    competitorB = rightWinner
                )
                boutIndex += 2
            } else if (leftWinner != null) {
                nextRound += com.summ0.tournamentscoringapp.engine.SparringBout(
                    boutNumber = 0,
                    competitorA = leftWinner,
                    competitorB = null
                )
                boutIndex += 1
            } else {
                boutIndex += 1
            }
        }

        currentRound = nextRound
        roundIndex += 1
    }

    val finalRound = rounds.getOrNull(rounds.lastIndex) ?: return emptySparringAwardPlacements()
    val finalBout = finalRound.firstOrNull() ?: return emptySparringAwardPlacements()
    val finalWinner = sparringWinners[rounds.lastIndex]?.get(0)
        ?: if (round0Bouts.size == 1) automaticBoutWinner(finalBout) else null
    if (finalWinner == null) {
        return emptySparringAwardPlacements()
    }
    val finalists = listOfNotNull(finalBout.competitorA, finalBout.competitorB).distinct()
    val champion = finalWinner
    val runnerUp = finalists.firstOrNull { it != champion }

    val semifinalRound = rounds.getOrNull(rounds.lastIndex - 1) ?: emptyList()
    val semifinalLosers = mutableListOf<Competitor>()
    semifinalRound.forEachIndexed { boutIndex, bout ->
        val boutWinner = automaticBoutWinner(bout)
            ?: sparringWinners[rounds.lastIndex - 1]?.get(boutIndex)
        if (boutWinner != null) {
            val loser = when {
                bout.competitorA == boutWinner -> bout.competitorB
                bout.competitorB == boutWinner -> bout.competitorA
                else -> null
            }
            if (loser != null) {
                semifinalLosers += loser
            }
        }
    }

    return listOfNotNull(
        "1st Place" to champion,
        "2nd Place" to runnerUp,
        "Co-3rd Place" to semifinalLosers.getOrNull(0),
        "Co-3rd Place" to semifinalLosers.getOrNull(1)
    )
}

private fun automaticBoutWinner(bout: com.summ0.tournamentscoringapp.engine.SparringBout): Competitor? {
    return when {
        !bout.isBye -> null
        bout.competitorA != null && bout.competitorB == null -> bout.competitorA
        bout.competitorB != null && bout.competitorA == null -> bout.competitorB
        else -> null
    }
}

private fun emptySparringAwardPlacements(): List<Pair<String, Competitor?>> {
    return listOf(
        "1st Place" to null,
        "2nd Place" to null,
        "Co-3rd Place" to null,
        "Co-3rd Place" to null
    )
}

private const val BRACKET_BASE_SLOT_HEIGHT = 200
private const val BRACKET_CARD_WIDTH = 260
private const val BRACKET_COLUMN_WIDTH = 336
private const val SPARRING_MATCH_DURATION_SECONDS = 120

private data class BracketParticipant(
    val competitor: Competitor?,
    val sourceLabel: String? = null
)

private data class BracketSheetBout(
    val boutNumber: Int,
    val blue: BracketParticipant,
    val red: BracketParticipant,
    val actualBye: Boolean,
    val showOnSheet: Boolean
)

private data class BracketSheetRound(
    val label: String,
    val slotHeightMultiplier: Int,
    val slots: List<BracketSheetBout>
)

@Composable
private fun BracketRoundColumn(
    roundIndex: Int,
    round: BracketSheetRound,
    totalRounds: Int,
    baseSlotCount: Int,
    roundWinners: Map<Int, Competitor>,
    boutProgressByKey: Map<String, SparringBoutProgress>,
    onOpenBout: (boutIndex: Int, bout: BracketSheetBout) -> Unit
) {
    Column(
        modifier = Modifier
            .width(BRACKET_COLUMN_WIDTH.dp)
            .padding(end = 8.dp)
    ) {
        Text(round.label, modifier = Modifier.padding(bottom = 4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height((BRACKET_BASE_SLOT_HEIGHT * baseSlotCount).dp)
        ) {
            if (roundIndex < totalRounds - 1) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val regionHeight = size.height / round.slots.size.toFloat()
                    val xStart = BRACKET_CARD_WIDTH.dp.toPx()
                    val xMid = xStart + 18.dp.toPx()
                    val xEnd = size.width
                    for (pairStart in round.slots.indices step 2) {
                        val firstSlot = round.slots[pairStart]
                        val secondSlot = round.slots.getOrNull(pairStart + 1)
                        val firstCenterY = (pairStart * regionHeight) + (regionHeight / 2f)
                        val secondCenterY = secondSlot?.let {
                            ((pairStart + 1) * regionHeight) + (regionHeight / 2f)
                        }
                        val firstVisible = shouldDrawBracketConnector(roundIndex, firstSlot)
                        val secondVisible = secondSlot?.let { shouldDrawBracketConnector(roundIndex, it) } == true

                        if (firstVisible) {
                            drawLine(
                                color = Color(0xFF9AA0A6),
                                start = Offset(xStart, firstCenterY),
                                end = Offset(xMid, firstCenterY),
                                strokeWidth = 2.dp.toPx()
                            )
                        }
                        if (secondVisible && secondCenterY != null) {
                            drawLine(
                                color = Color(0xFF9AA0A6),
                                start = Offset(xStart, secondCenterY),
                                end = Offset(xMid, secondCenterY),
                                strokeWidth = 2.dp.toPx()
                            )
                        }

                        when {
                            firstVisible && secondVisible && secondCenterY != null -> {
                                val secondY = secondCenterY
                                val targetY = (firstCenterY + secondY) / 2f
                                drawLine(
                                    color = Color(0xFF9AA0A6),
                                    start = Offset(xMid, firstCenterY),
                                    end = Offset(xMid, secondY),
                                    strokeWidth = 2.dp.toPx()
                                )
                                drawLine(
                                    color = Color(0xFF9AA0A6),
                                    start = Offset(xMid, targetY),
                                    end = Offset(xEnd, targetY),
                                    strokeWidth = 2.dp.toPx()
                                )
                            }
                            firstVisible -> {
                                drawLine(
                                    color = Color(0xFF9AA0A6),
                                    start = Offset(xMid, firstCenterY),
                                    end = Offset(xEnd, firstCenterY),
                                    strokeWidth = 2.dp.toPx()
                                )
                            }
                            secondVisible && secondCenterY != null -> {
                                val secondY = secondCenterY
                                drawLine(
                                    color = Color(0xFF9AA0A6),
                                    start = Offset(xMid, secondY),
                                    end = Offset(xEnd, secondY),
                                    strokeWidth = 2.dp.toPx()
                                )
                            }
                        }
                    }
                }
            }
            Column(modifier = Modifier.fillMaxSize()) {
                round.slots.forEachIndexed { boutIndex, bout ->
                    val effectiveWinner = selectedWinnerForDisplay(roundIndex, boutIndex, bout, roundWinners)
                    val progress = boutProgressByKey[sparringBoutKey(roundIndex, boutIndex)]
                    Box(
                        modifier = Modifier
                            .height((BRACKET_BASE_SLOT_HEIGHT * round.slotHeightMultiplier).dp)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        BracketBoutCard(
                            bout = bout,
                            progress = progress,
                            effectiveWinner = effectiveWinner,
                            onOpenBout = { onOpenBout(boutIndex, bout) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BracketBoutCard(
    bout: BracketSheetBout,
    progress: SparringBoutProgress?,
    effectiveWinner: Competitor?,
    onOpenBout: () -> Unit
) {
    if (!bout.showOnSheet) {
        return
    }
    Card(
        modifier = Modifier
            .width(BRACKET_CARD_WIDTH.dp)
            .padding(horizontal = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Bout ${bout.boutNumber}")
            Text(
                text = "Blue: ${bracketParticipantLabel(bout.blue)}",
                color = if (effectiveWinner != null && effectiveWinner == bout.blue.competitor) {
                    Color(0xFF2E7D32)
                } else {
                    Color.Unspecified
                },
                maxLines = 2
            )
            Text(
                text = "Red: ${bracketParticipantLabel(bout.red)}",
                color = if (effectiveWinner != null && effectiveWinner == bout.red.competitor) {
                    Color(0xFF2E7D32)
                } else {
                    Color.Unspecified
                },
                maxLines = 2
            )
            if (bout.actualBye) {
                Text("BYE", color = Color.Gray)
            } else if (bout.blue.competitor == null || bout.red.competitor == null) {
                Text("Awaiting prior winner", color = Color.Gray)
            } else {
                progress?.let {
                    Text(
                        "Score ${adjustedSparringScore(it.bluePoints, it.blueWarnings)} - ${adjustedSparringScore(it.redPoints, it.redWarnings)} | W ${it.blueWarnings}-${it.redWarnings}"
                    )
                    formatBracketOutcomeLabel(it.outcomeLabel)?.let { label ->
                        Text(label, color = Color.Gray)
                    }
                }
                if (effectiveWinner == null) {
                    Button(onClick = onOpenBout) {
                        Text("Score Bout")
                    }
                }
            }
        }
    }
}

@Composable
private fun SparringBoutDialog(
    bout: BracketSheetBout,
    initialProgress: SparringBoutProgress,
    onDismiss: () -> Unit,
    onSave: (SparringBoutProgress) -> Unit
) {
    val saveKey = listOfNotNull(
        bout.boutNumber.toString(),
        bout.blue.competitor?.id,
        bout.red.competitor?.id,
        bout.blue.sourceLabel,
        bout.red.sourceLabel
    ).joinToString("|")
    var bluePoints by rememberSaveable(saveKey) { mutableStateOf(initialProgress.bluePoints) }
    var redPoints by rememberSaveable(saveKey) { mutableStateOf(initialProgress.redPoints) }
    var blueWarnings by rememberSaveable(saveKey) { mutableStateOf(initialProgress.blueWarnings) }
    var redWarnings by rememberSaveable(saveKey) { mutableStateOf(initialProgress.redWarnings) }
    var elapsedSeconds by rememberSaveable(saveKey) { mutableStateOf(initialProgress.elapsedSeconds) }
    var timerRunning by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val assessment = assessSparringBout(
        bout = bout,
        bluePoints = bluePoints,
        redPoints = redPoints,
        blueWarnings = blueWarnings,
        redWarnings = redWarnings,
        elapsedSeconds = elapsedSeconds
    )
    val clockExpired = elapsedSeconds >= SPARRING_MATCH_DURATION_SECONDS

    // Auto-stop timer when winner is declared
    LaunchedEffect(assessment.winner) {
        if (assessment.winner != null) timerRunning = false
    }

    LaunchedEffect(timerRunning, elapsedSeconds) {
        if (timerRunning && !clockExpired) {
            delay(1000)
            elapsedSeconds += 1
        } else if (timerRunning) {
            timerRunning = false
        }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.97f)
                .fillMaxHeight(0.95f)
                .imePadding(),
            shape = RoundedCornerShape(18.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                item {
                    Text(
                        text = "Bout ${bout.boutNumber}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Button(
                                    onClick = { timerRunning = true },
                                    enabled = !timerRunning && !clockExpired && assessment.winner == null,
                                    modifier = Modifier.width(84.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) { Text("Start") }
                                Button(
                                    onClick = { timerRunning = false },
                                    enabled = timerRunning,
                                    modifier = Modifier.width(84.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) { Text("Stop") }
                            }
                            OutlinedButton(
                                onClick = { timerRunning = false; elapsedSeconds = 0 },
                                modifier = Modifier.width(172.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) { Text("Reset") }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(top = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = formatRemainingTime(elapsedSeconds),
                                fontSize = 38.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (clockExpired) Color(0xFFD32F2F) else Color.Unspecified
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (assessment.winner == null) {
                                        message = "A clear winner is required before posting."
                                    } else {
                                        onSave(
                                            SparringBoutProgress(
                                                bluePoints = bluePoints,
                                                redPoints = redPoints,
                                                blueWarnings = blueWarnings,
                                                redWarnings = redWarnings,
                                                elapsedSeconds = elapsedSeconds,
                                                winnerId = assessment.winner.id,
                                                outcomeLabel = assessment.outcomeLabel
                                            )
                                        )
                                    }
                                },
                                modifier = Modifier.width(90.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("POST")
                            }
                            TextButton(
                                onClick = onDismiss,
                                modifier = Modifier.width(90.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("Cancel")
                            }
                        }
                    }
                }

                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        SparringCompetitorPanel(
                            colorLabel = "RED",
                            panelColor = Color(0xFFFFE0E0),
                            labelColor = Color(0xFFD32F2F),
                            participant = bout.red,
                            points = redPoints,
                            warnings = redWarnings,
                            adjustedScore = assessment.redAdjustedScore,
                            disqualified = assessment.redDisqualified,
                            isWinner = assessment.winner != null && assessment.winner == bout.red.competitor,
                            onPointsChanged = { redPoints = it },
                            onWarningsChanged = { redWarnings = it },
                            modifier = Modifier.weight(1f)
                        )
                        SparringCompetitorPanel(
                            colorLabel = "BLUE",
                            panelColor = Color(0xFFE3F2FD),
                            labelColor = Color(0xFF1565C0),
                            participant = bout.blue,
                            points = bluePoints,
                            warnings = blueWarnings,
                            adjustedScore = assessment.blueAdjustedScore,
                            disqualified = assessment.blueDisqualified,
                            isWinner = assessment.winner != null && assessment.winner == bout.blue.competitor,
                            onPointsChanged = { bluePoints = it },
                            onWarningsChanged = { blueWarnings = it },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (message != null) {
                    item {
                        Text(
                            text = message ?: "",
                            color = Color(0xFFD32F2F),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SparringCompetitorPanel(
    colorLabel: String,
    panelColor: Color,
    labelColor: Color,
    participant: BracketParticipant,
    points: Int,
    warnings: Int,
    adjustedScore: Int,
    disqualified: Boolean,
    isWinner: Boolean,
    onPointsChanged: (Int) -> Unit,
    onWarningsChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.heightIn(min = 220.dp),
        colors = CardDefaults.cardColors(containerColor = panelColor)
    ) {
        Column(
            modifier = Modifier
                .padding(6.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = colorLabel,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = labelColor
            )
            Text(
                text = bracketParticipantLabel(participant),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            when {
                isWinner -> Text(
                    text = "?? WINNER",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32)
                )
                disqualified -> Text(
                    text = "DQ",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFD32F2F)
                )
                else -> Spacer(modifier = Modifier.height(14.dp))
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

            Text(
                text = "$adjustedScore",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = labelColor
            )
            Text(
                text = "Score",
                fontSize = 10.sp,
                color = Color.Gray
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { onPointsChanged(points - 1) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) { Text("-", fontSize = 18.sp) }
                Button(
                    onClick = { onPointsChanged(points + 1) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) { Text("+", fontSize = 18.sp) }
            }

            Text(
                text = "$warnings",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = if (disqualified) Color(0xFFD32F2F) else Color.Unspecified
            )
            Text(
                text = "Warnings",
                fontSize = 10.sp,
                color = Color.Gray
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { onWarningsChanged((warnings - 1).coerceAtLeast(0)) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) { Text("-") }
                OutlinedButton(
                    onClick = { onWarningsChanged(warnings + 1) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) { Text("+") }
            }
        }
    }
}

private fun bracketParticipantLabel(participant: BracketParticipant): String {
    return participant.competitor?.let { "${it.name} (${it.studio})" }
        ?: participant.sourceLabel
        ?: "Open slot"
}

private fun sparringBoutKey(roundIndex: Int, boutIndex: Int): String {
    return "r${roundIndex}_b${boutIndex}"
}

private fun shouldDrawBracketConnector(roundIndex: Int, slot: BracketSheetBout): Boolean {
    return roundIndex > 0 || slot.showOnSheet
}

private fun selectedWinnerForDisplay(
    roundIndex: Int,
    boutIndex: Int,
    bout: BracketSheetBout,
    roundWinners: Map<Int, Competitor>
): Competitor? {
    return if (roundIndex == 0 && bout.actualBye) {
        bout.blue.competitor ?: bout.red.competitor
    } else {
        roundWinners[boutIndex]
    }
}

private fun adjustedSparringScore(points: Int, warnings: Int): Int {
    return points - (warnings / 2)
}

private fun formatBracketOutcomeLabel(outcomeLabel: String): String? {
    if (outcomeLabel.isBlank()) return null
    val normalized = outcomeLabel.trim()
    return if (normalized.contains("DQ", ignoreCase = true)) {
        normalized
    } else {
        null
    }
}

private fun assessSparringBout(
    bout: BracketSheetBout,
    bluePoints: Int,
    redPoints: Int,
    blueWarnings: Int,
    redWarnings: Int,
    elapsedSeconds: Int
): SparringBoutAssessment {
    val blueCompetitor = bout.blue.competitor
    val redCompetitor = bout.red.competitor
    val blueAdjustedScore = adjustedSparringScore(bluePoints, blueWarnings)
    val redAdjustedScore = adjustedSparringScore(redPoints, redWarnings)
    val blueDisqualified = blueWarnings >= 3
    val redDisqualified = redWarnings >= 3

    val winner = when {
        blueDisqualified && redDisqualified -> null
        blueDisqualified -> redCompetitor
        redDisqualified -> blueCompetitor
        blueAdjustedScore >= 3 || redAdjustedScore >= 3 -> when {
            blueAdjustedScore > redAdjustedScore -> blueCompetitor
            redAdjustedScore > blueAdjustedScore -> redCompetitor
            else -> null
        }

        elapsedSeconds >= SPARRING_MATCH_DURATION_SECONDS -> when {
            blueAdjustedScore > redAdjustedScore -> blueCompetitor
            redAdjustedScore > blueAdjustedScore -> redCompetitor
            else -> null
        }

        else -> null
    }
    val outcomeLabel = when {
        blueDisqualified -> "Blue DQ"
        redDisqualified -> "Red DQ"
        else -> "In progress"
    }

    return SparringBoutAssessment(
        blueAdjustedScore = blueAdjustedScore,
        redAdjustedScore = redAdjustedScore,
        blueDisqualified = blueDisqualified,
        redDisqualified = redDisqualified,
        winner = winner,
        outcomeLabel = outcomeLabel
    )
}

private fun formatRemainingTime(elapsedSeconds: Int): String {
    val remaining = (SPARRING_MATCH_DURATION_SECONDS - elapsedSeconds).coerceAtLeast(0)
    val minutes = remaining / 60
    val seconds = remaining % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}

private fun calculateScoreSummary(scoreInputs: List<String>): String {
    val total = calculateScoreTotal(scoreInputs) ?: return when {
        scoreInputs.any { it.isNotBlank() && it.toDoubleOrNull() == null } -> "Invalid score"
        scoreInputs.any { it.isNotBlank() } -> "Need 3 or 5 scores"
        else -> ""
    }
    return formatOneDecimal(total)
}

private fun formatOneDecimal(value: Double): String {
    return String.format(Locale.US, "%.1f", value)
}

private fun buildScoreDisplayLabel(baseLabel: String, tieBreakDetail: String?): String {
    if (baseLabel.isBlank()) return ""
    val detail = tieBreakDetail?.trim().orEmpty()
    return if (detail.isBlank()) baseLabel else "$baseLabel  $detail"
}

private fun buildSparringReviewLines(
    round0Bouts: List<com.summ0.tournamentscoringapp.engine.SparringBout>,
    winnersByRound: Map<Int, Map<Int, Competitor>>,
    boutProgressByKey: Map<String, SparringBoutProgress>
): List<String> {
    if (round0Bouts.isEmpty()) return emptyList()

    val rounds = buildBracketSheetRounds(round0Bouts, winnersByRound)
    val lines = mutableListOf<String>()
    rounds.forEachIndexed { roundIndex, round ->
        val roundWinners = winnersByRound[roundIndex] ?: emptyMap()
        round.slots.forEachIndexed { boutIndex, bout ->
            if (!bout.showOnSheet) return@forEachIndexed

            val blueLabel = bracketParticipantLabel(bout.blue)
            val redLabel = bracketParticipantLabel(bout.red)
            val progress = boutProgressByKey[sparringBoutKey(roundIndex, boutIndex)]
            val winner = selectedWinnerForDisplay(roundIndex, boutIndex, bout, roundWinners)
            val scoreLabel = when {
                progress != null -> {
                    "Score ${adjustedSparringScore(progress.bluePoints, progress.blueWarnings)}-" +
                        "${adjustedSparringScore(progress.redPoints, progress.redWarnings)} " +
                        "W ${progress.blueWarnings}-${progress.redWarnings}"
                }
                bout.actualBye -> "BYE"
                else -> "No posted score"
            }
            val outcomeLabel = progress?.outcomeLabel?.trim().orEmpty()
            val outcomeSuffix = if (outcomeLabel.isBlank()) "" else " | $outcomeLabel"
            val winnerLabel = winner?.name ?: "N/A"
            lines += "${round.label} Bout ${bout.boutNumber}: Blue $blueLabel vs Red $redLabel | $scoreLabel | Winner: $winnerLabel$outcomeSuffix"
        }
    }
    return lines
}

private fun calculateScoreTotal(scoreInputs: List<String>): Double? {
    val entered = scoreInputs.filter { it.isNotBlank() }
    if (entered.isEmpty()) return null

    val parsed = entered.map { it.toDoubleOrNull() }
    if (parsed.any { it == null }) return null
    val scores = parsed.filterNotNull()
    if (scores.size != 3 && scores.size != 5) return null

    return TournamentEngine.calculateHyungTotal(scores)
}

private val rankDivisionLevels = mapOf(
    "1st gup" to 1,
    "2nd gup" to 2,
    "3rd gup" to 3,
    "4th gup" to 4,
    "5th gup" to 5,
    "6th gup" to 6,
    "7th gup" to 7,
    "8th gup" to 8,
    "9th gup" to 9,
    "10th gup" to 10,
    "cdb" to 0,
    "cho dan bo" to 0,
    "cho dan" to -1,
    "e dan" to -2,
    "sam dan" to -3
)

private val rankSortOrder = mapOf(
    "10th gup" to 1,
    "9th gup" to 2,
    "8th gup" to 3,
    "7th gup" to 4,
    "6th gup" to 5,
    "5th gup" to 6,
    "4th gup" to 7,
    "3rd gup" to 8,
    "2nd gup" to 9,
    "1st gup" to 10,
    "cdb" to 11,
    "cho dan bo" to 11,
    "cho dan" to 12,
    "e dan" to 13,
    "sam dan" to 14
)

private fun normalizeRank(rank: String): String {
    return rank.trim().lowercase()
}

private fun rankLevelFor(rank: String): Int {
    return rankDivisionLevels[normalizeRank(rank)] ?: Int.MAX_VALUE
}

private fun rankLabelForLevel(level: Int): String {
    return when (level) {
        1 -> "1st Gup"
        2 -> "2nd Gup"
        3 -> "3rd Gup"
        4 -> "4th Gup"
        5 -> "5th Gup"
        6 -> "6th Gup"
        7 -> "7th Gup"
        8 -> "8th Gup"
        9 -> "9th Gup"
        10 -> "10th Gup"
        0 -> "Cho Dan Bo"
        -1 -> "Cho Dan"
        -2 -> "E Dan"
        -3 -> "Sam Dan"
        else -> level.toString()
    }
}

private fun allRankLabels(): List<String> {
    return listOf(
        "1st Gup",
        "2nd Gup",
        "3rd Gup",
        "4th Gup",
        "5th Gup",
        "6th Gup",
        "7th Gup",
        "8th Gup",
        "9th Gup",
        "10th Gup",
        "Cho Dan Bo",
        "Cho Dan",
        "E Dan",
        "Sam Dan"
    )
}

@Composable
private fun DropdownSelector(
    label: String,
    selectedValue: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedTextField(
            value = selectedValue,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { expanded = true },
            enabled = options.isNotEmpty(),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp)
        ) {
            Text("Select")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun validateCompetitorForGroup(
    division: Division,
    competitor: Competitor
): String? {
    if (competitor.name.isBlank()) return "Name is required."
    if (competitor.studio.isBlank()) return "Studio is required."
    if (competitor.age !in division.ageRange) {
        return "Age ${competitor.age} is outside this division range (${division.ageRange.first}-${division.ageRange.last})."
    }
    if (competitor.rankLevel !in division.rankRange) {
        return "Rank ${competitor.rank} is outside this division range."
    }
    if (competitor.heightInInches <= 0) {
        return "Height must be greater than zero."
    }
    return null
}

@Preview(showBackground = true)
@Composable
fun CheckInScreenPreview() {
    TournamentScoringAppTheme {
        CheckInScreen()
    }
}

@Composable
private fun CheckInColumnHeaderRow(
    dense: Boolean,
    modifier: Modifier = Modifier
) {
    val headerSize = if (dense) 10.sp else 11.sp
    val controlWidth = if (dense) 60.dp else 64.dp
    val controlGap = 10.dp
    Row(
        modifier = modifier.padding(horizontal = 4.dp, vertical = if (dense) 0.dp else 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Competitor",
            fontWeight = FontWeight.SemiBold,
            fontSize = headerSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(controlGap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.width(controlWidth), contentAlignment = Alignment.Center) {
                Text(
                text = "Checked In",
                fontWeight = FontWeight.SemiBold,
                fontSize = headerSize,
                textAlign = TextAlign.Center,
                maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(modifier = Modifier.width(controlWidth), contentAlignment = Alignment.Center) {
                Text(
                text = "Sparring",
                fontWeight = FontWeight.SemiBold,
                fontSize = headerSize,
                textAlign = TextAlign.Center,
                maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun LandscapeCheckInHeaderRow(
    modifier: Modifier = Modifier
) {
    val groupGap = 16.dp
    Row(
        modifier = modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CheckInHeaderGroup(Modifier.weight(1f))
        Spacer(modifier = Modifier.width(groupGap))
        CheckInHeaderGroup(Modifier.weight(1f))
    }
}

@Composable
private fun CheckInHeaderGroup(
    modifier: Modifier = Modifier
) {
    val headerSize = 10.sp
    val controlWidth = 56.dp
    val controlGap = 0.dp
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Competitor",
            fontWeight = FontWeight.SemiBold,
            fontSize = headerSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(controlGap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.width(controlWidth), contentAlignment = Alignment.Center) {
                Text(
                    text = "Checked In",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = headerSize,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(modifier = Modifier.width(controlWidth), contentAlignment = Alignment.Center) {
                Text(
                    text = "Sparring",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = headerSize,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CompactCompetitorCheckInRow(
    competitor: Competitor,
    sparringOptedOut: Boolean,
    canEditSparring: Boolean,
    dense: Boolean,
    onToggleCheckIn: () -> Unit,
    onToggleSparringOptOut: () -> Unit
) {
    val controlWidth = if (dense) 60.dp else 64.dp
    val controlGap = 10.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (dense) 2.dp else 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = competitor.name,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(controlGap), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.width(controlWidth), contentAlignment = Alignment.Center) {
                CheckInCircleToggle(
                    selected = competitor.checkInStatus == CheckInStatus.CHECKED_IN,
                    fillColor = AndroidColor.parseColor("#2E7D32"),
                    contentDescription = if (competitor.checkInStatus == CheckInStatus.CHECKED_IN) {
                        "Checked in"
                    } else {
                        "Not checked in"
                    },
                    sizeDp = if (dense) 32.dp else 30.dp,
                    onClick = onToggleCheckIn
                )
            }
            Box(modifier = Modifier.width(controlWidth), contentAlignment = Alignment.Center) {
                CheckInCircleToggle(
                    selected = !sparringOptedOut,
                    fillColor = AndroidColor.parseColor("#6A1B9A"),
                    contentDescription = if (sparringOptedOut) {
                        "Sparring opted out"
                    } else {
                        "Sparring active"
                    },
                    enabled = canEditSparring && competitor.checkInStatus == CheckInStatus.CHECKED_IN,
                    sizeDp = if (dense) 32.dp else 30.dp,
                    onClick = onToggleSparringOptOut
                )
            }
        }
    }
}

@Composable
private fun LandscapeCompetitorCheckInRow(
    competitor: Competitor,
    sparringOptedOut: Boolean,
    canEditSparring: Boolean,
    onToggleCheckIn: () -> Unit,
    onToggleSparringOptOut: () -> Unit
) {
    val controlWidth = 56.dp
    val controlGap = 0.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = competitor.name,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(controlGap), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.width(controlWidth), contentAlignment = Alignment.Center) {
                CheckInCircleToggle(
                    selected = competitor.checkInStatus == CheckInStatus.CHECKED_IN,
                    fillColor = AndroidColor.parseColor("#2E7D32"),
                    contentDescription = if (competitor.checkInStatus == CheckInStatus.CHECKED_IN) {
                        "Checked in"
                    } else {
                        "Not checked in"
                    },
                    sizeDp = 30.dp,
                    onClick = onToggleCheckIn
                )
            }
            Box(modifier = Modifier.width(controlWidth), contentAlignment = Alignment.Center) {
                CheckInCircleToggle(
                    selected = !sparringOptedOut,
                    fillColor = AndroidColor.parseColor("#6A1B9A"),
                    contentDescription = if (sparringOptedOut) {
                        "Sparring opted out"
                    } else {
                        "Sparring active"
                    },
                    enabled = canEditSparring && competitor.checkInStatus == CheckInStatus.CHECKED_IN,
                    sizeDp = 30.dp,
                    onClick = onToggleSparringOptOut
                )
            }
        }
    }
}

@Composable
private fun CheckInCircleToggle(
    selected: Boolean,
    fillColor: Int,
    contentDescription: String,
    enabled: Boolean = true,
    sizeDp: Dp,
    onClick: () -> Unit
) {
    val strokeColor = AndroidColor.parseColor("#B8B8B8")
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(sizeDp),
        shape = RoundedCornerShape(50),
        contentPadding = PaddingValues(0.dp),
        border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color(strokeColor)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) androidx.compose.ui.graphics.Color(fillColor) else androidx.compose.ui.graphics.Color.Transparent,
            contentColor = if (selected) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color(strokeColor)
        )
    ) {
        Text(
            text = if (selected) "●" else "○",
            modifier = Modifier,
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
    }
}

@Composable
private fun CompetitorCheckInCard(
    competitor: Competitor,
    dense: Boolean,
    onToggleCheckIn: () -> Unit,
    sparringOptedOut: Boolean,
    onToggleSparringOptOut: () -> Unit,
    canEditSparring: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (dense) 96.dp else 112.dp)
    ) {
        Column(
            modifier = Modifier.padding(if (dense) 8.dp else 10.dp),
            verticalArrangement = Arrangement.spacedBy(if (dense) 2.dp else 3.dp)
        ) {
            Text(text = competitor.name, fontWeight = FontWeight.Bold, fontSize = if (dense) 13.sp else 14.sp)
            Text(text = "Rank: ${competitor.rank} · Age: ${competitor.age}", fontSize = if (dense) 12.sp else 13.sp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (dense) 6.dp else 8.dp)
            ) {
                Button(
                    onClick = onToggleCheckIn,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (competitor.checkInStatus == CheckInStatus.CHECKED_IN) "●" else "○")
                }
                Button(
                    onClick = onToggleSparringOptOut,
                    enabled = canEditSparring && competitor.checkInStatus == CheckInStatus.CHECKED_IN,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (sparringOptedOut) "○" else "●")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (competitor.checkInStatus == CheckInStatus.CHECKED_IN) "Present" else "Absent",
                    fontSize = if (dense) 11.sp else 12.sp
                )
                Text(
                    text = if (sparringOptedOut) "No Sparring" else "Sparring",
                    fontSize = if (dense) 11.sp else 12.sp
                )
            }
        }
    }
}

private fun isSparringOptedOut(competitor: Competitor): Boolean {
    return competitor.competitionEntries[CompetitionType.SPARRING]?.status == CompetitionRegistrationStatus.SCRATCHED
}

private fun applyCheckInDefaults(competitor: Competitor): Competitor {
    val hasWeapons = try {
        TournamentEngine.getAllowedHyungForms(competitor.rank, HyungDiscipline.WEAPONS).isNotEmpty()
    } catch (_: Exception) {
        false
    }
    var updated = competitor
    if (CompetitionType.HYUNGS !in updated.competitionEntries) {
        updated = TournamentEngine.updateCompetitionEntryStatus(
            updated, CompetitionType.HYUNGS, CompetitionRegistrationStatus.REGISTERED
        )
    }
    updated = TournamentEngine.updateCompetitionEntryStatus(
        updated, CompetitionType.SPARRING, CompetitionRegistrationStatus.REGISTERED
    )
    if (hasWeapons && CompetitionType.WEAPONS !in updated.competitionEntries) {
        updated = TournamentEngine.updateCompetitionEntryStatus(
            updated, CompetitionType.WEAPONS, CompetitionRegistrationStatus.REGISTERED
        )
    }
    return updated
}

private fun capitalizeWords(input: String): String {
    return input
        .split(" ")
        .joinToString(" ") { word -> capitalizeNameWord(word) }
}

private fun capitalizeNameWord(word: String): String {
    if (word.isEmpty()) return word

    val normalized = word.lowercase(Locale.getDefault())
    val result = StringBuilder(normalized.length)
    var capitalizeNext = true

    normalized.forEach { character ->
        when {
            capitalizeNext && character.isLetter() -> {
                result.append(character.titlecase(Locale.getDefault()))
                capitalizeNext = false
            }

            else -> {
                result.append(character)
                capitalizeNext = character == '-' || character == '\''
            }
        }
    }

    return result.toString()
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

private fun isManualCompetitor(competitor: Competitor?): Boolean {
    return competitor?.id?.startsWith("manual-") == true
}

private fun entrantsForPhase(
    competitors: List<Competitor>,
    competitionType: CompetitionType
): Set<String> {
    return competitors
        .filter { TournamentEngine.isEligibleForCompetition(it, competitionType) }
        .map { it.id }
        .toSet()
}

private fun phaseEntrantCount(
    competitionType: CompetitionType,
    phaseEntrants: Map<CompetitionType, Set<String>>,
    competitors: List<Competitor>
): Int {
    return phaseEntrants[competitionType]?.size ?: entrantsForPhase(competitors, competitionType).size
}

private fun rankOrder(rank: String): Int {
    return rankSortOrder[normalizeRank(rank)] ?: Int.MAX_VALUE
}

private fun buildBracketSheetRounds(
    round0Bouts: List<com.summ0.tournamentscoringapp.engine.SparringBout>,
    winners: Map<Int, Map<Int, Competitor>>
): List<BracketSheetRound> {
    if (round0Bouts.isEmpty()) return emptyList()

    val rounds = mutableListOf<List<BracketSheetBout>>()
    var globalBoutNumber = 1
    var currentRound = round0Bouts.map { bout ->
        BracketSheetBout(
            boutNumber = globalBoutNumber++,
            blue = BracketParticipant(
                competitor = bout.competitorA,
                sourceLabel = if (bout.isBye && bout.competitorA == null) "BYE" else null
            ),
            red = BracketParticipant(
                competitor = bout.competitorB,
                sourceLabel = if (bout.isBye && bout.competitorB == null) "BYE" else null
            ),
            actualBye = bout.isBye,
            showOnSheet = true
        )
    }
    var roundIndex = 0

    while (true) {
        rounds += currentRound
        if (currentRound.size == 1) break

        val nextRoundSlots = mutableListOf<BracketSheetBout>()
        var slotIndex = 0
        while (slotIndex < currentRound.size) {
            val leftBout = currentRound[slotIndex]
            val leftWinner = selectedWinnerForDisplay(
                roundIndex = roundIndex,
                boutIndex = slotIndex,
                bout = leftBout,
                roundWinners = winners[roundIndex] ?: emptyMap()
            )

            val rightIndex = slotIndex + 1
            val rightBout = currentRound.getOrNull(rightIndex)
            val rightWinner = rightBout?.let {
                selectedWinnerForDisplay(
                    roundIndex = roundIndex,
                    boutIndex = rightIndex,
                    bout = it,
                    roundWinners = winners[roundIndex] ?: emptyMap()
                )
            }
            nextRoundSlots += BracketSheetBout(
                boutNumber = globalBoutNumber++,
                blue = BracketParticipant(
                    competitor = leftWinner,
                    sourceLabel = if (leftWinner == null) {
                        sourceLabelFor(roundIndex, leftBout.boutNumber)
                    } else {
                        null
                    }
                ),
                red = BracketParticipant(
                    competitor = rightWinner,
                    sourceLabel = if (rightWinner == null && rightBout != null) {
                        sourceLabelFor(roundIndex, rightBout.boutNumber)
                    } else {
                        null
                    }
                ),
                actualBye = false,
                showOnSheet = true
            )
            slotIndex += 2
        }

        if (nextRoundSlots.isEmpty()) {
            break
        }

        currentRound = nextRoundSlots
        roundIndex++
    }

    return rounds.mapIndexed { index, slots ->
        BracketSheetRound(
            label = roundLabelFor(index, rounds.size),
            slotHeightMultiplier = 1 shl index,
            slots = slots
        )
    }
}

private fun roundLabelFor(roundIndex: Int, totalRounds: Int): String {
    return when {
        totalRounds == 1 -> "Finals"
        roundIndex == totalRounds - 1 -> "Finals"
        else -> "Round ${roundIndex + 1}"
    }
}

private fun sourceLabelFor(roundIndex: Int, boutNumber: Int): String {
    return "Winner R${roundIndex + 1}B$boutNumber"
}

private fun separateStudioPairs(
    bouts: List<com.summ0.tournamentscoringapp.engine.SparringBout>
): List<com.summ0.tournamentscoringapp.engine.SparringBout> {
    if (bouts.size < 2) return bouts
    val result = bouts.toMutableList()
    for (i in 0 until result.size - 1) {
        val bout = result[i]
        val a = bout.competitorA ?: continue
        val b = bout.competitorB ?: continue
        if (a.studio != b.studio) continue
        val j = i + 1
        val swapBout = result[j]
        val c = swapBout.competitorA ?: continue
        val d = swapBout.competitorB ?: continue
        if (abs(a.heightInInches - c.heightInInches) <= 4 &&
            abs(b.heightInInches - d.heightInInches) <= 4 &&
            a.studio != c.studio
        ) {
            result[i] = bout.copy(competitorB = c)
            result[j] = swapBout.copy(competitorA = b)
        }
    }
    return result
}
