package com.summ0.tournamentscoringapp.engine

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Instant

/** Lifecycle of a division stored durably on the tablet. */
enum class DivisionRecordStatus {
    LOADED,
    IN_PROGRESS,
    COMPLETED,
    SUBMITTED;

    companion object {
        fun parse(value: String): DivisionRecordStatus =
            entries.firstOrNull { it.name == value } ?: LOADED
    }
}

data class StoredServicePacket(
    val packet: JSONObject,
    val acknowledgement: JSONObject?,
    val eventName: String
)

/** Catalog entry for one stored division. */
data class DivisionRecord(
    val anchor: String,
    val groupId: String,
    val groupDivisionNumber: Int?,
    val name: String,
    val competitorCount: Int,
    val status: DivisionRecordStatus,
    val source: String,
    val loadedAt: String,
    val updatedAt: String,
    val submissionId: String = ""
)

/** Stable directory-safe identifier for a division. */
fun divisionAnchor(group: RemoteGroup): String {
    val number = group.groupDivisionNumber
    if (number != null && number > 0) return number.toString()
    return group.groupId.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "division" }
}

/** Serializes a [RemoteGroup] into the stored division document (lossless round trip). */
fun serializeStoredDivision(group: RemoteGroup, source: String, storedAt: String = Instant.now().toString()): JSONObject {
    return JSONObject().apply {
        put("schemaVersion", 1)
        put("storedAt", storedAt)
        put("source", source)
        put("group", JSONObject().apply {
            put("groupId", group.groupId)
            put("groupDivisionNumber", group.groupDivisionNumber ?: JSONObject.NULL)
            put("groupDivisionName", group.groupDivisionName ?: JSONObject.NULL)
            put("name", group.name)
            put("ageMin", group.ageRange.first)
            put("ageMax", group.ageRange.last)
            put("rankLow", group.rankRange.first)
            put("rankHigh", group.rankRange.last)
            put("rankRangeLabel", group.rankRangeLabel)
            put("matNumber", group.matNumber)
            put("competitors", JSONArray().apply {
                group.competitors.forEach { competitor ->
                    put(JSONObject().apply {
                        put("id", competitor.id)
                        put("name", competitor.name)
                        put("studio", competitor.studio)
                        put("rank", competitor.rank)
                        put("age", competitor.age)
                        put("heightInInches", competitor.heightInInches)
                    })
                }
            })
        })
    }
}

/** Parses a stored division document back into the [RemoteGroup] it was saved from. */
fun parseStoredDivision(root: JSONObject): RemoteGroup {
    require(root.optInt("schemaVersion", 0) == 1) { "Unsupported stored division schema" }
    val group = root.getJSONObject("group")
    val competitorsJson = group.getJSONArray("competitors")
    val competitors = mutableListOf<RemoteCompetitor>()
    for (index in 0 until competitorsJson.length()) {
        val item = competitorsJson.getJSONObject(index)
        competitors += RemoteCompetitor(
            id = item.getString("id"),
            name = item.optString("name", "Unknown"),
            studio = item.optString("studio", "Unknown Studio"),
            rank = item.optString("rank", "G10"),
            age = item.optInt("age", 18),
            heightInInches = item.optInt("heightInInches", 60)
        )
    }
    return RemoteGroup(
        groupId = group.optString("groupId", "group-unknown"),
        groupDivisionNumber = if (group.isNull("groupDivisionNumber")) null else group.optInt("groupDivisionNumber"),
        groupDivisionName = if (group.isNull("groupDivisionName")) null else group.optString("groupDivisionName"),
        name = group.optString("name", "Group"),
        ageRange = group.optInt("ageMin", 0)..group.optInt("ageMax", 0),
        rankRange = group.optInt("rankLow", 0)..group.optInt("rankHigh", 0),
        rankRangeLabel = group.optString("rankRangeLabel", "0-0"),
        matNumber = group.optInt("matNumber", 1),
        competitors = competitors
    )
}

/**
 * Durable multi-division storage under `filesDir/divisions`:
 * - `index.json` catalog with per-division status and the active division anchor
 * - `{anchor}/division.json` immutable loaded division
 * - `{anchor}/session.json` latest check-in/scoring snapshot
 *
 * All writes are atomic (temp file + rename) so a crash or power loss never
 * leaves a partially written document.
 */
class DivisionStore(baseDir: File) {
    private val rootDir = File(baseDir, "divisions")
    private val indexFile = File(rootDir, "index.json")
    private val servicePacketsDir = File(rootDir, "service-packets")
    private val judgePlansDir = File(baseDir, "judge-plans")

