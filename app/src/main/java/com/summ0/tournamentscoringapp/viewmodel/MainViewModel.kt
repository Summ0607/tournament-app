package com.summ0.tournamentscoringapp

import android.content.Context
import com.summ0.tournamentscoringapp.engine.DivisionResultPacketBuildRequest
import com.summ0.tournamentscoringapp.engine.DivisionResultPacketBuilder
import com.summ0.tournamentscoringapp.engine.DivisionResultPacketStore
import com.summ0.tournamentscoringapp.engine.DivisionResultSubmissionResult
import com.summ0.tournamentscoringapp.engine.DivisionResultSubmissionRingAssignment
import com.summ0.tournamentscoringapp.engine.HeartbeatProgressSnapshot
import com.summ0.tournamentscoringapp.engine.HttpProbeResult
import com.summ0.tournamentscoringapp.engine.JsonFetchResult
import com.summ0.tournamentscoringapp.engine.RemoteGroupFetchResult
import com.summ0.tournamentscoringapp.engine.RingAssignmentFetchResult
import com.summ0.tournamentscoringapp.engine.RingConfigFetchResult
import com.summ0.tournamentscoringapp.engine.ServerConnectionConfig
import com.summ0.tournamentscoringapp.engine.ServerConnectionMode
import com.summ0.tournamentscoringapp.engine.buildServerBaseUrl
import com.summ0.tournamentscoringapp.engine.fetchRemoteGroup
import com.summ0.tournamentscoringapp.engine.fetchRingAssignment
import com.summ0.tournamentscoringapp.engine.fetchRingConfig
import com.summ0.tournamentscoringapp.engine.fetchServerVersionCode
import com.summ0.tournamentscoringapp.engine.loadServerConnectionConfig
import com.summ0.tournamentscoringapp.engine.parseServerConnectionMode
import com.summ0.tournamentscoringapp.engine.persistServerConnectionConfig
import com.summ0.tournamentscoringapp.engine.probeHttpOk
import com.summ0.tournamentscoringapp.engine.sendRingHeartbeat
import com.summ0.tournamentscoringapp.engine.serializeDivisionResultPacket
import com.summ0.tournamentscoringapp.engine.submitDivisionResult
import org.json.JSONObject
import java.io.File
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URI


sealed interface DivisionResultUploadStatus {
    object Idle : DivisionResultUploadStatus
    object Saving : DivisionResultUploadStatus
    object Uploading : DivisionResultUploadStatus
    data class Accepted(
        val submissionId: String,
        val serverRecordId: String,
        val ringAssignment: DivisionResultSubmissionRingAssignment?
    ) : DivisionResultUploadStatus

    data class Rejected(
        val submissionId: String,
        val errors: List<String>
    ) : DivisionResultUploadStatus

    data class PendingRetry(
        val submissionId: String,
        val message: String
    ) : DivisionResultUploadStatus
}

