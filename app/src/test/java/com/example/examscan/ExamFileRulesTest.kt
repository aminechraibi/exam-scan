package com.example.examscan.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ExamFileRulesTest {
    @Test fun `safe filename keeps portable characters`() {
        assertEquals("Math_2026-final.pdf", ExamFileRules.safeFileName("Math_2026-final.pdf"))
    }

    @Test fun `safe filename replaces spaces and unsafe characters`() {
        assertEquals("Final_Exam_July", ExamFileRules.safeFileName("Final Exam: July?"))
    }

    @Test fun `safe filename trims replacement separators`() {
        assertEquals("Biology", ExamFileRules.safeFileName("///Biology///"))
    }

    @Test fun `blank safe filename falls back to exam`() {
        assertEquals("exam", ExamFileRules.safeFileName("   "))
    }
}
