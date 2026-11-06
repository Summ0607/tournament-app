package com.summ0.tournamentscoringapp.engine

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RingPairingCodeTest {
    private fun code() = JSONObject().put("type", "tournament-ring").put("version", 1)
        .put("server", "https://tourn-svr.home.arpa:3443").put("event", "Test Tournament")
        .put("ringId", "ring-a-1").put("day", "2026-11-05").put("token", "a".repeat(64))

    @Test fun dailyRingPayloadParsesWithoutManualRingSelection() {
        val parsed = parseRingPairingCode(code().toString())
        assertEquals("https://tourn-svr.home.arpa:3443", parsed.server)
        assertEquals("ring-a-1", parsed.ringId)
        assertEquals("Test Tournament", parsed.event)
        assertEquals("2026-11-05", parsed.day)
    }

    @Test fun ipAddressIsSupportedWithoutDisablingHttps() {
        assertEquals("https://192.168.4.42:3443",
            parseRingPairingCode(code().put("server", "https://192.168.4.42:3443/").toString()).server)
    }

    @Test fun rejectsUnsafeAddressesAndInvalidCodes() {
        for (server in listOf("http://tourn-svr.home.arpa:3000", "https://user:pass@server",
            "https://server/path", "https://server?token=x", "https://server#fragment")) {
            assertThrows(IllegalArgumentException::class.java) {
                parseRingPairingCode(code().put("server", server).toString())
            }
        }
        for ((key, value) in listOf("version" to "2", "type" to "other", "ringId" to "../ring",
            "token" to "invalid", "event" to "", "day" to "today")) {
            assertThrows(IllegalArgumentException::class.java) {
                parseRingPairingCode(code().put(key, value).toString())
            }
        }
    }
}
