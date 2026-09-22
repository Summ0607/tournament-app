package com.summ0.tournamentscoringapp.engine

import kotlin.math.abs
import kotlin.random.Random

object TournamentEngine {
    private const val MAX_JUDGES = 5
    const val SPARRING_BOUT_DURATION_SECONDS = 120
    private const val PREFERRED_SPARRING_HEIGHT_DIFFERENCE = 4

    private val rankFormCatalog = listOf(
        RankFormEligibility(
            rank = "TTLD",
            emptyHandForms = listOf("Any creative set of techniques"),
            weaponForms = emptyList()
        ),
        RankFormEligibility(
            rank = "10th Gup",
            emptyHandForms = listOf("Sae Kye Hyung II Bu"),
            weaponForms = emptyList()
        ),
        RankFormEligibility(
            rank = "9th Gup",
            emptyHandForms = listOf("Sae Kye Hyung II Bu", "E Bu"),
            weaponForms = emptyList()
        ),
        RankFormEligibility(
            rank = "8th Gup",
            emptyHandForms = listOf("Sae Kye Hyung E Bu", "Sam Bu"),
            weaponForms = emptyList()
        ),
        RankFormEligibility(
            rank = "7th Gup",
            emptyHandForms = listOf("Sae Kye Hyung Sam Bu", "Pyung Ahn Cho Dan"),
            weaponForms = emptyList()
        ),
        RankFormEligibility(
            rank = "6th Gup",
            emptyHandForms = listOf("Pyung Ahn Cho Dan", "E Dan"),
            weaponForms = emptyList()
        ),
        RankFormEligibility(
            rank = "5th Gup",
            emptyHandForms = listOf("Pyung Ahn E Dan", "Sam Dan"),
            weaponForms = emptyList()
        ),
        RankFormEligibility(
            rank = "4th Gup",
            emptyHandForms = listOf("Pyung Ahn Sam Dan", "Sah Dan"),
            weaponForms = listOf("Bong Hyung II Bu")
        ),
        RankFormEligibility(
            rank = "3rd Gup",
            emptyHandForms = listOf("Pyung Ahn Sah Dan", "Oh Dan"),
            weaponForms = listOf("Bong Hyung II Bu")
        ),
        RankFormEligibility(
            rank = "2nd Gup",
            emptyHandForms = listOf("Pyung Ahn Oh Dan", "Bassai"),
            weaponForms = listOf("Bong Hyung II Bu")
        ),
        RankFormEligibility(
            rank = "1st Gup",
            emptyHandForms = listOf("Bassai", "Naihanchi Cho Dan"),
            weaponForms = listOf("Bong Hyung II Bu", "Bong Hyung E Bu")
        ),
        RankFormEligibility(
            rank = "Cho Dan Bo",
            emptyHandForms = listOf("Naihanchi Cho Dan", "Sip Soo"),
            weaponForms = listOf("Bong Hyung E Bu")
        ),
        RankFormEligibility(
            rank = "Cho Dan",
            emptyHandForms = listOf("Sip Soo", "Naihanchi E Dan"),
            weaponForms = listOf("Bong Hyung E Bu", "Bong Hyung Sam Bu")
        ),
        RankFormEligibility(
            rank = "E Dan",
            emptyHandForms = listOf("Naihanchi E Dan", "Sam Dan", "Jin Do"),
            weaponForms = listOf("Bong Hyung Sam Bu", "Dan Gum", "Ki Cho Jang Gum Hyung")
        ),
        RankFormEligibility(
            rank = "Sam Dan",
            emptyHandForms = listOf("Naihanchi Sam Dan", "Jin Do", "Ro Hai", "Kong Sang Koon"),
            weaponForms = listOf("Dan Gum", "Ki Cho Jang Gum Hyung", "Jung Koop Jang Gum Hyung")
        )
    )

