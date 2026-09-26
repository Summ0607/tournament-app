package com.summ0.tournamentscoringapp

import com.summ0.tournamentscoringapp.engine.CheckInStatus
import com.summ0.tournamentscoringapp.engine.CompetitionEntry
import com.summ0.tournamentscoringapp.engine.CompetitionRegistrationStatus
import com.summ0.tournamentscoringapp.engine.CompetitionType
import com.summ0.tournamentscoringapp.engine.Competitor
import com.summ0.tournamentscoringapp.engine.CompetitorSide
import com.summ0.tournamentscoringapp.engine.DivisionResultPacketBuildRequest
import com.summ0.tournamentscoringapp.engine.DivisionResultPacketBuilder
import com.summ0.tournamentscoringapp.engine.DivisionResultPacketStore
import com.summ0.tournamentscoringapp.engine.HyungDiscipline
import com.summ0.tournamentscoringapp.engine.HyungResult
import com.summ0.tournamentscoringapp.engine.OverallAwardsSummary
import com.summ0.tournamentscoringapp.engine.PlacementFinalizeState
import com.summ0.tournamentscoringapp.engine.SignatureEntry
import com.summ0.tournamentscoringapp.engine.SparringBout
import com.summ0.tournamentscoringapp.engine.SparringBoutResult
import com.summ0.tournamentscoringapp.engine.SparringCompetitorResult
import com.summ0.tournamentscoringapp.engine.SparringOutcome
import com.summ0.tournamentscoringapp.engine.SparringRoundResult
import com.summ0.tournamentscoringapp.engine.SparringTournamentResult
import com.summ0.tournamentscoringapp.engine.SparringWarning
import com.summ0.tournamentscoringapp.engine.SparringWarningType
import com.summ0.tournamentscoringapp.engine.serializeDivisionResultPacket
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DivisionResultPacketBuilderTest {
    @Test
    fun buildsPacketWithSampleShape() {
        val packet = serializeDivisionResultPacket(
            DivisionResultPacketBuilder.build(sampleRequest(), submissionId = SAMPLE_SUBMISSION_ID)
        )
        val sample = JSONObject(SAMPLE_JSON)
        assertSameShape(sample, packet)
    }

    @Test
    fun buildsPacketWithSparringWarningsAndSignatureFields() {
        val packet = serializeDivisionResultPacket(
            DivisionResultPacketBuilder.build(packetWithSparring(), submissionId = SAMPLE_SUBMISSION_ID)
        )

        assertHasKeys(
            packet,
            "schemaVersion",
            "submissionId",
            "eventName",
            "groupId",
            "groupDivisionNumber",
            "groupName",
            "ringId",
            "ringLabel",
            "completedAt",
            "participants",
            "disciplines",
            "overallAwards",
            "signatures",
            "source"
        )

        val signatures = packet.getJSONArray("signatures")
        assertEquals(1, signatures.length())
        assertHasKeys(signatures.getJSONObject(0), "role", "name", "dan", "signedAt")
        assertEquals("Referee", signatures.getJSONObject(0).optString("role"))
        assertEquals(SAMPLE_COMPLETED_AT, signatures.getJSONObject(0).optString("signedAt"))

        val sparring = packet.getJSONObject("disciplines").getJSONObject("sparring")
        assertHasKeys(sparring, "status", "bracketSize", "awards", "rounds")
        val rounds = sparring.getJSONArray("rounds")
        assertEquals(1, rounds.length())
        val boutWithBye = rounds.getJSONObject(0).getJSONArray("bouts").getJSONObject(0)
        assertHasKeys(
            boutWithBye,
            "boutNumber",
            "blueParticipantId",
            "redParticipantId",
            "winnerParticipantId",
            "outcome",
            "bluePoints",
            "redPoints",
            "blueWarnings",
            "redWarnings",
            "elapsedSeconds",
            "blueDisqualified",
            "redDisqualified",
            "warnings"
        )
        assertEquals(JSONObject.NULL, boutWithBye.get("redParticipantId"))
        assertEquals(0, boutWithBye.getJSONArray("warnings").length())

        val warningBout = rounds.getJSONObject(0).getJSONArray("bouts").getJSONObject(1)
        assertEquals(1, warningBout.getJSONArray("warnings").length())
        assertHasKeys(warningBout.getJSONArray("warnings").getJSONObject(0), "side", "type", "reason")
    }

    @Test
    fun tieUsesFinalizedPlacementNotResultOrder() {
        val packet = serializeDivisionResultPacket(
            DivisionResultPacketBuilder.build(tiedHyungsRequest(), submissionId = SAMPLE_SUBMISSION_ID)
        )
        val hyungs = packet.getJSONObject("disciplines").getJSONObject("hyungs").getJSONArray("results")
        assertEquals("1st", hyungs.getJSONObject(0).getString("place"))
        assertEquals("competitor-2", hyungs.getJSONObject(0).getString("participantId"))
        assertEquals("2nd", hyungs.getJSONObject(1).getString("place"))
        assertEquals("competitor-1", hyungs.getJSONObject(1).getString("participantId"))
    }

    @Test
    fun buildsNineCompetitorHyungsPacketWithNonMedalists() {
        val competitors = listOf(
            baseCompetitor("competitor-1", "Alpha", "G2"),
            baseCompetitor("competitor-2", "Bravo", "G2"),
            baseCompetitor("competitor-3", "Charlie", "G2"),
            baseCompetitor("competitor-4", "Delta", "G2"),
            baseCompetitor("competitor-5", "Echo", "G2"),
            baseCompetitor("competitor-6", "Foxtrot", "G2"),
            baseCompetitor("competitor-7", "Golf", "G2"),
            baseCompetitor("competitor-8", "Hotel", "G2"),
            baseCompetitor("competitor-9", "India", "G2")
        )
        val resultsById = mapOf(
            "competitor-7" to 88.4,
            "competitor-2" to 92.6,
            "competitor-4" to 91.9,
            "competitor-1" to 94.8,
            "competitor-5" to 89.3,
            "competitor-8" to 90.1,
            "competitor-3" to 93.7,
            "competitor-6" to 87.5,
            "competitor-9" to 86.2
        )
        val request = sampleRequest().copy(
            competitors = competitors,
            hyungsResults = listOf(
                hyungResultFor(competitors[6], resultsById.getValue("competitor-7")),
                hyungResultFor(competitors[1], resultsById.getValue("competitor-2")),
                hyungResultFor(competitors[3], resultsById.getValue("competitor-4")),
                hyungResultFor(competitors[0], resultsById.getValue("competitor-1")),
                hyungResultFor(competitors[4], resultsById.getValue("competitor-5")),
                hyungResultFor(competitors[7], resultsById.getValue("competitor-8")),
                hyungResultFor(competitors[2], resultsById.getValue("competitor-3")),
                hyungResultFor(competitors[5], resultsById.getValue("competitor-6")),
                hyungResultFor(competitors[8], resultsById.getValue("competitor-9"))
            ),
            hyungsFinalizeState = PlacementFinalizeState(
                labels = mapOf(
                    "competitor-1" to "1st Place",
                    "competitor-2" to "2nd Place",
                    "competitor-3" to "Co-3rd Place",
                    "competitor-4" to "Co-3rd Place"
                ),
                tieBreakDetails = mapOf(
                    "competitor-3" to "TB-1",
                    "competitor-4" to "TB-2"
                )
            ),
            overallAwards = OverallAwardsSummary(
                weapons = emptyList(),
                hyungs = listOf(
                    "1st Place" to competitors[0],
                    "2nd Place" to competitors[1],
                    "Co-3rd Place" to competitors[2],
                    "Co-3rd Place" to competitors[3]
                ),
                sparring = emptyList()
            )
        )

        val packet = serializeDivisionResultPacket(
            DivisionResultPacketBuilder.build(request, submissionId = SAMPLE_SUBMISSION_ID)
        )
        val hyungs = packet.getJSONObject("disciplines").getJSONObject("hyungs").getJSONArray("results")

        assertEquals(9, hyungs.length())
        assertEquals("1st", hyungs.getJSONObject(0).getString("place"))
        assertEquals("2nd", hyungs.getJSONObject(1).getString("place"))
        assertEquals("Co-3rd", hyungs.getJSONObject(2).getString("place"))
        assertEquals("Co-3rd", hyungs.getJSONObject(3).getString("place"))

        val medalPlaces = listOf(
            hyungs.getJSONObject(0).getString("participantId"),
            hyungs.getJSONObject(1).getString("participantId"),
            hyungs.getJSONObject(2).getString("participantId"),
            hyungs.getJSONObject(3).getString("participantId")
        )
        assertEquals(listOf("competitor-1", "competitor-2", "competitor-3", "competitor-4"), medalPlaces)

        val nonMedalPlaces = (4 until hyungs.length()).map { index -> hyungs.getJSONObject(index).getString("place") }
        assertEquals(listOf("", "", "", "", ""), nonMedalPlaces)
        assertEquals(
            listOf("competitor-8", "competitor-5", "competitor-7", "competitor-6", "competitor-9"),
            (4 until hyungs.length()).map { index -> hyungs.getJSONObject(index).getString("participantId") }
        )
    }

    @Test
    fun retryReusesSubmissionIdFromPendingFile() {
        val tempDir = createTempDir()
        try {
            val store = DivisionResultPacketStore(tempDir)
            val packet = serializeDivisionResultPacket(
                DivisionResultPacketBuilder.build(sampleRequest(), submissionId = SAMPLE_SUBMISSION_ID)
            )
            val pendingFile = store.savePending(packet, "http://server", "ring-a-1")
            val loaded = store.loadPacket(pendingFile)
            assertEquals(SAMPLE_SUBMISSION_ID, loaded.optString("submissionId"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun sampleRequest(): DivisionResultPacketBuildRequest {
        val competitor = baseCompetitor(
            id = "competitor-1",
            name = "Alex Example",
            rank = "G2"
        )
        return DivisionResultPacketBuildRequest(
            eventName = "spring-championship",
            groupId = "group-12",
            groupDivisionNumber = 12,
            groupName = "G3-G1 Male 10-13",
            ringId = "ring-a-1",
            ringLabel = "A1",
            completedAt = SAMPLE_COMPLETED_AT,
            competitors = listOf(competitor),
            weaponsResults = listOf(
                HyungResult(
                    competitor = competitor,
                    discipline = HyungDiscipline.WEAPONS,
                    scores = listOf(8.5, 8.7, 8.6),
                    total = 25.8
                )
            ),
            hyungsResults = listOf(
                HyungResult(
                    competitor = competitor,
                    discipline = HyungDiscipline.HYUNGS,
                    scores = listOf(9.0, 9.1, 9.2),
                    total = 27.3
                )
            ),
            sparringTournament = null,
            overallAwards = OverallAwardsSummary(
                weapons = listOf("1st Place" to competitor),
                hyungs = listOf("1st Place" to competitor),
                sparring = emptyList()
            ),
            signatures = emptyList(),
            tabletLabel = "Tablet A1",
            weaponsFinalizeState = PlacementFinalizeState(
                labels = mapOf("competitor-1" to "1st Place"),
                tieBreakDetails = mapOf("competitor-1" to "")
            ),
            hyungsFinalizeState = PlacementFinalizeState(
                labels = mapOf("competitor-1" to "1st Place"),
                tieBreakDetails = mapOf("competitor-1" to "")
            ),
            participantDivisionNumbers = mapOf("competitor-1" to 1201),
            participantRankCodes = mapOf("competitor-1" to "G2")
        )
    }

    private fun packetWithSparring(): DivisionResultPacketBuildRequest {
        val blue = baseCompetitor(
            id = "competitor-blue",
            name = "Blue Example",
            rank = "G2"
        )
        val red = baseCompetitor(
            id = "competitor-red",
            name = "Red Example",
            rank = "G2"
        )
        val normalBout = SparringBout(
            boutNumber = 2,
            competitorA = blue,
            competitorB = red
        )
        return sampleRequest().copy(
            signatures = listOf(
                SignatureEntry(name = "Jordan Smith", dan = "5th Dan", role = "Referee", signedAt = "")
            ),
            sparringTournament = SparringTournamentResult(
                rounds = listOf(
                    SparringRoundResult(
                        roundNumber = 1,
                        boutResults = listOf(
                            SparringBoutResult(
                                bout = SparringBout(
                                    boutNumber = 1,
                                    competitorA = blue,
                                    competitorB = null
                                ),
                                competitorAResult = SparringCompetitorResult(
                                    competitor = blue,
                                    rawPoints = 0,
                                    standardWarningCount = 0,
                                    severeWarningCount = 0,
                                    pointDeductions = 0,
                                    adjustedPoints = 0,
                                    disqualified = false
                                ),
                                competitorBResult = SparringCompetitorResult(
                                    competitor = null,
                                    rawPoints = 0,
                                    standardWarningCount = 0,
                                    severeWarningCount = 0,
                                    pointDeductions = 0,
                                    adjustedPoints = 0,
                                    disqualified = false
                                ),
                                warnings = emptyList(),
                                elapsedSeconds = 0,
                                outcome = SparringOutcome.BYE,
                                winner = blue
                            ),
                            SparringBoutResult(
                                bout = normalBout,
                                competitorAResult = SparringCompetitorResult(
                                    competitor = blue,
                                    rawPoints = 2,
                                    standardWarningCount = 1,
                                    severeWarningCount = 0,
                                    pointDeductions = 0,
                                    adjustedPoints = 2,
                                    disqualified = false
                                ),
                                competitorBResult = SparringCompetitorResult(
                                    competitor = red,
                                    rawPoints = 1,
                                    standardWarningCount = 0,
                                    severeWarningCount = 0,
                                    pointDeductions = 0,
                                    adjustedPoints = 1,
                                    disqualified = false
                                ),
                                warnings = listOf(
                                    SparringWarning(
                                        side = CompetitorSide.A,
                                        type = SparringWarningType.STANDARD,
                                        reason = "Contact"
                                    )
                                ),
                                elapsedSeconds = 90,
                                outcome = SparringOutcome.TIME_EXPIRED,
                                winner = blue
                            )
                        ),
                        winners = listOf(blue, blue)
                    )
                ),
                champion = blue,
                bracketSize = 2
            ),
            overallAwards = OverallAwardsSummary(
                weapons = listOf("1st Place" to blue),
                hyungs = listOf("1st Place" to blue),
                sparring = listOf("1st Place" to blue)
            )
        )
    }

    private fun tiedHyungsRequest(): DivisionResultPacketBuildRequest {
        val first = baseCompetitor(
            id = "competitor-1",
            name = "Alpha",
            rank = "G2"
        )
        val second = baseCompetitor(
            id = "competitor-2",
            name = "Bravo",
            rank = "G2"
        )
        return sampleRequest().copy(
            competitors = listOf(first, second),
            weaponsResults = emptyList(),
            weaponsFinalizeState = null,
            hyungsResults = listOf(
                HyungResult(
                    competitor = second,
                    discipline = HyungDiscipline.HYUNGS,
                    scores = listOf(9.5, 9.4, 9.6),
                    total = 28.5
                ),
                HyungResult(
                    competitor = first,
                    discipline = HyungDiscipline.HYUNGS,
                    scores = listOf(9.1, 9.2, 9.3),
                    total = 27.6
                )
            ),
            hyungsFinalizeState = PlacementFinalizeState(
                labels = mapOf(
                    "competitor-1" to "2nd Place",
                    "competitor-2" to "1st Place"
                ),
                tieBreakDetails = mapOf(
                    "competitor-1" to "JR 1-2",
                    "competitor-2" to "JR 2-1"
                )
            ),
            overallAwards = OverallAwardsSummary(
                weapons = emptyList(),
                hyungs = listOf(
                    "1st Place" to second,
                    "2nd Place" to first
                ),
                sparring = emptyList()
            )
        )
    }

    private fun baseCompetitor(id: String, name: String, rank: String): Competitor {
        return Competitor(
            id = id,
            name = name,
            studio = "Example Studio",
            rank = rank,
            rankLevel = 2,
            age = 11,
            heightInInches = 58,
            checkInStatus = CheckInStatus.CHECKED_IN,
            competitionEntries = mapOf(
                CompetitionType.WEAPONS to CompetitionEntry(CompetitionType.WEAPONS, CompetitionRegistrationStatus.COMPLETED),
                CompetitionType.HYUNGS to CompetitionEntry(CompetitionType.HYUNGS, CompetitionRegistrationStatus.COMPLETED),
                CompetitionType.SPARRING to CompetitionEntry(CompetitionType.SPARRING, CompetitionRegistrationStatus.COMPLETED)
            )
        )
    }

    private fun hyungResultFor(competitor: Competitor, total: Double): HyungResult {
        return HyungResult(
            competitor = competitor,
            discipline = HyungDiscipline.HYUNGS,
            scores = listOf(total - 0.2, total - 0.1, total),
            total = total
        )
    }

    private fun assertHasKeys(json: JSONObject, vararg keys: String) {
        val actualKeys = json.keys().asSequence().toSet()
        keys.forEach { key ->
            assertTrue("Missing key: $key", actualKeys.contains(key))
        }
    }

    private fun assertSameShape(expected: JSONObject, actual: JSONObject) {
        val expectedKeys = expected.keys().asSequence().toSet()
        val actualKeys = actual.keys().asSequence().toSet()
        assertEquals(expectedKeys, actualKeys)
        expectedKeys.forEach { key ->
            assertSameShape(expected.get(key), actual.get(key))
        }
    }

    private fun assertSameShape(expected: Any, actual: Any) {
        when (expected) {
            is JSONObject -> {
                assertEquals(JSONObject::class.java, actual::class.java)
                assertSameShape(expected, actual as JSONObject)
            }
            is JSONArray -> {
                assertEquals(JSONArray::class.java, actual::class.java)
                val actualArray = actual as JSONArray
                if (expected.length() > 0 && actualArray.length() > 0) {
                    assertSameShape(expected.get(0), actualArray.get(0))
                } else {
                    assertEquals(expected.length(), actualArray.length())
                }
            }
            is Number -> assertTrue(actual is Number)
            is String -> assertTrue(actual is String)
            is Boolean -> assertTrue(actual is Boolean)
            else -> assertEquals(expected.javaClass, actual.javaClass)
        }
    }

    private companion object {
        const val SAMPLE_SUBMISSION_ID = "550e8400-e29b-41d4-a716-446655440000"
        const val SAMPLE_COMPLETED_AT = "2026-09-24T18:00:00.000Z"
        val SAMPLE_JSON = """
            {
              "schemaVersion": 1,
              "submissionId": "550e8400-e29b-41d4-a716-446655440000",
              "eventName": "spring-championship",
              "groupId": "group-12",
              "groupDivisionNumber": 12,
              "groupName": "G3-G1 Male 10-13",
              "ringId": "ring-a-1",
              "ringLabel": "A1",
              "completedAt": "2026-09-24T18:00:00.000Z",
              "participants": [
                {
                  "participantId": "competitor-1",
                  "competitionDivisionNumber": 1201,
                  "name": "Alex Example",
                  "studio": "Example Studio",
                  "rankCode": "G2",
                  "rankLabel": "2nd Gup",
                  "age": 11,
                  "heightInInches": 58,
                  "checkInStatus": "CHECKED_IN",
                  "entries": {
                    "weapons": "COMPLETED",
                    "hyungs": "COMPLETED",
                    "sparring": "COMPLETED"
                  }
                }
              ],
              "disciplines": {
                "weapons": {
                  "status": "COMPLETED",
                  "judgeCount": 3,
                  "results": [
                    {
                      "participantId": "competitor-1",
                      "scores": [8.5, 8.7, 8.6],
                      "total": 25.8,
                      "place": "1st",
                      "tieBreakDetail": ""
                    }
                  ]
                },
                "hyungs": {
                  "status": "COMPLETED",
                  "judgeCount": 3,
                  "results": [
                    {
                      "participantId": "competitor-1",
                      "scores": [9, 9.1, 9.2],
                      "total": 27.3,
                      "place": "1st",
                      "tieBreakDetail": ""
                    }
                  ]
                },
                "sparring": {
                  "status": "NOT_HELD",
                  "bracketSize": 0,
                  "awards": [],
                  "rounds": []
                }
              },
              "overallAwards": {
                "weapons": [{"place": "1st", "participantId": "competitor-1"}],
                "hyungs": [{"place": "1st", "participantId": "competitor-1"}],
                "sparring": []
              },
              "signatures": [],
              "source": {
                "client": "TournamentScoringApp",
                "appVersionName": "1.0.0",
                "appVersionCode": 1,
                "tabletLabel": "Tablet A1",
                "createdAt": "2026-09-24T18:00:00.000Z"
              }
            }
        """.trimIndent()
    }
}
