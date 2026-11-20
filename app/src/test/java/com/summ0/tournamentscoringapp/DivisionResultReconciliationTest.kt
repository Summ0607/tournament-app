package com.summ0.tournamentscoringapp

import com.summ0.tournamentscoringapp.engine.DivisionRecord
import com.summ0.tournamentscoringapp.engine.DivisionRecordStatus
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DivisionResultReconciliationTest {
    private val division = DivisionRecord(
        anchor = "25",
        groupId = "server-group-25",
        groupDivisionNumber = 25,
        name = "Division 25",
        competitorCount = 4,
        status = DivisionRecordStatus.COMPLETED,
        source = "qr",
        loadedAt = "2026-01-01T00:00:00Z",
        updatedAt = "2026-01-01T00:00:00Z"
    )

    @Test
    fun `matches sent result by division number when packet group id differs`() {
        val packet = JSONObject()
            .put("groupId", "25")
            .put("groupDivisionNumber", 25)

        assertEquals(division, divisionRecordForResultPacket(listOf(division), packet))
    }

    @Test
    fun `does not match unrelated result packet`() {
        val packet = JSONObject()
            .put("groupId", "other-group")
            .put("groupDivisionNumber", 26)

        assertNull(divisionRecordForResultPacket(listOf(division), packet))
    }
}
