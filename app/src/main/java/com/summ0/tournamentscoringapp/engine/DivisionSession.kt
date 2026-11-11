package com.summ0.tournamentscoringapp.engine

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * Everything needed to resume a division after a process or device restart.
 * Sparring winners are stored as competitor IDs and re-resolved against the
 * restored competitor list; an in-flight bout timer is intentionally not
 * persisted (reopen the bout, recorded points and warnings remain).
 */
data class DivisionSession(
    val competitors: List<Competitor> = emptyList(),
    val lockedPhases: Set<CompetitionType> = emptySet(),
    val phaseEntrants: Map<CompetitionType, Set<String>> = emptyMap(),
    val useOnlyThreeJudges: Boolean = false,
    val currentScreenName: String = "",
    val controlBoardPhase: String = "setup",
    val controlBoardPhaseStartedAt: String = "",
    val phaseHistory: List<PhaseTiming> = emptyList(),
    val weaponsScores: Map<String, List<String>> = emptyMap(),
    val hyungsScores: Map<String, List<String>> = emptyMap(),
    val weaponsTieBreakScores: Map<String, List<String>> = emptyMap(),
    val hyungsTieBreakScores: Map<String, List<String>> = emptyMap(),
    val weaponsJudgeChoices: Map<String, List<String>> = emptyMap(),
    val hyungsJudgeChoices: Map<String, List<String>> = emptyMap(),
    val weaponsFinalize: PlacementFinalizeState = PlacementFinalizeState(),
    val hyungsFinalize: PlacementFinalizeState = PlacementFinalizeState(),
    val pendingTieBreak: PendingTieBreak? = null,
    val sparringWinnerIds: Map<Int, Map<Int, String>> = emptyMap(),
    val sparringBoutProgress: Map<String, SparringBoutProgress> = emptyMap()
) {
    /** True when the session matches a freshly loaded division with no operator activity. */
    fun isPristine(): Boolean {
        return competitors.all {
            it.checkInStatus == CheckInStatus.REGISTERED && it.competitionEntries.isEmpty()
        } &&
            lockedPhases.isEmpty() && phaseEntrants.isEmpty() && !useOnlyThreeJudges &&
            (currentScreenName.isBlank() || currentScreenName == CompetitionScreen.CHECK_IN.name) &&
            controlBoardPhase == "setup" && phaseHistory.isEmpty() &&
            weaponsScores.isEmpty() && hyungsScores.isEmpty() &&
            weaponsTieBreakScores.isEmpty() && hyungsTieBreakScores.isEmpty() &&
            weaponsJudgeChoices.isEmpty() && hyungsJudgeChoices.isEmpty() &&
            weaponsFinalize == PlacementFinalizeState() && hyungsFinalize == PlacementFinalizeState() &&
            pendingTieBreak == null && sparringWinnerIds.isEmpty() && sparringBoutProgress.isEmpty()
    }
}

fun serializeCompetitorList(competitors: List<Competitor>): JSONArray {
    val root = JSONArray()
    competitors.forEach { competitor ->
        root.put(JSONObject().apply {
            put("id", competitor.id)
            put("name", competitor.name)
            put("studio", competitor.studio)
            put("rank", competitor.rank)
            put("rankLevel", competitor.rankLevel)
            put("age", competitor.age)
            put("heightInInches", competitor.heightInInches)
            put("checkInStatus", competitor.checkInStatus.name)
            put("competitionEntries", JSONArray().apply {
                competitor.competitionEntries.forEach { (type, entry) ->
                    put(JSONArray().apply {
                        put(type.name)
                        put(entry.status.name)
                    })
                }
            })
        })
    }
    return root
}

fun parseCompetitorList(root: JSONArray): List<Competitor> {
    val parsed = mutableListOf<Competitor>()
    for (index in 0 until root.length()) {
        val item = root.getJSONObject(index)
        val competitionEntryMap = mutableMapOf<CompetitionType, CompetitionEntry>()
        val entries = item.optJSONArray("competitionEntries") ?: JSONArray()
        for (entryIndex in 0 until entries.length()) {
            val pair = entries.getJSONArray(entryIndex)
            val type = runCatching { CompetitionType.valueOf(pair.optString(0, "")) }.getOrNull() ?: continue
            val status = runCatching { CompetitionRegistrationStatus.valueOf(pair.optString(1, "")) }
                .getOrDefault(CompetitionRegistrationStatus.REGISTERED)
            competitionEntryMap[type] = CompetitionEntry(type = type, status = status)
        }
        parsed += Competitor(
            id = item.optString("id", ""),
            name = item.optString("name", ""),
            studio = item.optString("studio", ""),
            rank = item.optString("rank", ""),
            rankLevel = item.optInt("rankLevel", 0),
            age = item.optInt("age", 0),
            heightInInches = item.optInt("heightInInches", 60),
            checkInStatus = runCatching {
                CheckInStatus.valueOf(item.optString("checkInStatus", CheckInStatus.REGISTERED.name))
            }.getOrDefault(CheckInStatus.REGISTERED),
            competitionEntries = competitionEntryMap
        )
    }
    return parsed
}

