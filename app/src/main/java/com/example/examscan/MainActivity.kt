package com.example.examscan

import android.app.Activity
import android.content.*
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult
import androidx.activity.viewModels
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import coil3.compose.AsyncImage
import com.example.examscan.data.*
import com.example.examscan.diagnostics.DiagnosticCategory
import com.example.examscan.diagnostics.Diagnostics
import com.example.examscan.ui.AppViewModel
import com.example.examscan.ui.ExamUiRules
import com.google.mlkit.vision.documentscanner.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity:ComponentActivity(){
    private val vm by viewModels<AppViewModel>()
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{ExamScanTheme{ExamScanApp(vm)}}}
}

@Composable fun ExamScanTheme(content: @Composable () -> Unit){MaterialTheme(colorScheme=lightColorScheme(primary=androidx.compose.ui.graphics.Color(0xFF166534),secondary=androidx.compose.ui.graphics.Color(0xFF2563EB),surfaceVariant=androidx.compose.ui.graphics.Color(0xFFF0FDF4)),content=content)}

@Composable fun ExamScanApp(vm:AppViewModel){
    val nav=rememberNavController()
    NavHost(nav,"home"){
        composable("home"){HomeScreen(vm,nav)}
        composable("exam/{id}"){b->ExamScreen(vm,nav,b.arguments!!.getString("id")!!.toLong())}
        composable("paper/{id}/{expected}"){b->PaperScreen(vm,nav,b.arguments!!.getString("id")!!.toLong(),b.arguments!!.getString("expected")!!.toInt())}
        composable("settings"){DiagnosticsSettingsScreen(nav)}
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun HomeScreen(vm:AppViewModel,nav:NavHostController){
    val exams by vm.exams.collectAsStateWithLifecycle();var show by remember{mutableStateOf(false)}
    Scaffold(topBar={TopAppBar(title={Column{Text("ExamScan",fontWeight=FontWeight.Bold);Text("Fast exam paper capture",style=MaterialTheme.typography.labelMedium)}},actions={IconButton({nav.navigate("settings")},modifier=Modifier.testTag("diagnostics_settings_button")){Icon(Icons.Default.Settings,"Diagnostics settings")}})},floatingActionButton={ExtendedFloatingActionButton(text={Text("New exam")},icon={Icon(Icons.Default.Add,null)},onClick={show=true},modifier=Modifier.testTag("create_exam_button"))}){pad->
        if(exams.isEmpty()) EmptyState("No exams yet","Create an exam folder, set the expected pages, then start scanning.",Modifier.padding(pad)) else LazyColumn(Modifier.padding(pad).fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){items(exams,key={it.id}){e->ExamCard(e,{nav.navigate("exam/${e.id}")},{vm.deleteExam(e)})}}
    }
    if(show) NewExamDialog({show=false}){n,d,c->vm.createExam(n,d,c){nav.navigate("exam/$it")};show=false}
}

@Composable fun ExamCard(e:ExamEntity,open:()->Unit,delete:()->Unit){Card(onClick=open,modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically){Surface(shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.primaryContainer){Icon(Icons.Default.Folder,null,Modifier.padding(14.dp),tint=MaterialTheme.colorScheme.primary)};Spacer(Modifier.width(14.dp));Column(Modifier.weight(1f)){Text(e.name,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium);Text("${e.examDate}  •  ${e.pagesPerPaper} page(s) per paper",style=MaterialTheme.typography.bodySmall)};ConfirmDeleteButton("Delete exam",delete)}}}

@Composable fun NewExamDialog(close:()->Unit,create:(String,String,Int)->Unit){var name by remember{mutableStateOf("")};var date by remember{mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))};var pages by remember{mutableStateOf("2")};AlertDialog(onDismissRequest=close,title={Text("Create exam")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){OutlinedTextField(name,{name=it},label={Text("Exam name")},singleLine=true,modifier=Modifier.testTag("exam_name_input"));OutlinedTextField(date,{date=it},label={Text("Date (YYYY-MM-DD)")},singleLine=true,modifier=Modifier.testTag("exam_date_input"));OutlinedTextField(pages,{pages=it.filter(Char::isDigit)},label={Text("Pages per paper")},singleLine=true,modifier=Modifier.testTag("pages_per_paper_input"))}},confirmButton={Button(onClick={create(name,date,pages.toInt())},enabled=ExamUiRules.canCreateExam(name,pages),modifier=Modifier.testTag("confirm_create_exam_button")){Text("Create")}},dismissButton={TextButton(onClick=close){Text("Cancel")}})}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ExamScreen(vm:AppViewModel,nav:NavHostController,examId:Long){
    val exam by vm.exam(examId).collectAsState(initial=null);val papers by vm.papers(examId).collectAsState(initial=emptyList());val activity=LocalActivity.current ?: return;val scope=rememberCoroutineScope();var menu by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}
    var pending by remember{mutableStateOf<ScanMode?>(null)};var bulkActive by remember{mutableStateOf(false)};var saving by remember{mutableStateOf(false)};var bulkRound by remember{mutableIntStateOf(0)}
    val launcher=rememberLauncherForActivityResult(StartIntentSenderForResult()){r->if(r.resultCode==Activity.RESULT_OK){val result=GmsDocumentScanningResult.fromActivityResultIntent(r.data);val uris=result?.pages?.map{it.imageUri}.orEmpty();Diagnostics.log(DiagnosticCategory.ML_KIT,"scanner_result",mapOf("mode" to pending?.name,"page_count" to uris.size));val e=exam;if(e!=null&&uris.isNotEmpty()){saving=true;when(pending){ScanMode.Single->vm.addSingle(examId,uris,{saving=false;Diagnostics.log(DiagnosticCategory.ML_KIT,"scan_saved",mapOf("mode" to "single","page_count" to uris.size))},{saving=false;error=it.message});ScanMode.Bulk->vm.addBulk(examId,e.pagesPerPaper,uris,{saving=false;Diagnostics.log(DiagnosticCategory.ML_KIT,"bulk_batch_saved",mapOf("page_count" to uris.size,"continue" to bulkActive));if(bulkActive)bulkRound++},{saving=false;bulkActive=false;error=it.message});null->{saving=false}}}}else{if(pending==ScanMode.Bulk)bulkActive=false;Diagnostics.log(DiagnosticCategory.ML_KIT,"scanner_cancelled",mapOf("result_code" to r.resultCode,"bulk_session_ended" to (pending==ScanMode.Bulk)))}}
    fun scan(mode:ScanMode){val e=exam?:return;if(mode==ScanMode.Bulk)bulkActive=true;pending=mode;val limit=if(mode==ScanMode.Single)e.pagesPerPaper else 200;Diagnostics.log(DiagnosticCategory.ML_KIT,"scanner_requested",mapOf("mode" to mode.name,"page_limit" to limit,"bulk_round" to bulkRound));val options=GmsDocumentScannerOptions.Builder().setGalleryImportAllowed(true).setPageLimit(limit).setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG).setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL).build();GmsDocumentScanning.getClient(options).getStartScanIntent(activity).addOnSuccessListener{Diagnostics.log(DiagnosticCategory.ML_KIT,"scanner_ready");launcher.launch(IntentSenderRequest.Builder(it).build())}.addOnFailureListener{bulkActive=false;Diagnostics.log(DiagnosticCategory.ML_KIT,"scanner_launch_failed",error=it);error=it.message?:"Scanner unavailable"}}
    LaunchedEffect(bulkRound){if(bulkRound>0&&bulkActive&&!saving){Diagnostics.log(DiagnosticCategory.ML_KIT,"bulk_scanner_relaunch");scan(ScanMode.Bulk)}}
    val canExport=ExamUiRules.canExport(papers.map{it.pages.size})
    fun export(type:String){scope.launch{try{val uri=if(type=="pdf")vm.exportPdf(examId) else vm.exportZip(examId);Diagnostics.log(DiagnosticCategory.SHARING,"export_ready",mapOf("type" to type));share(activity,uri,if(type=="pdf")"application/pdf" else "application/zip")}catch(t:Throwable){Diagnostics.log(DiagnosticCategory.STORAGE,"export_failed",mapOf("type" to type),t);error=t.message?:"Export failed"}};menu=false}
    Scaffold(topBar={TopAppBar(title={Column{Text(exam?.name?:"Exam",fontWeight=FontWeight.Bold);exam?.let{Text("${it.examDate} • ${papers.size} papers",style=MaterialTheme.typography.labelSmall)}}},navigationIcon={IconButton({nav.popBackStack()}){Icon(Icons.Default.ArrowBack,null)}},actions={IconButton({menu=true},modifier=Modifier.testTag("export_menu_button")){Icon(Icons.Default.MoreVert,null)};DropdownMenu(menu,{menu=false}){DropdownMenuItem({Text("Export PDF")},{export("pdf")},leadingIcon={Icon(Icons.Default.PictureAsPdf,null)},enabled=canExport,modifier=Modifier.testTag("export_pdf_button"));DropdownMenuItem({Text("Export images ZIP")},{export("zip")},leadingIcon={Icon(Icons.Default.FolderZip,null)},enabled=canExport,modifier=Modifier.testTag("export_zip_button"))}})},floatingActionButton={Column(horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.spacedBy(10.dp)){SmallFloatingActionButton({scan(ScanMode.Bulk)},modifier=Modifier.testTag("bulk_scan_button")){Icon(Icons.Default.Collections,null)};ExtendedFloatingActionButton(text={Text("Scan paper")},icon={Icon(Icons.Default.DocumentScanner,null)},onClick={scan(ScanMode.Single)},modifier=Modifier.testTag("single_scan_button"))}}){pad->
        if(papers.isEmpty()) EmptyState("Ready to scan","Use Scan paper for one submission or the upper button for a continuous bulk session.",Modifier.padding(pad)) else LazyColumn(Modifier.padding(pad).fillMaxSize(),contentPadding=PaddingValues(16.dp,16.dp,16.dp,120.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){items(papers,key={it.paper.id}){pw->PaperCard(pw,exam?.pagesPerPaper?:0,{nav.navigate("paper/${pw.paper.id}/${exam?.pagesPerPaper?:1}")},{vm.deletePaper(pw.paper)})}}
    }
    error?.let{AlertDialog(onDismissRequest={error=null},title={Text("Scanner error")},text={Text(it)},confirmButton={TextButton({error=null}){Text("OK")}})}
    if(saving)Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha=.35f)),contentAlignment=Alignment.Center){Card{Column(Modifier.padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally){CircularProgressIndicator();Spacer(Modifier.height(12.dp));Text(if(bulkActive)"Saving batch… bulk scan will continue" else "Saving scan…")}}}
}

