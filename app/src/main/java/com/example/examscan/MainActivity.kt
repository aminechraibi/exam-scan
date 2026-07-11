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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import coil3.compose.AsyncImage
import com.example.examscan.data.*
import com.example.examscan.ui.AppViewModel
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
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun HomeScreen(vm:AppViewModel,nav:NavHostController){
    val exams by vm.exams.collectAsStateWithLifecycle();var show by remember{mutableStateOf(false)}
    Scaffold(topBar={TopAppBar(title={Column{Text("ExamScan",fontWeight=FontWeight.Bold);Text("Fast exam paper capture",style=MaterialTheme.typography.labelMedium)}})},floatingActionButton={ExtendedFloatingActionButton(text={Text("New exam")},icon={Icon(Icons.Default.Add,null)},onClick={show=true})}){pad->
        if(exams.isEmpty()) EmptyState("No exams yet","Create an exam folder, set the expected pages, then start scanning.",Modifier.padding(pad)) else LazyColumn(Modifier.padding(pad).fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){items(exams,key={it.id}){e->ExamCard(e,{nav.navigate("exam/${e.id}")},{vm.deleteExam(e)})}}
    }
    if(show) NewExamDialog({show=false}){n,d,c->vm.createExam(n,d,c){nav.navigate("exam/$it")};show=false}
}

@Composable fun ExamCard(e:ExamEntity,open:()->Unit,delete:()->Unit){Card(onClick=open,modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically){Surface(shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.primaryContainer){Icon(Icons.Default.Folder,null,Modifier.padding(14.dp),tint=MaterialTheme.colorScheme.primary)};Spacer(Modifier.width(14.dp));Column(Modifier.weight(1f)){Text(e.name,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium);Text("${e.examDate}  •  ${e.pagesPerPaper} page(s) per paper",style=MaterialTheme.typography.bodySmall)};IconButton(onClick=delete){Icon(Icons.Default.DeleteOutline,"Delete")}}}}

@Composable fun NewExamDialog(close:()->Unit,create:(String,String,Int)->Unit){var name by remember{mutableStateOf("")};var date by remember{mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))};var pages by remember{mutableStateOf("2")};AlertDialog(onDismissRequest=close,title={Text("Create exam")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){OutlinedTextField(name,{name=it},label={Text("Exam name")},singleLine=true);OutlinedTextField(date,{date=it},label={Text("Date (YYYY-MM-DD)")},singleLine=true);OutlinedTextField(pages,{pages=it.filter(Char::isDigit)},label={Text("Pages per paper")},singleLine=true)}},confirmButton={Button(onClick={create(name,date,pages.toIntOrNull()?.coerceIn(1,50)?:1)},enabled=name.isNotBlank()){Text("Create")}},dismissButton={TextButton(onClick=close){Text("Cancel")}})}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ExamScreen(vm:AppViewModel,nav:NavHostController,examId:Long){
    val exam by vm.exam(examId).collectAsState(initial=null);val papers by vm.papers(examId).collectAsState(initial=emptyList());val activity=LocalActivity.current ?: return;val scope=rememberCoroutineScope();var menu by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}
    var pending by remember{mutableStateOf<ScanMode?>(null)}
    val launcher=rememberLauncherForActivityResult(StartIntentSenderForResult()){r->if(r.resultCode==Activity.RESULT_OK){val result=GmsDocumentScanningResult.fromActivityResultIntent(r.data);val uris=result?.pages?.map{it.imageUri}.orEmpty();val e=exam;if(e!=null&&uris.isNotEmpty()){when(pending){ScanMode.Single->vm.addSingle(examId,uris);ScanMode.Bulk->vm.addBulk(examId,e.pagesPerPaper,uris);null->{}}}}}
    fun scan(mode:ScanMode){val e=exam?:return;pending=mode;val limit=if(mode==ScanMode.Single)e.pagesPerPaper else 200;val options=GmsDocumentScannerOptions.Builder().setGalleryImportAllowed(true).setPageLimit(limit).setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG).setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL).build();GmsDocumentScanning.getClient(options).getStartScanIntent(activity).addOnSuccessListener{launcher.launch(IntentSenderRequest.Builder(it).build())}.addOnFailureListener{error=it.message?:"Scanner unavailable"}}
    Scaffold(topBar={TopAppBar(title={Column{Text(exam?.name?:"Exam",fontWeight=FontWeight.Bold);exam?.let{Text("${it.examDate} • ${papers.size} papers",style=MaterialTheme.typography.labelSmall)}}},navigationIcon={IconButton({nav.popBackStack()}){Icon(Icons.Default.ArrowBack,null)}},actions={IconButton({menu=true}){Icon(Icons.Default.MoreVert,null)};DropdownMenu(menu,{menu=false}){DropdownMenuItem({Text("Export PDF")},{scope.launch{share(activity,vm.exportPdf(examId),"application/pdf")};menu=false},leadingIcon={Icon(Icons.Default.PictureAsPdf,null)});DropdownMenuItem({Text("Export images ZIP")},{scope.launch{share(activity,vm.exportZip(examId),"application/zip")};menu=false},leadingIcon={Icon(Icons.Default.FolderZip,null)})}})},floatingActionButton={Column(horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.spacedBy(10.dp)){SmallFloatingActionButton({scan(ScanMode.Bulk)}){Icon(Icons.Default.Collections,null)};ExtendedFloatingActionButton(text={Text("Scan paper")},icon={Icon(Icons.Default.DocumentScanner,null)},onClick={scan(ScanMode.Single)})}}){pad->
        if(papers.isEmpty()) EmptyState("Ready to scan","Use Scan paper for one submission or the upper button for a continuous bulk session.",Modifier.padding(pad)) else LazyColumn(Modifier.padding(pad).fillMaxSize(),contentPadding=PaddingValues(16.dp,16.dp,16.dp,120.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){items(papers,key={it.paper.id}){pw->PaperCard(pw,exam?.pagesPerPaper?:0,{nav.navigate("paper/${pw.paper.id}/${exam?.pagesPerPaper?:1}")},{vm.deletePaper(pw.paper)})}}
    }
    error?.let{AlertDialog(onDismissRequest={error=null},title={Text("Scanner error")},text={Text(it)},confirmButton={TextButton({error=null}){Text("OK")}})}
}

enum class ScanMode{Single,Bulk}

@Composable fun PaperCard(pw:PaperWithPages,expected:Int,open:()->Unit,delete:()->Unit){val count=pw.pages.size;val complete=count==expected;Card(onClick=open,Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Row(verticalAlignment=Alignment.CenterVertically){Text("Paper ${pw.paper.paperNumber}",fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium);Spacer(Modifier.weight(1f));Badge(containerColor=if(complete)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error){Text("$count/$expected",Modifier.padding(horizontal=7.dp,vertical=3.dp))};IconButton(delete){Icon(Icons.Default.DeleteOutline,"Delete paper")}};if(pw.pages.isNotEmpty()){Spacer(Modifier.height(8.dp));LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){items(pw.pages.sortedBy{it.pageNumber}){p->AsyncImage(File(p.labeledPath),null,Modifier.size(72.dp,96.dp).clip(RoundedCornerShape(8.dp)),contentScale=ContentScale.Crop)}}};if(!complete)Text(if(count<expected)"Missing ${expected-count} page(s)" else "${count-expected} extra page(s)",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.labelMedium,modifier=Modifier.padding(top=8.dp))}}}

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

private fun share(activity:Activity,uri:Uri,mime:String){activity.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type=mime;putExtra(Intent.EXTRA_STREAM,uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)},"Share export"))}