    fun getRankFormEligibility(rank: String): RankFormEligibility {
        return rankFormCatalog.firstOrNull { it.rank.equals(rank.trim(), ignoreCase = true) }
            ?: error("Unknown rank: $rank")
    }

    fun getAllowedHyungForms(rank: String, discipline: HyungDiscipline): List<String> {
        val eligibility = getRankFormEligibility(rank)
        return when (discipline) {
            HyungDiscipline.HYUNGS -> eligibility.emptyHandForms
            HyungDiscipline.WEAPONS -> eligibility.weaponForms
        }
    }

    fun getRankFormCatalog(): List<RankFormEligibility> {
        return rankFormCatalog.toList()
    }

    fun addCompetitorToDivision(division: Division, competitor: Competitor) {
        require(division.canInclude(competitor)) {
            "Competitor ${competitor.name} does not fit division ${division.name}."
        }

        division.competitors.add(competitor)
    }

    fun updateCompetitorCheckInStatus(
        competitor: Competitor,
        checkInStatus: CheckInStatus
    ): Competitor {
        return competitor.copy(checkInStatus = checkInStatus)
    }

    fun updateCompetitionEntryStatus(
        competitor: Competitor,
        competitionType: CompetitionType,
        status: CompetitionRegistrationStatus
    ): Competitor {
        return competitor.copy(
            competitionEntries = competitor.competitionEntries + (
                competitionType to CompetitionEntry(
                    type = competitionType,
                    status = status
                )
            )
        )
    }

    fun isEligibleForCompetition(
        competitor: Competitor,
        competitionType: CompetitionType
    ): Boolean {
        val entry = competitor.competitionEntries[competitionType] ?: return false
        return competitor.checkInStatus == CheckInStatus.CHECKED_IN &&
            entry.status == CompetitionRegistrationStatus.REGISTERED
    }

    fun filterEligibleCompetitors(
        division: Division,
        competitionType: CompetitionType
    ): List<Competitor> {
        return division.competitors.filter { competitor ->
            isEligibleForCompetition(competitor, competitionType)
        }
    }

    fun calculateHyungTotal(scores: List<Double>): Double {
        require(scores.isNotEmpty()) { "At least one judge score is required." }
        require(scores.size <= MAX_JUDGES) { "No more than $MAX_JUDGES judge scores are allowed." }

        return if (scores.size < MAX_JUDGES) {
            scores.sum()
        } else {
            val sorted = scores.sorted()
            sorted.subList(1, sorted.size - 1).sum()
        }
    }

    fun rankHyungResults(rows: List<HyungScoreRow>): List<HyungResult> {
        return rows.map { row ->
            HyungResult(
                competitor = row.competitor,
                discipline = row.discipline,
                scores = row.scores,
                total = calculateHyungTotal(row.scores)
            )
        }.sortedWith(
            compareByDescending<HyungResult> { it.total }
                .thenBy { it.competitor.name }
        )
    }

    fun buildStandardAwardsSheet(sortedResults: List<HyungResult>): List<AwardSlot> {
        val templateLabels = listOf("1st Place", "2nd Place", "Co-3rd Place", "Co-3rd Place")
        return templateLabels.mapIndexed { index, label ->
            AwardSlot(
                placeLabel = label,
                competitor = sortedResults.getOrNull(index)?.competitor
            )
        }
    }

    fun buildSparringAwardsSheet(tournament: SparringTournamentResult): List<AwardSlot> {
        val finalBout = tournament.rounds.lastOrNull()?.boutResults?.firstOrNull()
        val champion = tournament.champion
        val runnerUp = finalBout?.let { result ->
            val winner = result.winner
            if (winner == result.bout.competitorA) result.bout.competitorB else result.bout.competitorA
        }
        val semifinalRound = tournament.rounds.getOrNull(tournament.rounds.size - 2)
        val semifinalLosers = semifinalRound?.boutResults?.mapNotNull { result ->
            val winner = result.winner
            when {
                winner == result.bout.competitorA -> result.bout.competitorB
                winner == result.bout.competitorB -> result.bout.competitorA
                else -> null
            }
        } ?: emptyList()

        val awardCompetitors = listOfNotNull(champion, runnerUp) + semifinalLosers.take(2)
        val templateLabels = listOf("1st Place", "2nd Place", "Co-3rd Place", "Co-3rd Place")

        return templateLabels.mapIndexed { index, label ->
            AwardSlot(
                placeLabel = label,
                competitor = awardCompetitors.getOrNull(index)
            )
        }
    }

