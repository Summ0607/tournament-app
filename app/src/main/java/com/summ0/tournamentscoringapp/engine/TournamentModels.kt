package com.summ0.tournamentscoringapp.engine

enum class HyungDiscipline {
    HYUNGS,
    WEAPONS
}

data class RankFormEligibility(
    val rank: String,
    val emptyHandForms: List<String>,
    val weaponForms: List<String>
)

enum class CompetitionType {
    HYUNGS,
    WEAPONS,
    SPARRING
}

enum class CheckInStatus {
    REGISTERED,
    CHECKED_IN,
    NO_SHOW
}

enum class CompetitionRegistrationStatus {
    REGISTERED,
    SCRATCHED,
    COMPLETED
}

data class CompetitionEntry(
    val type: CompetitionType,
    val status: CompetitionRegistrationStatus = CompetitionRegistrationStatus.REGISTERED
)

data class Competitor(
    val id: String,
    val name: String,
    val studio: String,
    val rank: String,
    val rankLevel: Int,
    val age: Int,
    val heightInInches: Int,
    val checkInStatus: CheckInStatus = CheckInStatus.REGISTERED,
    val competitionEntries: Map<CompetitionType, CompetitionEntry> = emptyMap()
)

data class Judge(
    val id: String,
    val name: String,
    val rank: String
)

data class Division(
    val id: String,
    val name: String,
    val rankRange: IntRange,
    val rankRangeLabel: String = "0-0",
    val ageRange: IntRange = 0..0,
    val competitors: MutableList<Competitor> = mutableListOf(),
    val judges: MutableList<Judge> = mutableListOf()
) {
    fun canInclude(competitor: Competitor): Boolean {
        return competitor.rankLevel in rankRange && competitor.age in ageRange
    }
}

data class HyungScoreRow(
    val competitor: Competitor,
    val discipline: HyungDiscipline,
    val scores: List<Double>
)

data class HyungResult(
    val competitor: Competitor,
    val discipline: HyungDiscipline,
    val scores: List<Double>,
    val total: Double
)

data class AwardSlot(
    val placeLabel: String,
    val competitor: Competitor?
)

enum class CompetitorSide {
    A,
    B
}

enum class SparringWarningType {
    STANDARD,
    SEVERE
}

data class SparringWarning(
    val side: CompetitorSide,
    val type: SparringWarningType,
    val reason: String
)

enum class SparringOutcome {
    BYE,
    FIRST_TO_THREE,
    TIME_EXPIRED,
    WARNING_DISQUALIFICATION,
    SEVERE_WARNING_DISQUALIFICATION,
    DOUBLE_DISQUALIFICATION,
    TIE_BREAK_REQUIRED,
    IN_PROGRESS
}

data class SparringCompetitorResult(
    val competitor: Competitor?,
    val rawPoints: Int,
    val standardWarningCount: Int,
    val severeWarningCount: Int,
    val pointDeductions: Int,
    val adjustedPoints: Int,
    val disqualified: Boolean
)

data class SparringBout(
    val boutNumber: Int,
    val competitorA: Competitor?,
    val competitorB: Competitor?
) {
    val isBye: Boolean = (competitorA == null) xor (competitorB == null)
}

data class SparringBoutResult(
    val bout: SparringBout,
    val competitorAResult: SparringCompetitorResult,
    val competitorBResult: SparringCompetitorResult,
    val warnings: List<SparringWarning>,
    val elapsedSeconds: Int,
    val outcome: SparringOutcome,
    val winner: Competitor?
)

data class SampleTournamentScenario(
    val division: Division,
    val weaponsRows: List<HyungScoreRow>,
    val hyungRows: List<HyungScoreRow>,
    val sparringBouts: List<SparringBout>,
    val noShowCompetitorId: String,
    val lateArrivalCompetitorId: String,
    val nonSparringCompetitorIds: Set<String>
)

data class SparringRoundResult(
    val roundNumber: Int,
    val boutResults: List<SparringBoutResult>,
    val winners: List<Competitor>
)

data class SparringTournamentResult(
    val rounds: List<SparringRoundResult>,
    val champion: Competitor?,
    val bracketSize: Int
)
