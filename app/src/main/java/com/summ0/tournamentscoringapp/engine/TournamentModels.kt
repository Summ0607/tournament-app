package com.summ0.tournamentscoringapp.engine

import kotlin.math.roundToInt

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

internal enum class CompetitionScreen {
    CHECK_IN,
    WEAPONS_SCORING,
    HYUNGS_SCORING,
    SPARRING_BRACKET,
    OVERALL_AWARDS
}

data class PlacementFinalizeState(
    val labels: Map<String, String> = emptyMap(),
    val tieBreakDetails: Map<String, String> = emptyMap(),
    val message: String? = null
)

data class PendingTieBreak(
    val discipline: HyungDiscipline,
    val competitorIds: List<String>,
    val judgeCount: Int,
    val groupKey: String
)

data class PlacementCandidate(
    val competitor: Competitor,
    val scores: List<Double>,
    val total: Double,
    val judgeCount: Int
)

data class TieResolution(
    val orderedCandidates: List<PlacementCandidate>,
    val tieBreakDetails: Map<String, String>
)

sealed interface PlacementFinalizeResult {
    data class Completed(
        val labels: Map<String, String>,
        val tieBreakDetails: Map<String, String>
    ) : PlacementFinalizeResult

    data class NeedsTieBreak(
        val pendingLabels: Map<String, String>,
        val request: PendingTieBreak
    ) : PlacementFinalizeResult

    data class Incomplete(val message: String) : PlacementFinalizeResult
}

data class ActiveSparringBout(
    val roundIndex: Int,
    val boutIndex: Int,
    val bout: BracketSheetBout
)

data class SparringBoutProgress(
    val bluePoints: Int = 0,
    val redPoints: Int = 0,
    val blueWarnings: Int = 0,
    val redWarnings: Int = 0,
    val elapsedSeconds: Int = 0,
    val winnerId: String? = null,
    val outcomeLabel: String = ""
)

data class SparringBoutAssessment(
    val blueAdjustedScore: Int,
    val redAdjustedScore: Int,
    val blueDisqualified: Boolean,
    val redDisqualified: Boolean,
    val winner: Competitor?,
    val outcomeLabel: String
)

data class RemoteCompetitor(
    val id: String,
    val name: String,
    val studio: String,
    val rank: String,
    val age: Int,
    val heightInInches: Int
)

data class RemoteGroup(
    val groupId: String,
    val groupDivisionNumber: Int? = null,
    val groupDivisionName: String? = null,
    val name: String,
    val ageRange: IntRange,
    val rankRange: IntRange,
    val rankRangeLabel: String,
    val matNumber: Int,
    val competitors: List<RemoteCompetitor>
)

data class RemoteGroupFetchResult(
    val group: RemoteGroup? = null,
    val errorMessage: String? = null
)

data class PhaseTiming(
    val name: String,
    val startTime: String,
    val endTime: String?,
    val elapsed: String,
    val estimated: String
)

data class RingAssignment(
    val ringId: String,
    val ringLabel: String,
    val serverBaseUrl: String,
    val eventName: String = "",
    val eventNameSource: String = "",
    val groupDivisionNumber: Int? = null,
    val currentGroup: RemoteGroup?,
    val currentPhase: String = "",
    val phasePlan: String = "",
    val currentPhaseName: String = "",
    val currentPhaseStartTime: String = "",
    val currentPhaseEndTime: String? = null,
    val currentPhaseElapsed: String = "",
    val currentPhaseEstimated: String = "",
    val currentPhaseCompletedCount: Int = 0,
    val currentPhaseTotalCount: Int = 0,
    val sparringByeCount: Int = 0,
    val sparringActualBoutCount: Int = 0,
    val sparringCompletedBoutCount: Int = 0,
    val ringElapsed: String = "",
    val ringEstimated: String = "",
    val ringPacePercent: Int = 0,
    val phaseHistory: List<PhaseTiming> = emptyList(),
    val queuedGroupDivisionNumbers: List<Int> = emptyList(),
    val completedGroupDivisionNumbers: List<Int> = emptyList()
)

