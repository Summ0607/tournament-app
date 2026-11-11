package com.summ0.tournamentscoringapp.engine

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DivisionPackageTest {

    // Payloads produced by the server encoder (backend/divisionPackage.js) for a
    // two-competitor division, embedded verbatim to prove cross-repo compatibility.
    private val serverQr =
        """{"type":"tournament-division-qr","version":1,"checksum":"bc6c115cddc09780","data":"Vc1BS8QwFATgvxLm4iUL2aBbzE0pyIKuBxdFxEO0jyYrfSnpyx4s_e8ile16m8PMNyPoSCw73xEcXiILZbVPJbPviEVZYytoNPEYh5gYbkSbU-m3DdycVtZAz7H-a-1K90EZzhoNnuXXVCSoSsJqI0HdlR4an6nrSaKkPMC9jYi_pjVmjdPshr26J4LGIKWJCQ7PvrSBLwZVp4PnFhrZ8xccqhPsW4K71ggU2yBwV3bSC28X_pZYPWaJ3-cHD6mw-Mhq_1Qv-ua_vjZn_OX0Pk0_"}"""

    private val serverPackage =
        """{"type":"tournament-division-package","version":1,"eventName":"Winter Tournament 2027","generatedAt":"2026-11-11T01:22:40.892Z","divisions":[{"groupId":"group-20","groupDivisionNumber":20,"name":"Youth 7th-6th Gup","competitors":[{"id":"2001","name":"Ana Lee","studio":"Vaughn's Dojang","rank":"7th Gup","age":9,"height":52},{"id":"2002","name":"Ben Ortiz","studio":"Mountain TSD","rank":"6th Gup","age":10,"height":54}]}],"ringId":"ring-a-1","ringLabel":"A1"}"""

    @Test
    fun decodesServerGeneratedDivisionQrPayload() {
        val content = decodeDivisionQrPayload(serverQr)
        assertEquals("Winter Tournament 2027", content.eventName)
        assertEquals("group-20", content.group.groupId)
        assertEquals(20, content.group.groupDivisionNumber)
        assertEquals(2, content.group.competitors.size)
        val ana = content.group.competitors[0]
        assertEquals("2001", ana.id)
        assertEquals("Ana Lee", ana.name)
        assertEquals("Vaughn's Dojang", ana.studio)
        assertEquals("7th Gup", ana.rank)
        assertEquals(9, ana.age)
        assertEquals(52, ana.heightInInches)
        assertEquals(54, content.group.competitors[1].heightInInches)
    }

    @Test
    fun qrAndNetworkPackageProduceTheSameGroup() {
        val fromQr = decodeDivisionQrPayload(serverQr).group
        val fromPackage = parseDivisionPackage(JSONObject(serverPackage)).groups.single()
        assertEquals(fromQr, fromPackage)
    }

    @Test
    fun parsesNetworkDivisionPackageMetadata() {
        val content = parseDivisionPackage(JSONObject(serverPackage))
        assertEquals("Winter Tournament 2027", content.eventName)
        assertEquals("ring-a-1", content.ringId)
        assertEquals("A1", content.ringLabel)
        assertEquals(listOf(20), content.groups.map { it.groupDivisionNumber })
    }

    @Test
    fun rejectsForeignTamperedAndWrongVersionQrCodes() {
        assertThrows(DivisionQrException::class.java) {
            decodeDivisionQrPayload("""{"type":"tournament-ring","version":1}""")
        }
        assertThrows(DivisionQrException::class.java) {
            decodeDivisionQrPayload("not-json")
        }
        val tampered = JSONObject(serverQr).put("checksum", "0".repeat(16)).toString()
        val integrity = assertThrows(DivisionQrException::class.java) { decodeDivisionQrPayload(tampered) }
        assertTrue(integrity.message!!.contains("integrity"))
        val wrongVersion = JSONObject(serverQr).put("version", 2).toString()
        assertThrows(DivisionQrException::class.java) { decodeDivisionQrPayload(wrongVersion) }
    }

    @Test
    fun exposesTabletCompetitorCap() {
        assertEquals(12, MAX_TABLET_DIVISION_COMPETITORS)
    }
}