enum class ScanMode{Single,Bulk}

@Composable fun PaperCard(pw:PaperWithPages,expected:Int,open:()->Unit,delete:()->Unit){val count=pw.pages.size;val complete=ExamUiRules.isComplete(count,expected);Card(onClick=open,Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Row(verticalAlignment=Alignment.CenterVertically){Text("Paper ${pw.paper.paperNumber}",fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium);Spacer(Modifier.weight(1f));Badge(containerColor=if(complete)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error){Text("$count/$expected",Modifier.padding(horizontal=7.dp,vertical=3.dp))};ConfirmDeleteButton("Delete paper",delete)};if(pw.pages.isNotEmpty()){Spacer(Modifier.height(8.dp));LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){items(pw.pages.sortedBy{it.pageNumber}){p->AsyncImage(File(p.labeledPath),null,Modifier.size(72.dp,96.dp).clip(RoundedCornerShape(8.dp)),contentScale=ContentScale.Crop)}}};if(!complete)Text(if(count<expected)"Missing ${expected-count} page(s)" else "${count-expected} extra page(s)",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.labelMedium,modifier=Modifier.padding(top=8.dp))}}}

@Composable fun ConfirmDeleteButton(description:String,onConfirm:()->Unit){var show by remember{mutableStateOf(false)};IconButton({show=true}){Icon(Icons.Default.DeleteOutline,description)};if(show)AlertDialog(onDismissRequest={show=false},title={Text("Confirm deletion")},text={Text("This cannot be undone.")},confirmButton={Button({show=false;onConfirm()},modifier=Modifier.testTag("confirm_delete_button")){Text("Delete")}},dismissButton={TextButton({show=false},modifier=Modifier.testTag("cancel_delete_button")){Text("Cancel")}})}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun PaperScreen(vm:AppViewModel,nav:NavHostController,paperId:Long,expected:Int){
    val pages by vm.pages(paperId).collectAsState(initial=emptyList());val activity=LocalActivity.current ?: return;var action by remember{mutableStateOf<PageAction?>(null)};var error by remember{mutableStateOf<String?>(null)}
    val launcher=rememberLauncherForActivityResult(StartIntentSenderForResult()){r->if(r.resultCode==Activity.RESULT_OK){GmsDocumentScanningResult.fromActivityResultIntent(r.data)?.pages?.firstOrNull()?.imageUri?.let{u->when(val a=action){is PageAction.Replace->vm.replace(a.id,u);is PageAction.Insert->vm.insert(paperId,a.after,u);null->{}}}}}
    fun scan(a:PageAction){action=a;val options=GmsDocumentScannerOptions.Builder().setGalleryImportAllowed(true).setPageLimit(1).setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG).setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL).build();GmsDocumentScanning.getClient(options).getStartScanIntent(activity).addOnSuccessListener{launcher.launch(IntentSenderRequest.Builder(it).build())}.addOnFailureListener{error=it.message}}
    Scaffold(topBar={TopAppBar(title={Column{Text("Paper editor",fontWeight=FontWeight.Bold);Text("${pages.size}/$expected pages",style=MaterialTheme.typography.labelSmall)}},navigationIcon={IconButton({nav.popBackStack()}){Icon(Icons.Default.ArrowBack,null)}})},floatingActionButton={ExtendedFloatingActionButton(text={Text("Add page")},icon={Icon(Icons.Default.Add,null)},onClick={scan(PageAction.Insert(pages.size))})}){pad->
        if(pages.isEmpty())EmptyState("No pages","Add a scanned page.",Modifier.padding(pad)) else LazyColumn(Modifier.padding(pad).fillMaxSize(),contentPadding=PaddingValues(16.dp,16.dp,16.dp,100.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){items(pages.sortedBy{it.pageNumber},key={it.id}){p->PageEditorCard(p,{scan(PageAction.Replace(p.id))},{scan(PageAction.Insert(p.pageNumber))},{vm.deletePage(p)})}}
    };error?.let{message->AlertDialog(onDismissRequest={error=null},title={Text("Scanner error")},text={Text(message)},confirmButton={TextButton(onClick={error=null}){Text("OK")}})}
}
sealed interface PageAction{data class Replace(val id:Long):PageAction;data class Insert(val after:Int):PageAction}