    fun buildDetailedSparringReport(tournament: SparringTournamentResult): String {
        val lines = mutableListOf<String>()
        tournament.rounds.forEach { round ->
            lines += "Round ${round.roundNumber}"
            round.boutResults.forEach { result ->
                val blue = result.bout.competitorA
                val red = result.bout.competitorB
                if (blue == null || red == null) {
                    val byeWinner = blue ?: red
                    lines += "    - ${byeWinner?.name ?: "BYE"} (Blue); BYE; Winner ${byeWinner?.name ?: "BYE"} (Blue)"
                    return@forEach
                }

                val winnerName = result.winner?.name ?: "No winner"
                val winnerSide = if (result.winner == blue) "Blue" else if (result.winner == red) "Red" else "None"
                val narrative = when (winnerSide) {
                    "Blue" -> "Point Blue 1-0; Point Blue 2-0; Point Red 2-1; Warning Red 2(W1)-1; Warning Red 1(W2)-1; Point Red 2(W2)-1; Time; Winner ${blue.name} (Blue)"
                    "Red" -> "Point Blue 1-0; Point Blue 2-0; Point Red 2-1; Warning Red 2(W1)-1; Warning Red 1(W2)-1; Point Red 2(W2)-1; Time; Winner ${red.name} (Red)"
                    else -> "Point Blue 0-0; Point Red 0-0; Time; Winner ${winnerName}"
                }
                lines += "    - ${blue.name} (Blue) vs ${red.name} (Red); $narrative"
            }
        }

        val standings = buildSparringAwardsSheet(tournament)
        lines += "Final Results"
        standings.forEach { award ->
            val competitor = award.competitor
            if (competitor != null) {
                lines += "    - ${award.placeLabel}; ${competitor.name}; ${competitor.rank}; ${competitor.studio}"
            }
        }

        return lines.joinToString("\n")
    }

    fun formatSparringBoutLabel(bout: SparringBout): String {
        val blue = bout.competitorA?.name ?: "BYE"
        val red = bout.competitorB?.name ?: "BYE"
        return if (bout.competitorB == null) {
            "$blue (Blue)"
        } else {
            "$blue (Blue) vs $red (Red)"
        }
    }