    fun saveDivision(group: RemoteGroup, source: String): DivisionRecord {
        val anchor = divisionAnchor(group)
        val now = Instant.now().toString()
        writeAtomic(divisionFile(anchor), serializeStoredDivision(group, source, now).toString(2))
        // Reloading a division starts it over: any previous session no longer applies.
        sessionFile(anchor).delete()
        val record = DivisionRecord(
            anchor = anchor,
            groupId = group.groupId,
            groupDivisionNumber = group.groupDivisionNumber,
            name = group.name,
            competitorCount = group.competitors.size,
            status = DivisionRecordStatus.LOADED,
            source = source,
            loadedAt = now,
            updatedAt = now
        )
        updateIndex { index -> index.apply { divisions[anchor] = record } }
        return record
    }

    fun loadDivision(anchor: String): RemoteGroup? {
        val file = divisionFile(anchor)
        if (!file.isFile) return null
        return try {
            parseStoredDivision(JSONObject(file.readText()))
        } catch (_: Exception) {
            null
        }
    }

    fun listDivisions(): List<DivisionRecord> =
        readIndex().divisions.values.sortedBy { it.loadedAt }

    fun record(anchor: String): DivisionRecord? = readIndex().divisions[anchor]

    fun saveSession(anchor: String, session: JSONObject): Boolean {
        val existing = record(anchor) ?: return false
        writeAtomic(sessionFile(anchor), session.toString(2))
        val nextStatus = if (existing.status == DivisionRecordStatus.LOADED) {
            DivisionRecordStatus.IN_PROGRESS
        } else {
            existing.status
        }
        touch(anchor) { it.copy(status = nextStatus) }
        return true
    }

    fun loadSession(anchor: String): JSONObject? {
        val file = sessionFile(anchor)
        if (!file.isFile) return null
        return try {
            JSONObject(file.readText())
        } catch (_: Exception) {
            null
        }
    }

    fun saveService(anchor: String, service: JSONObject): Boolean {
        if (record(anchor) == null) return false
        val phase = service.optString("phase", "")
        val target = if (phase == "P1" || phase == "P2") phaseServiceFile(anchor, phase) else serviceFile(anchor)
        writeAtomic(target, service.toString(2))
        return true
    }

    fun loadService(anchor: String, phase: String? = null): JSONObject? {
        val file = when (phase) {
            "P1", "P2" -> phaseServiceFile(anchor, phase).takeIf { it.isFile }
                ?: if (phaseServiceFile(anchor, if (phase == "P1") "P2" else "P1").isFile) null
                else serviceFile(anchor)
            else -> listOf("P1", "P2").map { phaseServiceFile(anchor, it) }.firstOrNull { it.isFile }
                ?: serviceFile(anchor)
        }
        if (file == null || !file.isFile) return null
        return try {
            JSONObject(file.readText())
        } catch (_: Exception) {
            null
        }
    }

    fun saveJudgePlan(eventName: String, ringId: String, phase: String, plan: JSONObject): Boolean {
        require(eventName.isNotBlank() && ringId.isNotBlank()) { "Event and ring are required to save a judge plan." }
        require(phase == "P1" || phase == "P2") { "Judge plan phase must be P1 or P2." }
        require(plan.optString("type") == "tournament-judge-plan" &&
            plan.optInt("schemaVersion", 0) == 1 &&
            plan.optString("eventName") == eventName &&
            plan.optString("ringId") == ringId &&
            plan.optString("phase") == phase &&
            plan.optString("assignmentRevision").isNotBlank() &&
            plan.optString("sourceRevision").isNotBlank()) {
            "The server returned an invalid judge plan."
        }
        val verifier = plan.optJSONObject("replacementPinVerifier")
        require(plan.optBoolean("replacementPinConfigured") == (verifier != null)) {
            "The server returned inconsistent replacement PIN configuration."
        }
        if (verifier != null) {
            require(verifier.optString("algorithm") == "SHA-256-salted-v1" &&
                verifier.optString("salt").matches(Regex("[a-fA-F0-9]{32}")) &&
                verifier.optString("digest").matches(Regex("[a-fA-F0-9]{64}"))) {
                "The server returned an invalid replacement PIN verifier."
            }
        }
        judgePlansDir.mkdirs()
        writeAtomic(judgePlanFile(eventName, ringId, phase), plan.toString(2))
        return true
    }

    fun loadJudgePlan(eventName: String, ringId: String, phase: String): JSONObject? {
        val file = judgePlanFile(eventName, ringId, phase)
        if (!file.isFile) return null
        return try {
            JSONObject(file.readText()).takeIf {
                it.optString("type") == "tournament-judge-plan" &&
                    it.optInt("schemaVersion", 0) == 1 &&
                    it.optString("eventName") == eventName &&
                    it.optString("ringId") == ringId &&
                    it.optString("phase") == phase &&
                    it.optString("assignmentRevision").isNotBlank() &&
                    it.optString("sourceRevision").isNotBlank()
            }
        } catch (_: Exception) {
            null
        }
    }

