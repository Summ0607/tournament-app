package com.summ0.tournamentscoringapp

import com.summ0.tournamentscoringapp.engine.DivisionRecordStatus
import com.summ0.tournamentscoringapp.engine.RemoteGroup
import com.summ0.tournamentscoringapp.engine.RingAssignment
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DivisionIdleStateTest {
    private fun assignment(
        ringId: String = "ring-a-1",
        currentGroup: RemoteGroup? = null,
        queuedDivisions: List<Int> = emptyList()
    ) = RingAssignment(
        ringId = ringId,
        ringLabel = "A1",
        serverBaseUrl = "https://tournament.example",
        currentGroup = currentGroup,
        queuedGroupDivisionNumbers = queuedDivisions
    )

    @Test
    fun `resolved division is released only when its connected ring is idle and no results remain`() {
        assertTrue(
            shouldReleaseResolvedDivisionForIdleRing(
                divisionStatus = DivisionRecordStatus.SUBMITTED,
                pendingResultCount = 0,
                currentRingId = "ring-a-1",
                assignment = assignment()
            )
        )
        assertTrue(
            shouldReleaseResolvedDivisionForIdleRing(
                DivisionRecordStatus.ALREADY_RECEIVED, 0, "ring-a-1", assignment()
            )
        )
    }

    @Test
    fun `incomplete or pending divisions are not released`() {
        assertFalse(
            shouldReleaseResolvedDivisionForIdleRing(
                DivisionRecordStatus.COMPLETED, 0, "ring-a-1", assignment()
            )
        )
        assertFalse(
            shouldReleaseResolvedDivisionForIdleRing(
                DivisionRecordStatus.SUBMITTED, 1, "ring-a-1", assignment()
            )
        )
    }

    @Test
    fun `non-idle or mismatched ring assignments are not released`() {
        val currentGroup = RemoteGroup(
            groupId = "25",
            name = "Division 25",
            ageRange = 1..99,
            rankRange = 1..9,
            rankRangeLabel = "1-9",
            matNumber = 1,
            competitors = emptyList()
        )
        assertFalse(
            shouldReleaseResolvedDivisionForIdleRing(
                DivisionRecordStatus.SUBMITTED, 0, "ring-a-1", assignment(ringId = "ring-a-2")
            )
        )
        assertFalse(
            shouldReleaseResolvedDivisionForIdleRing(
                DivisionRecordStatus.SUBMITTED, 0, "ring-a-1", assignment(queuedDivisions = listOf(25))
            )
        )
        assertFalse(
            shouldReleaseResolvedDivisionForIdleRing(
                DivisionRecordStatus.SUBMITTED, 0, "ring-a-1", assignment(currentGroup = currentGroup)
            )
        )
    }
}
