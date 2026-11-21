package com.summ0.tournamentscoringapp.engine

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerConnectionSecurityTest {
    @Test
    fun updateVersionCanBeCheckedBeforeAnyRingIsAuthorized() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/version") { exchange ->
            assertEquals("GET", exchange.requestMethod)
            assertNull(exchange.requestHeaders.getFirst("Authorization"))
            val response = """{"versionCode":59,"versionName":"1.58"}""".toByteArray()
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()
        try {
            assertEquals(59, fetchServerVersionCode("http://127.0.0.1:${server.address.port}/"))
        } finally {
            server.stop(0)
        }
    }

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
            "https://10.90.111.23:3443",
            buildServerBaseUrl(ServerConnectionMode.IP, "10.90.111.23")
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

    @Test
    fun serverVersionCheckReturnsHttpFailuresForConnectionStatus() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/version") { exchange ->
            val response = "temporarily unavailable".toByteArray()
            exchange.sendResponseHeaders(503, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()
        try {
            val result = checkServerVersion("http://127.0.0.1:${server.address.port}/")

            assertNull(result.versionCode)
            assertTrue(result.errorMessage.orEmpty().contains("HTTP 503"))
        } finally {
            server.stop(0)
        }
    }
}