data class RingAssignmentFetchResult(
    val assignment: RingAssignment? = null,
    val errorMessage: String? = null
)

data class RingOption(
    val ringId: String,
    val ringLabel: String,
    val isAvailable: Boolean,
    val statusLabel: String
)

data class RingConfigFetchResult(
    val serverBaseUrl: String? = null,
    val rings: List<RingOption> = emptyList(),
    val errorMessage: String? = null
)

data class RingGridCell(
    val letter: String,
    val number: Int,
    val ring: RingOption?
)

data class JsonFetchResult(
    val root: org.json.JSONObject? = null,
    val errorMessage: String? = null
)

data class HttpProbeResult(
    val ok: Boolean,
    val errorMessage: String? = null
)

enum class ServerConnectionMode {
    DNS,
    IP
}

data class ServerConnectionConfig(
    val mode: ServerConnectionMode,
    val lastDnsName: String,
    val lastServerAddress: String
)

data class GroupBanner(
    val groupDivisionNumber: Int? = null,
    val groupDivisionName: String? = null,
    val statusText: String = ""
) {
    val isLoaded: Boolean get() = groupDivisionNumber != null
    val displayId: String get() = when {
        groupDivisionNumber != null -> {
            val name = groupDivisionName?.trim().orEmpty()
            if (name.isBlank()) "Division $groupDivisionNumber" else "Division $groupDivisionNumber: $name"
        }
        statusText.isNotEmpty() -> statusText
        else -> "? No Division Loaded"
    }
    val details: String get() = groupDivisionName.orEmpty()
}

fun parseDivisionNumberFromLegacyGroupId(groupId: String): Int? {
    val match = Regex("\\d+").find(groupId) ?: return null
    return match.value.toIntOrNull()
}

fun formatDivisionDisplayLabel(groupDivisionNumber: Int?, groupDivisionName: String?, statusText: String = ""): String {
    val number = groupDivisionNumber?.takeIf { it > 0 }
    val name = groupDivisionName?.trim().orEmpty()
    return when {
        number != null && name.isNotBlank() -> "Division $number: $name"
        number != null -> "Division $number"
        name.isNotBlank() -> name
        statusText.isNotBlank() -> statusText
        else -> "? No Division Loaded"
    }
}

fun RemoteGroup.effectiveDivisionNumber(): Int? {
    return groupDivisionNumber ?: parseDivisionNumberFromLegacyGroupId(groupId)
}

fun RemoteGroup.effectiveDivisionName(): String? {
    return groupDivisionName?.trim()?.takeIf { it.isNotEmpty() } ?: name.trim().takeIf { it.isNotEmpty() }
}

data class ParsedRankRange(
    val range: IntRange,
    val label: String
)

data class HeartbeatProgressSnapshot(
    val completedCount: Int,
    val totalCount: Int
) {
    val percent: Int
        get() = if (totalCount <= 0) {
            0
        } else {
            ((completedCount.coerceAtMost(totalCount).toDouble() / totalCount.toDouble()) * 100.0)
                .roundToInt()
                .coerceIn(0, 100)
        }
}

data class OverallAwardsSummary(
    val weapons: List<Pair<String, Competitor?>>,
    val hyungs: List<Pair<String, Competitor?>>,
    val sparring: List<Pair<String, Competitor?>>
)

data class SignatureEntry(
    val name: String = "",
    val rank: String = "",
    val number: String = "",
    val role: String = "",
    val signedAt: String = ""
)

data class BracketParticipant(
    val competitor: Competitor?,
    val sourceLabel: String? = null
)

data class BracketSheetBout(
    val boutNumber: Int,
    val blue: BracketParticipant,
    val red: BracketParticipant,
    val actualBye: Boolean,
    val showOnSheet: Boolean
)

data class BracketSheetRound(
    val label: String,
    val slots: List<BracketSheetBout>,
    val slotHeightMultiplier: Int
)
