package com.summ0.tournamentscoringapp.engine

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

/** Positions a judge can serve at a ring, matching the server's assignment roles. */
enum class ServiceRole(val wire: String) {
    CENTER("center"),
    CORNER("corner"),
    SCOREKEEPER("scorekeeper"),
    TIMEKEEPER("timekeeper"),
    SKTK("sktk"),
    ARBITRATOR("arbitrator");

    val selectable: Boolean get() = this != SKTK

    val label: String
        get() = when (this) {
            CENTER -> "Center"
            CORNER -> "Corner"
            SCOREKEEPER -> "Scorekeeper"
            TIMEKEEPER -> "Timekeeper"
            SKTK -> "SK/TK (legacy)"
            ARBITRATOR -> "Arbitrator"
        }

    companion object {
        fun fromWire(value: String): ServiceRole? = entries.firstOrNull { it.wire == value }
    }
}

/** How a seat changed over the course of a phase. */
enum class ServiceAction {
    SEATED,
    REPLACED,
    SWAPPED;

    companion object {
        fun parse(value: String): ServiceAction? = entries.firstOrNull { it.name == value }
    }
}

/** A judge actually serving, identified by Association ID. */
data class ServiceJudge(
    val associationId: String,
    val name: String,
    val rank: String
)

/** One timestamped change to the ring's service lineup. */
data class ServiceHistoryEntry(
    val at: String,
    val action: ServiceAction,
    val role: ServiceRole,
    val judgeAssociationId: String,
    val previousJudgeAssociationId: String? = null
)

/**
 * Actual judge service for one division at one ring/phase: the current seated
 * lineup plus a full history of seatings, replacements and role swaps. The
 * final [seats] are always explicit; [history] records exactly when each change
 * happened. Transferred in Stage 5 as a separate per-ring-phase service packet.
 */
data class RingPhaseService(
    val ringId: String,
    val phase: String,
    val groupAnchor: String,
    val seats: Map<ServiceRole, List<ServiceJudge>> = emptyMap(),
    val history: List<ServiceHistoryEntry> = emptyList()
) {
    fun judgeAt(role: ServiceRole, associationId: String): ServiceJudge? =
        seats[role]?.firstOrNull { it.associationId == associationId }

    fun roleOf(associationId: String): ServiceRole? =
        seats.entries.firstOrNull { (_, judges) -> judges.any { it.associationId == associationId } }?.key

    /** Seats a judge into a role (no-op if already seated there). */
    fun seat(role: ServiceRole, judge: ServiceJudge, at: String = Instant.now().toString()): RingPhaseService {
        if (judgeAt(role, judge.associationId) != null) return this
        val nextSeats = seats.toMutableMap()
        nextSeats[role] = (nextSeats[role].orEmpty()) + judge
        return copy(
            seats = nextSeats,
            history = history + ServiceHistoryEntry(at, ServiceAction.SEATED, role, judge.associationId)
        )
    }

    /** Replaces an outgoing judge in a role with an incoming judge. */
    fun replace(
        role: ServiceRole,
        outgoingAssociationId: String,
        incoming: ServiceJudge,
        at: String = Instant.now().toString()
    ): RingPhaseService {
        val current = seats[role].orEmpty()
        if (current.none { it.associationId == outgoingAssociationId }) return this
        val nextSeats = seats.toMutableMap()
        nextSeats[role] = current.map { if (it.associationId == outgoingAssociationId) incoming else it }
        return copy(
            seats = nextSeats,
            history = history + ServiceHistoryEntry(
                at, ServiceAction.REPLACED, role, incoming.associationId, outgoingAssociationId
            )
        )
    }

    /** Moves an already-seated judge from one role to another. */
    fun swapRole(associationId: String, toRole: ServiceRole, at: String = Instant.now().toString()): RingPhaseService {
        val fromRole = roleOf(associationId) ?: return this
        if (fromRole == toRole) return this
        val judge = judgeAt(fromRole, associationId) ?: return this
        val nextSeats = seats.toMutableMap()
        nextSeats[fromRole] = seats[fromRole].orEmpty().filterNot { it.associationId == associationId }
        nextSeats[toRole] = (seats[toRole].orEmpty()) + judge
        return copy(
            seats = nextSeats.pruneEmptyRoles(),
            history = history + ServiceHistoryEntry(at, ServiceAction.SWAPPED, toRole, associationId, fromRole.wire)
        )
    }

    /** Removes a judge from the lineup entirely (recorded as leaving its role). */
    fun unseat(associationId: String, at: String = Instant.now().toString()): RingPhaseService {
        val role = roleOf(associationId) ?: return this
        val nextSeats = seats.toMutableMap()
        nextSeats[role] = seats[role].orEmpty().filterNot { it.associationId == associationId }
        return copy(
            seats = nextSeats.pruneEmptyRoles(),
            history = history + ServiceHistoryEntry(at, ServiceAction.REPLACED, role, "", associationId)
        )
    }
}

