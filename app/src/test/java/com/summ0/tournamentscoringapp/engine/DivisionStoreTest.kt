package com.summ0.tournamentscoringapp.engine

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DivisionStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun sampleGroup(divisionNumber: Int, competitorCount: Int = 4): RemoteGroup {
        return RemoteGroup(
            groupId = "group-$divisionNumber",
            groupDivisionNumber = divisionNumber,
            groupDivisionName = "Youth Gup $divisionNumber",
            name = "Youth Gup $divisionNumber",
            ageRange = 8..11,
            rankRange = 2..4,
            rankRangeLabel = "7th Gup - 5th Gup",
            matNumber = 1,
            competitors = (1..competitorCount).map { index ->
                RemoteCompetitor(
                    id = "$divisionNumber-$index",
                    name = "Competitor $index",
                    studio = "Studio ${index % 2}",
                    rank = "7th Gup",
                    age = 9,
                    heightInInches = 52 + index
                )
            }
        )
    }

    @Test
    fun storedDivisionRoundTripsLosslessly() {
        val group = sampleGroup(20)
        val parsed = parseStoredDivision(serializeStoredDivision(group, "network"))
        assertEquals(group, parsed)
    }

    @Test
    fun multipleDivisionsAreCatalogedAndReloadableAfterRestart() {
        val store = DivisionStore(folder.root)
        store.saveDivision(sampleGroup(20), "network")
        store.saveDivision(sampleGroup(21, 6), "qr")
        store.saveDivision(sampleGroup(22, 3), "network")
        store.setActiveAnchor("21")

        // A new instance over the same directory simulates an app/device restart.
        val reopened = DivisionStore(folder.root)
        val records = reopened.listDivisions()
        assertEquals(listOf("20", "21", "22"), records.map { it.anchor })
        assertEquals(listOf(4, 6, 3), records.map { it.competitorCount })
        assertEquals("qr", reopened.record("21")?.source)
        assertEquals(DivisionRecordStatus.LOADED, reopened.record("21")?.status)
        assertEquals("21", reopened.activeAnchor())
        assertEquals(sampleGroup(21, 6), reopened.loadDivision("21"))
    }

    @Test
    fun sessionSnapshotsSurviveRestartAndAdvanceStatus() {
        val store = DivisionStore(folder.root)
        store.saveDivision(sampleGroup(20), "network")
        assertFalse(store.hasSession("20"))

        val session = JSONObject().put("schemaVersion", 1).put("controlBoardPhase", "weapons")
        assertTrue(store.saveSession("20", session))
        assertTrue(store.hasSession("20"))
        assertEquals(DivisionRecordStatus.IN_PROGRESS, store.record("20")?.status)

        val reopened = DivisionStore(folder.root)
        assertEquals("weapons", reopened.loadSession("20")?.optString("controlBoardPhase"))
        assertEquals(DivisionRecordStatus.IN_PROGRESS, reopened.record("20")?.status)
    }

    @Test
    fun ringJudgePlansAndHiddenPinVerifierSurviveRestartAndRemainScoped() {
        val plan = JSONObject()
            .put("type", "tournament-judge-plan")
            .put("schemaVersion", 1)
            .put("eventName", "Event A")
            .put("ringId", "ring-a-1")
            .put("phase", "P1")
            .put("assignmentRevision", "revision-1")
            .put("sourceRevision", "source-1")
            .put("replacementPinConfigured", true)
            .put("replacementPinVerifier", JSONObject()
                .put("algorithm", "SHA-256-salted-v1")
                .put("salt", "a".repeat(32))
                .put("digest", "b".repeat(64)))
            .put("assigned", true)
            .put("positions", org.json.JSONObject()
                .put("center", org.json.JSONArray()
                    .put(JSONObject().put("associationId", "ID1").put("name", "Center Judge"))))

        assertTrue(DivisionStore(folder.root).saveJudgePlan("Event A", "ring-a-1", "P1", plan))
        val reopened = DivisionStore(folder.root)
        assertEquals(plan.toString(), reopened.loadJudgePlan("Event A", "ring-a-1", "P1")?.toString())
        assertNull(reopened.loadJudgePlan("Event A", "ring-a-1", "P2"))
        assertNull(reopened.loadJudgePlan("Event B", "ring-a-1", "P1"))
        assertTrue(reopened.deleteJudgePlan("Event A", "ring-a-1", "P1"))
        assertNull(reopened.loadJudgePlan("Event A", "ring-a-1", "P1"))
    }

    @Test
    fun ringPhaseAttendanceSurvivesRestartAndIsScopedToPlanRevision() {
        val store = DivisionStore(folder.root)
        store.saveDivision(sampleGroup(20), "network")
        val plan = JSONObject()
            .put("type", "tournament-judge-plan")
            .put("schemaVersion", 1)
            .put("eventName", "Event A")
            .put("ringId", "ring-a-1")
            .put("phase", "P1")
            .put("assignmentRevision", "revision-1")
            .put("sourceRevision", "source-1")
            .put("assigned", true)
            .put("positions", JSONObject()
                .put("center", org.json.JSONArray()
                    .put(JSONObject().put("associationId", "ID1").put("name", "Center Judge")))
                .put("corner", org.json.JSONArray()
                    .put(JSONObject().put("associationId", "ID2").put("name", "Corner Judge"))))
        val initial = newJudgeAttendance(plan)
        assertTrue(initial.officials.all { it.status == JudgeAttendanceStatus.UNCONFIRMED })

        val updated = updateJudgeAttendance(initial, "id1", JudgeAttendanceStatus.PRESENT, "2026-11-11T12:00:00Z")
            .let { updateJudgeAttendance(it, "ID2", JudgeAttendanceStatus.ABSENT, "2026-11-11T12:01:00Z") }
        assertTrue(store.saveJudgeAttendance(plan, updated))
        val reopened = DivisionStore(folder.root).loadJudgeAttendance(plan)
        assertNotNull(reopened)
        assertEquals(
            listOf(JudgeAttendanceStatus.PRESENT, JudgeAttendanceStatus.ABSENT),
            reopened?.officials?.map { it.status }
        )
        assertEquals("2026-11-11T12:00:00Z", reopened?.officials?.first()?.markedAt)

        val revisedPlan = JSONObject(plan.toString()).put("assignmentRevision", "revision-2")
        assertNull(DivisionStore(folder.root).loadJudgeAttendance(revisedPlan))
        assertThrows(IllegalArgumentException::class.java) {
            updateJudgeAttendance(updated, "ID3", JudgeAttendanceStatus.PRESENT)
        }
        assertThrows(IllegalArgumentException::class.java) {
            store.prepareServicePacket("ring-a-1", "P1", "Event A")
        }
    }

    @Test
    fun presentJudgesCreateServiceBeforeADivisionAndReplacementsCarryForwardAfterRestart() {
        val plan = JSONObject()
            .put("type", "tournament-judge-plan").put("schemaVersion", 1)
            .put("eventName", "Event A").put("ringId", "ring-a-1").put("phase", "P1")
            .put("assignmentRevision", "revision-1").put("assigned", true)
            .put("positions", JSONObject()
                .put("center", org.json.JSONArray().put(JSONObject()
                    .put("associationId", "D10000").put("name", "Center Judge").put("rank", "D2")))
                .put("sktk", org.json.JSONArray().put(JSONObject()
                    .put("associationId", "G20000").put("name", "Scorekeeper").put("rank", "G1"))))
        val store = DivisionStore(folder.root)
        val initial = newJudgeAttendance(plan)
        val present = initial.officials.fold(initial) { attendance, official ->
            updateJudgeAttendance(attendance, official.associationId, JudgeAttendanceStatus.PRESENT)
        }
        val service = store.loadJudgeService(plan, present, "")
        store.saveJudgeCheckIn(plan, present, service)
        val reopened = DivisionStore(folder.root)
        assertEquals(listOf("D2", "G1"), reopened.loadJudgeAttendance(plan)!!.officials.map { it.rank })
        assertEquals(ServiceRole.SKTK, service.roleOf("G20000"))
        assertEquals(service, reopened.loadJudgeService(plan, present, ""))
        assertEquals(service, reopened.loadJudgeService(plan, present, "")
            .withJudgePresence(present.officials.first(), true))

        reopened.saveDivision(sampleGroup(20), "network")
        val first = reopened.loadJudgeService(plan, present, "20")
        assertTrue(first.history.isEmpty())
        assertEquals(service.seats, first.seats)
        reopened.saveJudgeCheckIn(plan, present, first)
        val replaced = first.replace(ServiceRole.CENTER, "D10000",
            ServiceJudge("D30000", "Relief Judge", "D3"), "revision-1", true)
        assertThrows(IllegalArgumentException::class.java) {
            replaced.withJudgePresence(present.officials.first(), true)
        }
        val afterReplacement = attendanceFromService(present, replaced)
        assertEquals(JudgeAttendanceStatus.ABSENT, afterReplacement.officials.first().status)
        reopened.saveJudgeCheckIn(plan, afterReplacement, replaced)

        val restart = DivisionStore(folder.root)
        restart.saveDivision(sampleGroup(21), "network")
        val next = restart.loadJudgeService(plan, restart.loadJudgeAttendance(plan)!!, "21")
        assertEquals("D30000", next.seats[ServiceRole.CENTER]!!.single().associationId)
        assertNull(next.roleOf("D10000"))
        assertTrue(next.history.isEmpty())
        restart.saveJudgeCheckIn(plan, attendanceFromService(afterReplacement, next), next)
        val packet = restart.prepareServicePacket("ring-a-1", "P1", "Event A").packet
        val divisions = packet.getJSONArray("divisions")
        assertEquals(2, divisions.length())
        val byAnchor = (0 until divisions.length()).associate {
            divisions.getJSONObject(it).getString("groupAnchor") to divisions.getJSONObject(it)
        }
        assertEquals("D10000", byAnchor.getValue("20").getJSONArray("history")
            .getJSONObject(0).getString("previousJudgeAssociationId"))
        assertEquals(0, byAnchor.getValue("21").getJSONArray("history").length())
        assertNull(restart.loadJudgeAttendance(JSONObject(plan.toString()).put("phase", "P2")))
    }

    @Test
    fun uncheckedJudgesDoNotCreateServiceAndLeavingPreservesPriorServiceHistory() {
        val plan = JSONObject()
            .put("type", "tournament-judge-plan").put("schemaVersion", 1)
            .put("eventName", "Event A").put("ringId", "ring-a-1").put("phase", "P1")
            .put("assignmentRevision", "revision-1").put("assigned", true)
            .put("positions", JSONObject().put("corner", org.json.JSONArray().put(JSONObject()
                .put("associationId", "D10000").put("name", "Corner Judge").put("rank", "D2"))))
        val store = DivisionStore(folder.root)
        store.saveDivision(sampleGroup(20), "network")
        val initial = newJudgeAttendance(plan)
        val empty = store.loadJudgeService(plan, initial, "20")
        store.saveJudgeCheckIn(plan, initial, empty)
        assertThrows(IllegalArgumentException::class.java) {
            store.prepareServicePacket("ring-a-1", "P1", "Event A")
        }
        val official = initial.officials.single()
        val serving = empty.withJudgePresence(official, true, "2026-11-14T12:00:00Z")
        val left = serving.withJudgePresence(official, false, "2026-11-14T12:30:00Z")
        store.saveJudgeCheckIn(plan, attendanceFromService(initial, left), left)
        assertTrue(left.seats.isEmpty())
        assertEquals(listOf(ServiceAction.SEATED, ServiceAction.REPLACED), left.history.map { it.action })
        assertEquals("D10000", left.history.last().previousJudgeAssociationId)
        assertEquals(left, DivisionStore(folder.root).loadJudgeService(plan, initial, "20"))
        val packet = store.prepareServicePacket("ring-a-1", "P1", "Event A").packet
        assertEquals(2, packet.getJSONArray("divisions").getJSONObject(0).getJSONArray("history").length())
        assertThrows(IllegalArgumentException::class.java) {
            store.saveJudgeCheckIn(plan, initial, serving.copy(ringId = "ring-b-1"))
        }
        assertThrows(java.io.IOException::class.java) {
            store.saveJudgeCheckIn(plan, initial, serving.copy(groupAnchor = "missing"))
        }
    }

    @Test
    fun olderAttendanceWithoutRankRestoresThePlannedRank() {
        val plan = JSONObject()
            .put("type", "tournament-judge-plan").put("schemaVersion", 1)
            .put("eventName", "Event A").put("ringId", "ring-a-1").put("phase", "P1")
            .put("assignmentRevision", "revision-1").put("assigned", true)
            .put("positions", JSONObject().put("corner", org.json.JSONArray().put(JSONObject()
                .put("associationId", "D10000").put("name", "Corner Judge").put("rank", "D2"))))
        val attendance = newJudgeAttendance(plan)
        val legacy = serializeJudgeAttendance(attendance)
        legacy.getJSONArray("officials").getJSONObject(0).remove("rank")
        assertEquals("D2", parseJudgeAttendance(legacy, plan).officials.single().rank)
        val store = DivisionStore(folder.root)
        store.saveDivision(sampleGroup(20), "network")
        val present = updateJudgeAttendance(parseJudgeAttendance(legacy, plan), "D10000", JudgeAttendanceStatus.PRESENT)
        store.saveJudgeAttendance(plan, present)
        store.saveService("20", serializeRingPhaseService(RingPhaseService("ring-a-1", "P1", "20")))
        val restored = store.loadJudgeService(plan, present, "20")
        assertEquals("D10000", restored.seats[ServiceRole.CORNER]!!.single().associationId)
    }

    @Test
    fun statusLifecycleTracksCompletionAndSubmission() {
        val store = DivisionStore(folder.root)
        store.saveDivision(sampleGroup(20), "network")
        store.saveSession("20", JSONObject().put("schemaVersion", 1))
        store.markCompleted("20")
        assertEquals(DivisionRecordStatus.COMPLETED, store.record("20")?.status)
        // Later session writes (e.g., retry edits) never regress a completed division.
        store.saveSession("20", JSONObject().put("schemaVersion", 1))
        assertEquals(DivisionRecordStatus.COMPLETED, store.record("20")?.status)
        store.markSubmitted("20", "submission-uuid")
        assertEquals(DivisionRecordStatus.SUBMITTED, store.record("20")?.status)
        assertEquals("submission-uuid", store.record("20")?.submissionId)
        store.markAlreadyReceived("20", "duplicate-submission-uuid")
        assertEquals(DivisionRecordStatus.ALREADY_RECEIVED, store.record("20")?.status)
        assertEquals("duplicate-submission-uuid", store.record("20")?.submissionId)
    }

    @Test
    fun reloadingADivisionDiscardsItsPreviousSession() {
        val store = DivisionStore(folder.root)
        store.saveDivision(sampleGroup(20), "network")
        store.saveSession("20", JSONObject().put("schemaVersion", 1))
        store.markCompleted("20")

        store.saveDivision(sampleGroup(20), "network")
        assertNull(store.loadSession("20"))
        assertEquals(DivisionRecordStatus.LOADED, store.record("20")?.status)
    }

    @Test
    fun sessionsForUnknownDivisionsAreRejected() {
        val store = DivisionStore(folder.root)
        assertFalse(store.saveSession("99", JSONObject()))
        assertNull(store.loadDivision("99"))
        assertNull(store.loadSession("99"))
    }

    @Test
    fun serviceRecordsPersistPerDivisionAndSurviveRestart() {
        val store = DivisionStore(folder.root)
        store.saveDivision(sampleGroup(20), "qr")
        assertFalse(store.hasService("20"))
        assertFalse(store.saveService("99", JSONObject()))

        val service = JSONObject().put("schemaVersion", 1).put("phase", "P1")
        assertTrue(store.saveService("20", service))
        assertTrue(store.hasService("20"))

        val reopened = DivisionStore(folder.root)
        assertEquals("P1", reopened.loadService("20")?.optString("phase"))
        // A service record never changes the division's lifecycle status.
        assertEquals(DivisionRecordStatus.LOADED, reopened.record("20")?.status)
    }

    @Test
    fun ringPhaseServiceSummaryShowsSavedDivisionsAndTransferableEntries() {
        val store = DivisionStore(folder.root)
        store.saveDivision(sampleGroup(20), "qr")
        store.saveDivision(sampleGroup(21), "qr")
        store.saveDivision(sampleGroup(22), "qr")
        store.saveService(
            "20",
            serializeRingPhaseService(
                RingPhaseService("ring-a-1", "P1", "20")
                    .seat(ServiceRole.CENTER, ServiceJudge("D10000", "Center Judge", "D3"), at = "t1")
            )
        )
        store.saveService("21", serializeRingPhaseService(RingPhaseService("ring-a-1", "P1", "21")))
        store.saveService(
            "21",
            serializeRingPhaseService(
                RingPhaseService("ring-a-1", "P2", "21")
                    .seat(ServiceRole.CORNER, ServiceJudge("D20000", "Corner Judge", "D2"), at = "t2")
            )
        )
        store.saveService("22", serializeRingPhaseService(RingPhaseService("ring-b-1", "P1", "22")))

        val summary = DivisionStore(folder.root).summarizeRingPhaseService("ring-a-1", "P1")
        assertEquals(listOf("20", "21"), summary.map { it.groupAnchor })
        assertEquals(listOf(1, 0), summary.map { it.seatedJudgeCount })
        assertEquals(listOf(true, false), summary.map { it.hasTransferableService })
        assertEquals(listOf("21"), store.summarizeRingPhaseService("ring-a-1", "P2").map { it.groupAnchor })
        assertEquals(listOf("22"), store.summarizeRingPhaseService("ring-b-1", "P1").map { it.groupAnchor })
    }

    @Test
    fun ringPhaseServicePacketIsDurableAndReusesItsReceiptIdentity() {
        val store = DivisionStore(folder.root)
        store.saveDivision(sampleGroup(20), "qr")
        store.saveDivision(sampleGroup(21), "network")
        val firstService = RingPhaseService("ring-a-1", "P1", "20")
            .seat(ServiceRole.CENTER, ServiceJudge("ID1", "Center Judge", "D5"), at = "t0")
            .replace(
                ServiceRole.CENTER,
                "ID1",
                ServiceJudge("ID2", "Relief Judge", "D4"),
                assignmentRevision = "revision-1",
                replacementPinVerified = true,
                at = "t1"
            )
        val secondService = RingPhaseService("ring-a-1", "P1", "21")
            .seat(ServiceRole.SKTK, ServiceJudge("ID3", "SK/TK", "D1"), at = "t2")
        store.saveService("20", serializeRingPhaseService(firstService))
        store.saveService("21", serializeRingPhaseService(secondService))

        val prepared = store.prepareServicePacket("ring-a-1", "P1", "Event A")
        assertEquals(2, prepared.packet.getJSONArray("divisions").length())
        val submissionId = prepared.packet.getString("submissionId")
        val reopened = DivisionStore(folder.root)
        val retry = reopened.prepareServicePacket("ring-a-1", "P1", "Event A")
        assertEquals(submissionId, retry.packet.getString("submissionId"))
        assertEquals(prepared.packet.toString(), retry.packet.toString())

        val receipt = JSONObject().put("accepted", true).put("submissionId", submissionId)
            .put("serverRecordId", "server-record").put("receivedAt", "t3")
        assertTrue(reopened.acknowledgeServicePacket(submissionId, receipt))
        val acknowledged = DivisionStore(folder.root).prepareServicePacket("ring-a-1", "P1", "Event A")
        assertEquals("server-record", acknowledged.acknowledgement?.optString("serverRecordId"))
        val nextEventPacket = DivisionStore(folder.root).prepareServicePacket("ring-a-1", "P1", "Event B")
        assertNotNull(nextEventPacket.packet)
        assertTrue(nextEventPacket.packet.getString("submissionId") != submissionId)
    }

    @Test
    fun removingADivisionClearsItsFilesAndActiveAnchor() {
        val store = DivisionStore(folder.root)
        store.saveDivision(sampleGroup(20), "network")
        store.setActiveAnchor("20")
        store.removeDivision("20")
        assertNull(store.activeAnchor())
        assertNull(store.loadDivision("20"))
        assertTrue(store.listDivisions().isEmpty())
    }

    @Test
    fun anchorsFallBackToSanitizedGroupIds() {
        val group = sampleGroup(20).copy(groupDivisionNumber = null, groupId = "legacy/group 7")
        assertEquals("legacy_group_7", divisionAnchor(group))
        val store = DivisionStore(folder.root)
        val record = store.saveDivision(group, "network")
        assertEquals("legacy_group_7", record.anchor)
        assertNotNull(store.loadDivision("legacy_group_7"))
    }

    @Test
    fun corruptIndexOrDivisionFilesDegradeToEmptyInsteadOfCrashing() {
        val store = DivisionStore(folder.root)
        store.saveDivision(sampleGroup(20), "network")
        folder.root.resolve("divisions/index.json").writeText("not json")
        folder.root.resolve("divisions/20/division.json").writeText("{broken")
        val reopened = DivisionStore(folder.root)
        assertTrue(reopened.listDivisions().isEmpty())
        assertNull(reopened.loadDivision("20"))
    }
}
