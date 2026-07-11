package com.example.examscan.data

import java.io.File

internal object ExamFileRules {
    fun <T> groupPages(pages: List<T>, pagesPerPaper: Int): List<List<T>> {
        require(pagesPerPaper > 0) { "Pages per paper must be greater than zero" }
        return pages.chunked(pagesPerPaper)
    }

    fun safeFileName(value: String): String = value
        .replace(Regex("[^A-Za-z0-9._-]+"), "_")
        .trim('_')
        .ifBlank { "exam" }

    fun paperLabel(paperNumber: Int): String {
        require(paperNumber > 0) { "Paper number must be greater than zero" }
        return "Paper $paperNumber"
    }

    fun uniqueFile(directory: File, fileName: String): File {
        val requested = File(directory, fileName)
        if (!requested.exists()) return requested
        val extension = requested.extension.takeIf { it.isNotEmpty() }?.let { ".$it" }.orEmpty()
        val base = requested.name.removeSuffix(extension)
        var suffix = 2
        while (true) {
            val candidate = File(directory, "${base}_$suffix$extension")
            if (!candidate.exists()) return candidate
            suffix++
        }
    }
}
