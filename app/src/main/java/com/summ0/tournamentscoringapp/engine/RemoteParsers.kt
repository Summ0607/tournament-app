package com.summ0.tournamentscoringapp.engine

import com.summ0.tournamentscoringapp.rankLabelForLevel
import com.summ0.tournamentscoringapp.rankLevelFor
import com.summ0.tournamentscoringapp.RankFormatter
import org.json.JSONArray
import org.json.JSONObject

fun JSONObject.optNonBlank(vararg keys: String): String? {
    keys.forEach { key ->
        val value = optString(key, "").trim()
        if (value.isNotEmpty()) return value
    }
    return null
}

fun JSONObject.optNullableString(vararg keys: String): String? {
    keys.forEach { key ->
        if (!has(key) || isNull(key)) return@forEach
        val value = optString(key, "").trim()
        if (value.isNotEmpty()) return value
    }
    return null
}

fun parseRankRange(root: JSONObject, rawCompetitors: JSONArray): ParsedRankRange {
    val rankRangeJson = root.optJSONObject("rankRange")
    val lowLabel = rankRangeJson?.optNonBlank("low", "min")
    val highLabel = rankRangeJson?.optNonBlank("high", "max")

    val competitorRankLevels = mutableListOf<Int>()
    for (index in 0 until rawCompetitors.length()) {
        val competitorJson = rawCompetitors.getJSONObject(index)
        val rankLevel = rankLevelFor(competitorJson.optNonBlank("rankCode", "rank").orEmpty())
        if (rankLevel != Int.MAX_VALUE) {
            competitorRankLevels += rankLevel
        }
    }

    val derivedRange = if (competitorRankLevels.isEmpty()) {
        0..0
    } else {
        competitorRankLevels.minOrNull()!!..competitorRankLevels.maxOrNull()!!
    }

    val parsedRange = if (lowLabel != null && highLabel != null) {
        val lowLevel = rankLevelFor(lowLabel)
        val highLevel = rankLevelFor(highLabel)
        if (lowLevel != Int.MAX_VALUE && highLevel != Int.MAX_VALUE) {
            minOf(lowLevel, highLevel)..maxOf(lowLevel, highLevel)
        } else {
            derivedRange
        }
    } else {
        derivedRange
    }

    val label = when {
        lowLabel != null && highLabel != null -> "${RankFormatter.formatForDisplay(lowLabel)} - ${RankFormatter.formatForDisplay(highLabel)}"
        parsedRange.isEmpty() -> "0-0"
        else -> "${rankLabelForLevel(parsedRange.last)} - ${rankLabelForLevel(parsedRange.first)}"
    }

    return ParsedRankRange(parsedRange, label)
}

fun JSONObject.optHeightInches(defaultValue: Int = 60): Int {
    val fromNumber = optInt("heightInInches", Int.MIN_VALUE)
    if (fromNumber != Int.MIN_VALUE && fromNumber > 0) return fromNumber

    val fromHeight = optString("height", "").trim().toIntOrNull()
    if (fromHeight != null && fromHeight > 0) return fromHeight

    return defaultValue
}

fun JSONObject.optStringList(key: String): List<String> {
    val values = optJSONArray(key) ?: return emptyList()
    val result = mutableListOf<String>()
    for (index in 0 until values.length()) {
        val value = values.optString(index, "").trim()
        if (value.isNotEmpty()) {
            result += value
        }
    }
    return result
}

internal data class ParsedRingAssignmentState(
    val groupDivisionNumber: Int?,
    val queuedGroupDivisionNumbers: List<Int>,
    val completedGroupDivisionNumbers: List<Int>
)

internal fun JSONObject.parseRingAssignmentState(currentGroup: RemoteGroup? = null): ParsedRingAssignmentState {
    return ParsedRingAssignmentState(
        groupDivisionNumber = optPositiveInt("groupDivisionNumber") ?: currentGroup?.effectiveDivisionNumber(),
        queuedGroupDivisionNumbers = optPositiveIntList("queuedGroupDivisionNumbers"),
        completedGroupDivisionNumbers = optPositiveIntList("completedGroupDivisionNumbers")
    )
}

internal fun JSONObject.optPositiveInt(key: String): Int? {
    if (!has(key) || isNull(key)) return null
    return when (val value = opt(key)) {
        is Number -> value.toInt()
        is String -> value.trim().toIntOrNull()
        else -> null
    }?.takeIf { it > 0 }
}

