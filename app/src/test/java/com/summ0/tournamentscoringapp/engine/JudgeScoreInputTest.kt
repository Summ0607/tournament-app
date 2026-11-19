package com.summ0.tournamentscoringapp.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JudgeScoreInputTest {
    @Test
    fun insertsDecimalAfterFirstValidDigit() {
        assertEquals("5.", normalizeJudgeScoreInput("", "5"))
        assertEquals("9.", normalizeJudgeScoreInput("", "9"))
    }

    @Test
    fun acceptsEveryDecimalDigitAndLimitsInputToOnePlace() {
        assertEquals("5.0", normalizeJudgeScoreInput("5.", "5.0"))
        assertEquals("7.4", normalizeJudgeScoreInput("7.", "7.4"))
        assertEquals("9.9", normalizeJudgeScoreInput("9.", "9.987"))
    }

    @Test
    fun rejectsInvalidWholeDigitsAndPreservesCurrentValue() {
        assertEquals("", normalizeJudgeScoreInput("", "4"))
        assertEquals("", normalizeJudgeScoreInput("", "0"))
        assertEquals("7.4", normalizeJudgeScoreInput("7.4", "4.4"))
    }

    @Test
    fun handlesBackspaceAndClear() {
        assertEquals("7.", normalizeJudgeScoreInput("7.4", "7."))
        assertEquals("", normalizeJudgeScoreInput("7.", "7"))
        assertEquals("", normalizeJudgeScoreInput("7.4", ""))
    }

    @Test
    fun validatesScoreRangeAndPartialInput() {
        assertTrue(isValidJudgeScore("5"))
        assertTrue(isValidJudgeScore("5.0"))
        assertTrue(isValidJudgeScore("9.9"))
        assertFalse(isValidJudgeScore("5."))
        assertFalse(isValidJudgeScore("4.9"))
        assertFalse(isValidJudgeScore("10.0"))
        assertTrue(isPartialJudgeScore("8."))
        assertFalse(isPartialJudgeScore("8.1"))
    }

    @Test
    fun advancesFocusWhenTheSecondDigitCompletesAScore() {
        assertTrue(shouldAdvanceJudgeScoreFocus("", "85"))
        assertTrue(shouldAdvanceJudgeScoreFocus("8.", "8.5"))
        assertFalse(shouldAdvanceJudgeScoreFocus("", "8."))
        assertFalse(shouldAdvanceJudgeScoreFocus("8.5", "9.5"))
    }
}
