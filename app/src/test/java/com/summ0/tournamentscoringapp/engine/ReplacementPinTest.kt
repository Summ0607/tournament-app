package com.summ0.tournamentscoringapp.engine

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplacementPinTest {
    private fun plan(configured: Boolean = true) = JSONObject()
        .put("replacementPinConfigured", configured)
        .put(
            "replacementPinVerifier",
            JSONObject()
                .put("algorithm", "SHA-256-salted-v1")
                .put("salt", "00112233445566778899aabbccddeeff")
                .put("digest", "c8efc3aa91e52e520229a8eac86fb6a851bcc6c0d00cc00c009dafe0e1298ad1")
        )

    @Test
    fun verifiesServerCompatibleEventPinWithoutAcceptingInvalidInput() {
        val savedPlan = plan()
        assertTrue(hasReplacementPinVerifier(savedPlan))
        assertTrue(verifyReplacementPin(savedPlan, "AB12"))
        assertFalse(verifyReplacementPin(savedPlan, "AB13"))
        assertFalse(verifyReplacementPin(savedPlan, "ABC"))
        assertFalse(verifyReplacementPin(savedPlan, "AB 2"))
        assertFalse(verifyReplacementPin(plan(configured = false), "AB12"))
    }

    @Test
    fun rejectsMalformedVerifier() {
        val savedPlan = plan().put(
            "replacementPinVerifier",
            JSONObject()
                .put("algorithm", "SHA-256-salted-v1")
                .put("salt", "short")
                .put("digest", "invalid")
        )
        assertFalse(hasReplacementPinVerifier(savedPlan))
        assertFalse(verifyReplacementPin(savedPlan, "AB12"))
    }
}
