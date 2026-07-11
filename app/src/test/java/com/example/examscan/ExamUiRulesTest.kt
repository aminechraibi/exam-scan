package com.example.examscan

import com.example.examscan.ui.ExamUiRules
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamUiRulesTest {
    @Test fun creationRequiresNameAndPageCountBetweenOneAndFifty() {
        assertFalse(ExamUiRules.canCreateExam("", "2"))
        assertFalse(ExamUiRules.canCreateExam("Math", "0"))
        assertFalse(ExamUiRules.canCreateExam("Math", "-1"))
        assertFalse(ExamUiRules.canCreateExam("Math", "51"))
        assertFalse(ExamUiRules.canCreateExam("Math", "not a number"))
        assertTrue(ExamUiRules.canCreateExam("Math", "1"))
        assertTrue(ExamUiRules.canCreateExam("Math", "50"))
    }

    @Test fun exportRequiresAtLeastOnePage() {
        assertFalse(ExamUiRules.canExport(emptyList()))
        assertFalse(ExamUiRules.canExport(listOf(0, 0)))
        assertTrue(ExamUiRules.canExport(listOf(0, 1)))
    }

    @Test fun completenessRequiresExactPositiveExpectedCount() {
        assertFalse(ExamUiRules.isComplete(0, 0))
        assertFalse(ExamUiRules.isComplete(1, 2))
        assertTrue(ExamUiRules.isComplete(2, 2))
        assertFalse(ExamUiRules.isComplete(3, 2))
    }
}
