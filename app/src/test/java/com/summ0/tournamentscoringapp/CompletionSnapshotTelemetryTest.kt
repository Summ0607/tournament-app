package com.summ0.tournamentscoringapp

import com.summ0.tournamentscoringapp.engine.HeartbeatProgressSnapshot
import com.summ0.tournamentscoringapp.engine.PhaseTiming
import com.summ0.tournamentscoringapp.engine.RingAssignment
import org.junit.Assert.assertEquals
import org.junit.Test

class CompletionSnapshotTelemetryTest {
    @Test
    fun buildCompletionTelemetrySnapshotPopulatesCumulativeSummaryMetrics() {
        val assignment = RingAssignment(
            ringId = "ring-1",
            ringLabel = "Ring 1",
            serverBaseUrl = "http://localhost:3000",
            currentGroup = null,
            currentPhase = "awards",
            currentPhaseStartTime = "2026-10-04T15:00:00Z",
            currentPhaseEstimated = "0:20",
            ringElapsed = "9:00",
            ringEstimated = "9:00",
            ringPacePercent = 0
        )
        val progress = HeartbeatProgressSnapshot(completedCount = 7, totalCount = 10)
        val phaseHistory = listOf(
            PhaseTiming(
                name = "check-in",
                startTime = "2026-10-04T14:50:00Z",
                endTime = "2026-10-04T14:55:00Z",
                elapsed = "0:05",
                estimated = "0:10"
            ),
            PhaseTiming(
                name = "weapons",
                startTime = "2026-10-04T14:55:00Z",
                endTime = "2026-10-04T15:00:00Z",
                elapsed = "0:10",
                estimated = "0:20"
            )
        )

        val snapshot = buildCompletionTelemetrySnapshot(
            ringAssignment = assignment,
            progress = progress,
            currentPhase = "awards",
            phaseHistory = phaseHistory,
            checkInCount = 5,
            checkInTotal = 10,
            completedAt = "2026-10-04T15:01:30Z"
        )

        assertEquals(5, snapshot.getInt("checkInCount"))
        assertEquals(10, snapshot.getInt("checkInTotal"))
        assertEquals(2, snapshot.getInt("phaseCompletedCount"))
        assertEquals(2, snapshot.getInt("phaseTotalCount"))
        assertEquals(100, snapshot.getInt("phaseProgress"))
        assertEquals("0:15", snapshot.getString("ringElapsed"))
        assertEquals("0:30", snapshot.getString("ringEstimated"))
        assertEquals(50, snapshot.getInt("ringPacePercent"))

        val telemetry = snapshot.getJSONObject("telemetry")
        assertEquals(5, telemetry.getInt("checkInCount"))
        assertEquals(10, telemetry.getInt("checkInTotal"))
        assertEquals(100, telemetry.getInt("phaseProgress"))
        assertEquals("0:15", telemetry.getString("ringElapsed"))
        assertEquals("0:30", telemetry.getString("ringEstimated"))
        assertEquals(50, telemetry.getInt("ringPacePercent"))
    }
}