private fun stringListMapToJson(values: Map<String, List<String>>): JSONObject {
    return JSONObject().apply {
        values.forEach { (key, list) -> put(key, JSONArray(list)) }
    }
}

private fun jsonToStringListMap(root: JSONObject?): Map<String, List<String>> {
    if (root == null) return emptyMap()
    val result = mutableMapOf<String, List<String>>()
    root.keys().forEach { key ->
        val array = root.optJSONArray(key) ?: return@forEach
        result[key] = buildList {
            for (index in 0 until array.length()) add(array.optString(index, ""))
        }
    }
    return result
}

private fun stringMapToJson(values: Map<String, String>): JSONObject {
    return JSONObject().apply { values.forEach { (key, value) -> put(key, value) } }
}

private fun jsonToStringMap(root: JSONObject?): Map<String, String> {
    if (root == null) return emptyMap()
    val result = mutableMapOf<String, String>()
    root.keys().forEach { key -> result[key] = root.optString(key, "") }
    return result
}

private fun finalizeStateToJson(state: PlacementFinalizeState): JSONObject {
    return JSONObject().apply {
        put("labels", stringMapToJson(state.labels))
        put("tieBreakDetails", stringMapToJson(state.tieBreakDetails))
        put("message", state.message ?: JSONObject.NULL)
    }
}

private fun jsonToFinalizeState(root: JSONObject?): PlacementFinalizeState {
    if (root == null) return PlacementFinalizeState()
    return PlacementFinalizeState(
        labels = jsonToStringMap(root.optJSONObject("labels")),
        tieBreakDetails = jsonToStringMap(root.optJSONObject("tieBreakDetails")),
        message = if (root.isNull("message")) null else root.optString("message")
    )
}

fun serializeDivisionSession(session: DivisionSession, savedAt: String = Instant.now().toString()): JSONObject {
    return JSONObject().apply {
        put("schemaVersion", 1)
        put("savedAt", savedAt)
        put("competitors", serializeCompetitorList(session.competitors))
        put("lockedPhases", JSONArray(session.lockedPhases.map { it.name }))
        put("phaseEntrants", JSONObject().apply {
            session.phaseEntrants.forEach { (type, ids) -> put(type.name, JSONArray(ids.toList())) }
        })
        put("useOnlyThreeJudges", session.useOnlyThreeJudges)
        put("currentScreen", session.currentScreenName)
        put("controlBoardPhase", session.controlBoardPhase)
        put("controlBoardPhaseStartedAt", session.controlBoardPhaseStartedAt)
        put("phaseHistory", JSONArray().apply {
            session.phaseHistory.forEach { timing ->
                put(JSONObject().apply {
                    put("name", timing.name)
                    put("startTime", timing.startTime)
                    put("endTime", timing.endTime ?: JSONObject.NULL)
                    put("elapsed", timing.elapsed)
                    put("estimated", timing.estimated)
                })
            }
        })
        put("weaponsScores", stringListMapToJson(session.weaponsScores))
        put("hyungsScores", stringListMapToJson(session.hyungsScores))
        put("weaponsTieBreakScores", stringListMapToJson(session.weaponsTieBreakScores))
        put("hyungsTieBreakScores", stringListMapToJson(session.hyungsTieBreakScores))
        put("weaponsJudgeChoices", stringListMapToJson(session.weaponsJudgeChoices))
        put("hyungsJudgeChoices", stringListMapToJson(session.hyungsJudgeChoices))
        put("weaponsFinalize", finalizeStateToJson(session.weaponsFinalize))
        put("hyungsFinalize", finalizeStateToJson(session.hyungsFinalize))
        put("pendingTieBreak", session.pendingTieBreak?.let { pending ->
            JSONObject().apply {
                put("discipline", pending.discipline.name)
                put("competitorIds", JSONArray(pending.competitorIds))
                put("judgeCount", pending.judgeCount)
                put("groupKey", pending.groupKey)
            }
        } ?: JSONObject.NULL)
        put("sparringWinners", JSONObject().apply {
            session.sparringWinnerIds.forEach { (round, bouts) ->
                put(round.toString(), JSONObject().apply {
                    bouts.forEach { (bout, competitorId) -> put(bout.toString(), competitorId) }
                })
            }
        })
        put("sparringBoutProgress", JSONObject().apply {
            session.sparringBoutProgress.forEach { (key, progress) ->
                put(key, JSONObject().apply {
                    put("bluePoints", progress.bluePoints)
                    put("redPoints", progress.redPoints)
                    put("blueWarnings", progress.blueWarnings)
                    put("redWarnings", progress.redWarnings)
                    put("elapsedSeconds", progress.elapsedSeconds)
                    put("winnerId", progress.winnerId ?: JSONObject.NULL)
                    put("outcomeLabel", progress.outcomeLabel)
                })
            }
        })
    }
}