    fun deleteJudgePlan(eventName: String, ringId: String, phase: String): Boolean {
        val file = judgePlanFile(eventName, ringId, phase)
        return !file.exists() || file.delete()
    }

    fun loadJudgeAttendance(plan: JSONObject): RingPhaseJudgeAttendance? {
        val file = judgeAttendanceFile(plan)
        if (!file.isFile) return null
        return try {
            parseJudgeAttendance(JSONObject(file.readText()), plan)
        } catch (error: Exception) {
            throw IOException("Unable to recover saved judge attendance.", error)
        }
    }

    fun saveJudgeAttendance(plan: JSONObject, attendance: RingPhaseJudgeAttendance): Boolean {
        val validated = parseJudgeAttendance(serializeJudgeAttendance(attendance), plan)
        writeAtomic(judgeAttendanceFile(plan), serializeJudgeAttendance(validated).toString(2))
        return true
    }

    fun prepareServicePacket(ringId: String, phase: String, eventName: String): StoredServicePacket {
        require(phase == "P1" || phase == "P2") { "Select P1 or P2 for service transfer." }
        require(eventName.isNotBlank()) { "Pair this tablet with a tournament before transferring service." }
        servicePacketsDir.mkdirs()
        val storedPackets = servicePacketsDir.listFiles()
            ?.filter { it.isFile && it.name.matches(Regex("[a-fA-F0-9-]{36}\\.json")) }
            .orEmpty()
        for (file in storedPackets) {
            val envelope = try {
                JSONObject(file.readText())
            } catch (error: Exception) {
                throw IOException("Unable to read saved service packet ${file.name}", error)
            }
            if (envelope.optInt("schemaVersion", 0) != 1) {
                throw IOException("Unsupported saved service packet ${file.name}")
            }
            val existing = envelope.optJSONObject("packet")
                ?: throw IOException("Saved service packet ${file.name} is missing its payload")
            if (envelope.optString("eventName") == eventName &&
                existing.optString("ringId") == ringId && existing.optString("phase") == phase) {
                return StoredServicePacket(
                    packet = existing,
                    acknowledgement = envelope.optJSONObject("acknowledgement"),
                    eventName = eventName
                )
            }
        }
        val services = listDivisions().mapNotNull { record ->
            loadPhaseServiceForPacket(record.anchor, phase)
        }.map(::parseRingPhaseService)
        val packet = buildRingPhaseServicePacket(ringId, phase, services)
        writeAtomic(servicePacketFile(packet.getString("submissionId")), JSONObject().apply {
            put("schemaVersion", 1)
            put("eventName", eventName)
            put("packet", packet)
            put("acknowledgement", JSONObject.NULL)
        }.toString(2))
        return StoredServicePacket(packet, null, eventName)
    }

    fun acknowledgeServicePacket(submissionId: String, acknowledgement: JSONObject): Boolean {
        if (!submissionId.matches(Regex("[a-fA-F0-9-]{36}"))) return false
        val file = servicePacketFile(submissionId)
        if (!file.isFile) return false
        val envelope = try {
            JSONObject(file.readText())
        } catch (_: Exception) {
            return false
        }
        if (envelope.optJSONObject("packet")?.optString("submissionId") != submissionId ||
            acknowledgement.optBoolean("accepted") != true ||
            acknowledgement.optString("submissionId") != submissionId ||
            acknowledgement.optString("serverRecordId").isBlank()) return false
        envelope.put("acknowledgement", acknowledgement)
        writeAtomic(file, envelope.toString(2))
        return true
    }

    fun markCompleted(anchor: String) {
        touch(anchor) { it.copy(status = DivisionRecordStatus.COMPLETED) }
    }

    fun markSubmitted(anchor: String, submissionId: String) {
        touch(anchor) { it.copy(status = DivisionRecordStatus.SUBMITTED, submissionId = submissionId) }
    }

    fun activeAnchor(): String? = readIndex().activeAnchor.ifBlank { null }

    fun setActiveAnchor(anchor: String?) {
        updateIndex { index -> index.apply { activeAnchor = anchor.orEmpty() } }
    }

    fun removeDivision(anchor: String) {
        updateIndex { index ->
            index.apply {
                divisions.remove(anchor)
                if (activeAnchor == anchor) activeAnchor = ""
            }
        }
        File(rootDir, anchor).deleteRecursively()
    }

    private fun divisionFile(anchor: String) = File(File(rootDir, anchor), "division.json")

    private fun sessionFile(anchor: String) = File(File(rootDir, anchor), "session.json")

    private fun serviceFile(anchor: String) = File(File(rootDir, anchor), "service.json")

    private fun phaseServiceFile(anchor: String, phase: String) = File(File(rootDir, anchor), "service-$phase.json")

