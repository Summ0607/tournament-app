package com.summ0.tournamentscoringapp

import com.summ0.tournamentscoringapp.engine.PhaseTiming
import org.junit.Assert.assertEquals
import org.junit.Test

class PhaseHistoryImportTest {
    @Test
    fun normalizeImportedPhaseHistoryDropsOpenCurrentPhaseEntry() {
        val importedHistory = listOf(
            PhaseTiming(
                name = "setup",
                startTime = "2026-10-05T16:01:23.632Z",
                endTime = null,
                elapsed = "0:00",
                estimated = "4:30"
            ),
            PhaseTiming(
                name = "setup",
                startTime = "2026-10-05T16:01:23.632Z",
                endTime = "2026-10-05T16:01:37.712154Z",
                elapsed = "0:14",
                estimated = "4:30"
            )
        )

        val normalized = normalizeImportedPhaseHistory(
            phaseHistory = importedHistory,
            currentPhaseName = "setup",
            currentPhaseStartTime = "2026-10-05T16:01:23.632Z"
        )

        assertEquals("setup", normalized.phaseName)
        assertEquals("2026-10-05T16:01:23.632Z", normalized.phaseStartedAt)
        assertEquals(1, normalized.history.size)
        assertEquals("setup", normalized.history[0].name)
        assertEquals("2026-10-05T16:01:37.712154Z", normalized.history[0].endTime)
    }
}