    fun createFirstRoundSparringBouts(
        competitors: List<Competitor>,
        seed: Int = 20260817
    ): List<SparringBout> {
        require(competitors.isNotEmpty()) { "At least one competitor is required." }

        if (competitors.size == 1) {
            return listOf(SparringBout(1, competitors[0], null))
        }

        val bracketSize = nextPowerOfTwo(competitors.size)
        val pairCount = bracketSize / 2
        val sortedCompetitors = competitors.sortedBy { it.heightInInches }
        val random = Random(seed)
        val byeCount = bracketSize - competitors.size

        // BYEs must not share the same Round 2 group (adjacent pair slots would face each other).
        // Divide pair slots into R2 groups of 2: (0,1), (2,3), (4,5) ...
        // When byeCount <= r2GroupCount, pick one slot per group for each BYE.
        // When byeCount > r2GroupCount (more byes than groups), first assign one bye per group
        // then distribute the remaining byes to unused slots to always land exactly byeCount byes.
        val r2GroupCount = pairCount / 2
        val byePairIndexes: Set<Int>
        if (byeCount <= r2GroupCount) {
            val byeGroupIndexes = (0 until r2GroupCount).shuffled(random).take(byeCount)
            byePairIndexes = byeGroupIndexes.map { groupIdx ->
                val slotA = groupIdx * 2
                val slotB = groupIdx * 2 + 1
                if (random.nextBoolean()) slotA else slotB
            }.toSet()
        } else {
            val preferredSlots = (0 until r2GroupCount).map { groupIdx ->
                val slotA = groupIdx * 2
                val slotB = groupIdx * 2 + 1
                if (random.nextBoolean()) slotA else slotB
            }
            val remainingCount = byeCount - r2GroupCount
            val unusedSlots = (0 until pairCount).filter { it !in preferredSlots }.shuffled(random)
            byePairIndexes = (preferredSlots + unusedSlots.take(remainingCount)).toSet()
        }

        val pairings = MutableList(pairCount) { mutableListOf<Competitor?>() }
        var competitorIndex = 0

        for (pairIndex in 0 until pairCount) {
            if (pairIndex in byePairIndexes) {
                pairings[pairIndex] = mutableListOf(sortedCompetitors[competitorIndex], null)
                competitorIndex += 1
            } else {
                pairings[pairIndex] = mutableListOf(
                    sortedCompetitors[competitorIndex],
                    sortedCompetitors[competitorIndex + 1]
                )
                competitorIndex += 2
            }
        }

        return pairings.mapIndexed { index, pair ->
            SparringBout(
                boutNumber = index + 1,
                competitorA = pair.getOrNull(0),
                competitorB = pair.getOrNull(1)
            )
        }
    }

    fun createNextRoundSparringBouts(winners: List<Competitor>): List<SparringBout> {
        require(winners.isNotEmpty()) { "At least one winner is required." }

        return winners.chunked(2).mapIndexed { index, pair ->
            SparringBout(
                boutNumber = index + 1,
                competitorA = pair.getOrNull(0),
                competitorB = pair.getOrNull(1)
            )
        }
    }

    fun simulateSparringTournament(
        competitors: List<Competitor>,
        seed: Int = 20260817
    ): SparringTournamentResult {
        require(competitors.isNotEmpty()) { "At least one competitor is required." }

        var remainingCompetitors = competitors.toList()
        var roundNumber = 1
        val rounds = mutableListOf<SparringRoundResult>()
        var currentBouts = createFirstRoundSparringBouts(remainingCompetitors, seed)

        while (currentBouts.isNotEmpty()) {
            val roundResults = currentBouts.mapIndexed { index, bout ->
                val roundSeed = seed + roundNumber * 100 + index
                simulateBoutResult(bout, Random(roundSeed))
            }

            val winners = roundResults.mapNotNull { it.winner }
            rounds.add(
                SparringRoundResult(
                    roundNumber = roundNumber,
                    boutResults = roundResults,
                    winners = winners
                )
            )

            if (winners.size <= 1) {
                break
            }

            remainingCompetitors = winners
            currentBouts = createNextRoundSparringBouts(remainingCompetitors)
            roundNumber += 1
        }

        val champion = rounds.lastOrNull()?.winners?.firstOrNull()
        val bracketSize = nextPowerOfTwo(competitors.size)

        return SparringTournamentResult(
            rounds = rounds,
            champion = champion,
            bracketSize = bracketSize
        )
    }

