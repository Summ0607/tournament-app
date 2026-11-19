package com.summ0.tournamentscoringapp.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompetitionPhaseNavigationTest {
    private fun progress(
        weapons: CompetitionPhaseStatus = CompetitionPhaseStatus(true, false, false),
        hyungs: CompetitionPhaseStatus = CompetitionPhaseStatus(true, false, false),
        sparring: CompetitionPhaseStatus = CompetitionPhaseStatus(true, false, false)
    ) = CompetitionPhaseProgress(weapons, hyungs, sparring)

    @Test
    fun startedIncompletePhaseCannotBeLeft() {
        val state = progress(weapons = CompetitionPhaseStatus(true, true, false))

        assertFalse(canNavigateCompetitionPhase(
            CompetitionScreen.WEAPONS_SCORING,
            CompetitionScreen.CHECK_IN,
            state
        ))
        assertFalse(canNavigateCompetitionPhase(
            CompetitionScreen.WEAPONS_SCORING,
            CompetitionScreen.HYUNGS_SCORING,
            state
        ))
    }

    @Test
    fun progressionCannotSkipAnIncompleteAvailablePhase() {
        val state = progress(weapons = CompetitionPhaseStatus(true, false, false))

        assertTrue(canNavigateCompetitionPhase(
            CompetitionScreen.CHECK_IN,
            CompetitionScreen.WEAPONS_SCORING,
            state
        ))
        assertFalse(canNavigateCompetitionPhase(
            CompetitionScreen.CHECK_IN,
            CompetitionScreen.HYUNGS_SCORING,
            state
        ))
        assertFalse(canNavigateCompetitionPhase(
            CompetitionScreen.CHECK_IN,
            CompetitionScreen.SPARRING_BRACKET,
            state
        ))
    }

    @Test
    fun completedPhasesCanBeReviewedAndOnlyUnlockedNextPhaseCanOpen() {
        val state = progress(
            weapons = CompetitionPhaseStatus(true, true, true),
            hyungs = CompetitionPhaseStatus(true, false, false)
        )

        assertTrue(canNavigateCompetitionPhase(
            CompetitionScreen.HYUNGS_SCORING,
            CompetitionScreen.WEAPONS_SCORING,
            state
        ))
        assertTrue(canNavigateCompetitionPhase(
            CompetitionScreen.WEAPONS_SCORING,
            CompetitionScreen.HYUNGS_SCORING,
            state
        ))
        assertFalse(canNavigateCompetitionPhase(
            CompetitionScreen.WEAPONS_SCORING,
            CompetitionScreen.SPARRING_BRACKET,
            state
        ))
        assertTrue(state.completedScreens().contains(CompetitionScreen.WEAPONS_SCORING))
    }

    @Test
    fun unavailablePhasesDoNotBlockProgressionOrAwards() {
        val state = progress(
            weapons = CompetitionPhaseStatus(false, false, true),
            hyungs = CompetitionPhaseStatus(false, false, true),
            sparring = CompetitionPhaseStatus(true, true, true)
        )

        assertTrue(canNavigateCompetitionPhase(
            CompetitionScreen.CHECK_IN,
            CompetitionScreen.SPARRING_BRACKET,
            state
        ))
        assertTrue(canNavigateCompetitionPhase(
            CompetitionScreen.SPARRING_BRACKET,
            CompetitionScreen.OVERALL_AWARDS,
            state
        ))
        assertFalse(state.completedScreens().contains(CompetitionScreen.WEAPONS_SCORING))
    }

    @Test
    fun awardsStayClosedUntilACompetitionPhaseIsAvailableAndComplete() {
        val noAvailablePhases = progress(
            weapons = CompetitionPhaseStatus(false, false, true),
            hyungs = CompetitionPhaseStatus(false, false, true),
            sparring = CompetitionPhaseStatus(false, false, true)
        )
        val incompletePhase = progress(
            weapons = CompetitionPhaseStatus(true, false, false),
            hyungs = CompetitionPhaseStatus(false, false, true),
            sparring = CompetitionPhaseStatus(false, false, true)
        )
        val completedPhase = progress(
            weapons = CompetitionPhaseStatus(true, true, true),
            hyungs = CompetitionPhaseStatus(false, false, true),
            sparring = CompetitionPhaseStatus(false, false, true)
        )

        assertFalse(canOpenOverallAwards(noAvailablePhases))
        assertFalse(canNavigateCompetitionPhase(
            CompetitionScreen.CHECK_IN,
            CompetitionScreen.OVERALL_AWARDS,
            noAvailablePhases
        ))
        assertFalse(canOpenOverallAwards(incompletePhase))
        assertTrue(canOpenOverallAwards(completedPhase))
    }
}
