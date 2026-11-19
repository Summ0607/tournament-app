package com.summ0.tournamentscoringapp.engine

import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

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
        writeAtomic(
            metadataFile(submissionId),
            JSONObject().apply {
                put("submissionId", submissionId)
                put("serverBaseUrl", serverBaseUrl)
                put("ringId", ringId)
            }.toString(2)
        )
        writeAtomic(packetFile, packetJson.toString(2))
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

    fun findPendingPacket(
        eventName: String,
        ringId: String,
        groupId: String,
        groupDivisionNumber: Int
    ): File? = listPending().firstOrNull { file ->
        val packet = loadPacket(file)
        packet.optString("eventName") == eventName &&
            packet.optString("ringId") == ringId &&
            packet.optString("groupId") == groupId &&
            packet.optInt("groupDivisionNumber") == groupDivisionNumber
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

    fun loadSentPacket(submissionId: String): JSONObject? {
        val file = File(File(sentDir, submissionId), "packet.json")
        return if (file.isFile) JSONObject(file.readText()) else null
    }

    fun listSentPackets(): List<JSONObject> {
        sentDir.mkdirs()
        return sentDir.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { directory ->
                File(directory, "packet.json").takeIf { it.isFile }?.let { JSONObject(it.readText()) }
            }
            ?: emptyList()
    }

    fun markSent(packetFile: File, serverResponse: JSONObject): File {
        val submissionId = submissionIdFromPacketFile(packetFile)
        val sentPacketDir = File(sentDir, submissionId)
        sentPacketDir.mkdirs()

        val sentPacketFile = File(sentPacketDir, "packet.json")
        val sentResponseFile = File(sentPacketDir, "response.json")
        val sentMetadataFile = File(sentPacketDir, "metadata.json")
        val sanitizedResponse = JSONObject(serverResponse.toString()).apply {
            remove("status")
        }

        packetFile.copyTo(sentPacketFile, overwrite = true)
        sanitizedResponse.toString(2).let(sentResponseFile::writeText)
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

    private fun writeAtomic(target: File, content: String) {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.writeText(content)
        try {
            Files.move(
                temp.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: Exception) {
            target.delete()
            if (!temp.renameTo(target)) throw IOException("Unable to persist ${target.name}")
        }
    }
}
