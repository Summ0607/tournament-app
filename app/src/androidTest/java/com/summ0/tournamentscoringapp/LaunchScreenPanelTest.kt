package com.summ0.tournamentscoringapp

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LaunchScreenPanelTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun waitingRingCanOpenCheckInAndFinalizeWithoutADivisionOrNetwork() {
        var checkIns = 0
        var finalizations = 0
        showPanel(
            ringId = "ring-a-1",
            onCheckIn = { checkIns++ },
            onFinalize = { finalizations++ }
        )

        compose.onNodeWithText("Judge Check-In").performScrollTo().performClick()
        compose.onNodeWithText("Finalize phase / upload service").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, checkIns)
            assertEquals(1, finalizations)
        }
    }

    @Test
    fun unassignedTabletDoesNotOfferRingServiceActions() {
        showPanel(ringId = "")

        compose.onNodeWithText("Judge Check-In").assertDoesNotExist()
        compose.onNodeWithText("Finalize phase / upload service").assertDoesNotExist()
    }

    private fun showPanel(
        ringId: String,
        onCheckIn: () -> Unit = {},
        onFinalize: () -> Unit = {}
    ) {
        compose.setContent {
            MaterialTheme {
                LaunchScreenPanel(
                    currentRingLabel = if (ringId.isBlank()) "" else "A1",
                    currentRingId = ringId,
                    status = "Waiting for the next group.",
                    serverReachable = false,
                    availableRings = emptyList(),
                    selectedRingId = ringId,
                    isCompactScreen = true,
                    onSelectRing = {},
                    onConnectSelectedRing = {},
                    onScanRing = {},
                    onScanDivisionQr = {},
                    onOpenJudgeService = onCheckIn,
                    onFinalizeJudgeService = onFinalize,
                    onOpenStoredDivisions = {},
                    storedDivisionCount = 2,
                    onEditServer = {},
                    onExit = {}
                )
            }
        }
    }
}
