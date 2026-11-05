package com.summ0.tournamentscoringapp.engine

import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerConnectionSecurityTest {
    @Test
    fun dnsAddressAlwaysUsesConfiguredHttpsEndpoint() {
        assertEquals(
            "https://tourn-svr.home.arpa:3443",
            buildServerBaseUrl(ServerConnectionMode.DNS, "http://tourn-svr.home.arpa:3000/old-path")
        )
    }

    @Test
    fun manualIpAddressUsesHttpsAndKeepsTheCertificateAddress() {
        assertEquals(
            "https://192.168.4.42:3443",
            buildServerBaseUrl(ServerConnectionMode.IP, "192.168.4.42")
        )
        assertEquals(
            "https://192.168.4.42:3443",
            buildServerBaseUrl(ServerConnectionMode.IP, "http://192.168.4.42:3000")
        )
    }

    @Test
    fun onlyThePreviouslyConfiguredDefaultDnsNameIsMigrated() {
        assertEquals(
            DEFAULT_SERVER_HOSTNAME,
            migrateLegacyDnsServerName(LEGACY_DEFAULT_SERVER_HOSTNAME)
        )
        assertEquals(
            "custom-tournament-host.local",
            migrateLegacyDnsServerName("custom-tournament-host.local")
        )
    }

    @Test
    fun pendingSubmissionBaseUrlsMigrateToHttpsWithoutChangingCustomHosts() {
        assertEquals(
            "https://tourn-svr.home.arpa:3443",
            migrateLegacyServerBaseUrl("http://HomePC-Sum2:3000")
        )
        assertEquals(
            "https://192.168.4.42:3443",
            migrateLegacyServerBaseUrl("http://192.168.4.42:3000")
        )
        assertEquals(
            "https://custom-host.local:3443",
            migrateLegacyServerBaseUrl("http://custom-host.local:3000")
        )
    }

    @Test
    fun certificateFailureExplainsSafeNextSteps() {
        val message = describeNetworkError(SSLHandshakeException("certificate unknown"))

        assertTrue(message.contains("TLS certificate validation failed"))
        assertTrue(message.contains("tourn-svr.home.arpa"))
        assertTrue(message.contains("Do not switch to HTTP"))
    }
}
