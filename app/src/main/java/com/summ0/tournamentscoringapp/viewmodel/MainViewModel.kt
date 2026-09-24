package com.summ0.tournamentscoringapp

import android.content.Context
import com.summ0.tournamentscoringapp.engine.HeartbeatProgressSnapshot
import com.summ0.tournamentscoringapp.engine.HttpProbeResult
import com.summ0.tournamentscoringapp.engine.JsonFetchResult
import com.summ0.tournamentscoringapp.engine.RemoteGroupFetchResult
import com.summ0.tournamentscoringapp.engine.RingAssignmentFetchResult
import com.summ0.tournamentscoringapp.engine.RingConfigFetchResult
import com.summ0.tournamentscoringapp.engine.ServerConnectionConfig
import com.summ0.tournamentscoringapp.engine.fetchRemoteGroup
import com.summ0.tournamentscoringapp.engine.fetchRingAssignment
import com.summ0.tournamentscoringapp.engine.fetchRingConfig
import com.summ0.tournamentscoringapp.engine.fetchServerVersionCode
import com.summ0.tournamentscoringapp.engine.loadServerConnectionConfig
import com.summ0.tournamentscoringapp.engine.parseServerConnectionMode
import com.summ0.tournamentscoringapp.engine.persistServerConnectionConfig
import com.summ0.tournamentscoringapp.engine.probeHttpOk
import com.summ0.tournamentscoringapp.engine.sendRingHeartbeat
import com.summ0.tournamentscoringapp.engine.buildServerBaseUrl
import com.summ0.tournamentscoringapp.engine.ServerConnectionMode
import org.json.JSONObject

class MainViewModel(private val appContext: Context) {
    fun loadServerConnectionConfig(): ServerConnectionConfig =
        com.summ0.tournamentscoringapp.engine.loadServerConnectionConfig(appContext)

    fun saveServerConnectionConfig(config: ServerConnectionConfig) {
        persistServerConnectionConfig(appContext, config)
    }

    fun resolveServerBaseUrl(mode: ServerConnectionMode, serverAddressInput: String): String? {
        return buildServerBaseUrl(mode, serverAddressInput)
    }

    fun parseConnectionMode(modeName: String): ServerConnectionMode = parseServerConnectionMode(modeName)

    suspend fun requestRemoteGroup(urlString: String): RemoteGroupFetchResult = fetchRemoteGroup(urlString)

    suspend fun requestRingAssignment(
        urlString: String,
        method: String = "GET",
        jsonBody: JSONObject? = null
    ): RingAssignmentFetchResult = fetchRingAssignment(urlString, method = method, jsonBody = jsonBody)

    suspend fun requestRingConfig(urlString: String, tabletLabel: String = ""): RingConfigFetchResult {
        return fetchRingConfig(urlString, tabletLabel)
    }

    suspend fun requestServerHealth(urlString: String): HttpProbeResult = probeHttpOk(urlString)

    suspend fun sendHeartbeat(
        serverBaseUrl: String,
        ringId: String,
        phase: String,
        tabletLabel: String,
        progress: HeartbeatProgressSnapshot
    ): JsonFetchResult = sendRingHeartbeat(serverBaseUrl, ringId, phase, tabletLabel, progress)

    suspend fun requestServerVersionCode(serverBaseUrl: String): Int = fetchServerVersionCode(serverBaseUrl)
}
