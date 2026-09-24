package com.summ0.tournamentscoringapp

import androidx.compose.runtime.saveable.Saver
import com.summ0.tournamentscoringapp.engine.CheckInStatus
import com.summ0.tournamentscoringapp.engine.CompetitionEntry
import com.summ0.tournamentscoringapp.engine.CompetitionRegistrationStatus
import com.summ0.tournamentscoringapp.engine.CompetitionType
import com.summ0.tournamentscoringapp.engine.Competitor
import org.json.JSONArray
import org.json.JSONObject

val competitorListSaver = Saver<List<Competitor>, String>(
    save = { competitors ->
        val root = JSONArray()
        competitors.forEach { competitor ->
            val item = JSONObject().apply {
                put("id", competitor.id)
                put("name", competitor.name)
                put("studio", competitor.studio)
                put("rank", competitor.rank)
                put("rankLevel", competitor.rankLevel)
                put("age", competitor.age)
                put("heightInInches", competitor.heightInInches)
                put("checkInStatus", competitor.checkInStatus.name)
                val entries = JSONArray()
                competitor.competitionEntries.forEach { (type, entry) ->
                    val pair = JSONArray().apply {
                        put(type.name)
                        put(entry.status.name)
                    }
                    entries.put(pair)
                }
                put("competitionEntries", entries)
            }
            root.put(item)
        }
        root.toString()
    },
    restore = { saved ->
        val root = JSONArray(saved ?: "[]")
        val parsed = mutableListOf<Competitor>()
        for (index in 0 until root.length()) {
            val item = root.getJSONObject(index)
            val competitionEntryMap = mutableMapOf<CompetitionType, CompetitionEntry>()
            val entries = item.optJSONArray("competitionEntries") ?: JSONArray()
            for (entryIndex in 0 until entries.length()) {
                val pair = entries.getJSONArray(entryIndex)
                val typeName = pair.optString(0, "")
                val statusName = pair.optString(1, "")
                val type = try {
                    CompetitionType.valueOf(typeName)
                } catch (_: IllegalArgumentException) {
                    continue
                }
                val status = try {
                    CompetitionRegistrationStatus.valueOf(statusName)
                } catch (_: IllegalArgumentException) {
                    CompetitionRegistrationStatus.REGISTERED
                }
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
                checkInStatus = try {
                    CheckInStatus.valueOf(item.optString("checkInStatus", CheckInStatus.REGISTERED.name))
                } catch (_: IllegalArgumentException) {
                    CheckInStatus.REGISTERED
                },
                competitionEntries = competitionEntryMap
            )
        }
        parsed
    }
)
