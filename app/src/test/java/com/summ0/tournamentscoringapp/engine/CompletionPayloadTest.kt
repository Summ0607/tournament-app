package com.summ0.tournamentscoringapp.engine

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class CompletionPayloadTest {
    @Test
    fun buildFrozenCompletionSubmissionPayloadCopiesPacketAndSnapshot() {
        val packetJson = JSONObject(
            """
            {
              "submissionId": "submission-1",
              "groupId": "group-12",
              "groupDivisionNumber": 12
            }
            """.trimIndent()
        )
        val snapshotJson = JSONObject(
            """
            {
              "currentPhase": "awards",
              "checkInCount": 7,
              "checkInTotal": 10,
              "phaseCompletedCount": 3,
              "phaseTotalCount": 3,
              "phaseProgress": 100,
              "ringElapsed": "0:15",
              "ringEstimated": "0:30",
              "ringPacePercent": 50,
              "telemetry": {
                "checkInCount": 7,
                "checkInTotal": 10,
                "phaseProgress": 100,
                "ringElapsed": "0:15",
                "ringEstimated": "0:30",
                "ringPacePercent": 83
              },
              "phaseHistory": [
                {
                  "name": "check-in",
                  "startTime": "2026-10-04T12:00:00Z",
                  "endTime": "2026-10-04T12:05:00Z",
                  "elapsed": "00:05:00",
                  "estimated": "00:10:00"
                }
              ]
            }
            """.trimIndent()
        )

        val payload = buildFrozenCompletionSubmissionPayload(packetJson, snapshotJson)

        packetJson.put("groupId", "mutated-group")
        snapshotJson.getJSONObject("telemetry").put("checkInCount", 99)

        assertEquals("group-12", payload.getString("groupId"))
        val completionSnapshot = payload.getJSONObject("completionSnapshot")
        assertEquals("awards", completionSnapshot.getString("currentPhase"))
        assertEquals(7, completionSnapshot.getInt("checkInCount"))
        assertEquals(10, completionSnapshot.getInt("checkInTotal"))
        assertEquals(3, completionSnapshot.getInt("phaseCompletedCount"))
        assertEquals(3, completionSnapshot.getInt("phaseTotalCount"))
        assertEquals(100, completionSnapshot.getInt("phaseProgress"))
        assertEquals("0:15", completionSnapshot.getString("ringElapsed"))
        assertEquals("0:30", completionSnapshot.getString("ringEstimated"))
        assertEquals(50, completionSnapshot.getInt("ringPacePercent"))
        assertEquals(7, completionSnapshot.getJSONObject("telemetry").getInt("checkInCount"))
        assertEquals(10, completionSnapshot.getJSONObject("telemetry").getInt("checkInTotal"))
        assertEquals(100, completionSnapshot.getJSONObject("telemetry").getInt("phaseProgress"))
        assertEquals("0:15", completionSnapshot.getJSONObject("telemetry").getString("ringElapsed"))
        assertEquals("0:30", completionSnapshot.getJSONObject("telemetry").getString("ringEstimated"))
        assertEquals(83, completionSnapshot.getJSONObject("telemetry").getInt("ringPacePercent"))
        val packetBlock = completionSnapshot.getJSONObject("packetBlock")
        assertEquals("submission-1", packetBlock.getString("submissionId"))
        assertEquals(false, packetBlock.has("schemaVersion"))
        assertEquals(false, packetBlock.has("eventName"))
        assertEquals(false, packetBlock.has("groupId"))
        assertEquals(false, packetBlock.has("groupDivisionNumber"))
        assertEquals(false, packetBlock.has("groupName"))
        assertEquals(false, packetBlock.has("ringId"))
        assertEquals(false, packetBlock.has("ringLabel"))
        assertEquals(false, packetBlock.has("completedAt"))
        assertEquals(1, completionSnapshot.getJSONArray("phaseHistory").length())
    }
}