private fun Map<ServiceRole, List<ServiceJudge>>.pruneEmptyRoles(): Map<ServiceRole, List<ServiceJudge>> =
    filterValues { it.isNotEmpty() }

fun serializeRingPhaseService(service: RingPhaseService): JSONObject {
    return JSONObject().apply {
        put("schemaVersion", 1)
        put("ringId", service.ringId)
        put("phase", service.phase)
        put("groupAnchor", service.groupAnchor)
        put("seats", JSONObject().apply {
            service.seats.forEach { (role, judges) ->
                put(role.wire, JSONArray().apply {
                    judges.forEach { judge ->
                        put(JSONObject().apply {
                            put("associationId", judge.associationId)
                            put("name", judge.name)
                            put("rank", judge.rank)
                        })
                    }
                })
            }
        })
        put("history", JSONArray().apply {
            service.history.forEach { entry ->
                put(JSONObject().apply {
                    put("at", entry.at)
                    put("action", entry.action.name)
                    put("role", entry.role.wire)
                    put("judgeAssociationId", entry.judgeAssociationId)
                    put("previousJudgeAssociationId", entry.previousJudgeAssociationId ?: JSONObject.NULL)
                })
            }
        })
    }
}

fun parseRingPhaseService(root: JSONObject): RingPhaseService {
    val seats = mutableMapOf<ServiceRole, List<ServiceJudge>>()
    root.optJSONObject("seats")?.let { seatsJson ->
        seatsJson.keys().forEach { key ->
            val role = ServiceRole.fromWire(key) ?: return@forEach
            val judgesJson = seatsJson.optJSONArray(key) ?: return@forEach
            val judges = buildList {
                for (index in 0 until judgesJson.length()) {
                    val item = judgesJson.getJSONObject(index)
                    add(
                        ServiceJudge(
                            associationId = item.optString("associationId", ""),
                            name = item.optString("name", ""),
                            rank = item.optString("rank", "")
                        )
                    )
                }

            }
            if (judges.isNotEmpty()) seats[role] = judges
        }
    }
    val history = mutableListOf<ServiceHistoryEntry>()
    root.optJSONArray("history")?.let { historyJson ->
        for (index in 0 until historyJson.length()) {
            val item = historyJson.getJSONObject(index)
            val action = ServiceAction.parse(item.optString("action", "")) ?: continue
            val role = ServiceRole.fromWire(item.optString("role", "")) ?: continue
            history += ServiceHistoryEntry(
                at = item.optString("at", ""),
                action = action,
                role = role,
                judgeAssociationId = item.optString("judgeAssociationId", ""),
                previousJudgeAssociationId = if (item.isNull("previousJudgeAssociationId")) {
                    null
                } else {
                    item.optString("previousJudgeAssociationId").ifBlank { null }
                }
            )
        }
    }
    return RingPhaseService(
        ringId = root.optString("ringId", ""),
        phase = root.optString("phase", ""),
        groupAnchor = root.optString("groupAnchor", ""),
        seats = seats,
        history = history
    )
}

fun buildRingPhaseServicePacket(
    ringId: String,
    phase: String,
    services: List<RingPhaseService>,
    submissionId: String = UUID.randomUUID().toString()
): JSONObject {
    require(ringId.isNotBlank()) { "A ring is required for service transfer." }
    require(phase == "P1" || phase == "P2") { "Select P1 or P2 for service transfer." }
    val matching = services.filter { it.ringId == ringId && it.phase == phase }
        .distinctBy { it.groupAnchor }
        .filter { it.seats.values.any { judges -> judges.isNotEmpty() } || it.history.isNotEmpty() }
    require(matching.isNotEmpty()) { "No recorded judge service exists for $ringId, $phase." }
    return JSONObject().apply {
        put("type", "tournament-judge-service")
        put("schemaVersion", 1)
        put("submissionId", submissionId)
        put("ringId", ringId)
        put("phase", phase)
        put("divisions", JSONArray().apply {
            matching.forEach { service ->
                val encoded = serializeRingPhaseService(service)
                put(JSONObject().apply {
                    put("groupAnchor", service.groupAnchor)
                    put("seats", encoded.getJSONObject("seats"))
                    put("history", encoded.getJSONArray("history"))
                })
            }
        })
    }
}
