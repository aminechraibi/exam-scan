package com.example.examscan.ui

internal object ExamUiRules {
    fun canCreateExam(name: String, pagesText: String): Boolean =
        name.isNotBlank() && pagesText.toIntOrNull() in 1..50

    fun canExport(pageCounts: List<Int>): Boolean = pageCounts.any { it > 0 }

    fun isComplete(actualPages: Int, expectedPages: Int): Boolean =
        expectedPages > 0 && actualPages == expectedPages
}
