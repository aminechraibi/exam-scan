package com.example.examscan.data

internal object ExamFileRules {
    fun <T> groupPages(pages: List<T>, pagesPerPaper: Int): List<List<T>> {
        require(pagesPerPaper > 0) { "Pages per paper must be greater than zero" }
        return pages.chunked(pagesPerPaper)
    }

    fun safeFileName(value: String): String = value
        .replace(Regex("[^A-Za-z0-9._-]+"), "_")
        .trim('_')
        .ifBlank { "exam" }
}
