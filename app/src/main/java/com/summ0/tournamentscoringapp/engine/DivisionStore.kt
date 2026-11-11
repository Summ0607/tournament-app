package com.summ0.tournamentscoringapp.engine

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
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

    fun hasSession(anchor: String): Boolean = sessionFile(anchor).isFile

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
