package com.summ0.tournamentscoringapp.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
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
            .replace(
                ServiceRole.CORNER,
                "TSD-201",
                reliefCorner,
                assignmentRevision = "revision-1",
                replacementPinVerified = true,
                at = "t2"
            )
        assertEquals(listOf(reliefCorner, corner2), service.seats[ServiceRole.CORNER])
        val last = service.history.last()
        assertEquals(ServiceAction.REPLACED, last.action)
        assertEquals("TSD-203", last.judgeAssociationId)
        assertEquals("TSD-201", last.previousJudgeAssociationId)
        assertEquals("revision-1", last.assignmentRevision)
        assertEquals(true, last.replacementPinVerified)
        assertTrue(last.requiresCoordinatorReview)
        // Replacing someone who is not seated is a no-op.
        assertEquals(
            service,
            service.replace(
                ServiceRole.CORNER,
                "TSD-999",
                reliefCorner,
                assignmentRevision = "revision-1",
                replacementPinVerified = true
            )
        )
    }

    @Test
    fun replacingAJudgeRequiresVerifiedPinAndAssignmentRevision() {
        val service = base().seat(ServiceRole.CENTER, center, at = "t0")
        assertThrows(IllegalArgumentException::class.java) {
            service.replace(
                ServiceRole.CENTER,
                center.associationId,
                reliefCorner,
                assignmentRevision = "revision-1",
                replacementPinVerified = false
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.replace(
                ServiceRole.CENTER,
                center.associationId,
                reliefCorner,
                assignmentRevision = "",
                replacementPinVerified = true
            )
        }
    }

    @Test
    fun swappingCenterAndCornerMovesBothJudgesAndRecordsCenterCredit() {
        val service = base()
            .seat(ServiceRole.CENTER, center, at = "t0")
            .seat(ServiceRole.CORNER, corner1, at = "t1")
            .seat(ServiceRole.CORNER, corner2, at = "t2")
            .swapRoles(corner1.associationId, center.associationId, at = "t3")
        assertEquals(listOf(corner1), service.seats[ServiceRole.CENTER])
        assertEquals(listOf(corner2, center), service.seats[ServiceRole.CORNER])
        val last = service.history.last()
        assertEquals(ServiceAction.SWAPPED, last.action)
        assertEquals(ServiceRole.CENTER, last.role)
        assertEquals("corner", last.previousJudgeAssociationId)
        assertEquals(center.associationId, last.swapPartnerAssociationId)
        assertEquals(ServiceRole.CORNER, last.otherRole)
        assertEquals(ServiceRole.CENTER, last.creditedRole)
        assertEquals(ServiceRole.CENTER, service.roleOf(corner1.associationId))
        assertEquals(ServiceRole.CORNER, service.roleOf(center.associationId))
        assertEquals(service, service.swapRoles("TSD-999", corner1.associationId))
        val sameRole = base()
            .seat(ServiceRole.CORNER, corner1, at = "t0")
            .seat(ServiceRole.CORNER, corner2, at = "t1")
        assertThrows(IllegalArgumentException::class.java) {
            sameRole.swapRoles(corner1.associationId, corner2.associationId)
        }
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
            .seat(ServiceRole.SCOREKEEPER, corner2, at = "t2")
            .replace(
                ServiceRole.CORNER,
                "TSD-201",
                reliefCorner,
                assignmentRevision = "revision-1",
                replacementPinVerified = true,
                at = "t3"
            )
            .swapRoles("TSD-203", "TSD-100", at = "t4")
        val parsed = parseRingPhaseService(serializeRingPhaseService(service))
        assertEquals(service, parsed)
    }

    @Test
    fun ringPhasePacketCombinesDivisionHistoriesWithoutChangingTheirAnchors() {
        val first = base().copy(groupAnchor = "20")
            .seat(ServiceRole.CENTER, center, at = "t0")
            .replace(
                ServiceRole.CENTER,
                center.associationId,
                reliefCorner,
                assignmentRevision = "revision-1",
                replacementPinVerified = true,
                at = "t1"
            )
        val second = base().copy(groupAnchor = "21")
            .seat(ServiceRole.TIMEKEEPER, corner1, at = "t2")
        val packet = buildRingPhaseServicePacket(
            ringId = "ring-a-1",
            phase = "P1",
            services = listOf(first, second, base().copy(ringId = "ring-b-1")),
            submissionId = "123e4567-e89b-12d3-a456-426614174000"
        )
        assertEquals("tournament-judge-service", packet.getString("type"))
        assertEquals("P1", packet.getString("phase"))
        assertEquals(listOf("20", "21"), (0 until packet.getJSONArray("divisions").length())
            .map { packet.getJSONArray("divisions").getJSONObject(it).getString("groupAnchor") })
        assertEquals(2, packet.getJSONArray("divisions").getJSONObject(0).getJSONArray("history").length())
        assertEquals(corner1.associationId, packet.getJSONArray("divisions").getJSONObject(1)
            .getJSONObject("seats").getJSONArray("timekeeper").getJSONObject(0).getString("associationId")
        )
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
