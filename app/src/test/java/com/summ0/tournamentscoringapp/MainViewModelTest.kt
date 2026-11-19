package com.summ0.tournamentscoringapp

import com.summ0.tournamentscoringapp.engine.DivisionResultPacketStore
import com.summ0.tournamentscoringapp.engine.DivisionResultSubmissionNetworkFailure
import com.summ0.tournamentscoringapp.engine.DivisionResultSubmissionResult
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MainViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun pendingSubmissionSyncUsesOldestFirstAndStopsAtFirstFailure() = runBlocking {
        val store = DivisionResultPacketStore(folder.root)
        store.savePending(
            JSONObject().put("submissionId", "older").put("ringId", "ring-1"),
            "https://server",
            "ring-1"
        ).setLastModified(1L)
        store.savePending(
            JSONObject().put("submissionId", "newer").put("ringId", "ring-1"),
            "https://server",
            "ring-1"
        ).setLastModified(2L)
        val attempted = mutableListOf<String>()
        val viewModel = MainViewModel(
            packetStore = store,
            submitter = { _, _, packet ->
                attempted += packet.getString("submissionId")
                DivisionResultSubmissionResult.NetworkFailure(
                    DivisionResultSubmissionNetworkFailure("offline")
                )
            }
        )

        val results = viewModel.retryPendingSubmissions()

        assertEquals(listOf("older"), attempted)
        assertEquals(1, results.size)
        assertEquals(listOf("older.json", "newer.json"), store.listPending().map { it.name })
    }
}
