package com.summ0.tournamentscoringapp.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DivisionSessionTest {

    private fun sampleCompetitor(id: String, checkedIn: Boolean = true): Competitor {
        return Competitor(
            id = id,
            name = "Competitor $id",
            studio = "Studio A",
            rank = "7th Gup",
            rankLevel = 3,
            age = 10,
            heightInInches = 54,
            checkInStatus = if (checkedIn) CheckInStatus.CHECKED_IN else CheckInStatus.REGISTERED,
            competitionEntries = if (checkedIn) {
                mapOf(
                    CompetitionType.WEAPONS to CompetitionEntry(CompetitionType.WEAPONS),
                    CompetitionType.SPARRING to CompetitionEntry(
                        CompetitionType.SPARRING,
                        CompetitionRegistrationStatus.COMPLETED
                    )
                )
            } else {
                emptyMap()
            }
        )
    }

    private fun fullSession(): DivisionSession {
        return DivisionSession(
            competitors = listOf(sampleCompetitor("c1"), sampleCompetitor("c2", checkedIn = false)),
            lockedPhases = setOf(CompetitionType.WEAPONS, CompetitionType.SPARRING),
            phaseEntrants = mapOf(CompetitionType.WEAPONS to setOf("c1", "c2")),
            useOnlyThreeJudges = true,
            currentScreenName = "SPARRING_BRACKET",
            controlBoardPhase = "sparring",
            controlBoardPhaseStartedAt = "2026-11-10T14:00:00Z",
            phaseHistory = listOf(
                PhaseTiming("setup", "2026-11-10T13:00:00Z", "2026-11-10T13:10:00Z", "10:00", "08:00"),
                PhaseTiming("weapons", "2026-11-10T13:10:00Z", null, "", "12:00")
            ),
            weaponsScores = mapOf("c1" to listOf("8.5", "8.7", "8.9")),
            hyungsScores = mapOf("c2" to listOf("9.0", "9.1", "9.2")),
            weaponsTieBreakScores = mapOf("c1" to listOf("9.9")),
            hyungsTieBreakScores = emptyMap(),
            weaponsJudgeChoices = mapOf("group-1" to listOf("c1", "c2")),
            hyungsJudgeChoices = emptyMap(),
            weaponsFinalize = PlacementFinalizeState(
                labels = mapOf("c1" to "1st Place"),
                tieBreakDetails = mapOf("c1" to "High/low added back"),
                message = "Finalized"
            ),
            hyungsFinalize = PlacementFinalizeState(),
            pendingTieBreak = PendingTieBreak(
                discipline = HyungDiscipline.WEAPONS,
                competitorIds = listOf("c1", "c2"),
                judgeCount = 3,
                groupKey = "group-1"
            ),
            sparringWinnerIds = mapOf(0 to mapOf(0 to "c1", 1 to "c2"), 1 to mapOf(0 to "c1")),
            sparringBoutProgress = mapOf(
                "0-0" to SparringBoutProgress(
                    bluePoints = 3,
                    redPoints = 1,
                    blueWarnings = 1,
                    redWarnings = 0,
                    elapsedSeconds = 75,
                    winnerId = "c1",
                    outcomeLabel = "First to three"
                )
            )
        )
    }

    @Test
    fun fullSessionRoundTripsLosslessly() {
        val session = fullSession()
        val parsed = parseDivisionSession(serializeDivisionSession(session))
        assertEquals(session, parsed)
    }

    @Test
    fun emptySessionRoundTripsWithDefaults() {
        val parsed = parseDivisionSession(serializeDivisionSession(DivisionSession()))
        assertEquals(DivisionSession(), parsed)
    }

    @Test
    fun freshlyLoadedSessionsArePristineUntilOperatorActivity() {
        val fresh = DivisionSession(
            competitors = listOf(sampleCompetitor("c1", checkedIn = false)),
            currentScreenName = "CHECK_IN"
        )
        assertTrue(fresh.isPristine())
        assertFalse(fresh.copy(competitors = listOf(sampleCompetitor("c1"))).isPristine())
        assertFalse(fresh.copy(weaponsScores = mapOf("c1" to listOf("9.0"))).isPristine())
        assertFalse(fresh.copy(controlBoardPhase = "weapons").isPristine())
        assertFalse(fresh.copy(useOnlyThreeJudges = true).isPristine())
        assertFalse(fullSession().isPristine())
    }

    @Test
    fun unknownEnumValuesAreDroppedInsteadOfCrashing() {
        val json = serializeDivisionSession(fullSession())
        json.getJSONArray("lockedPhases").put("NOT_A_PHASE")
        json.getJSONObject("phaseEntrants").put("NOT_A_PHASE", org.json.JSONArray(listOf("c9")))
        json.getJSONObject("pendingTieBreak").put("discipline", "NOT_A_DISCIPLINE")
        val parsed = parseDivisionSession(json)
        assertEquals(setOf(CompetitionType.WEAPONS, CompetitionType.SPARRING), parsed.lockedPhases)
        assertEquals(setOf(CompetitionType.WEAPONS), parsed.phaseEntrants.keys)
        assertEquals(null, parsed.pendingTieBreak)
    }
}
