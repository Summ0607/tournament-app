package com.summ0.tournamentscoringapp.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RingPhaseServiceTest {

    private val center = ServiceJudge("TSD-100", "Grand Master Vaughn", "D8")
    private val corner1 = ServiceJudge("TSD-201", "Ms. Lee", "D3")
    private val corner2 = ServiceJudge("TSD-202", "Mr. Ortiz", "D2")
    private val reliefCorner = ServiceJudge("TSD-203", "Ms. Park", "D4")

    private fun base() = RingPhaseService(ringId = "ring-a-1", phase = "P1", groupAnchor = "20")

    @Test
    fun seatingBuildsTheLineupAndRecordsHistory() {
        val service = base()
            .seat(ServiceRole.CENTER, center, at = "t0")
            .seat(ServiceRole.CORNER, corner1, at = "t1")
            .seat(ServiceRole.CORNER, corner2, at = "t2")
        assertEquals(listOf(center), service.seats[ServiceRole.CENTER])
        assertEquals(listOf(corner1, corner2), service.seats[ServiceRole.CORNER])
        assertEquals(3, service.history.size)
        assertEquals(ServiceAction.SEATED, service.history.first().action)
        // Re-seating the same judge in the same role is a no-op.
        assertEquals(service, service.seat(ServiceRole.CENTER, center))
    }

    @Test
    fun replacingAJudgeKeepsHistoryAndFinalSeats() {
        val service = base()
            .seat(ServiceRole.CORNER, corner1, at = "t0")
            .seat(ServiceRole.CORNER, corner2, at = "t1")
            .replace(ServiceRole.CORNER, "TSD-201", reliefCorner, at = "t2")
        assertEquals(listOf(reliefCorner, corner2), service.seats[ServiceRole.CORNER])
        val last = service.history.last()
        assertEquals(ServiceAction.REPLACED, last.action)
        assertEquals("TSD-203", last.judgeAssociationId)
        assertEquals("TSD-201", last.previousJudgeAssociationId)
        // Replacing someone who is not seated is a no-op.
        assertEquals(service, service.replace(ServiceRole.CORNER, "TSD-999", reliefCorner))
    }

    @Test
    fun swappingRolesMovesTheJudgeAndRecordsBothRoles() {
        val service = base()
            .seat(ServiceRole.CENTER, center, at = "t0")
            .seat(ServiceRole.CORNER, corner1, at = "t1")
            .swapRole("TSD-201", ServiceRole.CENTER, at = "t2")
        assertEquals(listOf(center, corner1), service.seats[ServiceRole.CENTER])
        assertNull(service.seats[ServiceRole.CORNER])
        val last = service.history.last()
        assertEquals(ServiceAction.SWAPPED, last.action)
        assertEquals(ServiceRole.CENTER, last.role)
        assertEquals("corner", last.previousJudgeAssociationId)
        assertEquals(ServiceRole.CENTER, service.roleOf("TSD-201"))
        // Swapping to the same role, or an unseated judge, is a no-op.
        assertEquals(service, service.swapRole("TSD-201", ServiceRole.CENTER))
        assertEquals(service, service.swapRole("TSD-999", ServiceRole.CORNER))
    }

    @Test
    fun unseatingRemovesAJudgeFromTheLineup() {
        val service = base()
            .seat(ServiceRole.CORNER, corner1, at = "t0")
            .unseat("TSD-201", at = "t1")
        assertEquals(emptyList<ServiceJudge>(), service.seats[ServiceRole.CORNER].orEmpty())
        assertNull(service.roleOf("TSD-201"))
        assertEquals("TSD-201", service.history.last().previousJudgeAssociationId)
    }

    @Test
    fun serviceRoundTripsLosslessly() {
        val service = base()
            .seat(ServiceRole.CENTER, center, at = "t0")
            .seat(ServiceRole.CORNER, corner1, at = "t1")
            .seat(ServiceRole.SKTK, corner2, at = "t2")
            .replace(ServiceRole.CORNER, "TSD-201", reliefCorner, at = "t3")
            .swapRole("TSD-203", ServiceRole.SKTK, at = "t4")
        val parsed = parseRingPhaseService(serializeRingPhaseService(service))
        assertEquals(service, parsed)
    }

    @Test
    fun emptyServiceRoundTripsWithDefaults() {
        val parsed = parseRingPhaseService(serializeRingPhaseService(base()))
        assertEquals(base(), parsed)
    }

    @Test
    fun unknownRolesAndActionsAreDroppedWhenParsing() {
        val json = serializeRingPhaseService(base().seat(ServiceRole.CENTER, center, at = "t0"))
        json.getJSONObject("seats").put("mystery", org.json.JSONArray())
        json.getJSONArray("history").getJSONObject(0).put("action", "TELEPORTED")
        val parsed = parseRingPhaseService(json)
        assertEquals(setOf(ServiceRole.CENTER), parsed.seats.keys)
        assertTrue(parsed.history.isEmpty())
    }
}
