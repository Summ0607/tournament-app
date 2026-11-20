package com.summ0.tournamentscoringapp

import com.summ0.tournamentscoringapp.engine.DivisionResultPacketStore
import com.summ0.tournamentscoringapp.engine.DivisionResultSubmissionAcceptedResponse
import com.summ0.tournamentscoringapp.engine.DivisionResultSubmissionNetworkFailure
import com.summ0.tournamentscoringapp.engine.DivisionResultSubmissionResult
import com.summ0.tournamentscoringapp.engine.DivisionResultSubmissionRejectedResponse
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MainViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun pendingSubmissionSyncUsesOldestFirstAndStopsAtFirstFailure() = runBlocking {
        val store = DivisionResultPacketStore(folder.root)
        store.savePending(
            JSONObject().put("submissionId", "older").put("ringId", "ring-1"),
            "https://server",
            "ring-1"
        ).setLastModified(1L)
        store.savePending(
            JSONObject().put("submissionId", "newer").put("ringId", "ring-1"),
            "https://server",
            "ring-1"
        ).setLastModified(2L)
        val attempted = mutableListOf<String>()
        val viewModel = MainViewModel(
            packetStore = store,
            submitter = { _, _, packet ->
                attempted += packet.getString("submissionId")
                DivisionResultSubmissionResult.NetworkFailure(
                    DivisionResultSubmissionNetworkFailure("offline")
                )
            }
        )

        val results = viewModel.retryPendingSubmissions()

        assertEquals(listOf("older"), attempted)
        assertEquals(1, results.size)
        assertEquals(listOf("older.json", "newer.json"), store.listPending().map { it.name })
    }

    @Test
    fun duplicateGroupResultIsResolvedAndLaterPendingResultsContinue() = runBlocking {
        val store = DivisionResultPacketStore(folder.root)
        store.savePending(
            JSONObject().put("submissionId", "older").put("ringId", "ring-1").put("groupId", "group-25"),
            "https://server",
            "ring-1"
        ).setLastModified(1L)
        store.savePending(
            JSONObject().put("submissionId", "newer").put("ringId", "ring-1").put("groupId", "group-26"),
            "https://server",
            "ring-1"
        ).setLastModified(2L)
        val attempted = mutableListOf<String>()
        val viewModel = MainViewModel(
            packetStore = store,
            submitter = { _, _, packet ->
                val submissionId = packet.getString("submissionId")
                attempted += submissionId
                if (submissionId == "older") {
                    DivisionResultSubmissionResult.Rejected(
                        DivisionResultSubmissionRejectedResponse(
                            statusCode = 409,
                            errors = listOf("Group already has an accepted result: group-25"),
                            rawBody = """{"reason":"duplicate-group-result"}""",
                            reason = "duplicate-group-result",
                            duplicateGroupId = "group-25",
                            existingServerRecordId = "server-record-25",
                            existingReceivedAt = "2026-11-19T12:00:00Z"
                        )
                    )
                } else {
                    val receipt = JSONObject()
                        .put("accepted", true)
                        .put("submissionId", submissionId)
                        .put("serverRecordId", "server-record-26")
                    DivisionResultSubmissionResult.Accepted(
                        DivisionResultSubmissionAcceptedResponse(
                            ok = true,
                            status = "accepted",
                            serverRecordId = "server-record-26",
                            submissionId = submissionId,
                            receivedAt = "2026-11-19T12:01:00Z",
                            ringAssignment = null,
                            rawJson = receipt
                        )
                    )
                }
            }
        )

        val results = viewModel.retryPendingSubmissions()

        assertEquals(listOf("older", "newer"), attempted)
        assertEquals(2, results.size)
        assertTrue(results[0] is DivisionResultSubmissionResult.AlreadyReceived)
        assertTrue(results[1] is DivisionResultSubmissionResult.Accepted)
        assertTrue(store.listPending().isEmpty())
        assertEquals("older", store.loadSentPacket("older")?.optString("submissionId"))
        assertEquals("already_received", store.loadSentResponse("older")?.optString("resolution"))
        assertEquals("server-record-25", store.loadSentResponse("older")?.optString("serverRecordId"))
    }

    @Test
    fun duplicateGroupResponseForAnotherGroupRemainsBlocked() = runBlocking {
        val store = DivisionResultPacketStore(folder.root)
        store.savePending(
            JSONObject().put("submissionId", "older").put("ringId", "ring-1").put("groupId", "group-25"),
            "https://server",
            "ring-1"
        ).setLastModified(1L)
        store.savePending(
            JSONObject().put("submissionId", "newer").put("ringId", "ring-1").put("groupId", "group-26"),
            "https://server",
            "ring-1"
        ).setLastModified(2L)
        var attempts = 0
        val viewModel = MainViewModel(
            packetStore = store,
            submitter = { _, _, _ ->
                attempts++
                DivisionResultSubmissionResult.Rejected(
                    DivisionResultSubmissionRejectedResponse(
                        statusCode = 409,
                        errors = listOf("Group already has an accepted result: group-other"),
                        rawBody = """{"reason":"duplicate-group-result"}""",
                        reason = "duplicate-group-result",
                        duplicateGroupId = "group-other",
                        existingServerRecordId = "server-record-other"
                    )
                )
            }
        )

        val results = viewModel.retryPendingSubmissions()

        assertEquals(1, attempts)
        assertTrue(results.single() is DivisionResultSubmissionResult.Rejected)
        assertEquals(listOf("older.json", "newer.json"), store.listPending().map { it.name })
    }
}
