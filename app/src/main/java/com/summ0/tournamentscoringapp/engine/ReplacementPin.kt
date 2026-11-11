package com.summ0.tournamentscoringapp.engine

import org.json.JSONObject
import java.security.MessageDigest

private const val REPLACEMENT_PIN_ALGORITHM = "SHA-256-salted-v1"
private val replacementPinPattern = Regex("^[\\x21-\\x7E]{4}$")
private val saltPattern = Regex("^[a-fA-F0-9]{32}$")
private val digestPattern = Regex("^[a-fA-F0-9]{64}$")

fun hasReplacementPinVerifier(plan: JSONObject?): Boolean {
    val verifier = plan?.optJSONObject("replacementPinVerifier") ?: return false
    return plan.optBoolean("replacementPinConfigured") &&
        verifier.optString("algorithm") == REPLACEMENT_PIN_ALGORITHM &&
        saltPattern.matches(verifier.optString("salt")) &&
        digestPattern.matches(verifier.optString("digest"))
}

fun verifyReplacementPin(plan: JSONObject?, pin: String): Boolean {
    if (!hasReplacementPinVerifier(plan) || !replacementPinPattern.matches(pin)) return false
    val verifier = plan?.optJSONObject("replacementPinVerifier") ?: return false
    val salt = verifier.getString("salt")
    val expectedDigest = decodeHex(verifier.getString("digest")) ?: return false
    val actualDigest = MessageDigest.getInstance("SHA-256")
        .digest("$salt:$pin".toByteArray(Charsets.UTF_8))
    return MessageDigest.isEqual(expectedDigest, actualDigest)
}

private fun decodeHex(value: String): ByteArray? {
    if (!digestPattern.matches(value)) return null
    return ByteArray(value.length / 2) { index ->
        value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}