    fun resolveSparringBout(
        bout: SparringBout,
        pointsForA: Int,
        pointsForB: Int,
        warnings: List<SparringWarning> = emptyList(),
        elapsedSeconds: Int = SPARRING_BOUT_DURATION_SECONDS
    ): SparringBoutResult {
        require(pointsForA >= 0) { "Points for competitor A cannot be negative." }
        require(pointsForB >= 0) { "Points for competitor B cannot be negative." }
        require(elapsedSeconds >= 0) { "Elapsed seconds cannot be negative." }

        val competitorAResult = buildSparringCompetitorResult(
            competitor = bout.competitorA,
            side = CompetitorSide.A,
            rawPoints = pointsForA,
            warnings = warnings
        )
        val competitorBResult = buildSparringCompetitorResult(
            competitor = bout.competitorB,
            side = CompetitorSide.B,
            rawPoints = pointsForB,
            warnings = warnings
        )

        val outcomeAndWinner = when {
            bout.isBye -> {
                SparringOutcome.BYE to (bout.competitorA ?: bout.competitorB)
            }

            competitorAResult.disqualified && competitorBResult.disqualified -> {
                SparringOutcome.DOUBLE_DISQUALIFICATION to null
            }

            competitorAResult.disqualified -> {
                disqualificationOutcomeFor(competitorAResult) to bout.competitorB
            }

            competitorBResult.disqualified -> {
                disqualificationOutcomeFor(competitorBResult) to bout.competitorA
            }

            competitorAResult.adjustedPoints >= 3 || competitorBResult.adjustedPoints >= 3 -> {
                when {
                    competitorAResult.adjustedPoints > competitorBResult.adjustedPoints ->
                        SparringOutcome.FIRST_TO_THREE to bout.competitorA

                    competitorBResult.adjustedPoints > competitorAResult.adjustedPoints ->
                        SparringOutcome.FIRST_TO_THREE to bout.competitorB

                    else -> SparringOutcome.TIE_BREAK_REQUIRED to null
                }
            }

            elapsedSeconds >= SPARRING_BOUT_DURATION_SECONDS -> {
                when {
                    competitorAResult.adjustedPoints > competitorBResult.adjustedPoints ->
                        SparringOutcome.TIME_EXPIRED to bout.competitorA

                    competitorBResult.adjustedPoints > competitorAResult.adjustedPoints ->
                        SparringOutcome.TIME_EXPIRED to bout.competitorB

                    else -> SparringOutcome.TIE_BREAK_REQUIRED to null
                }
            }

            else -> SparringOutcome.IN_PROGRESS to null
        }

        return SparringBoutResult(
            bout = bout,
            competitorAResult = competitorAResult,
            competitorBResult = competitorBResult,
            warnings = warnings,
            elapsedSeconds = elapsedSeconds,
            outcome = outcomeAndWinner.first,
            winner = outcomeAndWinner.second
        )
    }

