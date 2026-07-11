package com.example.examscan.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipInputStream

@RunWith(AndroidJUnit4::class)
class ExamRepositoryIntegrationTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: ExamRepository

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repository = ExamRepository(context, db.examDao(), db.paperDao(), db.pageDao())
        File(context.filesDir, "scans").deleteRecursively()
        File(context.cacheDir, "exports").deleteRecursively()
    }

    @After fun tearDown() {
        db.close()
        File(context.filesDir, "scans").deleteRecursively()
        File(context.cacheDir, "exports").deleteRecursively()
        File(context.cacheDir, "test-inputs").deleteRecursively()
    }

    @Test fun bulkScanCreatesOrderedPapersAndRealImageFiles() = runBlocking {
        val examId = repository.createExam("Math", "2026-07-11", 2)
        val ids = repository.addBulk(examId, 2, imageUris(5))
        assertEquals(3, ids.size)
        val papers = db.paperDao().getForExam(examId)
        assertEquals(listOf(1, 2, 3), papers.map { it.paper.paperNumber })
        assertEquals(listOf(2, 2, 1), papers.map { it.pages.size })
        papers.flatMap { it.pages }.forEach { page ->
            assertTrue(File(page.originalPath).isFile)
            assertTrue(File(page.labeledPath).isFile)
            assertNotEquals(File(page.originalPath).readBytes().toList(), File(page.labeledPath).readBytes().toList())
        }
    }

    @Test fun deleteInsertAndRetakeKeepPositionsAndCleanOldFiles() = runBlocking {
        val examId = repository.createExam("Physics", "2026-07-11", 3)
        val paperId = repository.addSinglePaper(examId, imageUris(3))
        var pages = db.pageDao().getForPaper(paperId)
        val deleted = pages[1]
        repository.deletePage(deleted)
        assertFalse(File(deleted.originalPath).exists())
        assertFalse(File(deleted.labeledPath).exists())
        assertEquals(listOf(1, 2), db.pageDao().getForPaper(paperId).map { it.pageNumber })

        repository.insertPage(paperId, 0, imageUris(1, "insert").single())
        pages = db.pageDao().getForPaper(paperId)
        assertEquals(listOf(1, 2, 3), pages.map { it.pageNumber })
        assertEquals(3, pages.map { it.pageNumber }.distinct().size)

        val oldOriginal = pages[1].originalPath
        val oldLabeled = pages[1].labeledPath
        repository.replacePage(pages[1].id, imageUris(1, "retake").single())
        val replaced = db.pageDao().get(pages[1].id)!!
        assertEquals(2, replaced.pageNumber)
        assertFalse(File(oldOriginal).exists())
        assertFalse(File(oldLabeled).exists())
        assertTrue(File(replaced.originalPath).exists())
        assertTrue(File(replaced.labeledPath).exists())
    }

    @Test fun deletingPaperAndExamRemovesStorageTrees() = runBlocking {
        val examId = repository.createExam("Chemistry", "2026-07-11", 2)
        val firstId = repository.addSinglePaper(examId, imageUris(2, "first"))
        val secondId = repository.addSinglePaper(examId, imageUris(1, "second"))
        val first = db.paperDao().get(firstId)!!
        val firstDirectory = File(context.filesDir, "scans/exam_$examId/paper_1")
        repository.deletePaper(first)
        assertFalse(firstDirectory.exists())
        assertTrue(File(context.filesDir, "scans/exam_$examId/paper_2").exists())
        repository.deleteExam(db.examDao().get(examId)!!)
        assertFalse(File(context.filesDir, "scans/exam_$examId").exists())
        assertEquals(null, db.paperDao().get(secondId))
    }

    @Test fun pdfAndZipExportsPreservePageCountAndEntryOrder() = runBlocking {
        val examId = repository.createExam("Python / Session 2", "2026-07-11", 2)
        repository.addBulk(examId, 2, imageUris(5))

        val pdfUri = repository.exportPdf(examId)
        context.contentResolver.openFileDescriptor(pdfUri, "r")!!.use { descriptor ->
            PdfRenderer(descriptor).use { renderer -> assertEquals(5, renderer.pageCount) }
        }

        val zipUri = repository.exportImagesZip(examId)
        val entries = mutableListOf<String>()
        ZipInputStream(context.contentResolver.openInputStream(zipUri)!!).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries += entry.name
                assertTrue(zip.readBytes().isNotEmpty())
            }
        }
        assertEquals(listOf(
            "Paper_001/Page_01.jpg", "Paper_001/Page_02.jpg",
            "Paper_002/Page_01.jpg", "Paper_002/Page_02.jpg",
            "Paper_003/Page_01.jpg"
        ), entries)

        assertNotEquals(pdfUri, repository.exportPdf(examId))
        assertNotEquals(zipUri, repository.exportImagesZip(examId))
    }

    @Test fun emptyPdfExportIsRejected() {
        val examId = runBlocking { repository.createExam("Empty", "2026-07-11", 2) }
        assertThrows(IllegalStateException::class.java) { runBlocking { repository.exportPdf(examId) } }
    }

    private fun imageUris(count: Int, prefix: String = "page"): List<Uri> {
        val directory = File(context.cacheDir, "test-inputs").apply { mkdirs() }
        return (1..count).map { number ->
            val file = File(directory, "${prefix}_${System.nanoTime()}_$number.jpg")
            val bitmap = Bitmap.createBitmap(320, 480, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.rgb((number * 40) % 255, 120, 200))
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            bitmap.recycle()
            Uri.fromFile(file)
        }
    }
}
