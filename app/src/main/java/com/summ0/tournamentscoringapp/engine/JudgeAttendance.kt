package com.summ0.tournamentscoringapp.engine

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Locale

enum class JudgeAttendanceStatus {
    UNCONFIRMED,
    PRESENT,
    ABSENT
}

data class PlannedRingJudge(
    val associationId: String,
    val name: String,
    val assignedRole: String
)

data class JudgeAttendanceEntry(
    val associationId: String,
    val name: String,
    val assignedRole: String,
    val status: JudgeAttendanceStatus = JudgeAttendanceStatus.UNCONFIRMED,
    val markedAt: String = ""
)

data class RingPhaseJudgeAttendance(
    val eventName: String,
    val ringId: String,
    val phase: String,
    val assignmentRevision: String,
    val officials: List<JudgeAttendanceEntry>
)

fun plannedRingJudges(plan: JSONObject): List<PlannedRingJudge> {
    require(plan.optString("type") == "tournament-judge-plan" &&
        plan.optInt("schemaVersion", 0) == 1 &&
        plan.optBoolean("assigned")) { "A saved ring/phase assignment is required for attendance." }
    val positions = plan.optJSONObject("positions")
        ?: throw IllegalArgumentException("The saved judge plan is missing its positions.")
    val ids = mutableSetOf<String>()
    return listOf("center", "corner", "sktk").flatMap { role ->
        val judges = positions.optJSONArray(role) ?: JSONArray()
        (0 until judges.length()).map { index ->
            val judge = judges.optJSONObject(index)
                ?: throw IllegalArgumentException("The saved judge plan contains an invalid official.")
            val associationId = judge.optString("associationId").trim()
            require(associationId.isNotEmpty()) { "Every planned judge must have an Association ID." }
            require(ids.add(associationId.uppercase(Locale.ROOT))) {
                "A planned judge cannot occupy multiple positions in the same ring and phase."
            }
            PlannedRingJudge(
                associationId = associationId,
                name = judge.optString("name").trim(),
                assignedRole = role
            )
        }
    }
}

fun newJudgeAttendance(plan: JSONObject): RingPhaseJudgeAttendance =
    RingPhaseJudgeAttendance(
        eventName = plan.getString("eventName"),
        ringId = plan.getString("ringId"),
        phase = plan.getString("phase"),
        assignmentRevision = plan.getString("assignmentRevision"),
        officials = plannedRingJudges(plan).map {
            JudgeAttendanceEntry(it.associationId, it.name, it.assignedRole)
        }
    )

fun updateJudgeAttendance(
    attendance: RingPhaseJudgeAttendance,
    associationId: String,
    status: JudgeAttendanceStatus,
    markedAt: String = Instant.now().toString()
): RingPhaseJudgeAttendance {
    require(status == JudgeAttendanceStatus.UNCONFIRMED || isValidInstant(markedAt)) {
        "Attendance timestamp must be a valid instant."
    }
    val normalizedId = associationId.uppercase(Locale.ROOT)
    val index = attendance.officials.indexOfFirst {
        it.associationId.uppercase(Locale.ROOT) == normalizedId
    }
    require(index >= 0) { "The judge is not on this ring/phase assignment." }
    return attendance.copy(officials = attendance.officials.toMutableList().apply {
        this[index] = this[index].copy(
            status = status,
            markedAt = if (status == JudgeAttendanceStatus.UNCONFIRMED) "" else markedAt
        )
    })
}

fun serializeJudgeAttendance(attendance: RingPhaseJudgeAttendance): JSONObject =
    JSONObject().apply {
        put("schemaVersion", 1)
        put("eventName", attendance.eventName)
        put("ringId", attendance.ringId)
        put("phase", attendance.phase)
        put("assignmentRevision", attendance.assignmentRevision)
        put("officials", JSONArray().apply {
            attendance.officials.forEach { official ->
                put(JSONObject().apply {
                    put("associationId", official.associationId)
                    put("name", official.name)
                    put("assignedRole", official.assignedRole)
                    put("status", official.status.name)
                    put("markedAt", official.markedAt)
                })
            }
        })
    }

fun parseJudgeAttendance(root: JSONObject, plan: JSONObject): RingPhaseJudgeAttendance {
    val expected = newJudgeAttendance(plan)
    require(root.optInt("schemaVersion", 0) == 1 &&
        root.optString("eventName") == expected.eventName &&
        root.optString("ringId") == expected.ringId &&
        root.optString("phase") == expected.phase &&
        root.optString("assignmentRevision") == expected.assignmentRevision) {
        "Saved attendance belongs to a different ring/phase assignment revision."
    }
    val storedOfficials = root.optJSONArray("officials")
        ?: throw IllegalArgumentException("Saved attendance is missing its official list.")
    val byId = expected.officials.associateBy { it.associationId.uppercase(Locale.ROOT) }
    val seen = mutableSetOf<String>()
    val officials = (0 until storedOfficials.length()).map { index ->
        val stored = storedOfficials.optJSONObject(index)
            ?: throw IllegalArgumentException("Saved attendance contains an invalid official.")
        val associationId = stored.optString("associationId").trim()
        val key = associationId.uppercase(Locale.ROOT)
        val planned = byId[key] ?: throw IllegalArgumentException("Saved attendance contains an unassigned judge.")
        require(seen.add(key) &&
            stored.optString("assignedRole") == planned.assignedRole &&
            stored.optString("name") == planned.name) {
            "Saved attendance does not match the planned judge roster."
        }
        val status = try {
            JudgeAttendanceStatus.valueOf(stored.optString("status"))
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("Saved attendance has an unknown status.")
        }
        val markedAt = stored.optString("markedAt")
        require(
            (status == JudgeAttendanceStatus.UNCONFIRMED && markedAt.isEmpty()) ||
                (status != JudgeAttendanceStatus.UNCONFIRMED && isValidInstant(markedAt))
        ) { "Saved attendance has an invalid timestamp." }
        planned.copy(status = status, markedAt = markedAt)
    }
    require(seen == byId.keys) { "Saved attendance does not cover the planned judge roster." }
    return expected.copy(officials = officials)
}

private fun isValidInstant(value: String): Boolean = try {
    Instant.parse(value)
    true
} catch (_: DateTimeParseException) {
    false
}
