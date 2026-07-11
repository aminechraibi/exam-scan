package com.example.examscan.data

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExamRepository(
    private val context: Context,
    private val exams: ExamDao,
    private val papers: PaperDao,
    private val pages: PageDao
) {
    fun observeExams() = exams.observeAll()
    fun observeExam(id: Long) = exams.observe(id)
    fun observePapers(examId: Long) = papers.observeForExam(examId)
    fun observePages(paperId: Long) = pages.observeForPaper(paperId)
    suspend fun createExam(name:String,date:String,pagesPerPaper:Int)=exams.insert(ExamEntity(name=name.trim(),examDate=date,pagesPerPaper=pagesPerPaper))
    suspend fun deleteExam(e:ExamEntity)=withContext(Dispatchers.IO){ deleteExamFiles(e.id); exams.delete(e) }
    suspend fun deletePaper(p:PaperEntity)=withContext(Dispatchers.IO){ File(context.filesDir,"scans/exam_${p.examId}/paper_${p.paperNumber}").deleteRecursively(); papers.delete(p) }

    suspend fun addSinglePaper(examId:Long, uris:List<Uri>): Long = withContext(Dispatchers.IO) {
        val n=papers.nextNumber(examId); val pid=papers.insert(PaperEntity(examId=examId,paperNumber=n))
        try {
            uris.forEachIndexed { i,u -> savePage(examId,pid,n,i+1,u) }
            pid
        } catch (failure: Throwable) {
            File(context.filesDir,"scans/exam_$examId/paper_$n").deleteRecursively()
            papers.get(pid)?.let { papers.delete(it) }
            throw failure
        }
    }
    suspend fun addBulk(examId:Long, pagesPerPaper:Int, uris:List<Uri>): List<Long> = withContext(Dispatchers.IO) {
        val ids=mutableListOf<Long>(); ExamFileRules.groupPages(uris,pagesPerPaper).forEach { group -> ids += addSinglePaper(examId,group) }; ids
    }
    suspend fun replacePage(pageId:Long, uri:Uri)=withContext(Dispatchers.IO){
        val old=pages.get(pageId)?:return@withContext; val paper=papers.get(old.paperId)?:return@withContext
        val pair=copyAndLabel(uri,paper.examId,paper.paperNumber,old.pageNumber)
        try {
            pages.update(old.copy(originalPath=pair.first,labeledPath=pair.second,createdAt=System.currentTimeMillis()))
            papers.update(paper.copy(updatedAt=System.currentTimeMillis()))
            File(old.originalPath).delete(); File(old.labeledPath).delete()
        } catch (failure: Throwable) {
            File(pair.first).delete(); File(pair.second).delete()
            throw failure
        }
    }
    suspend fun insertPage(paperId:Long,afterPage:Int,uri:Uri)=withContext(Dispatchers.IO){
        val paper=papers.get(paperId)?:return@withContext
        val current=pages.getForPaper(paperId)
        current.filter{it.pageNumber>afterPage}.sortedByDescending{it.pageNumber}.forEach{ pages.update(it.copy(pageNumber=it.pageNumber+1)) }
        savePage(paper.examId,paper.id,paper.paperNumber,afterPage+1,uri)
        relabelPaper(paper)
    }
    suspend fun deletePage(page:PageEntity)=withContext(Dispatchers.IO){
        val paper=papers.get(page.paperId)?:return@withContext
        File(page.originalPath).delete(); File(page.labeledPath).delete(); pages.delete(page)
        pages.getForPaper(page.paperId).forEachIndexed{i,p-> pages.update(p.copy(pageNumber=i+1))}; relabelPaper(paper)
    }
    private suspend fun savePage(examId:Long,paperId:Long,paperNo:Int,pageNo:Int,uri:Uri){
        val pair=copyAndLabel(uri,examId,paperNo,pageNo)
        try {
            pages.insert(PageEntity(paperId=paperId,pageNumber=pageNo,originalPath=pair.first,labeledPath=pair.second))
        } catch (failure: Throwable) {
            File(pair.first).delete(); File(pair.second).delete()
            throw failure
        }
    }
    private fun copyAndLabel(uri:Uri,examId:Long,paperNo:Int,pageNo:Int):Pair<String,String>{
        val dir=File(context.filesDir,"scans/exam_$examId/paper_$paperNo").apply{mkdirs()}
        val token=System.currentTimeMillis(); val original=File(dir,"page_${pageNo}_${token}_original.jpg")
        val labeled=File(dir,"page_${pageNo}_${token}_labeled.jpg")
        try {
            val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open scan")
            input.use{source->original.outputStream().use{source.copyTo(it)}}
            labelBitmap(original,labeled,ExamFileRules.paperLabel(paperNo))
            return original.absolutePath to labeled.absolutePath
        } catch (failure: Throwable) {
            original.delete(); labeled.delete()
            throw failure
        }
    }
    private fun labelBitmap(input:File,output:File,label:String){
        val bitmap=BitmapFactory.decodeFile(input.absolutePath) ?: error("Cannot decode scan")
        val mutable=bitmap.copy(Bitmap.Config.ARGB_8888,true); val canvas=Canvas(mutable)
        val density=context.resources.displayMetrics.density; val text=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.WHITE;textSize=16*density;typeface=Typeface.DEFAULT_BOLD}
        val pad=9*density; val h=30*density; val width=text.measureText(label)+2*pad
        val bg=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.argb(190,0,0,0)}
        canvas.drawRoundRect(12*density,12*density,12*density+width,12*density+h,8*density,8*density,bg)
        canvas.drawText(label,12*density+pad,12*density+21*density,text)
        output.outputStream().use{mutable.compress(Bitmap.CompressFormat.JPEG,95,it)}; if(mutable!==bitmap) bitmap.recycle(); mutable.recycle()
    }
    private suspend fun relabelPaper(paper:PaperEntity){
        pages.getForPaper(paper.id).forEach{p->
            val out=File(p.labeledPath); labelBitmap(File(p.originalPath),out,ExamFileRules.paperLabel(paper.paperNumber))
        }
        papers.update(paper.copy(updatedAt=System.currentTimeMillis()))
    }
    suspend fun exportPdf(examId:Long):Uri=withContext(Dispatchers.IO){
        val exam=exams.get(examId)?:error("Exam not found"); val all=papers.getForExam(examId)
        val dir=File(context.cacheDir,"exports").apply{mkdirs()}; val file=ExamFileRules.uniqueFile(dir,ExamFileRules.safeFileName(exam.name)+"_${exam.examDate}.pdf")
        val doc=PdfDocument(); var index=1
        all.forEach{pw->pw.pages.sortedBy{it.pageNumber}.forEach{p->
            val bm=BitmapFactory.decodeFile(p.labeledPath)?:return@forEach
            val page=doc.startPage(PdfDocument.PageInfo.Builder(1240,1754,index++).create()); val c=page.canvas
            val scale=minOf(1240f/bm.width,1754f/bm.height); val w=bm.width*scale;val h=bm.height*scale
            c.drawColor(Color.WHITE); c.drawBitmap(bm,null,RectF((1240-w)/2,(1754-h)/2,(1240+w)/2,(1754+h)/2),Paint(Paint.FILTER_BITMAP_FLAG)); doc.finishPage(page);bm.recycle()
        }}
        if(index==1){doc.close();error("Exam has no scanned pages")}
        file.outputStream().use{doc.writeTo(it)};doc.close(); uri(file)
    }
    suspend fun exportImagesZip(examId:Long):Uri=withContext(Dispatchers.IO){
        val exam=exams.get(examId)?:error("Exam not found");val all=papers.getForExam(examId);val dir=File(context.cacheDir,"exports").apply{mkdirs()};val file=ExamFileRules.uniqueFile(dir,ExamFileRules.safeFileName(exam.name)+"_${exam.examDate}_images.zip")
        ZipOutputStream(BufferedOutputStream(file.outputStream())).use{zip->all.forEach{pw->pw.pages.sortedBy{it.pageNumber}.forEach{p->val src=File(p.labeledPath);zip.putNextEntry(ZipEntry("Paper_%03d/Page_%02d.jpg".format(pw.paper.paperNumber,p.pageNumber)));src.inputStream().use{it.copyTo(zip)};zip.closeEntry()}}};uri(file)
    }
    private fun uri(file:File)=FileProvider.getUriForFile(context,"${context.packageName}.files",file)
    private fun deleteExamFiles(id:Long)=File(context.filesDir,"scans/exam_$id").deleteRecursively()
}
