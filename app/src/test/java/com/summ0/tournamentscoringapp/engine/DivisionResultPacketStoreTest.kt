package com.summ0.tournamentscoringapp.engine

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DivisionResultPacketStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun packet(submissionId: String, ringId: String, groupId: String, division: Int) =
        JSONObject()
            .put("eventName", "Event")
            .put("submissionId", submissionId)
            .put("ringId", ringId)
            .put("groupId", groupId)
            .put("groupDivisionNumber", division)

    @Test
    fun keepsPendingPacketsAvailableForRetryUntilServerAcknowledgesThem() {
        val store = DivisionResultPacketStore(folder.root)
        store.savePending(packet("submission-1", "ring-1", "group-1", 1), "https://server", "ring-1")
        store.savePending(packet("submission-2", "ring-2", "group-2", 2), "https://server", "ring-2")

        assertEquals("submission-1", store.findPendingPacket("Event", "ring-1", "group-1", 1)?.nameWithoutExtension)
        assertEquals(null, store.findPendingPacket("Other Event", "ring-1", "group-1", 1))
        assertEquals(setOf("submission-1.json", "submission-2.json"), store.listPending().map { it.name }.toSet())

        val packetFile = store.listPending().first()
        val sent = store.markSent(
            packetFile,
            JSONObject()
                .put("accepted", true)
                .put("submissionId", "submission-1")
                .put("serverRecordId", "record-submission-1")
        )
        assertEquals("submission-1", store.loadSentPacket("submission-1")?.optString("submissionId"))
        assertEquals(listOf("submission-2.json"), store.listPending().map { it.name })
        assertTrue(sent.isFile)
    }
}
