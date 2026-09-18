package com.summ0.tournamentscoringapp

import com.summ0.tournamentscoringapp.engine.CheckInStatus
import com.summ0.tournamentscoringapp.engine.CompetitionRegistrationStatus
import com.summ0.tournamentscoringapp.engine.CompetitionType
import com.summ0.tournamentscoringapp.engine.Competitor
import com.summ0.tournamentscoringapp.engine.CompetitorSide
import com.summ0.tournamentscoringapp.engine.Division
import com.summ0.tournamentscoringapp.engine.HyungDiscipline
import com.summ0.tournamentscoringapp.engine.HyungScoreRow
import com.summ0.tournamentscoringapp.engine.SparringBout
import com.summ0.tournamentscoringapp.engine.SparringOutcome
import com.summ0.tournamentscoringapp.engine.SparringWarning
import com.summ0.tournamentscoringapp.engine.SparringWarningType
import com.summ0.tournamentscoringapp.engine.TournamentEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun getAllowedHyungForms_returnsEmptyHandCatalogForRanks() {
        assertEquals(
            listOf("Pyung Ahn Sah Dan", "Oh Dan"),
            TournamentEngine.getAllowedHyungForms("3rd Gup", HyungDiscipline.HYUNGS)
        )
        assertEquals(
            listOf("Naihanchi Sam Dan", "Jin Do", "Ro Hai", "Kong Sang Koon"),
            TournamentEngine.getAllowedHyungForms("Sam Dan", HyungDiscipline.HYUNGS)
        )
    }

    @Test
    fun getAllowedHyungForms_returnsWeaponCatalogForRanks() {
        assertEquals(
            listOf("Bong Hyung II Bu"),
            TournamentEngine.getAllowedHyungForms("4th Gup", HyungDiscipline.WEAPONS)
        )
        assertEquals(
            listOf("Bong Hyung E Bu", "Bong Hyung Sam Bu"),
            TournamentEngine.getAllowedHyungForms("Cho Dan", HyungDiscipline.WEAPONS)
        )
        assertEquals(
            listOf("Dan Gum", "Ki Cho Jang Gum Hyung", "Jung Koop Jang Gum Hyung"),
            TournamentEngine.getAllowedHyungForms("Sam Dan", HyungDiscipline.WEAPONS)
        )
    }

    @Test
    fun calculateHyungTotal_withFiveJudges_dropsHighAndLow() {
        val total = TournamentEngine.calculateHyungTotal(
            scores = listOf(8.0, 8.5, 9.0, 9.5, 10.0)
        )

        assertEquals(27.0, total, 0.0001)
    }

    @Test
    fun calculateHyungTotal_withFewerThanFiveJudges_sumsAll() {
        val total = TournamentEngine.calculateHyungTotal(
            scores = listOf(8.0, 9.0, 9.5, 10.0)
        )

        assertEquals(36.5, total, 0.0001)
    }

    @Test
    fun buildStandardAwardsSheet_fillsFixedTemplate() {
        val competitors = listOf(
            competitor("c1", "Alex"),
            competitor("c2", "Blake"),
            competitor("c3", "Casey")
        )
        val ranked = listOf(
            resultRow(competitors[0], 9.9),
            resultRow(competitors[1], 9.8),
            resultRow(competitors[2], 9.7)
        )
        val results = TournamentEngine.rankHyungResults(ranked)

        val awards = TournamentEngine.buildStandardAwardsSheet(results)

        assertEquals("1st Place", awards[0].placeLabel)
        assertEquals("Alex", awards[0].competitor?.name)
        assertEquals("2nd Place", awards[1].placeLabel)
        assertEquals("Blake", awards[1].competitor?.name)
        assertEquals("Co-3rd Place", awards[2].placeLabel)
        assertEquals("Casey", awards[2].competitor?.name)
        assertEquals("Co-3rd Place", awards[3].placeLabel)
        assertNull(awards[3].competitor)
    }

    @Test
    fun orderedSummaryPlacements_consumesDuplicateLabelsInOrder() {
        val competitors = listOf(
            competitor("c1", "Alex"),
            competitor("c2", "Blake"),
            competitor("c3", "Casey"),
            competitor("c4", "Drew")
        )
        val placements = linkedMapOf(
            "c1" to "1st Place",
            "c2" to "2nd Place",
            "c3" to "Co-3rd Place",
            "c4" to "Co-3rd Place"
        )

        val awards = orderedSummaryPlacements(
            competitors = competitors,
            placementsByCompetitorId = placements,
            placeLabels = listOf("1st Place", "2nd Place", "Co-3rd Place", "Co-3rd Place")
        )

        assertEquals("Alex", awards[0].second?.name)
        assertEquals("Blake", awards[1].second?.name)
        assertEquals("Casey", awards[2].second?.name)
        assertEquals("Drew", awards[3].second?.name)
    }

    @Test
    fun formatSummaryPlacementLine_usesNAWhenCompetitorMissing() {
        val line = formatSummaryPlacementLine("Co-3rd Place", null)

        assertEquals("Co-3rd Place: N/A", line)
    }

    @Test
    fun rankHyungResults_ordersTiedTotalsByName() {
        val competitorA = competitor("c7", "Gabriel")
        val competitorB = competitor("c9", "Isaac")
        val rows = listOf(
            HyungScoreRow(competitorB, HyungDiscipline.WEAPONS, listOf(7.0, 8.0, 8.0, 8.0, 9.0)),
            HyungScoreRow(competitorA, HyungDiscipline.WEAPONS, listOf(8.0, 8.0, 8.0, 8.0, 8.0))
        )

        val results = TournamentEngine.rankHyungResults(rows)

        assertEquals("Gabriel", results[0].competitor.name)
        assertEquals("Isaac", results[1].competitor.name)
        assertEquals(24.0, results[0].total, 0.01)
        assertEquals(24.0, results[1].total, 0.01)
    }

    @Test
    fun createFirstRoundSparringBouts_padsBracketWithByes() {
        val competitors = listOf(
            competitor("c1", "Alex"),
            competitor("c2", "Blake"),
            competitor("c3", "Casey"),
            competitor("c4", "Drew"),
            competitor("c5", "Evan")
        )

        val roundOne = TournamentEngine.createFirstRoundSparringBouts(competitors)

        assertEquals(4, roundOne.size)
        assertEquals(3, roundOne.count { it.isBye })
        assertEquals(1, roundOne.count { it.competitorA != null && it.competitorB != null })
    }

    @Test
    fun createNextRoundSparringBouts_pairsWinnersInOrder() {
        val winners = listOf(
            competitor("c1", "Alex"),
            competitor("c3", "Casey"),
            competitor("c5", "Evan")
        )

        val nextRound = TournamentEngine.createNextRoundSparringBouts(winners)

        assertEquals(2, nextRound.size)
        assertEquals("Alex", nextRound[0].competitorA?.name)
        assertEquals("Casey", nextRound[0].competitorB?.name)
        assertEquals("Evan", nextRound[1].competitorA?.name)
        assertNull(nextRound[1].competitorB)
    }

    @Test
    fun simulateSparringTournament_generatesCompleteBracketResults() {
        val competitors = listOf(
            competitor("c1", "Alex", 58),
            competitor("c2", "Blake", 60),
            competitor("c3", "Casey", 61),
            competitor("c4", "Drew", 62),
            competitor("c5", "Evan", 63),
            competitor("c6", "Frank", 64),
            competitor("c7", "Gabe", 65),
            competitor("c8", "Hank", 66),
            competitor("c9", "Isaac", 67),
            competitor("c10", "Jude", 68)
        )

        val tournament = TournamentEngine.simulateSparringTournament(competitors, seed = 20260817)

        assertEquals(4, tournament.rounds.size)
        assertEquals(16, tournament.bracketSize)
        assertEquals(1, tournament.rounds.last().winners.size)
        assertEquals(tournament.rounds.last().winners.first(), tournament.champion)
        assertTrue(tournament.rounds.all { round ->
            round.boutResults.all { result ->
                result.outcome == SparringOutcome.BYE || result.winner != null
            }
        })
    }

    @Test
    fun buildSparringAwardsSheet_returnsFirstSecondAndCoThird() {
        val scenario = TournamentEngine.createSampleTournamentScenario()
        val competitors = TournamentEngine.filterEligibleCompetitors(
            scenario.division,
            CompetitionType.SPARRING
        )
        val tournament = TournamentEngine.simulateSparringTournament(competitors, seed = 20260817)
        val awards = TournamentEngine.buildSparringAwardsSheet(tournament)

        assertEquals("1st Place", awards[0].placeLabel)
        assertEquals("Noah Lopez", awards[0].competitor?.name)
        assertEquals("2nd Place", awards[1].placeLabel)
        assertEquals("Caleb Brooks", awards[1].competitor?.name)
        assertEquals("Co-3rd Place", awards[2].placeLabel)
        assertTrue(awards[2].competitor != null)
        assertEquals("Co-3rd Place", awards[3].placeLabel)
        assertTrue(awards[3].competitor != null)
        assertTrue(awards[2].competitor?.name != awards[3].competitor?.name)
    }

    @Test
    fun buildSparringAwardPlacements_keepsAwardsBlankUntilBoutsHaveWinners() {
        val scenario = TournamentEngine.createSampleTournamentScenario()
        val competitors = TournamentEngine.filterEligibleCompetitors(
            scenario.division,
            CompetitionType.SPARRING
        )
        val seed = competitors.sortedBy { it.id }.fold(0) { acc, competitor ->
            31 * acc + competitor.id.hashCode()
        }

        val awards = buildSparringAwardPlacements(
            round0Bouts = TournamentEngine.createFirstRoundSparringBouts(competitors, seed),
            sparringWinners = emptyMap()
        )

        assertEquals(listOf("1st Place", "2nd Place", "Co-3rd Place", "Co-3rd Place"), awards.map { it.first })
        assertTrue(awards.all { it.second == null })
    }

    @Test
    fun buildSparringAwardPlacements_usesRecordedWinnersForCompletedBracket() {
        val alex = competitor("c1", "Alex")
        val blake = competitor("c2", "Blake")
        val casey = competitor("c3", "Casey")
        val drew = competitor("c4", "Drew")

        val round0Bouts = listOf(
            SparringBout(boutNumber = 1, competitorA = alex, competitorB = blake),
            SparringBout(boutNumber = 2, competitorA = casey, competitorB = drew)
        )

        val awards = buildSparringAwardPlacements(
            round0Bouts = round0Bouts,
            sparringWinners = mapOf(
                0 to mapOf(0 to alex, 1 to drew),
                1 to mapOf(0 to alex)
            )
        )

        assertEquals("Alex", awards[0].second?.name)
        assertEquals("Drew", awards[1].second?.name)
        assertEquals("Blake", awards[2].second?.name)
        assertEquals("Casey", awards[3].second?.name)
    }

    @Test
    fun buildSparringAwardPlacements_doesNotAwardFirstPlaceFromByePathAlone() {
        val alex = competitor("c1", "Alex")
        val blake = competitor("c2", "Blake")
        val casey = competitor("c3", "Casey")

        val awards = buildSparringAwardPlacements(
            round0Bouts = listOf(
                SparringBout(boutNumber = 1, competitorA = alex, competitorB = null),
                SparringBout(boutNumber = 2, competitorA = blake, competitorB = casey)
            ),
            sparringWinners = emptyMap()
        )

        assertTrue(awards.all { it.second == null })
    }

    @Test
    fun formatSparringBoutLabel_usesBlueAndRedSideNames() {
        val bout = SparringBout(
            boutNumber = 1,
            competitorA = competitor("c1", "Lucas Turner"),
            competitorB = competitor("c2", "Owen Price")
        )

        val label = TournamentEngine.formatSparringBoutLabel(bout)

        assertEquals("Lucas Turner (Blue) vs Owen Price (Red)", label)
    }

    @Test
    fun buildDetailedSparringReport_includesBoutBreakdownAndFinalStandings() {
        val scenario = TournamentEngine.createSampleTournamentScenario()
        val competitors = TournamentEngine.filterEligibleCompetitors(
            scenario.division,
            CompetitionType.SPARRING
        )
        val tournament = TournamentEngine.simulateSparringTournament(competitors, seed = 20260817)
        val report = TournamentEngine.buildDetailedSparringReport(tournament)

        assertTrue(report.contains("Round 1"))
        assertTrue(report.contains("Round 2"))
        assertTrue(report.contains("Round 3"))
        assertTrue(report.contains("Round 4"))
        assertTrue(report.contains("Final Results"))
        assertTrue(report.contains("1st Place; Noah Lopez"))
        assertTrue(report.contains("2nd Place; Caleb Brooks"))
    }

    @Test
    fun simulateSparringTournament_printsResultSummary() {
        val scenario = TournamentEngine.createSampleTournamentScenario()
        val competitors = TournamentEngine.filterEligibleCompetitors(
            scenario.division,
            CompetitionType.SPARRING
        )
        val tournament = TournamentEngine.simulateSparringTournament(competitors, seed = 20260817)

        println("Sparring entrants: ${competitors.map { it.name }}")
        tournament.rounds.forEach { round ->
            println("Round ${round.roundNumber}: ${round.boutResults.map { result ->
                val label = TournamentEngine.formatSparringBoutLabel(result.bout)
                val winner = result.winner?.name ?: "TIE/NO WINNER"
                "$label -> $winner"
            }.joinToString(" | ")}")
        }
        println("Champion: ${tournament.champion?.name}")

        assertTrue(tournament.champion != null)
    }

    @Test
    fun addCompetitorToDivision_addsLateEntryWhenDivisionMatches() {
        val division = Division(
            id = "d1",
            name = "Youth Color Belts",
            rankRange = 1..5,
            ageRange = 10..14
        )
        val lateEntry = competitor("c9", "Jordan")

        TournamentEngine.addCompetitorToDivision(division, lateEntry)

        assertEquals(1, division.competitors.size)
        assertEquals("Jordan", division.competitors.first().name)
    }

    @Test
    fun filterEligibleCompetitors_excludesCompetitorMarkedNoShow() {
        val checkedInForms = checkedInFor(competitor("c1", "Alex"), CompetitionType.HYUNGS)
        val noShowForms = TournamentEngine.updateCompetitionEntryStatus(
            competitor = TournamentEngine.updateCompetitorCheckInStatus(
                competitor("c2", "Blake"),
                CheckInStatus.NO_SHOW
            ),
            competitionType = CompetitionType.HYUNGS,
            status = CompetitionRegistrationStatus.REGISTERED
        )
        val division = divisionWith(checkedInForms, noShowForms)

        val eligible = TournamentEngine.filterEligibleCompetitors(
            division,
            CompetitionType.HYUNGS
        )

        assertEquals(1, eligible.size)
        assertEquals("Alex", eligible.first().name)
    }

    @Test
    fun filterEligibleCompetitors_allowsLateArrivalForSecondCompetition() {
        val lateArrival = TournamentEngine.updateCompetitionEntryStatus(
            competitor = TournamentEngine.updateCompetitionEntryStatus(
                competitor = TournamentEngine.updateCompetitorCheckInStatus(
                    competitor("c3", "Casey"),
                    CheckInStatus.CHECKED_IN
                ),
                competitionType = CompetitionType.HYUNGS,
                status = CompetitionRegistrationStatus.SCRATCHED
            ),
            competitionType = CompetitionType.SPARRING,
            status = CompetitionRegistrationStatus.REGISTERED
        )
        val division = divisionWith(lateArrival)

        val hyungEligible = TournamentEngine.filterEligibleCompetitors(
            division,
            CompetitionType.HYUNGS
        )
        val sparringEligible = TournamentEngine.filterEligibleCompetitors(
            division,
            CompetitionType.SPARRING
        )

        assertEquals(0, hyungEligible.size)
        assertEquals(1, sparringEligible.size)
        assertEquals("Casey", sparringEligible.first().name)
    }

    @Test
    fun filterEligibleCompetitors_supportsFormsOnlyCompetitor() {
        val formsOnlyCompetitor = checkedInFor(
            competitor("c4", "Drew"),
            CompetitionType.HYUNGS
        )
        val division = divisionWith(formsOnlyCompetitor)

        val hyungEligible = TournamentEngine.filterEligibleCompetitors(
            division,
            CompetitionType.HYUNGS
        )
        val sparringEligible = TournamentEngine.filterEligibleCompetitors(
            division,
            CompetitionType.SPARRING
        )

        assertEquals(1, hyungEligible.size)
        assertEquals("Drew", hyungEligible.first().name)
        assertEquals(0, sparringEligible.size)
    }

    @Test
    fun createFirstRoundSparringBouts_usesPowerOfTwoBracketWithRandomByes() {
        val competitors = listOf(
            competitor("c1", "A", heightInInches = 58),
            competitor("c2", "B", heightInInches = 60),
            competitor("c3", "C", heightInInches = 62),
            competitor("c4", "D", heightInInches = 64),
            competitor("c5", "E", heightInInches = 67),
            competitor("c6", "F", heightInInches = 68)
        )

        val bouts = TournamentEngine.createFirstRoundSparringBouts(competitors)

        assertEquals(4, bouts.size)
        assertEquals(2, bouts.count { it.competitorA != null && it.competitorB != null })
        assertEquals(2, bouts.count { it.isBye })
    }

    @Test
    fun createSampleTournamentScenario_builds_thirteen_competitor_tournament() {
        val scenario = TournamentEngine.createSampleTournamentScenario()

        assertEquals(13, scenario.division.competitors.size)
        assertEquals(13, scenario.division.competitors.map { it.id }.distinct().size)
        assertEquals(11, scenario.weaponsRows.size)
        assertEquals(12, scenario.hyungRows.size)
        assertEquals(8, scenario.sparringBouts.size)
        assertEquals("c13", scenario.noShowCompetitorId)
        assertEquals("c12", scenario.lateArrivalCompetitorId)
        assertEquals(setOf("c10", "c11"), scenario.nonSparringCompetitorIds)

        assertTrue(scenario.division.competitors.all { it.heightInInches in 58..68 })
        assertTrue(scenario.weaponsRows.all { row -> row.scores.all(::isValidScore) })
        assertTrue(scenario.hyungRows.all { row -> row.scores.all(::isValidScore) })

        val noShow = scenario.division.competitors.first { it.id == scenario.noShowCompetitorId }
        assertEquals(CheckInStatus.NO_SHOW, noShow.checkInStatus)

        val lateArrival = scenario.division.competitors.first { it.id == scenario.lateArrivalCompetitorId }
        assertEquals(
            CompetitionRegistrationStatus.SCRATCHED,
            lateArrival.competitionEntries[CompetitionType.WEAPONS]?.status
        )
        assertEquals(
            CompetitionRegistrationStatus.REGISTERED,
            lateArrival.competitionEntries[CompetitionType.HYUNGS]?.status
        )
        assertTrue(scenario.hyungRows.any { it.competitor.id == lateArrival.id })
        assertTrue(scenario.weaponsRows.none { it.competitor.id == lateArrival.id })

        assertTrue(scenario.nonSparringCompetitorIds.all { competitorId ->
            scenario.sparringBouts.none { bout ->
                bout.competitorA?.id == competitorId || bout.competitorB?.id == competitorId
            }
        })
        assertTrue(scenario.sparringBouts.all { bout ->
            bout.competitorB == null ||
                kotlin.math.abs(bout.competitorA!!.heightInInches - bout.competitorB.heightInInches) <= 4
        })
    }

    @Test
    fun prepareCompetitorsForCheckIn_requiresManualCheckInAndDefaultsSparringEntry() {
        val checkedIn = TournamentEngine.updateCompetitorCheckInStatus(
            competitor("c1", "Alex"),
            CheckInStatus.CHECKED_IN
        )
        val noShow = TournamentEngine.updateCompetitorCheckInStatus(
            competitor("c2", "Blake"),
            CheckInStatus.NO_SHOW
        )

        val prepared = prepareCompetitorsForCheckIn(listOf(checkedIn, noShow))

        assertEquals(CheckInStatus.REGISTERED, prepared[0].checkInStatus)
        assertEquals(CheckInStatus.NO_SHOW, prepared[1].checkInStatus)
        assertEquals(
            CompetitionRegistrationStatus.REGISTERED,
            prepared[0].competitionEntries[CompetitionType.SPARRING]?.status
        )
        assertEquals(
            CompetitionRegistrationStatus.REGISTERED,
            prepared[0].competitionEntries[CompetitionType.WEAPONS]?.status
        )
        assertEquals(
            CompetitionRegistrationStatus.REGISTERED,
            prepared[0].competitionEntries[CompetitionType.HYUNGS]?.status
        )
    }

    @Test
    fun resolveSparringBout_firstToThreeWinsBeforeTimeExpires() {
        val bout = sparringBout()

        val result = TournamentEngine.resolveSparringBout(
            bout = bout,
            pointsForA = 3,
            pointsForB = 1,
            elapsedSeconds = 75
        )

        assertEquals(SparringOutcome.FIRST_TO_THREE, result.outcome)
        assertEquals("Alex", result.winner?.name)
        assertEquals(3, result.competitorAResult.adjustedPoints)
    }

    @Test
    fun resolveSparringBout_timeExpiredHigherScoreWins() {
        val bout = sparringBout()

        val result = TournamentEngine.resolveSparringBout(
            bout = bout,
            pointsForA = 2,
            pointsForB = 1,
            elapsedSeconds = TournamentEngine.SPARRING_BOUT_DURATION_SECONDS
        )

        assertEquals(SparringOutcome.TIME_EXPIRED, result.outcome)
        assertEquals("Alex", result.winner?.name)
    }

    @Test
    fun resolveSparringBout_twoWarningsDeductsOnePoint() {
        val bout = sparringBout()

        val result = TournamentEngine.resolveSparringBout(
            bout = bout,
            pointsForA = 2,
            pointsForB = 1,
            warnings = listOf(
                SparringWarning(CompetitorSide.A, SparringWarningType.STANDARD, "Stepping out"),
                SparringWarning(CompetitorSide.A, SparringWarningType.STANDARD, "Holding")
            ),
            elapsedSeconds = TournamentEngine.SPARRING_BOUT_DURATION_SECONDS
        )

        assertEquals(2, result.competitorAResult.standardWarningCount)
        assertEquals(1, result.competitorAResult.pointDeductions)
        assertEquals(1, result.competitorAResult.adjustedPoints)
        assertEquals(SparringOutcome.TIE_BREAK_REQUIRED, result.outcome)
        assertNull(result.winner)
    }

    @Test
    fun resolveSparringBout_threeWarningsCausesDisqualification() {
        val bout = sparringBout()

        val result = TournamentEngine.resolveSparringBout(
            bout = bout,
            pointsForA = 2,
            pointsForB = 0,
            warnings = listOf(
                SparringWarning(CompetitorSide.A, SparringWarningType.STANDARD, "Stepping out"),
                SparringWarning(CompetitorSide.A, SparringWarningType.STANDARD, "Holding"),
                SparringWarning(CompetitorSide.A, SparringWarningType.STANDARD, "Excessive contact")
            ),
            elapsedSeconds = 60
        )

        assertEquals(SparringOutcome.WARNING_DISQUALIFICATION, result.outcome)
        assertTrue(result.competitorAResult.disqualified)
        assertEquals("Blake", result.winner?.name)
    }

    @Test
    fun resolveSparringBout_severeWarningCausesImmediateDisqualification() {
        val bout = sparringBout()

        val result = TournamentEngine.resolveSparringBout(
            bout = bout,
            pointsForA = 0,
            pointsForB = 1,
            warnings = listOf(
                SparringWarning(CompetitorSide.B, SparringWarningType.SEVERE, "Opponent injury with blood")
            ),
            elapsedSeconds = 20
        )

        assertEquals(SparringOutcome.SEVERE_WARNING_DISQUALIFICATION, result.outcome)
        assertTrue(result.competitorBResult.disqualified)
        assertEquals("Alex", result.winner?.name)
    }

    @Test
    fun resolveSparringBout_keepsBoutInProgressWhenNoRuleHasEndedIt() {
        val bout = sparringBout()

        val result = TournamentEngine.resolveSparringBout(
            bout = bout,
            pointsForA = 1,
            pointsForB = 1,
            elapsedSeconds = 45
        )

        assertEquals(SparringOutcome.IN_PROGRESS, result.outcome)
        assertNull(result.winner)
    }

    private fun competitor(id: String, name: String, heightInInches: Int = 60): Competitor {
        return Competitor(
            id = id,
            name = name,
            studio = "Summit MA",
            rank = "3rd Gup",
            rankLevel = 3,
            age = 12,
            heightInInches = heightInInches
        )
    }

    private fun resultRow(competitor: Competitor, base: Double): HyungScoreRow {
        return HyungScoreRow(
            competitor = competitor,
            discipline = HyungDiscipline.HYUNGS,
            scores = listOf(base, base, base, base, base)
        )
    }

    private fun sparringBout(): SparringBout {
        return SparringBout(
            boutNumber = 1,
            competitorA = competitor("c1", "Alex"),
            competitorB = competitor("c2", "Blake")
        )
    }

    private fun checkedInFor(
        competitor: Competitor,
        competitionType: CompetitionType
    ): Competitor {
        return TournamentEngine.updateCompetitionEntryStatus(
            competitor = TournamentEngine.updateCompetitorCheckInStatus(
                competitor,
                CheckInStatus.CHECKED_IN
            ),
            competitionType = competitionType,
            status = CompetitionRegistrationStatus.REGISTERED
        )
    }

    private fun divisionWith(vararg competitors: Competitor): Division {
        return Division(
            id = "d1",
            name = "Youth Color Belts",
            rankRange = 1..5,
            ageRange = 10..14,
            competitors = competitors.toMutableList()
        )
    }

    private fun isValidScore(score: Double): Boolean {
        val tenths = score * 10
        return score in 7.2..8.4 && kotlin.math.abs(tenths - tenths.toInt()) < 0.0001
    }
}