package com.summ0.tournamentscoringapp.engine

import com.summ0.tournamentscoringapp.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

data class DivisionResultPacketBuildRequest(
    val eventName: String,
    val groupId: String,
    val groupDivisionNumber: Int,
    val groupName: String,
    val ringId: String,
    val ringLabel: String,
    val completedAt: String = Instant.now().toString(),
    val competitors: List<Competitor>,
    val weaponsResults: List<HyungResult> = emptyList(),
    val hyungsResults: List<HyungResult> = emptyList(),
    val sparringTournament: SparringTournamentResult? = null,
    val overallAwards: OverallAwardsSummary,
    val signatures: List<SignatureEntry> = emptyList(),
    val tabletLabel: String = "",
    val weaponsFinalizeState: PlacementFinalizeState? = null,
    val hyungsFinalizeState: PlacementFinalizeState? = null,
    val participantDivisionNumbers: Map<String, Int> = emptyMap(),
    val participantRankCodes: Map<String, String> = emptyMap(),
    val weaponsTieBreakDetails: Map<String, String> = emptyMap(),
    val hyungsTieBreakDetails: Map<String, String> = emptyMap()
)

data class DivisionResultPacket(
    val schemaVersion: Int,
    val submissionId: String,
    val eventName: String,
    val groupId: String,
    val groupDivisionNumber: Int,
    val groupName: String,
    val ringId: String,
    val ringLabel: String,
    val completedAt: String,
    val participants: List<DivisionResultPacketParticipant>,
    val disciplines: DivisionResultPacketDisciplines,
    val overallAwards: DivisionResultPacketOverallAwards,
    val signatures: List<SignatureEntry>,
    val source: DivisionResultPacketSource
)

data class DivisionResultPacketSource(
    val client: String,
    val appVersionName: String,
    val appVersionCode: Int,
    val tabletLabel: String,
    val createdAt: String
)

data class DivisionResultPacketParticipant(
    val participantId: String,
    val competitionDivisionNumber: Int,
    val name: String,
    val studio: String,
    val rankCode: String,
    val rankLabel: String,
    val age: Int,
    val heightInInches: Int,
    val checkInStatus: String,
    val entries: DivisionResultPacketParticipantEntries
)

data class DivisionResultPacketParticipantEntries(
    val weapons: String,
    val hyungs: String,
    val sparring: String
)

data class DivisionResultPacketDisciplines(
    val weapons: DivisionResultPacketFormsDiscipline,
    val hyungs: DivisionResultPacketFormsDiscipline,
    val sparring: DivisionResultPacketSparringDiscipline
)

data class DivisionResultPacketFormsDiscipline(
    val status: String,
    val judgeCount: Int,
    val results: List<DivisionResultPacketFormsResult>
)

data class DivisionResultPacketFormsResult(
    val participantId: String,
    val scores: List<Double>,
    val total: Double,
    val place: String,
    val tieBreakDetail: String
)

data class DivisionResultPacketSparringDiscipline(
    val status: String,
    val bracketSize: Int,
    val awards: List<DivisionResultPacketAwardPlacement>,
    val rounds: List<DivisionResultPacketSparringRound>
)

data class DivisionResultPacketSparringRound(
    val roundNumber: Int,
    val bouts: List<DivisionResultPacketSparringBout>
)

data class DivisionResultPacketSparringBout(
    val boutNumber: Int,
    val blueParticipantId: String?,
    val redParticipantId: String?,
    val winnerParticipantId: String?,
    val outcome: String,
    val bluePoints: Int,
    val redPoints: Int,
    val blueWarnings: Int,
    val redWarnings: Int,
    val elapsedSeconds: Int,
    val blueDisqualified: Boolean,
    val redDisqualified: Boolean,
    val warnings: List<DivisionResultPacketSparringWarning>
)

data class DivisionResultPacketSparringWarning(
    val side: String,
    val type: String,
    val reason: String
)

data class DivisionResultPacketAwardPlacement(
    val place: String,
    val participantId: String
)

data class DivisionResultPacketOverallAwards(
    val weapons: List<DivisionResultPacketAwardPlacement>,
    val hyungs: List<DivisionResultPacketAwardPlacement>,
    val sparring: List<DivisionResultPacketAwardPlacement>
)

object DivisionResultPacketBuilder {
    private const val CLIENT_NAME = "TournamentScoringApp"
    private val normalizedAwardPlaces = listOf("1st", "2nd", "Co-3rd", "Co-3rd")
    private val finalizedPlaceOrder = listOf("1st Place", "2nd Place", "Co-3rd Place")

