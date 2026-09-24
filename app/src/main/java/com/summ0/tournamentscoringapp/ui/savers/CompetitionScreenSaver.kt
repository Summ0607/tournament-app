package com.summ0.tournamentscoringapp

import androidx.compose.runtime.saveable.Saver
import com.summ0.tournamentscoringapp.engine.CompetitionScreen

internal val competitionScreenSaver = Saver<CompetitionScreen, String>(
    save = { it.name },
    restore = { name ->
        try { CompetitionScreen.valueOf(name) } catch (_: IllegalArgumentException) { CompetitionScreen.CHECK_IN }
    }
)
