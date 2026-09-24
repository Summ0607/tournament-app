package com.summ0.tournamentscoringapp

import androidx.compose.runtime.saveable.Saver
import com.summ0.tournamentscoringapp.engine.GroupBanner
import org.json.JSONObject

val groupBannerSaver = Saver<GroupBanner, String>(
    save = { banner ->
        JSONObject().apply {
            put("groupId", banner.groupId)
            put("details", banner.details)
            put("statusText", banner.statusText)
        }.toString()
    },
    restore = { saved ->
        val json = saved ?: "{}"
        val map = JSONObject(json)
        GroupBanner(
            groupId = map.optString("groupId", ""),
            details = map.optString("details", ""),
            statusText = map.optString("statusText", "")
        )
    }
)