    fun build(request: DivisionResultPacketBuildRequest, submissionId: String = UUID.randomUUID().toString()): DivisionResultPacket {
        require(request.eventName.isNotBlank()) { "eventName is required" }
        require(request.groupId.isNotBlank()) { "groupId is required" }
        require(request.ringId.isNotBlank()) { "ringId is required" }
        require(request.groupName.isNotBlank()) { "groupName is required" }
        require(request.ringLabel.isNotBlank()) { "ringLabel is required" }

        val participants = request.competitors.map { competitor ->
            val divisionNumber = request.participantDivisionNumbers[competitor.id] ?: request.groupDivisionNumber
            val rankCode = request.participantRankCodes[competitor.id] ?: competitor.rank
            DivisionResultPacketParticipant(
                participantId = competitor.id,
                competitionDivisionNumber = divisionNumber,
                name = competitor.name,
                studio = competitor.studio,
                rankCode = rankCode,
                rankLabel = competitor.rank,
                age = competitor.age,
                heightInInches = competitor.heightInInches,
                checkInStatus = competitor.checkInStatus.name,
                entries = DivisionResultPacketParticipantEntries(
                    weapons = competitor.competitionEntries[CompetitionType.WEAPONS]?.status?.name
                        ?: CompetitionRegistrationStatus.REGISTERED.name,
                    hyungs = competitor.competitionEntries[CompetitionType.HYUNGS]?.status?.name
                        ?: CompetitionRegistrationStatus.REGISTERED.name,
                    sparring = competitor.competitionEntries[CompetitionType.SPARRING]?.status?.name
                        ?: CompetitionRegistrationStatus.REGISTERED.name
                )
            )
        }

        val weaponsResults = buildFormsResults(
            request.weaponsResults,
            request.weaponsFinalizeState,
            request.weaponsTieBreakDetails,
            "weapons"
        )
        val hyungsResults = buildFormsResults(
            request.hyungsResults,
            request.hyungsFinalizeState,
            request.hyungsTieBreakDetails,
            "hyungs"
        )
        val sparring = buildSparringDiscipline(
            request.sparringTournament,
            request.overallAwards.sparring
        )

        return DivisionResultPacket(
            schemaVersion = 1,
            submissionId = submissionId,
            eventName = request.eventName,
            groupId = request.groupId,
            groupDivisionNumber = request.groupDivisionNumber,
            groupName = request.groupName,
            ringId = request.ringId,
            ringLabel = request.ringLabel,
            completedAt = request.completedAt,
            participants = participants,
            disciplines = DivisionResultPacketDisciplines(
                weapons = DivisionResultPacketFormsDiscipline(
                    status = disciplineStatus(request.weaponsResults.isNotEmpty()),
                    judgeCount = request.weaponsResults.firstOrNull()?.scores?.size ?: 0,
                    results = weaponsResults
                ),
                hyungs = DivisionResultPacketFormsDiscipline(
                    status = disciplineStatus(request.hyungsResults.isNotEmpty()),
                    judgeCount = request.hyungsResults.firstOrNull()?.scores?.size ?: 0,
                    results = hyungsResults
                ),
                sparring = sparring
            ),
            overallAwards = DivisionResultPacketOverallAwards(
                weapons = request.overallAwards.weapons.mapNotNull { it.toPacketPlacement() },
                hyungs = request.overallAwards.hyungs.mapNotNull { it.toPacketPlacement() },
                sparring = request.overallAwards.sparring.mapNotNull { it.toPacketPlacement() }
            ),
            signatures = request.signatures.filter { it.name.isNotBlank() || it.dan.isNotBlank() },
            source = DivisionResultPacketSource(
                client = CLIENT_NAME,
                appVersionName = BuildConfig.VERSION_NAME,
                appVersionCode = BuildConfig.VERSION_CODE,
                tabletLabel = request.tabletLabel,
                createdAt = request.completedAt
            )
        )
    }

    private fun buildFormsResults(
        results: List<HyungResult>,
        finalizeState: PlacementFinalizeState?,
        tieBreakDetails: Map<String, String>,
        disciplineName: String
    ): List<DivisionResultPacketFormsResult> {
        if (results.isNotEmpty() && finalizeState == null) {
            error("Finalized placements are required for completed $disciplineName results.")
        }

        if (results.isNotEmpty() && !finalizeState?.message.isNullOrBlank()) {
            error("Cannot build $disciplineName packet results while a tie-break remains unresolved.")
        }

        val labels = finalizeState?.labels.orEmpty()
        if (results.isNotEmpty() && labels.isEmpty()) {
            error("Finalized placements are required for completed $disciplineName results.")
        }

        return results.map { result ->
            val placeLabel = labels[result.competitor.id].orEmpty()
            val packetPlace = normalizedPacketPlaceLabel(placeLabel)
            val resolvedPlace = when (packetPlace) {
                "1st", "2nd", "Co-3rd" -> packetPlace
                else -> placeLabel
            }
            DivisionResultPacketFormsResult(
                participantId = result.competitor.id,
                scores = result.scores,
                total = result.total,
                place = resolvedPlace,
                tieBreakDetail = finalizeState!!.tieBreakDetails[result.competitor.id]
                    ?: tieBreakDetails[result.competitor.id].orEmpty()
            )
        }.sortedWith(
            compareBy<DivisionResultPacketFormsResult> { placePriority(it.place) }
                .thenByDescending { it.total }
                .thenBy { it.participantId }
        )
    }