class MainViewModel(
    private val appContext: Context? = null,
    private val packetStore: DivisionResultPacketStore? = null,
    private val submitter: suspend (String, String, JSONObject) -> DivisionResultSubmissionResult =
        { serverBaseUrl, ringId, packetJson -> submitDivisionResult(serverBaseUrl, ringId, packetJson) }
) {
    var uploadStatus: DivisionResultUploadStatus = DivisionResultUploadStatus.Idle
        private set

    var latestRingAssignment: DivisionResultSubmissionRingAssignment? = null
        private set

    fun loadServerConnectionConfig(): ServerConnectionConfig =
        loadServerConnectionConfig(requireNotNull(appContext) { "Context is required" })

    fun saveServerConnectionConfig(config: ServerConnectionConfig) {
        persistServerConnectionConfig(requireNotNull(appContext) { "Context is required" }, config)
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

    suspend fun finalizeAndSubmitResults(
        serverBaseUrl: String,
        request: DivisionResultPacketBuildRequest
    ): DivisionResultSubmissionResult {
        Log.d(
            "DivisionResultUploader",
            "finalizeAndSubmitResults: eventName=${request.eventName} groupId=${request.groupId} " +
                "groupDivisionNumber=${request.groupDivisionNumber} groupName=${request.groupName} " +
                "competitors=${request.competitors.size} weaponsResults=${request.weaponsResults.size} " +
                "hyungsResults=${request.hyungsResults.size} sparringRounds=${request.sparringTournament?.rounds?.size ?: 0} " +
                "signatures=${request.signatures.size} weaponsFinalizeMessage=${request.weaponsFinalizeState?.message.orEmpty()} " +
                "hyungsFinalizeMessage=${request.hyungsFinalizeState?.message.orEmpty()}"
        )
        val packet = DivisionResultPacketBuilder.build(request)
        Log.d(
            "DivisionResultUploader",
            "Packet built: submissionId=${packet.submissionId} participants=${packet.participants.size} " +
                "weaponsResults=${packet.disciplines.weapons.results.size} hyungsResults=${packet.disciplines.hyungs.results.size} " +
                "sparringRounds=${packet.disciplines.sparring.rounds.size}"
        )
        return submitPacket(
            serverBaseUrl = serverBaseUrl,
            ringId = request.ringId,
            packetJson = serializeDivisionResultPacket(packet),
            submissionId = packet.submissionId
        )
    }

    class DivisionResultUploader(
        private val packetStore: DivisionResultPacketStore,
        private val serverBaseUrl: String // e.g., "http://192.168.4.10:3000/api"
    ) {
        companion object {
            private const val TAG = "DivisionResultUploader"
        }

        /**
         * Pulls unsent packets from the local store and pushes them to the server via HttpURLConnection.
         */
        suspend fun syncPendingPackets() {
            withContext(Dispatchers.IO) {
                val pendingPackets = packetStore.listPending()
                if (pendingPackets.isEmpty()) return@withContext

                for (packet in pendingPackets) {
                    var connection: HttpURLConnection? = null
                    try {
                        // Use URI to avoid deprecation warnings on the URL constructor
                        val url = URI("$serverBaseUrl/results").toURL()
                        connection = (url.openConnection() as HttpURLConnection).apply {
                            requestMethod = "POST"
                            connectTimeout = 10000 // 10-second timeout per HLD guidelines
                            readTimeout = 10000
                            setRequestProperty("Content-Type", "application/json; utf-8")
                            setRequestProperty("Accept", "application/json")
                            doOutput = true
                        }

                        // Write packet JSON payload to output stream
                        val jsonPayload = packet.readText()
                        OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                            writer.write(jsonPayload)
                            writer.flush()
                        }

                        val responseCode = connection.responseCode
                        if (responseCode in 200..299) {
                            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
                            val responseJson = runCatching {
                                JSONObject(responseBody.ifBlank { "{}" })
                            }.getOrElse { JSONObject() }
                            // Success: mark as synced in local packet store
                            packetStore.markSent(packet, responseJson)
                            Log.i(TAG, "Successfully uploaded packet: ${packet.nameWithoutExtension}")
                        } else {
                            // Server rejected or returned an error code; leave in store for retry
                            Log.w(TAG, "Server rejected packet ${packet.nameWithoutExtension} with HTTP code: $responseCode")
                            break
                        }
                    } catch (e: Exception) {
                        // Network dropped or timeout occurred; preserve locally and stop batch
                        Log.e(TAG, "Network error uploading packet ${packet.nameWithoutExtension}. Retaining locally.", e)
                        break
                    } finally {
                        connection?.disconnect()
                    }
                }
            }
        }
    }

    suspend fun retryPendingSubmissions(): List<DivisionResultSubmissionResult> {
        val store = packetStore()
        val pendingFiles = store.listPending()
        if (pendingFiles.isEmpty()) {
            uploadStatus = DivisionResultUploadStatus.Idle
            return emptyList()
        }

        val results = mutableListOf<DivisionResultSubmissionResult>()
        for (pendingFile in pendingFiles) {
            val metadata = store.loadMetadata(pendingFile)
            val packetText = pendingFile.readText()
            val packetJson = store.loadPacket(pendingFile)
            results += submitPacket(
                serverBaseUrl = metadata.serverBaseUrl,
                ringId = metadata.ringId,
                packetJson = packetJson,
                packetText = packetText,
                submissionId = packetJson.optString("submissionId", pendingFile.nameWithoutExtension),
                existingPendingFile = pendingFile
            )
        }
        return results
    }

    private suspend fun submitPacket(
        serverBaseUrl: String,
        ringId: String,
        packetJson: JSONObject,
        packetText: String = packetJson.toString(),
        submissionId: String,
        existingPendingFile: File? = null
    ): DivisionResultSubmissionResult {
        val store = packetStore()
        uploadStatus = DivisionResultUploadStatus.Saving
        val pendingFile = existingPendingFile ?: store.savePending(packetJson, serverBaseUrl, ringId)
        uploadStatus = DivisionResultUploadStatus.Uploading
        return when (val result = if (existingPendingFile == null) {
            submitter(serverBaseUrl, ringId, packetJson)
        } else {
            submitDivisionResult(serverBaseUrl, ringId, packetText)
        }) {
            is DivisionResultSubmissionResult.Accepted -> {
                store.markSent(pendingFile, result.response.rawJson)
                latestRingAssignment = result.response.ringAssignment
                uploadStatus = DivisionResultUploadStatus.Accepted(
                    submissionId = result.response.submissionId.ifBlank { submissionId },
                    serverRecordId = result.response.serverRecordId,
                    ringAssignment = result.response.ringAssignment
                )
                result
            }
            is DivisionResultSubmissionResult.Rejected -> {
                uploadStatus = DivisionResultUploadStatus.Rejected(
                    submissionId = submissionId,
                    errors = result.response.errors
                )
                result
            }
            is DivisionResultSubmissionResult.NetworkFailure -> {
                uploadStatus = DivisionResultUploadStatus.PendingRetry(
                    submissionId = submissionId,
                    message = result.failure.message
                )
                result
            }
        }
    }

    private fun packetStore(): DivisionResultPacketStore {
        return packetStore ?: DivisionResultPacketStore(requireNotNull(appContext) { "Context is required" }.filesDir)
    }
}