fun parseDivisionSession(root: JSONObject): DivisionSession {
    val lockedPhases = mutableSetOf<CompetitionType>()
    root.optJSONArray("lockedPhases")?.let { array ->
        for (index in 0 until array.length()) {
            runCatching { CompetitionType.valueOf(array.optString(index, "")) }
                .getOrNull()?.let(lockedPhases::add)
        }
    }
    val phaseEntrants = mutableMapOf<CompetitionType, Set<String>>()
    root.optJSONObject("phaseEntrants")?.let { entrants ->
        entrants.keys().forEach { key ->
            val type = runCatching { CompetitionType.valueOf(key) }.getOrNull() ?: return@forEach
            val ids = entrants.optJSONArray(key) ?: return@forEach
            phaseEntrants[type] = buildSet {
                for (index in 0 until ids.length()) add(ids.optString(index, ""))
            }
        }
    }
    val phaseHistory = mutableListOf<PhaseTiming>()
    root.optJSONArray("phaseHistory")?.let { array ->
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            phaseHistory += PhaseTiming(
                name = item.optString("name", ""),
                startTime = item.optString("startTime", ""),
                endTime = if (item.isNull("endTime")) null else item.optString("endTime"),
                elapsed = item.optString("elapsed", ""),
                estimated = item.optString("estimated", "")
            )
        }
    }
    val pendingTieBreak = root.optJSONObject("pendingTieBreak")?.let { pending ->
        val discipline = runCatching { HyungDiscipline.valueOf(pending.optString("discipline", "")) }.getOrNull()
        val competitorIds = pending.optJSONArray("competitorIds")?.let { ids ->
            buildList { for (index in 0 until ids.length()) add(ids.optString(index, "")) }
        } ?: emptyList()
        discipline?.let {
            PendingTieBreak(
                discipline = it,
                competitorIds = competitorIds,
                judgeCount = pending.optInt("judgeCount", 5),
                groupKey = pending.optString("groupKey", "")
            )
        }
    }
    val sparringWinnerIds = mutableMapOf<Int, Map<Int, String>>()
    root.optJSONObject("sparringWinners")?.let { winners ->
        winners.keys().forEach { roundKey ->
            val round = roundKey.toIntOrNull() ?: return@forEach
            val bouts = winners.optJSONObject(roundKey) ?: return@forEach
            val resolved = mutableMapOf<Int, String>()
            bouts.keys().forEach { boutKey ->
                val bout = boutKey.toIntOrNull() ?: return@forEach
                resolved[bout] = bouts.optString(boutKey, "")
            }
            if (resolved.isNotEmpty()) sparringWinnerIds[round] = resolved
        }
    }
    val sparringBoutProgress = mutableMapOf<String, SparringBoutProgress>()
    root.optJSONObject("sparringBoutProgress")?.let { progressRoot ->
        progressRoot.keys().forEach { key ->
            val item = progressRoot.optJSONObject(key) ?: return@forEach
            sparringBoutProgress[key] = SparringBoutProgress(
                bluePoints = item.optInt("bluePoints", 0),
                redPoints = item.optInt("redPoints", 0),
                blueWarnings = item.optInt("blueWarnings", 0),
                redWarnings = item.optInt("redWarnings", 0),
                elapsedSeconds = item.optInt("elapsedSeconds", 0),
                winnerId = if (item.isNull("winnerId")) null else item.optString("winnerId"),
                outcomeLabel = item.optString("outcomeLabel", "")
            )
        }
    }
    return DivisionSession(
        competitors = parseCompetitorList(root.optJSONArray("competitors") ?: JSONArray()),
        lockedPhases = lockedPhases,
        phaseEntrants = phaseEntrants,
        useOnlyThreeJudges = root.optBoolean("useOnlyThreeJudges", false),
        currentScreenName = root.optString("currentScreen", ""),
        controlBoardPhase = root.optString("controlBoardPhase", "setup"),
        controlBoardPhaseStartedAt = root.optString("controlBoardPhaseStartedAt", ""),
        phaseHistory = phaseHistory,
        weaponsScores = jsonToStringListMap(root.optJSONObject("weaponsScores")),
        hyungsScores = jsonToStringListMap(root.optJSONObject("hyungsScores")),
        weaponsTieBreakScores = jsonToStringListMap(root.optJSONObject("weaponsTieBreakScores")),
        hyungsTieBreakScores = jsonToStringListMap(root.optJSONObject("hyungsTieBreakScores")),
        weaponsJudgeChoices = jsonToStringListMap(root.optJSONObject("weaponsJudgeChoices")),
        hyungsJudgeChoices = jsonToStringListMap(root.optJSONObject("hyungsJudgeChoices")),
        weaponsFinalize = jsonToFinalizeState(root.optJSONObject("weaponsFinalize")),
        hyungsFinalize = jsonToFinalizeState(root.optJSONObject("hyungsFinalize")),
        pendingTieBreak = pendingTieBreak,
        sparringWinnerIds = sparringWinnerIds,
        sparringBoutProgress = sparringBoutProgress
    )
}
