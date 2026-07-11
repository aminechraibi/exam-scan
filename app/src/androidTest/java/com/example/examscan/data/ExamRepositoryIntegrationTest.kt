package com.example.examscan.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.content.pm.PackageManager
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertArrayEquals
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

    @Test fun successiveBulkBatchesFillIncompletePaperBeforeCreatingNext() = runBlocking {
        val examId = repository.createExam("Continuous bulk", "2026-07-11", 2)
        repository.addBulk(examId, 2, imageUris(3, "batch_one"))
        repository.addBulk(examId, 2, imageUris(2, "batch_two"))
        val result = db.paperDao().getForExam(examId)
        assertEquals(listOf(1, 2, 3), result.map { it.paper.paperNumber })
        assertEquals(listOf(2, 2, 1), result.map { it.pages.size })
        assertEquals(listOf(1, 2), result[1].pages.map { it.pageNumber })
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

    @Test fun sharedExportUsesProtectedContentUriWithoutPrivatePathDisclosure() = runBlocking {
        val examId = repository.createExam("Share", "2026-07-11", 1)
        repository.addSinglePaper(examId, imageUris(1, "share"))
        val uri = repository.exportPdf(examId)
        assertEquals("content", uri.scheme)
        assertEquals("${context.packageName}.files", uri.authority)
        assertFalse(uri.toString().contains(context.filesDir.absolutePath))
        val provider = context.packageManager.resolveContentProvider(uri.authority!!, PackageManager.MATCH_DEFAULT_ONLY)!!
        assertFalse(provider.exported)
        assertTrue(provider.grantUriPermissions)
        assertTrue(context.contentResolver.openInputStream(uri)!!.use { it.read() >= 0 })
    }

    @Test fun emptyPdfExportIsRejected() {
        val examId = runBlocking { repository.createExam("Empty", "2026-07-11", 2) }
        assertThrows(IllegalStateException::class.java) { runBlocking { repository.exportPdf(examId) } }
    }

    @Test fun deletingEveryPossiblePositionRenumbersWithoutChangingPaper() = runBlocking {
        listOf(0, 1, 2).forEachIndexed { case, index ->
            val examId = repository.createExam("Delete $case", "2026-07-11", 3)
            val paperId = repository.addSinglePaper(examId, imageUris(3, "delete_$case"))
            val target = db.pageDao().getForPaper(paperId)[index]
            repository.deletePage(target)
            val remaining = db.pageDao().getForPaper(paperId)
            assertEquals(listOf(1, 2), remaining.map { it.pageNumber })
            assertTrue(remaining.all { it.paperId == paperId })
        }
        val examId = repository.createExam("Only", "2026-07-11", 1)
        val paperId = repository.addSinglePaper(examId, imageUris(1, "only"))
        repository.deletePage(db.pageDao().getForPaper(paperId).single())
        assertTrue(db.pageDao().getForPaper(paperId).isEmpty())
        assertEquals(paperId, db.paperDao().get(paperId)?.id)
    }

    @Test fun insertingBeforeBetweenAndAfterProducesUniqueOrderedPositions() = runBlocking {
        listOf(0, 1, 3).forEachIndexed { case, after ->
            val examId = repository.createExam("Insert $case", "2026-07-11", 4)
            val paperId = repository.addSinglePaper(examId, imageUris(3, "insert_base_$case"))
            repository.insertPage(paperId, after, imageUris(1, "insert_new_$case").single())
            val result = db.pageDao().getForPaper(paperId)
            assertEquals(listOf(1, 2, 3, 4), result.map { it.pageNumber })
            assertEquals(4, result.map { it.pageNumber }.distinct().size)
            assertTrue(result.all { it.paperId == paperId })
        }
    }

    @Test fun retakingFirstMiddleAndFinalPagePreservesIdentityAndPosition() = runBlocking {
        listOf(0, 1, 2).forEachIndexed { case, index ->
            val examId = repository.createExam("Retake $case", "2026-07-11", 3)
            val paperId = repository.addSinglePaper(examId, imageUris(3, "retake_base_$case"))
            val before = db.pageDao().getForPaper(paperId)[index]
            repository.replacePage(before.id, imageUris(1, "retake_new_$case").single())
            val after = db.pageDao().get(before.id)!!
            assertEquals(before.id, after.id)
            assertEquals(before.paperId, after.paperId)
            assertEquals(before.pageNumber, after.pageNumber)
            assertFalse(File(before.originalPath).exists())
            assertFalse(File(before.labeledPath).exists())
        }
    }

    @Test fun invalidOrDeletedSourceDoesNotLeavePartialPaperOrFiles() = runBlocking {
        val examId = repository.createExam("Failure", "2026-07-11", 2)
        val missing = Uri.fromFile(File(context.cacheDir, "does-not-exist.jpg"))
        assertThrows(Throwable::class.java) { runBlocking { repository.addSinglePaper(examId, listOf(missing)) } }
        assertTrue(db.paperDao().getForExam(examId).isEmpty())
        assertFalse(File(context.filesDir, "scans/exam_$examId/paper_1").exists())

        val invalid = File(context.cacheDir, "test-inputs/invalid.jpg").apply {
            parentFile?.mkdirs(); writeText("not an image")
        }
        assertThrows(Throwable::class.java) { runBlocking { repository.addSinglePaper(examId, listOf(Uri.fromFile(invalid))) } }
        assertTrue(db.paperDao().getForExam(examId).isEmpty())
        assertFalse(File(context.filesDir, "scans/exam_$examId/paper_1").exists())
    }

    @Test fun failedRetakeKeepsExistingPageAndFiles() = runBlocking {
        val examId = repository.createExam("Safe retake", "2026-07-11", 1)
        val paperId = repository.addSinglePaper(examId, imageUris(1, "safe_retake"))
        val before = db.pageDao().getForPaper(paperId).single()
        val missing = Uri.fromFile(File(context.cacheDir, "missing-retake.jpg"))
        assertThrows(Throwable::class.java) { runBlocking { repository.replacePage(before.id, missing) } }
        assertEquals(before, db.pageDao().get(before.id))
        assertTrue(File(before.originalPath).exists())
        assertTrue(File(before.labeledPath).exists())
    }

    @Test fun labelIsUpperLeftReadableAndRegeneratedFromCleanOriginal() = runBlocking {
        val examId = repository.createExam("Labels", "2026-07-11", 2)
        val paperId = repository.addSinglePaper(examId, listOf(solidImageUri(Color.WHITE, "white")))
        val page = db.pageDao().getForPaper(paperId).single()
        val original = BitmapFactory.decodeFile(page.originalPath)
        val labeledBefore = BitmapFactory.decodeFile(page.labeledPath)
        val density = context.resources.displayMetrics.density
        val labelX = (20 * density).toInt().coerceAtMost(labeledBefore.width - 1)
        val labelY = (20 * density).toInt().coerceAtMost(labeledBefore.height - 1)
        assertTrue(Color.red(labeledBefore.getPixel(labelX, labelY)) < Color.red(original.getPixel(labelX, labelY)))
        assertTrue(Color.red(labeledBefore.getPixel(300, 450)) > 230)
        val bytesBefore = File(page.labeledPath).readBytes()
        repository.insertPage(paperId, 1, solidImageUri(Color.LTGRAY, "second"))
        assertArrayEquals(bytesBefore, File(db.pageDao().get(page.id)!!.labeledPath).readBytes())
        original.recycle(); labeledBefore.recycle()
    }

    @Test fun paperTwentyFiveUsesValidUncroppedLabel() = runBlocking {
        val examId = repository.createExam("Labels 25", "2026-07-11", 1)
        db.paperDao().insert(PaperEntity(examId = examId, paperNumber = 24))
        val paperId = repository.addSinglePaper(examId, listOf(solidImageUri(Color.WHITE, "paper25")))
        assertEquals(25, db.paperDao().get(paperId)?.paperNumber)
        val bitmap = BitmapFactory.decodeFile(db.pageDao().getForPaper(paperId).single().labeledPath)
        val density = context.resources.displayMetrics.density
        val labelX = (20 * density).toInt().coerceAtMost(bitmap.width - 1)
        val labelY = (20 * density).toInt().coerceAtMost(bitmap.height - 1)
        assertTrue(Color.red(bitmap.getPixel(labelX, labelY)) < 200)
        assertTrue(Color.red(bitmap.getPixel(bitmap.width - 5, bitmap.height - 5)) > 230)
        bitmap.recycle()
    }

    @Test fun twoHundredSyntheticPagesKeepFilesDatabasePdfAndZipConsistent() = runBlocking {
        val examId = repository.createExam("Stress", "2026-07-11", 2)
        repository.addBulk(examId, 2, imageUris(200, "stress", 64, 96))
        val papers = db.paperDao().getForExam(examId)
        assertEquals(100, papers.size)
        assertEquals((1..100).toList(), papers.map { it.paper.paperNumber })
        assertEquals(200, papers.sumOf { it.pages.size })
        assertTrue(papers.flatMap { it.pages }.all { File(it.originalPath).length() > 0 && File(it.labeledPath).length() > 0 })
        context.contentResolver.openFileDescriptor(repository.exportPdf(examId), "r")!!.use { descriptor ->
            PdfRenderer(descriptor).use { assertEquals(200, it.pageCount) }
        }
        var zipCount = 0
        ZipInputStream(context.contentResolver.openInputStream(repository.exportImagesZip(examId))!!).use { zip ->
            while (zip.nextEntry != null) {
                assertTrue(BitmapFactory.decodeStream(zip) != null)
                zipCount++
            }
        }
        assertEquals(200, zipCount)
    }

    private fun imageUris(count: Int, prefix: String = "page", width: Int = 320, height: Int = 480): List<Uri> {
        val directory = File(context.cacheDir, "test-inputs").apply { mkdirs() }
        return (1..count).map { number ->
            val file = File(directory, "${prefix}_${System.nanoTime()}_$number.jpg")
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.rgb((number * 40) % 255, 120, 200))
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            bitmap.recycle()
            Uri.fromFile(file)
        }
    }

    private fun solidImageUri(color: Int, prefix: String): Uri {
        val directory = File(context.cacheDir, "test-inputs").apply { mkdirs() }
        val file = File(directory, "${prefix}_${System.nanoTime()}.jpg")
        val bitmap = Bitmap.createBitmap(320, 480, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }
        bitmap.recycle()
        return Uri.fromFile(file)
    }
}
