package com.summ0.tournamentscoringapp

object RankFormatter {
    private val rankLevelByCode = mapOf(
        "TTLD" to 0,
        "G1" to 1,
        "G2" to 2,
        "G3" to 3,
        "G4" to 4,
        "G5" to 5,
        "G6" to 6,
        "G7" to 7,
        "G8" to 8,
        "G9" to 9,
        "G10" to 10,
        "CDB" to 11,
        "D1" to -1,
        "D2" to -2,
        "D3" to -3,
        "D4" to -4,
        "D5" to -5,
        "D6" to -6,
        "D7" to -7,
        "D8" to -8,
        "D9" to -9
    )

    private val displayByCode = mapOf(
        "TTLD" to "TTLD",
        "G1" to "1st Gup",
        "G2" to "2nd Gup",
        "G3" to "3rd Gup",
        "G4" to "4th Gup",
        "G5" to "5th Gup",
        "G6" to "6th Gup",
        "G7" to "7th Gup",
        "G8" to "8th Gup",
        "G9" to "9th Gup",
        "G10" to "10th Gup",
        "CDB" to "Cho Dan Bo",
        "D1" to "1st Dan",
        "D2" to "2nd Dan",
        "D3" to "3rd Dan",
        "D4" to "4th Dan",
        "D5" to "5th Dan",
        "D6" to "6th Dan",
        "D7" to "7th Dan",
        "D8" to "8th Dan",
        "D9" to "9th Dan"
    )

    private val codesInDisplayOrder = listOf(
        "TTLD",
        "G10",
        "G9",
        "G8",
        "G7",
        "G6",
        "G5",
        "G4",
        "G3",
        "G2",
        "G1",
        "CDB",
        "D1",
        "D2",
        "D3",
        "D4",
        "D5",
        "D6",
        "D7",
        "D8",
        "D9"
    )

    fun normalizeCode(rankCode: String): String {
        return rankCode.trim().uppercase()
    }

    fun rankLevelFor(rankCode: String): Int {
        return rankLevelByCode[normalizeCode(rankCode)] ?: Int.MAX_VALUE
    }

    fun formatForDisplay(rankCode: String): String {
        return displayByCode[normalizeCode(rankCode)] ?: rankCode.trim()
    }

    fun rankCodeForLevel(level: Int): String {
        return when (level) {
            0 -> "TTLD"
            1 -> "G1"
            2 -> "G2"
            3 -> "G3"
            4 -> "G4"
            5 -> "G5"
            6 -> "G6"
            7 -> "G7"
            8 -> "G8"
            9 -> "G9"
            10 -> "G10"
            11 -> "CDB"
            -1 -> "D1"
            -2 -> "D2"
            -3 -> "D3"
            -4 -> "D4"
            -5 -> "D5"
            -6 -> "D6"
            -7 -> "D7"
            -8 -> "D8"
            -9 -> "D9"
            else -> level.toString()
        }
    }

    fun allRankCodes(): List<String> = codesInDisplayOrder
}
