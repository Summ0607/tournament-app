package com.summ0.tournamentscoringapp

import androidx.compose.runtime.saveable.Saver
import com.summ0.tournamentscoringapp.engine.Competitor
import com.summ0.tournamentscoringapp.engine.parseCompetitorList
import com.summ0.tournamentscoringapp.engine.serializeCompetitorList
import org.json.JSONArray

val competitorListSaver = Saver<List<Competitor>, String>(
    save = { competitors -> serializeCompetitorList(competitors).toString() },
    restore = { saved -> parseCompetitorList(JSONArray(saved ?: "[]")) }
)