internal fun JSONObject.optPositiveIntList(key: String): List<Int> {
    val array = optJSONArray(key) ?: return emptyList()
    val values = buildList {
        for (index in 0 until array.length()) {
            val resolved = when (val rawValue = array.opt(index)) {
                is Number -> rawValue.toInt()
                is String -> rawValue.trim().toIntOrNull()
                else -> null
            }?.takeIf { it > 0 }
            if (resolved != null) add(resolved)
        }
    }
    return values
}

fun parsePhaseTimingList(root: JSONObject): List<PhaseTiming> {
    val values = root.optJSONArray("phaseHistory") ?: return emptyList()
    val totalCompetitors = root.optJSONObject("currentGroup")
        ?.optJSONArray("competitors")
        ?.length()
        ?: 0
    val result = mutableListOf<PhaseTiming>()
    for (index in 0 until values.length()) {
        val entry = values.optJSONObject(index) ?: continue
        val name = entry.optString("name", "").trim()
        val startTime = entry.optString("startTime", "").trim()
        if (name.isEmpty() || startTime.isEmpty()) continue
        val normalizedName = if (name.equals("check-in", ignoreCase = true)) "setup" else name
        val estimated = if (normalizedName.equals("setup", ignoreCase = true)) {
            estimatePhaseDuration(
                normalizedName,
                PhaseTimingEstimateContext(totalCompetitors = totalCompetitors)
            )
        } else {
            entry.optString("estimated", "").trim().ifBlank {
                estimatePhaseDuration(normalizedName)
            }
        }
        result += PhaseTiming(
            name = normalizedName,
            startTime = startTime,
            endTime = entry.optNullableString("endTime"),
            elapsed = entry.optString("elapsed", "").trim(),
            estimated = estimated
        )
    }
    return result
}

fun parseRemoteGroup(root: JSONObject): RemoteGroup {
    val groupId = root.optString("groupId", "").ifBlank { "group-unknown" }
    val groupDivisionNumber = when {
        root.has("groupDivisionNumber") && !root.isNull("groupDivisionNumber") -> {
            val rawValue = root.opt("groupDivisionNumber")
            when (rawValue) {
                is Number -> rawValue.toInt().takeIf { it > 0 }
                is String -> rawValue.trim().toIntOrNull()?.takeIf { it > 0 }
                else -> null
            }
        }
        else -> parseDivisionNumberFromLegacyGroupId(groupId)
    }
    val groupDivisionName = root.optNullableString("groupDivisionName", "groupDivisionLabel", "name")
    val name = root.optString("name", "Group")
    val ageRangeJson = root.optJSONObject("ageRange")
    val ageRange = if (ageRangeJson != null) {
        ageRangeJson.optInt("min", 0)..ageRangeJson.optInt("max", 0)
    } else {
        0..0
    }
    val rawCompetitors = root.optJSONArray("competitors")
        ?: error("Response did not include a competitors array")
    val rankRange = parseRankRange(root, rawCompetitors)
    val matNumber = root.optInt("matNumber", 1)

    val competitors = mutableListOf<RemoteCompetitor>()
    for (index in 0 until rawCompetitors.length()) {
        val competitorJson = rawCompetitors.getJSONObject(index)
        val competitorName = competitorJson.optNonBlank("name", "fullName") ?: "Unknown"
        val competitorId = competitorJson.optNonBlank("id", "competitorId")
            ?: "${groupId}-$index-${competitorName.lowercase().replace(" ", "-")}"
        val studioName = competitorJson.optNonBlank("studio", "school") ?: "Unknown Studio"
        competitors += RemoteCompetitor(
            id = competitorId,
            name = competitorName,
            studio = studioName,
            rank = competitorJson.optNonBlank("rankCode", "rank") ?: "G10",
            age = competitorJson.optInt("age", 18),
            heightInInches = competitorJson.optHeightInches()
        )
    }

    return RemoteGroup(
        groupId = groupId,
        groupDivisionNumber = groupDivisionNumber,
        groupDivisionName = groupDivisionName ?: name,
        name = name,
        ageRange = ageRange,
        rankRange = rankRange.range,
        rankRangeLabel = rankRange.label,
        matNumber = matNumber,
        competitors = competitors
    )
}

fun RemoteCompetitor.toCompetitor(): Competitor = Competitor(
    id = id,
    name = name,
    studio = studio,
    rank = rank,
    rankLevel = rankLevelFor(rank.trim()),
    age = age,
    heightInInches = heightInInches,
    checkInStatus = CheckInStatus.REGISTERED,
    competitionEntries = emptyMap()
)