    private fun loadPhaseServiceForPacket(anchor: String, phase: String): JSONObject? {
        val file = phaseServiceFile(anchor, phase)
        if (!file.isFile) return null
        return JSONObject(file.readText()).takeIf {
            it.optString("ringId").isNotBlank() && it.optString("phase") == phase
        }
    }

    private fun servicePacketFile(submissionId: String) = File(servicePacketsDir, "$submissionId.json")

    private fun judgePlanFile(eventName: String, ringId: String, phase: String): File {
        require(eventName.isNotBlank() && ringId.isNotBlank() && (phase == "P1" || phase == "P2")) {
            "Event, ring and a valid phase are required to access a judge plan."
        }
        val key = "$eventName\u0000$ringId\u0000$phase"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(key.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(judgePlansDir, "$digest.json")
    }

    private fun judgeAttendanceFile(plan: JSONObject): File {
        val eventName = plan.optString("eventName")
        val ringId = plan.optString("ringId")
        val phase = plan.optString("phase")
        val revision = plan.optString("assignmentRevision")
        require(eventName.isNotBlank() && ringId.isNotBlank() &&
            (phase == "P1" || phase == "P2") && revision.isNotBlank()) {
            "A revisioned event/ring/phase plan is required for attendance."
        }
        val key = "$eventName\u0000$ringId\u0000$phase\u0000$revision"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(key.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val directory = File(judgePlansDir, "attendance")
        directory.mkdirs()
        return File(directory, "$digest.json")
    }

    fun hasSession(anchor: String): Boolean = sessionFile(anchor).isFile

    fun hasService(anchor: String): Boolean = serviceFile(anchor).isFile ||
        phaseServiceFile(anchor, "P1").isFile || phaseServiceFile(anchor, "P2").isFile

    private fun touch(anchor: String, transform: (DivisionRecord) -> DivisionRecord) {
        updateIndex { index ->
            index.apply {
                divisions[anchor]?.let { existing ->
                    divisions[anchor] = transform(existing).copy(updatedAt = Instant.now().toString())
                }
            }
        }
    }

    private class IndexState(
        var activeAnchor: String,
        val divisions: LinkedHashMap<String, DivisionRecord>
    )

    private fun readIndex(): IndexState {
        if (!indexFile.isFile) return IndexState("", LinkedHashMap())
        return try {
            val root = JSONObject(indexFile.readText())
            val divisions = LinkedHashMap<String, DivisionRecord>()
            val entries = root.optJSONArray("divisions") ?: JSONArray()
            for (index in 0 until entries.length()) {
                val item = entries.getJSONObject(index)
                val anchor = item.optString("anchor", "").trim()
                if (anchor.isEmpty()) continue
                divisions[anchor] = DivisionRecord(
                    anchor = anchor,
                    groupId = item.optString("groupId", ""),
                    groupDivisionNumber = if (item.isNull("groupDivisionNumber")) null else item.optInt("groupDivisionNumber"),
                    name = item.optString("name", ""),
                    competitorCount = item.optInt("competitorCount", 0),
                    status = DivisionRecordStatus.parse(item.optString("status", "")),
                    source = item.optString("source", ""),
                    loadedAt = item.optString("loadedAt", ""),
                    updatedAt = item.optString("updatedAt", ""),
                    submissionId = item.optString("submissionId", "")
                )
            }
            IndexState(root.optString("activeAnchor", ""), divisions)
        } catch (_: Exception) {
            IndexState("", LinkedHashMap())
        }
    }

    private fun writeIndex(state: IndexState) {
        val root = JSONObject().apply {
            put("schemaVersion", 1)
            put("activeAnchor", state.activeAnchor)
            put("divisions", JSONArray().apply {
                state.divisions.values.forEach { record ->
                    put(JSONObject().apply {
                        put("anchor", record.anchor)
                        put("groupId", record.groupId)
                        put("groupDivisionNumber", record.groupDivisionNumber ?: JSONObject.NULL)
                        put("name", record.name)
                        put("competitorCount", record.competitorCount)
                        put("status", record.status.name)
                        put("source", record.source)
                        put("loadedAt", record.loadedAt)
                        put("updatedAt", record.updatedAt)
                        put("submissionId", record.submissionId)
                    })
                }
            })
        }
        writeAtomic(indexFile, root.toString(2))
    }

    @Synchronized
    private fun updateIndex(transform: (IndexState) -> IndexState) {
        writeIndex(transform(readIndex()))
    }

    private fun writeAtomic(target: File, content: String) {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.writeText(content)
        try {
            Files.move(
                temp.toPath(), target.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: Exception) {
            target.delete()
            if (!temp.renameTo(target)) {
                throw IOException("Unable to persist ${target.name}")
            }
        }
    }
}
