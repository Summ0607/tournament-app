package com.summ0.tournamentscoringapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.summ0.tournamentscoringapp.engine.CompetitionScreen
import com.summ0.tournamentscoringapp.engine.TabletAuthorization
import com.summ0.tournamentscoringapp.engine.appVersionLabel
import com.summ0.tournamentscoringapp.ui.theme.TournamentScoringAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TabletAuthorization.initialize(applicationContext)
        val launchScreen = intent?.getStringExtra(EXTRA_LAUNCH_SCREEN)
        val initialScreen = launchScreen?.let(::launchInitialScreen) ?: CompetitionScreen.CHECK_IN

        enableEdgeToEdge()
        setContent {
            TournamentScoringAppTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        Text(
                            text = appVersionLabel(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End
                        )
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        CheckInScreen(initialScreen = initialScreen)
                    }
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
