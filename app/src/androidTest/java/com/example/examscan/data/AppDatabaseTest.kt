package com.example.examscan.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {
    private lateinit var db: AppDatabase
    private lateinit var exams: ExamDao
    private lateinit var papers: PaperDao
    private lateinit var pages: PageDao

    @Before fun createDatabase() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
        exams = db.examDao()
        papers = db.paperDao()
        pages = db.pageDao()
    }

    @After fun closeDatabase() = db.close()

    @Test fun examCrudSupportsInternationalNames() = runBlocking {
        listOf("Python / Session 2", "IA & Vision", "اختبار العربية", "Épreuve française").forEach { name ->
            val id = exams.insert(ExamEntity(name = name, examDate = "2026-07-11", pagesPerPaper = 2))
            assertEquals(name, exams.get(id)?.name)
            exams.update(exams.get(id)!!.copy(name = "$name updated"))
            assertEquals("$name updated", exams.get(id)?.name)
            exams.delete(exams.get(id)!!)
            assertNull(exams.get(id))
        }
    }

    @Test fun deletingExamCascadesToPapersAndPages() = runBlocking {
        val examId = exams.insert(ExamEntity(name = "Math", examDate = "2026-07-11", pagesPerPaper = 2))
        val paperId = papers.insert(PaperEntity(examId = examId, paperNumber = 1))
        val pageId = pages.insert(PageEntity(paperId = paperId, pageNumber = 1, originalPath = "original.jpg", labeledPath = "labeled.jpg"))
        exams.delete(exams.get(examId)!!)
        assertNull(papers.get(paperId))
        assertNull(pages.get(pageId))
    }

    @Test fun deletingPaperCascadesToPages() = runBlocking {
        val examId = exams.insert(ExamEntity(name = "Math", examDate = "2026-07-11", pagesPerPaper = 2))
        val paperId = papers.insert(PaperEntity(examId = examId, paperNumber = 1))
        val pageId = pages.insert(PageEntity(paperId = paperId, pageNumber = 1, originalPath = "original.jpg", labeledPath = "labeled.jpg"))
        papers.delete(papers.get(paperId)!!)
        assertNull(pages.get(pageId))
    }

    @Test fun pagesAndPapersAreReturnedInPositionOrder() = runBlocking {
        val examId = exams.insert(ExamEntity(name = "Math", examDate = "2026-07-11", pagesPerPaper = 3))
        val paperIds = listOf(3, 1, 2).associateWith { number -> papers.insert(PaperEntity(examId = examId, paperNumber = number)) }
        listOf(3, 1, 2).forEach { position ->
            pages.insert(PageEntity(paperId = paperIds.getValue(1), pageNumber = position, originalPath = "o$position", labeledPath = "l$position"))
        }
        assertEquals(listOf(1, 2, 3), pages.getForPaper(paperIds.getValue(1)).map { it.pageNumber })
        assertEquals(listOf(1, 2, 3), papers.getForExam(examId).map { it.paper.paperNumber })
    }

    @Test fun closingAndReopeningDatabasePreservesData() = runBlocking {
        db.close()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "persistence-test.db"
        context.deleteDatabase(name)
        var persistent = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        val id = persistent.examDao().insert(ExamEntity(name = "Persistent", examDate = "2026-07-11", pagesPerPaper = 2))
        persistent.close()
        persistent = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        assertEquals("Persistent", persistent.examDao().get(id)?.name)
        persistent.close()
        context.deleteDatabase(name)
        createDatabase()
    }
}
