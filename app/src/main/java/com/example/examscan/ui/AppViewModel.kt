package com.example.examscan.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.examscan.ExamScanApplication
import com.example.examscan.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.example.examscan.diagnostics.DiagnosticCategory
import com.example.examscan.diagnostics.Diagnostics

class AppViewModel(app:Application):AndroidViewModel(app){
    private val repo=(app as ExamScanApplication).repository
    val exams=repo.observeExams().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000), emptyList())
    fun exam(id:Long)=repo.observeExam(id)
    fun papers(id:Long)=repo.observePapers(id)
    fun pages(id:Long)=repo.observePages(id)
    fun createExam(name:String,date:String,count:Int,onDone:(Long)->Unit)=viewModelScope.launch{onDone(repo.createExam(name,date,count))}
    fun deleteExam(e:ExamEntity)=viewModelScope.launch{repo.deleteExam(e)}
    fun deletePaper(p:PaperEntity)=viewModelScope.launch{repo.deletePaper(p)}
    fun addSingle(examId:Long,uris:List<Uri>,done:()->Unit={},failed:(Throwable)->Unit={})=viewModelScope.launch{try{repo.addSinglePaper(examId,uris);done()}catch(t:Throwable){Diagnostics.log(DiagnosticCategory.RUNTIME,"scan_save_failed",error=t);failed(t)}}
    fun addBulk(examId:Long,count:Int,uris:List<Uri>,done:()->Unit={},failed:(Throwable)->Unit={})=viewModelScope.launch{try{repo.addBulk(examId,count,uris);done()}catch(t:Throwable){Diagnostics.log(DiagnosticCategory.RUNTIME,"bulk_save_failed",error=t);failed(t)}}
    fun replace(pageId:Long,uri:Uri)=viewModelScope.launch{repo.replacePage(pageId,uri)}
    fun insert(paperId:Long,after:Int,uri:Uri)=viewModelScope.launch{repo.insertPage(paperId,after,uri)}
    fun deletePage(page:PageEntity)=viewModelScope.launch{repo.deletePage(page)}
    suspend fun exportPdf(id:Long)=repo.exportPdf(id)
    suspend fun exportZip(id:Long)=repo.exportImagesZip(id)
}