    private fun buildSparringDiscipline(
        tournament: SparringTournamentResult?,
        awards: List<Pair<String, Competitor?>>
    ): DivisionResultPacketSparringDiscipline {
        if (tournament == null || tournament.rounds.isEmpty()) {
            return DivisionResultPacketSparringDiscipline(
                status = "NOT_HELD",
                bracketSize = 0,
                awards = awards.mapNotNull { it.toPacketPlacement() },
                rounds = emptyList()
            )
        }

        return DivisionResultPacketSparringDiscipline(
            status = "COMPLETED",
            bracketSize = tournament.bracketSize,
            awards = awards.mapNotNull { it.toPacketPlacement() },
            rounds = tournament.rounds.map { round ->
                DivisionResultPacketSparringRound(
                    roundNumber = round.roundNumber,
                    bouts = round.boutResults.map { boutResult ->
                        DivisionResultPacketSparringBout(
                            boutNumber = boutResult.bout.boutNumber,
                            blueParticipantId = boutResult.bout.competitorA?.id,
                            redParticipantId = boutResult.bout.competitorB?.id,
                            winnerParticipantId = boutResult.winner?.id,
                            outcome = boutResult.outcome.name,
                            bluePoints = boutResult.competitorAResult.adjustedPoints,
                            redPoints = boutResult.competitorBResult.adjustedPoints,
                            blueWarnings = boutResult.competitorAResult.standardWarningCount + boutResult.competitorAResult.severeWarningCount,
                            redWarnings = boutResult.competitorBResult.standardWarningCount + boutResult.competitorBResult.severeWarningCount,
                            elapsedSeconds = boutResult.elapsedSeconds,
                            blueDisqualified = boutResult.competitorAResult.disqualified,
                            redDisqualified = boutResult.competitorBResult.disqualified,
                            warnings = boutResult.warnings.map { warning ->
                                DivisionResultPacketSparringWarning(
                                    side = warning.side.name,
                                    type = warning.type.name,
                                    reason = warning.reason
                                )
                            }
                        )
                    }
                )
            }
        )
    }

    private fun disciplineStatus(hasResults: Boolean): String = if (hasResults) "COMPLETED" else "NOT_HELD"

    private fun normalizedPacketPlaceLabel(place: String): String {
        return when (place) {
            "1st Place" -> "1st"
            "2nd Place" -> "2nd"
            "Co-3rd Place" -> "Co-3rd"
            "1st" -> "1st"
            "2nd" -> "2nd"
            "Co-3rd" -> "Co-3rd"
            else -> place
        }
    }

    private fun placePriority(place: String): Int {
        return when (normalizedPacketPlaceLabel(place)) {
            "1st" -> 0
            "2nd" -> 1
            "Co-3rd" -> 2
            else -> Int.MAX_VALUE
        }
    }

    private fun Pair<String, Competitor?>.toPacketPlacement(): DivisionResultPacketAwardPlacement? {
        val competitor = second ?: return null
        return DivisionResultPacketAwardPlacement(
            place = normalizedPacketPlaceLabel(first),
            participantId = competitor.id
        )
    }
}

internal fun SignatureEntry.toPacketJson(): JSONObject {
    return JSONObject().apply {
        put("name", name)
        put("dan", dan)
        put("role", role)
        put("signedAt", signedAt.ifBlank { "" })
    }
}

