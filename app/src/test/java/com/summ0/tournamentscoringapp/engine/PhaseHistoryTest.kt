package com.summ0.tournamentscoringapp.engine

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class PhaseHistoryTest {
    @Test
    fun appendCompletedPhaseTimingPreservesExistingEntries() {
        val existing = listOf(
            PhaseTiming(
                name = "check-in",
                startTime = "2026-10-04T12:00:00Z",
                endTime = "2026-10-04T12:05:00Z",
                elapsed = "5:00",
                estimated = "10:00"
            )
        )

        val updated = appendCompletedPhaseTiming(
            history = existing,
            name = "weapons",
            startTime = "2026-10-04T12:05:00Z",
            completedAt = "2026-10-04T12:12:30Z",
            estimated = "07:30"
        )

        assertEquals(2, updated.size)
        assertEquals("check-in", updated[0].name)
        assertEquals("weapons", updated[1].name)
        assertEquals("7:30", updated[1].elapsed)
        assertEquals("07:30", updated[1].estimated)
    }

    @Test
    fun formatElapsedDurationUsesHoursWhenNeeded() {
        assertEquals(
            "1:02:03",
            formatElapsedDuration("2026-10-04T12:00:00Z", "2026-10-04T13:02:03Z")
        )
    }

    @Test
    fun buildPhaseTimingEntryDerivesEstimateFromActiveCount() {
        val hyungsPhase = buildPhaseTimingEntry(
            name = "hyungs",
            startTime = "2026-10-04T12:00:00Z",
            endTime = "2026-10-04T12:03:00Z",
            estimateContext = PhaseTimingEstimateContext(participatingCompetitors = 4)
        )

        val sparringPhase = buildPhaseTimingEntry(
            name = "sparring",
            startTime = "2026-10-04T12:00:00Z",
            endTime = "2026-10-04T12:04:00Z",
            estimateContext = PhaseTimingEstimateContext(actualBoutCount = 5)
        )

        val sparringPhaseWithFiveCompetitors = buildPhaseTimingEntry(
            name = "sparring",
            startTime = "2026-10-04T12:00:00Z",
            endTime = "2026-10-04T12:04:00Z",
            estimateContext = PhaseTimingEstimateContext(actualBoutCount = 4)
        )

        val setupPhase = buildPhaseTimingEntry(
            name = "setup",
            startTime = "2026-10-04T12:00:00Z",
            endTime = "2026-10-04T12:01:00Z",
            estimateContext = PhaseTimingEstimateContext(totalCompetitors = 10)
        )

        val awardsPhase = buildPhaseTimingEntry(
            name = "awards",
            startTime = "2026-10-04T12:00:00Z",
            endTime = "2026-10-04T12:01:00Z",
            estimateContext = PhaseTimingEstimateContext(awardBlockCount = 2)
        )

        assertEquals("6:00", hyungsPhase.estimated)
        assertEquals("7:30", sparringPhase.estimated)
        assertEquals("6:00", sparringPhaseWithFiveCompetitors.estimated)
        assertEquals("4:30", setupPhase.estimated)
        assertEquals("6:00", awardsPhase.estimated)
    }

    @Test
    fun parsePhaseTimingListNormalizesSetupEstimate() {
        val root = JSONObject(
            """
            {
              "currentGroup": {
                "competitors": [
                  {}, {}, {}, {}, {}, {}, {}, {}, {}, {}
                ]
              },
              "phaseHistory": [
                {
                  "name": "setup",
                  "startTime": "2026-10-04T12:00:00Z",
                  "elapsed": "0:00",
                  "estimated": "1:10:00"
                }
              ]
            }
            """.trimIndent()
        )

        val phases = parsePhaseTimingList(root)

        assertEquals(1, phases.size)
        assertEquals("setup", phases[0].name)
        assertEquals("4:30", phases[0].estimated)
    }
}
