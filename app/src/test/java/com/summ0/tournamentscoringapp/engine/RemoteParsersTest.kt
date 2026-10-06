package com.summ0.tournamentscoringapp.engine

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteParsersTest {
    @Test
    fun parseRingAssignmentStateUsesCanonicalQueuedField() {
        val root = JSONObject(
            """
            {
              "groupDivisionNumber": 12,
              "queuedGroupDivisionNumbers": [13, 14],
              "completedGroupDivisionNumbers": [9]
            }
            """.trimIndent()
        )

        val state = root.parseRingAssignmentState()

        assertEquals(12, state.groupDivisionNumber)
        assertEquals(listOf(13, 14), state.queuedGroupDivisionNumbers)
        assertEquals(listOf(9), state.completedGroupDivisionNumbers)
    }

    @Test
    fun parseRingAssignmentStateIgnoresLegacyQueueTypo() {
        val root = JSONObject(
            """
            {
              "queueGroupDivisionNumbers": [99],
              "completedGroupDivisionNumbers": [9]
            }
            """.trimIndent()
        )

        val state = root.parseRingAssignmentState()

        assertEquals(null, state.groupDivisionNumber)
        assertEquals(emptyList<Int>(), state.queuedGroupDivisionNumbers)
        assertEquals(listOf(9), state.completedGroupDivisionNumbers)
    }
}
