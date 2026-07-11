package com.example.examscan.data

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.nio.file.Files

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

    @Test fun `paper labels contain paper ID only`() {
        assertEquals("Paper 1", ExamFileRules.paperLabel(1))
        assertEquals("Paper 25", ExamFileRules.paperLabel(25))
        assertFalse(ExamFileRules.paperLabel(4).contains("Page"))
    }

    @Test fun `existing export receives a non-overwriting name`() {
        val directory = Files.createTempDirectory("exam-export").toFile()
        directory.resolve("Math.pdf").writeText("existing")
        val next = ExamFileRules.uniqueFile(directory, "Math.pdf")
        assertEquals("Math_2.pdf", next.name)
        assertTrue(directory.resolve("Math.pdf").exists())
        directory.deleteRecursively()
    }
}
