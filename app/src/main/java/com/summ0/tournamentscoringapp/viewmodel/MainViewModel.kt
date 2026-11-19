package com.summ0.tournamentscoringapp

import android.content.Context
import com.summ0.tournamentscoringapp.engine.DivisionResultPacketBuildRequest
import com.summ0.tournamentscoringapp.engine.DivisionResultPacketBuilder
import com.summ0.tournamentscoringapp.engine.DivisionResultPacketStore
import com.summ0.tournamentscoringapp.engine.DivisionResultSubmissionResult
import com.summ0.tournamentscoringapp.engine.DivisionResultSubmissionNetworkFailure
import com.summ0.tournamentscoringapp.engine.DivisionResultSubmissionRingAssignment
import com.summ0.tournamentscoringapp.engine.buildFrozenCompletionSubmissionPayload
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
import com.summ0.tournamentscoringapp.engine.describeNetworkError
import com.summ0.tournamentscoringapp.engine.loadServerConnectionConfig
import com.summ0.tournamentscoringapp.engine.migrateLegacyServerBaseUrl
import com.summ0.tournamentscoringapp.engine.parseServerConnectionMode
import com.summ0.tournamentscoringapp.engine.persistServerConnectionConfig
import com.summ0.tournamentscoringapp.engine.probeHttpOk
import com.summ0.tournamentscoringapp.engine.sendRingHeartbeat
import com.summ0.tournamentscoringapp.engine.serializeDivisionResultPacket
import com.summ0.tournamentscoringapp.engine.submitDivisionResult
import org.json.JSONObject
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val pendingSyncMutex = Mutex()

    private companion object {
        private const val TAG = "DivisionResultUploader"
    }

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

    suspend fun requestServerVersionCode(serverBaseUrl: String): Int? = fetchServerVersionCode(serverBaseUrl)

    suspend fun finalizeAndSubmitResults(
        serverBaseUrl: String,
        request: DivisionResultPacketBuildRequest,
        completionSnapshot: JSONObject? = null,
        onPacketSaved: () -> Unit = {}
    ): DivisionResultSubmissionResult {
        val store = packetStore()
        val existingPending = store.findPendingPacket(
            eventName = request.eventName,
            ringId = request.ringId,
            groupId = request.groupId,
            groupDivisionNumber = request.groupDivisionNumber
        )
        val packetJson = existingPending?.let(store::loadPacket) ?: run {
            uploadStatus = DivisionResultUploadStatus.Saving
            val packet = DivisionResultPacketBuilder.build(request)
            val serialized = serializeDivisionResultPacket(packet)
            val outgoing = completionSnapshot?.let { snapshot ->
                buildFrozenCompletionSubmissionPayload(serialized, snapshot)
            } ?: serialized
            store.savePending(outgoing, serverBaseUrl, request.ringId)
            outgoing
        }
        onPacketSaved()
        val submissionId = packetJson.optString("submissionId")
        val results = syncPendingSubmissions()
        val targetResult = results.firstOrNull { it.first == submissionId }?.second
        if (targetResult != null) return targetResult

        val blockingResult = results.firstOrNull()
        val blockingSubmissionId = blockingResult?.first.orEmpty()
        val message = when (val blocking = blockingResult?.second) {
            is DivisionResultSubmissionResult.Rejected ->
                "Earlier division result $blockingSubmissionId was rejected by the server " +
                    "(HTTP ${blocking.response.statusCode}). Later results are held until it is resolved."
            is DivisionResultSubmissionResult.NetworkFailure ->
                "Earlier division result $blockingSubmissionId has no server receipt yet. " +
                    "Later results are held until it is accepted."
            else ->
                "An earlier saved division result has not been accepted yet. " +
                    "This division is saved on the tablet and will upload after the earlier result is resolved."
        }
        val statusCode = when (val blocking = blockingResult?.second) {
            is DivisionResultSubmissionResult.Rejected -> blocking.response.statusCode
            is DivisionResultSubmissionResult.NetworkFailure -> blocking.failure.statusCode
            else -> null
        }
        uploadStatus = DivisionResultUploadStatus.PendingRetry(submissionId, message)
        return DivisionResultSubmissionResult.NetworkFailure(
            DivisionResultSubmissionNetworkFailure(message, statusCode)
        )
    }

    class DivisionResultUploader(
        private val packetStore: DivisionResultPacketStore,
        private val serverBaseUrl: String // e.g., "https://192.168.4.42:3443"
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
                        Log.e(TAG, "Network error uploading packet ${packet.nameWithoutExtension}. Retaining locally: ${describeNetworkError(e)}", e)
                        break
                    } finally {
                        connection?.disconnect()
                    }
                }
            }
        }
    }

    suspend fun retryPendingSubmissions(): List<DivisionResultSubmissionResult> {
        return syncPendingSubmissions().map { it.second }
    }

    private suspend fun syncPendingSubmissions(): List<Pair<String, DivisionResultSubmissionResult>> =
        pendingSyncMutex.withLock {
        val store = packetStore()
        val pendingFiles = store.listPending()
        if (pendingFiles.isEmpty()) {
            uploadStatus = DivisionResultUploadStatus.Idle
            return@withLock emptyList()
        }

        val results = mutableListOf<Pair<String, DivisionResultSubmissionResult>>()
        for (pendingFile in pendingFiles) {
            val metadata = store.loadMetadata(pendingFile)
            val packetJson = store.loadPacket(pendingFile)
            val submissionId = packetJson.optString("submissionId", pendingFile.nameWithoutExtension)
            uploadStatus = DivisionResultUploadStatus.Uploading
            val result = submitter(
                migrateLegacyServerBaseUrl(metadata.serverBaseUrl),
                metadata.ringId,
                packetJson
            )
            when (result) {
                is DivisionResultSubmissionResult.Accepted -> {
                    if (
                        result.response.rawJson.optBoolean("accepted") != true ||
                        result.response.rawJson.optString("submissionId") != submissionId ||
                        result.response.serverRecordId.isBlank()
                    ) {
                        val failure = DivisionResultSubmissionResult.NetworkFailure(
                            DivisionResultSubmissionNetworkFailure(
                                "The server response did not confirm this division's submission ID and record."
                            )
                        )
                        uploadStatus = DivisionResultUploadStatus.PendingRetry(submissionId, failure.failure.message)
                        results += submissionId to failure
                        break
                    }
                    store.markSent(pendingFile, result.response.rawJson)
                    latestRingAssignment = result.response.ringAssignment
                    uploadStatus = DivisionResultUploadStatus.Accepted(
                        submissionId = submissionId,
                        serverRecordId = result.response.serverRecordId,
                        ringAssignment = result.response.ringAssignment
                    )
                    results += submissionId to result
                }
                is DivisionResultSubmissionResult.Rejected -> {
                    uploadStatus = DivisionResultUploadStatus.Rejected(
                        submissionId = submissionId,
                        errors = result.response.errors
                    )
                    results += submissionId to result
                    break
                }
                is DivisionResultSubmissionResult.NetworkFailure -> {
                    uploadStatus = DivisionResultUploadStatus.PendingRetry(
                        submissionId = submissionId,
                        message = result.failure.message
                    )
                    results += submissionId to result
                    break
                }
            }
        }
        results
    }

    private fun packetStore(): DivisionResultPacketStore {
        return packetStore ?: DivisionResultPacketStore(requireNotNull(appContext) { "Context is required" }.filesDir)
    }
}
