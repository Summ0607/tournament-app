package com.summ0.tournamentscoringapp.engine

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DivisionCompletionFeedbackTest {
    @Test
    fun duplicateResponseExplainsExistingResultWithoutClaimingCurrentScoresWereAccepted() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/rings/ring-a-1/complete") { exchange ->
            exchange.requestBody.use { it.readBytes() }
            val response = """
                {"reason":"duplicate-group-result","message":"Group already has an accepted result: 28","details":{"duplicateGroupId":"28","existingServerRecordId":"record-28","existingReceivedAt":"2026-11-19T12:00:00Z"}}
            """.trimIndent().toByteArray()
            exchange.sendResponseHeaders(409, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()
        try {
            val result = submitDivisionResult(
                "http://127.0.0.1:${server.address.port}", "ring-a-1", JSONObject()
            )
            val rejected = result as DivisionResultSubmissionResult.Rejected
            assertEquals("28", rejected.response.duplicateGroupId)
            assertEquals("record-28", rejected.response.existingServerRecordId)
            assertEquals("2026-11-19T12:00:00Z", rejected.response.existingReceivedAt)
            val feedback = divisionSubmissionFeedback(result)
            assertTrue(feedback.contains("HTTP 409"))
            assertTrue(feedback.contains("already has an accepted result"))
            assertTrue(feedback.contains("saved packet remains available for review"))
            assertTrue(feedback.contains("remain saved on this tablet"))
            assertTrue(feedback.contains("not marked submitted"))
        } finally {
            server.stop(0)
        }

        @Test
        fun alreadyReceivedFeedbackDoesNotClaimTheTabletPacketWasUploaded() {
            val feedback = divisionSubmissionFeedback(
                DivisionResultSubmissionResult.AlreadyReceived(
                    submissionId = "local-submission",
                    groupId = "28",
                    serverRecordId = "record-28",
                    receivedAt = "2026-11-19T12:00:00Z"
                )
            )
            assertTrue(feedback.contains("already has a result"))
            assertTrue(feedback.contains("record-28"))
            assertTrue(feedback.contains("retained and was not uploaded"))
        }
    }

    @Test
    fun authorizationRejectionShowsServerReasonAndRecoveryAction() {
        val feedback = rejectionFeedback(401, "", "Tablet authorization expired.")
        assertTrue(feedback.contains("Tablet authorization expired."))
        assertTrue(feedback.contains("Scan today's ring QR code"))
        assertTrue(feedback.contains("not marked submitted"))
    }

    @Test
    fun ringMismatchDoesNotGetMisreportedAsDuplicateResults() {
        val feedback = rejectionFeedback(409, "ring-group-mismatch", "Ring-group conflict")
        assertTrue(feedback.contains("active division for this ring"))
        assertTrue(feedback.contains("check the ring assignment"))
        assertTrue(feedback.contains("not marked submitted"))
    }

    @Test
    fun validationFeedbackKeepsTheSpecificServerError() {
        val feedback = rejectionFeedback(400, "validation_failed", "participants: must not be empty")
        assertTrue(feedback.contains("HTTP 400"))
        assertTrue(feedback.contains("participants: must not be empty"))
        assertTrue(feedback.contains("remain saved on this tablet"))
    }

    @Test
    fun networkFailureDistinguishesLocalSavingFromServerAcknowledgment() {
        val feedback = divisionSubmissionFeedback(
            DivisionResultSubmissionResult.NetworkFailure(
                DivisionResultSubmissionNetworkFailure("Connection timed out")
            )
        )
        assertTrue(feedback.contains("Connection timed out"))
        assertTrue(feedback.contains("remain saved on this tablet"))
        assertTrue(feedback.contains("server acceptance has not been confirmed"))
        assertTrue(feedback.contains("Do not clear app storage"))
    }

    private fun rejectionFeedback(status: Int, reason: String, error: String): String =
        divisionSubmissionFeedback(
            DivisionResultSubmissionResult.Rejected(
                DivisionResultSubmissionRejectedResponse(status, listOf(error), "", reason)
            )
        )
}