fun serializeDivisionResultPacket(packet: DivisionResultPacket): JSONObject {
    return JSONObject().apply {
        put("schemaVersion", packet.schemaVersion)
        put("submissionId", packet.submissionId)
        put("eventName", packet.eventName)
        put("groupId", packet.groupId)
        put("groupDivisionNumber", packet.groupDivisionNumber)
        put("groupName", packet.groupName)
        put("ringId", packet.ringId)
        put("ringLabel", packet.ringLabel)
        put("completedAt", packet.completedAt)
        put("participants", JSONArray().apply {
            packet.participants.forEach { participant ->
                put(JSONObject().apply {
                    put("participantId", participant.participantId)
                    put("competitionDivisionNumber", participant.competitionDivisionNumber)
                    put("name", participant.name)
                    put("studio", participant.studio)
                    put("rankCode", participant.rankCode)
                    put("rankLabel", participant.rankLabel)
                    put("age", participant.age)
                    put("heightInInches", participant.heightInInches)
                    put("checkInStatus", participant.checkInStatus)
                    put(
                        "entries",
                        JSONObject().apply {
                            put("weapons", participant.entries.weapons)
                            put("hyungs", participant.entries.hyungs)
                            put("sparring", participant.entries.sparring)
                        }
                    )
                })
            }
        })
        put(
            "disciplines",
            JSONObject().apply {
                put("weapons", packet.disciplines.weapons.toJson())
                put("hyungs", packet.disciplines.hyungs.toJson())
                put("sparring", packet.disciplines.sparring.toJson())
            }
        )
        put(
            "overallAwards",
            JSONObject().apply {
                put("weapons", JSONArray().apply {
                    packet.overallAwards.weapons.forEach { award ->
                        put(award.toJson())
                    }
                })
                put("hyungs", JSONArray().apply {
                    packet.overallAwards.hyungs.forEach { award ->
                        put(award.toJson())
                    }
                })
                put("sparring", JSONArray().apply {
                    packet.overallAwards.sparring.forEach { award ->
                        put(award.toJson())
                    }
                })
            }
        )
        put("signatures", JSONArray().apply {
            packet.signatures.forEach { signature ->
                put(
                    signature.toPacketJson().apply {
                        put("signedAt", signature.signedAt.ifBlank { packet.completedAt })
                    }
                )
            }
        })
        put(
            "source",
            JSONObject().apply {
                put("client", packet.source.client)
                put("appVersionName", packet.source.appVersionName)
                put("appVersionCode", packet.source.appVersionCode)
                put("tabletLabel", packet.source.tabletLabel)
                put("createdAt", packet.source.createdAt)
            }
        )
    }
}

private fun DivisionResultPacketFormsDiscipline.toJson(): JSONObject {
    return JSONObject().apply {
        put("status", status)
        put("judgeCount", judgeCount)
        put("results", JSONArray().apply {
            results.forEach { result ->
                put(result.toJson())
            }
        })
    }
}

private fun DivisionResultPacketFormsResult.toJson(): JSONObject {
    return JSONObject().apply {
        put("participantId", participantId)
        put("scores", JSONArray().apply {
            scores.forEach { put(it) }
        })
        put("total", total)
        put("place", place)
        put("tieBreakDetail", tieBreakDetail)
    }
}

private fun DivisionResultPacketSparringDiscipline.toJson(): JSONObject {
    return JSONObject().apply {
        put("status", status)
        put("bracketSize", bracketSize)
        put("awards", JSONArray().apply {
            awards.forEach { award ->
                put(award.toJson())
            }
        })
        put("rounds", JSONArray().apply {
            rounds.forEach { round ->
                put(round.toJson())
            }
        })
    }
}

private fun DivisionResultPacketSparringRound.toJson(): JSONObject {
    return JSONObject().apply {
        put("roundNumber", roundNumber)
        put("bouts", JSONArray().apply {
            bouts.forEach { bout ->
                put(bout.toJson())
            }
        })
    }
}

private fun DivisionResultPacketSparringBout.toJson(): JSONObject {
    return JSONObject().apply {
        put("boutNumber", boutNumber)
        put("blueParticipantId", blueParticipantId ?: JSONObject.NULL)
        put("redParticipantId", redParticipantId ?: JSONObject.NULL)
        put("winnerParticipantId", winnerParticipantId ?: JSONObject.NULL)
        put("outcome", outcome)
        put("bluePoints", bluePoints)
        put("redPoints", redPoints)
        put("blueWarnings", blueWarnings)
        put("redWarnings", redWarnings)
        put("elapsedSeconds", elapsedSeconds)
        put("blueDisqualified", blueDisqualified)
        put("redDisqualified", redDisqualified)
        put("warnings", JSONArray().apply {
            warnings.forEach { warning ->
                put(warning.toJson())
            }
        })
    }
}

private fun DivisionResultPacketSparringWarning.toJson(): JSONObject {
    return JSONObject().apply {
        put("side", side)
        put("type", type)
        put("reason", reason)
    }
}

private fun DivisionResultPacketAwardPlacement.toJson(): JSONObject {
    return JSONObject().apply {
        put("place", place)
        put("participantId", participantId)
    }
}