@Composable fun PageEditorCard(p:PageEntity,retake:()->Unit,insertAfter:()->Unit,delete:()->Unit){Card{Column{AsyncImage(File(p.labeledPath),"Page ${p.pageNumber}",Modifier.fillMaxWidth().aspectRatio(0.707f),contentScale=ContentScale.Fit);Row(Modifier.fillMaxWidth().padding(8.dp),verticalAlignment=Alignment.CenterVertically){Text("Page ${p.pageNumber}",fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));TextButton(retake){Icon(Icons.Default.Refresh,null);Spacer(Modifier.width(4.dp));Text("Retake")};IconButton(insertAfter){Icon(Icons.Default.PlaylistAdd,"Insert after")};IconButton(delete){Icon(Icons.Default.DeleteOutline,"Delete")}}}}}

@Composable fun EmptyState(title:String,text:String,modifier:Modifier=Modifier){Box(modifier.fillMaxSize(),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.padding(32.dp)){Icon(Icons.Default.DocumentScanner,null,Modifier.size(68.dp),tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.height(16.dp));Text(title,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Spacer(Modifier.height(8.dp));Text(text,style=MaterialTheme.typography.bodyMedium)}}}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun DiagnosticsSettingsScreen(nav:NavHostController){var enabled by remember{mutableStateOf(Diagnostics.isEnabled())};var size by remember{mutableLongStateOf(Diagnostics.logSize())};Scaffold(topBar={TopAppBar(title={Text("Diagnostics")},navigationIcon={IconButton({nav.popBackStack()}){Icon(Icons.Default.ArrowBack,null)}})}){pad->LazyColumn(Modifier.padding(pad).fillMaxSize().testTag("diagnostics_list"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){item{Text("Manual test logging",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Text("Opt-in logs contain device/build state and results, never scan images, exam names, or file paths.")};item{Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Enable diagnostics",fontWeight=FontWeight.Bold);Text(if(enabled)"Capturing enabled categories" else "No diagnostic events are stored",style=MaterialTheme.typography.bodySmall)};Switch(enabled,{enabled=it;Diagnostics.setEnabled(it);if(it)Diagnostics.checkpoint(DiagnosticCategory.RELEASE)},modifier=Modifier.testTag("diagnostics_master_switch"))}};items(DiagnosticCategory.entries){category->var checked by remember(category){mutableStateOf(Diagnostics.isCategoryEnabled(category))};Card{Column(Modifier.padding(14.dp)){Row(verticalAlignment=Alignment.CenterVertically){Text(category.title,Modifier.weight(1f),fontWeight=FontWeight.SemiBold);Switch(checked,{checked=it;Diagnostics.setCategoryEnabled(category,it)},enabled=enabled)};TextButton({Diagnostics.checkpoint(category);size=Diagnostics.logSize()},enabled=enabled){Text("Record checkpoint")}}}};item{Text("Log size: ${size/1024} KB",style=MaterialTheme.typography.bodySmall);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button({Diagnostics.share(nav.context);size=Diagnostics.logSize()},enabled=enabled,modifier=Modifier.testTag("share_diagnostics_button")){Icon(Icons.Default.Share,null);Spacer(Modifier.width(6.dp));Text("Share log")};OutlinedButton({Diagnostics.clear();size=0},enabled=size>0){Text("Clear")}}}}}}

private fun share(activity:Activity,uri:Uri,mime:String){Diagnostics.log(DiagnosticCategory.SHARING,"share_sheet_opened",mapOf("mime" to mime));activity.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type=mime;putExtra(Intent.EXTRA_STREAM,uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)},"Share export"))}
