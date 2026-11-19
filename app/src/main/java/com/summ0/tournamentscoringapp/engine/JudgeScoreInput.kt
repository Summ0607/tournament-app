package com.summ0.tournamentscoringapp.engine

private val COMPLETE_JUDGE_SCORE = Regex("[5-9](\\.[0-9])?")
private val PARTIAL_JUDGE_SCORE = Regex("[5-9]\\.")

fun normalizeJudgeScoreInput(currentValue: String, proposedValue: String): String {
    if (proposedValue.isEmpty()) return ""
    if (currentValue.endsWith(".") && proposedValue == currentValue.dropLast(1)) return ""

    val digits = proposedValue.filter { it in '0'..'9' }
    if (digits.isEmpty()) return currentValue

    val whole = digits.first()
    if (whole !in '5'..'9') return currentValue

    return if (digits.length == 1) "$whole." else "$whole.${digits[1]}"
}

fun isValidJudgeScore(value: String): Boolean =
    COMPLETE_JUDGE_SCORE.matches(value) && (value.toDoubleOrNull()?.let { it in 5.0..9.9 } == true)

fun isPartialJudgeScore(value: String): Boolean = PARTIAL_JUDGE_SCORE.matches(value)

fun shouldAdvanceJudgeScoreFocus(currentValue: String, proposedValue: String): Boolean {
    return !isValidJudgeScore(currentValue) &&
        isValidJudgeScore(normalizeJudgeScoreInput(currentValue, proposedValue))
}