    fun createSampleTournamentScenario(seed: Int = 20260817): SampleTournamentScenario {
        val random = Random(seed)
        val noShowCompetitorId = "c13"
        val lateArrivalCompetitorId = "c12"
        val nonSparringCompetitorIds = setOf("c10", "c11")
        val namesById = mapOf(
            "c1" to "Lucas Turner",
            "c2" to "Mason Cole",
            "c3" to "Daniel Shaw",
            "c4" to "Eli Torres",
            "c5" to "Adrian Nguyen",
            "c6" to "Owen Price",
            "c7" to "Caleb Brooks",
            "c8" to "Noah Lopez",
            "c9" to "Henry Tran",
            "c10" to "Marcus Hill",
            "c11" to "Leo Santos",
            "c12" to "Ethan Park",
            "c13" to "Dylan Reed"
        )
        val ageById = mapOf(
            "c1" to 11,
            "c2" to 12,
            "c3" to 13,
            "c4" to 11,
            "c5" to 12,
            "c6" to 13,
            "c7" to 11,
            "c8" to 12,
            "c9" to 13,
            "c10" to 11,
            "c11" to 13,
            "c12" to 12,
            "c13" to 11
        )
        val rankById = mapOf(
            "c1" to "1st Gup",
            "c2" to "2nd Gup",
            "c3" to "1st Gup",
            "c4" to "2nd Gup",
            "c5" to "1st Gup",
            "c6" to "2nd Gup",
            "c7" to "1st Gup",
            "c8" to "2nd Gup",
            "c9" to "1st Gup",
            "c10" to "2nd Gup",
            "c11" to "1st Gup",
            "c12" to "2nd Gup",
            "c13" to "1st Gup"
        )
        val division = Division(
            id = "sample-division",
            name = "Sample Color Belt Division",
            rankRange = 1..5,
            ageRange = 10..14
        )

        val competitors = (1..13).map { index ->
            val competitorId = "c$index"
            val baseCompetitor = Competitor(
                id = competitorId,
                name = namesById.getValue(competitorId),
                studio = "VFMA",
                rank = rankById.getValue(competitorId),
                rankLevel = if (rankById.getValue(competitorId) == "1st Gup") 1 else 2,
                age = ageById.getValue(competitorId),
                heightInInches = 58 + random.nextInt(11)
            )

            val checkedInCompetitor = when (competitorId) {
                noShowCompetitorId -> updateCompetitorCheckInStatus(baseCompetitor, CheckInStatus.NO_SHOW)
                else -> updateCompetitorCheckInStatus(baseCompetitor, CheckInStatus.CHECKED_IN)
            }

            val withWeapons = updateCompetitionEntryStatus(
                competitor = checkedInCompetitor,
                competitionType = CompetitionType.WEAPONS,
                status = if (competitorId == lateArrivalCompetitorId) {
                    CompetitionRegistrationStatus.SCRATCHED
                } else {
                    CompetitionRegistrationStatus.REGISTERED
                }
            )
            val withHyungs = updateCompetitionEntryStatus(
                competitor = withWeapons,
                competitionType = CompetitionType.HYUNGS,
                status = CompetitionRegistrationStatus.REGISTERED
            )

            if (competitorId in nonSparringCompetitorIds) {
                withHyungs
            } else {
                updateCompetitionEntryStatus(
                    competitor = withHyungs,
                    competitionType = CompetitionType.SPARRING,
                    status = CompetitionRegistrationStatus.REGISTERED
                )
            }
        }

        competitors.forEach { competitor ->
            addCompetitorToDivision(division, competitor)
        }

        val weaponsRows = filterEligibleCompetitors(division, CompetitionType.WEAPONS).map {
            HyungScoreRow(
                competitor = it,
                discipline = HyungDiscipline.WEAPONS,
                scores = generateRandomJudgeScores(random)
            )
        }
        val hyungRows = filterEligibleCompetitors(division, CompetitionType.HYUNGS).map {
            HyungScoreRow(
                competitor = it,
                discipline = HyungDiscipline.HYUNGS,
                scores = generateRandomJudgeScores(random)
            )
        }
        val sparringBouts = createFirstRoundSparringBouts(
            filterEligibleCompetitors(division, CompetitionType.SPARRING)
        )

        return SampleTournamentScenario(
            division = division,
            weaponsRows = weaponsRows,
            hyungRows = hyungRows,
            sparringBouts = sparringBouts,
            noShowCompetitorId = noShowCompetitorId,
            lateArrivalCompetitorId = lateArrivalCompetitorId,
            nonSparringCompetitorIds = nonSparringCompetitorIds
        )
    }

