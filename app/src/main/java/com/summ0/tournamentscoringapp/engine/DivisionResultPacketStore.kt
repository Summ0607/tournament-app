package com.summ0.tournamentscoringapp.engine

import org.json.JSONObject
import java.io.File

data class DivisionResultPacketPendingMetadata(
    val submissionId: String,
    val serverBaseUrl: String,
    val ringId: String
)

class DivisionResultPacketStore(private val baseDir: File) {
    private val rootDir = File(baseDir, "result-packets")
    private val pendingDir = File(rootDir, "pending")
    private val sentDir = File(rootDir, "sent")

    fun savePending(packetJson: JSONObject, serverBaseUrl: String, ringId: String): File {
        val submissionId = packetJson.optString("submissionId", "").trim()
        require(submissionId.isNotBlank()) { "Packet submissionId is required" }
        pendingDir.mkdirs()

        val packetFile = pendingFile(submissionId)
        packetFile.writeText(packetJson.toString(2))
        metadataFile(submissionId).writeText(
            JSONObject().apply {
                put("submissionId", submissionId)
                put("serverBaseUrl", serverBaseUrl)
                put("ringId", ringId)
            }.toString(2)
        )
        return packetFile
    }

    fun listPending(): List<File> {
        pendingDir.mkdirs()
        return pendingDir.listFiles()
            ?.filter { it.isFile && it.extension.equals("json", ignoreCase = true) && !it.name.endsWith(".meta.json") }
            ?.sortedBy { it.lastModified() }
            ?: emptyList()
    }

    fun loadPacket(packetFile: File): JSONObject {
        return JSONObject(packetFile.readText())
    }

    fun loadMetadata(packetFile: File): DivisionResultPacketPendingMetadata {
        val submissionId = submissionIdFromPacketFile(packetFile)
        val metadata = JSONObject(metadataFile(submissionId).readText())
        return DivisionResultPacketPendingMetadata(
            submissionId = metadata.optString("submissionId", submissionId),
            serverBaseUrl = metadata.optString("serverBaseUrl", ""),
            ringId = metadata.optString("ringId", "")
        )
    }

    fun markSent(packetFile: File, serverResponse: JSONObject): File {
        val submissionId = submissionIdFromPacketFile(packetFile)
        val sentPacketDir = File(sentDir, submissionId)
        sentPacketDir.mkdirs()

        val sentPacketFile = File(sentPacketDir, "packet.json")
        val sentResponseFile = File(sentPacketDir, "response.json")
        val sentMetadataFile = File(sentPacketDir, "metadata.json")

        packetFile.copyTo(sentPacketFile, overwrite = true)
        serverResponse.toString(2).let(sentResponseFile::writeText)
        if (metadataFile(submissionId).exists()) {
            metadataFile(submissionId).copyTo(sentMetadataFile, overwrite = true)
        }
        packetFile.delete()
        metadataFile(submissionId).delete()
        return sentPacketFile
    }

    private fun pendingFile(submissionId: String): File {
        return File(pendingDir, "$submissionId.json")
    }

    private fun metadataFile(submissionId: String): File {
        return File(pendingDir, "$submissionId.meta.json")
    }

    private fun submissionIdFromPacketFile(packetFile: File): String {
        return packetFile.nameWithoutExtension
    }
}
