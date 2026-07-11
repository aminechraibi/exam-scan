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

    @Test fun `one of two pages creates one incomplete paper`() = assertGroupSizes(1, 2, listOf(1))

    @Test fun `two of two pages creates one complete paper`() = assertGroupSizes(2, 2, listOf(2))

    @Test fun `nine pages creates four complete and one incomplete paper`() = assertGroupSizes(9, 2, listOf(2, 2, 2, 2, 1))

    @Test fun `eleven pages creates five complete and one incomplete paper`() = assertGroupSizes(11, 2, listOf(2, 2, 2, 2, 2, 1))

    @Test fun `twelve pages with three per paper creates four complete papers`() = assertGroupSizes(12, 3, listOf(3, 3, 3, 3))

    @Test fun `four hundred pages retain order across one hundred papers`() {
        val groups = ExamFileRules.groupPages((1..400).toList(), 4)
        assertEquals(100, groups.size)
        assertEquals((1..400).toList(), groups.flatten())
        assertEquals((1..100).toList(), groups.indices.map { it + 1 })
    }

    @Test fun `invalid pages per paper is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { ExamFileRules.groupPages(listOf(1), 0) }
    }

    private fun assertGroupSizes(pageCount: Int, pagesPerPaper: Int, expected: List<Int>) {
        assertEquals(expected, ExamFileRules.groupPages((1..pageCount).toList(), pagesPerPaper).map { it.size })
    }
}
