package com.example.examscan
import com.example.examscan.data.ExamFileRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
class BulkGroupingTest {
    @Test fun `ten pages with two per paper creates five papers`() {
        assertEquals(5, ExamFileRules.groupPages((1..10).toList(), 2).size)
    }

    @Test fun `incomplete final paper is retained`() {
        assertEquals(listOf(2, 2, 1), ExamFileRules.groupPages((1..5).toList(), 2).map { it.size })
    }

    @Test fun `empty scan creates no papers`() {
        assertEquals(emptyList<List<Int>>(), ExamFileRules.groupPages(emptyList<Int>(), 2))
    }

    @Test fun `one page per paper creates one paper for every page`() {
        assertEquals(listOf(listOf(1), listOf(2), listOf(3)), ExamFileRules.groupPages(listOf(1, 2, 3), 1))
    }

    @Test fun `invalid pages per paper is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { ExamFileRules.groupPages(listOf(1), 0) }
    }
}
