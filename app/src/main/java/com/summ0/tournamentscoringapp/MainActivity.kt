package com.summ0.tournamentscoringapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import com.summ0.tournamentscoringapp.engine.CompetitionScreen
import com.summ0.tournamentscoringapp.ui.theme.TournamentScoringAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launchScreen = intent?.getStringExtra(EXTRA_LAUNCH_SCREEN)
        val initialScreen = launchScreen?.let(::launchInitialScreen) ?: CompetitionScreen.CHECK_IN

        enableEdgeToEdge()
        setContent {
            TournamentScoringAppTheme {
                Scaffold(modifier = androidx.compose.ui.Modifier.fillMaxSize()) { innerPadding ->
                    CheckInScreen(
                        initialScreen = initialScreen,
                        modifier = androidx.compose.ui.Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

private fun launchInitialScreen(value: String): CompetitionScreen {
    return when (value) {
        LAUNCH_SCREEN_WEAPONS -> CompetitionScreen.WEAPONS_SCORING
        LAUNCH_SCREEN_HYUNGS -> CompetitionScreen.HYUNGS_SCORING
        else -> CompetitionScreen.CHECK_IN
    }
}