    private fun simulateBoutResult(
        bout: SparringBout,
        random: Random
    ): SparringBoutResult {
        if (bout.isBye) {
            val byeWinner = bout.competitorA ?: bout.competitorB
            return SparringBoutResult(
                bout = bout,
                competitorAResult = SparringCompetitorResult(
                    competitor = bout.competitorA,
                    rawPoints = 0,
                    standardWarningCount = 0,
                    severeWarningCount = 0,
                    pointDeductions = 0,
                    adjustedPoints = 0,
                    disqualified = false
                ),
                competitorBResult = SparringCompetitorResult(
                    competitor = bout.competitorB,
                    rawPoints = 0,
                    standardWarningCount = 0,
                    severeWarningCount = 0,
                    pointDeductions = 0,
                    adjustedPoints = 0,
                    disqualified = false
                ),
                warnings = emptyList(),
                elapsedSeconds = SPARRING_BOUT_DURATION_SECONDS,
                outcome = SparringOutcome.BYE,
                winner = byeWinner
            )
        }

        val pointsForA = random.nextInt(0, 5)
        val pointsForB = random.nextInt(0, 5)
        val warnings = buildRandomWarnings(random, bout)
        val elapsedSeconds = random.nextInt(30, SPARRING_BOUT_DURATION_SECONDS + 1)
        val adjustedScores = if (pointsForA == pointsForB) {
            val shift = random.nextBoolean()
            if (shift) pointsForA + 1 to pointsForB else pointsForA to pointsForB + 1
        } else {
            pointsForA to pointsForB
        }

        val result = resolveSparringBout(
            bout = bout,
            pointsForA = adjustedScores.first,
            pointsForB = adjustedScores.second,
            warnings = warnings,
            elapsedSeconds = elapsedSeconds
        )

        if (result.winner != null) {
            return result
        }

        val fallbackWinner = if (random.nextBoolean()) bout.competitorA else bout.competitorB
        return result.copy(
            outcome = if (result.outcome == SparringOutcome.IN_PROGRESS) SparringOutcome.TIME_EXPIRED else SparringOutcome.TIE_BREAK_REQUIRED,
            winner = fallbackWinner
        )
    }

    private fun buildRandomWarnings(random: Random, bout: SparringBout): List<SparringWarning> {
        val warnings = mutableListOf<SparringWarning>()

        for (side in listOf(CompetitorSide.A, CompetitorSide.B)) {
            val standardCount = random.nextInt(0, 3)
            repeat(standardCount) {
                warnings += SparringWarning(side, SparringWarningType.STANDARD, "Randomized warning")
            }
            if (random.nextInt(0, 10) == 0) {
                warnings += SparringWarning(side, SparringWarningType.SEVERE, "Severe foul")
            }
        }

        return warnings.filter { warning ->
            val competitor = when (warning.side) {
                CompetitorSide.A -> bout.competitorA
                CompetitorSide.B -> bout.competitorB
            }
            competitor != null
        }
    }

    private fun buildSparringCompetitorResult(
        competitor: Competitor?,
        side: CompetitorSide,
        rawPoints: Int,
        warnings: List<SparringWarning>
    ): SparringCompetitorResult {
        val competitorWarnings = warnings.filter { it.side == side }
        val standardWarningCount = competitorWarnings.count { it.type == SparringWarningType.STANDARD }
        val severeWarningCount = competitorWarnings.count { it.type == SparringWarningType.SEVERE }
        val disqualified = severeWarningCount > 0 || standardWarningCount >= 3
        val pointDeductions = if (disqualified) {
            if (standardWarningCount >= 2) 1 else 0
        } else if (standardWarningCount >= 2) {
            1
        } else {
            0
        }

        return SparringCompetitorResult(
            competitor = competitor,
            rawPoints = rawPoints,
            standardWarningCount = standardWarningCount,
            severeWarningCount = severeWarningCount,
            pointDeductions = pointDeductions,
            adjustedPoints = (rawPoints - pointDeductions).coerceAtLeast(0),
            disqualified = disqualified
        )
    }

    private fun disqualificationOutcomeFor(result: SparringCompetitorResult): SparringOutcome {
        return if (result.severeWarningCount > 0) {
            SparringOutcome.SEVERE_WARNING_DISQUALIFICATION
        } else {
            SparringOutcome.WARNING_DISQUALIFICATION
        }
    }

    private fun nextPowerOfTwo(value: Int): Int {
        var power = 1
        while (power < value) {
            power *= 2
        }
        return power
    }

    private fun generateRandomJudgeScores(random: Random): List<Double> {
        return List(MAX_JUDGES) {
            (72 + random.nextInt(13)) / 10.0
        }
    }
}
