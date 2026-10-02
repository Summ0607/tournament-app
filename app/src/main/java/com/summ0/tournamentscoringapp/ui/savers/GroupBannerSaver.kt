package com.summ0.tournamentscoringapp

import androidx.compose.runtime.saveable.Saver
import com.summ0.tournamentscoringapp.engine.GroupBanner
import com.summ0.tournamentscoringapp.engine.parseDivisionNumberFromLegacyGroupId
import org.json.JSONObject

val groupBannerSaver = Saver<GroupBanner, String>(
    save = { banner ->
        JSONObject().apply {
            put("groupDivisionNumber", banner.groupDivisionNumber)
            put("groupDivisionName", banner.groupDivisionName)
            put("statusText", banner.statusText)
        }.toString()
    },
    restore = { saved ->
        val json = saved ?: "{}"
        val map = JSONObject(json)
        val legacyGroupId = map.optString("groupId", "")
        val legacyDetails = map.optString("details", "")
        val savedNumber = when {
            map.has("groupDivisionNumber") && !map.isNull("groupDivisionNumber") -> {
                val rawValue = map.opt("groupDivisionNumber")
                when (rawValue) {
                    is Number -> rawValue.toInt().takeIf { it > 0 }
                    is String -> rawValue.trim().toIntOrNull()?.takeIf { it > 0 }
                    else -> null
                }
            }
            else -> parseDivisionNumberFromLegacyGroupId(legacyGroupId)
        }
        val savedName = map.optString("groupDivisionName", "").trim().takeIf { it.isNotEmpty() }
            ?: legacyDetails.trim().takeIf { it.isNotEmpty() }
        GroupBanner(
            groupDivisionNumber = savedNumber,
            groupDivisionName = savedName,
            statusText = map.optString("statusText", "")
        )
    }
)
