package com.summ0.tournamentscoringapp.engine

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Base64
import java.util.zip.Inflater

const val DIVISION_QR_TYPE = "tournament-division-qr"
const val DIVISION_PACKAGE_TYPE = "tournament-division-package"
const val DIVISION_PACKAGE_VERSION = 1
const val MAX_TABLET_DIVISION_COMPETITORS = 12
private const val CHECKSUM_LENGTH = 16

/** A single division decoded from a self-contained QR code. */
data class DivisionQrContent(
    val eventName: String,
    val group: RemoteGroup
)

/** The set of divisions delivered over the network for a ring. */
data class DivisionPackageContent(
    val eventName: String,
    val ringId: String,
    val ringLabel: String,
    val groups: List<RemoteGroup>
)

class DivisionQrException(message: String) : Exception(message)

private fun checksumOf(json: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(json.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }.substring(0, CHECKSUM_LENGTH)
}

private fun inflateRaw(data: ByteArray): ByteArray {
    val inflater = Inflater(true)
    inflater.setInput(data)
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(4096)
    try {
        while (!inflater.finished()) {
            val count = inflater.inflate(buffer)
            if (count == 0) {
                if (inflater.finished() || inflater.needsDictionary()) break
                if (inflater.needsInput()) throw DivisionQrException("Division QR code data is incomplete.")
            }
            output.write(buffer, 0, count)
        }
    } finally {
        inflater.end()
    }
    return output.toByteArray()
}

/**
 * Decodes a scanned `tournament-division-qr` payload. Mirrors the server
 * encoder in backend/divisionPackage.js: raw-deflate + base64url with a
 * SHA-256 integrity prefix. The embedded division is parsed by the same
 * [parseRemoteGroup] used for network groups, so both paths are identical.
 */
fun decodeDivisionQrPayload(scannedText: String): DivisionQrContent {
    val payload = try {
        JSONObject(scannedText.trim())
    } catch (_: Exception) {
        throw DivisionQrException("This is not a division QR code.")
    }
    if (payload.optString("type") != DIVISION_QR_TYPE) {
        throw DivisionQrException("This is not a division QR code.")
    }
    if (payload.optInt("version", -1) != DIVISION_PACKAGE_VERSION) {
        throw DivisionQrException("This division QR code uses an unsupported version.")
    }
    val data = payload.optString("data").trim()
    val checksum = payload.optString("checksum").trim()
    if (data.isEmpty()) throw DivisionQrException("Division QR code is missing its data.")
    if (checksum.length != CHECKSUM_LENGTH) throw DivisionQrException("Division QR code is missing its checksum.")

    val json = try {
        inflateRaw(Base64.getUrlDecoder().decode(data)).toString(Charsets.UTF_8)
    } catch (error: DivisionQrException) {
        throw error
    } catch (_: Exception) {
        throw DivisionQrException("Division QR code data could not be read.")
    }
    if (checksumOf(json) != checksum) {
        throw DivisionQrException("Division QR code failed its integrity check. Rescan or reprint it.")
    }

    val root = try {
        JSONObject(json)
    } catch (_: Exception) {
        throw DivisionQrException("Division QR code contained invalid data.")
    }
    val divisionJson = root.optJSONObject("division")
        ?: throw DivisionQrException("Division QR code did not contain a division.")
    val group = try {
        parseRemoteGroup(divisionJson)
    } catch (_: Exception) {
        throw DivisionQrException("Division QR code contained an invalid division.")
    }
    if (group.competitors.isEmpty() || group.competitors.size > MAX_TABLET_DIVISION_COMPETITORS) {
        throw DivisionQrException("Division QR code must contain 1 to $MAX_TABLET_DIVISION_COMPETITORS competitors.")
    }
    return DivisionQrContent(eventName = root.optString("eventName", ""), group = group)
}

/** Parses a `tournament-division-package` network response into its divisions. */
fun parseDivisionPackage(root: JSONObject): DivisionPackageContent {
    require(root.optString("type") == DIVISION_PACKAGE_TYPE) { "Not a division package response" }
    require(root.optInt("version", -1) == DIVISION_PACKAGE_VERSION) { "Unsupported division package version" }
    val divisionsJson = root.optJSONArray("divisions")
        ?: throw IllegalArgumentException("Division package did not include divisions")
    val groups = buildList {
        for (index in 0 until divisionsJson.length()) {
            add(parseRemoteGroup(divisionsJson.getJSONObject(index)))
        }
    }
    return DivisionPackageContent(
        eventName = root.optString("eventName", ""),
        ringId = root.optString("ringId", ""),
        ringLabel = root.optString("ringLabel", ""),
        groups = groups
    )
}
