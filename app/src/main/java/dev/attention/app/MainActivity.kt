package dev.attention.app
import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.*
import java.time.format.DateTimeFormatter
class MainActivity:ComponentActivity(){
 private val route=MutableStateFlow("");private val epoch=MutableStateFlow(0)
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);enableEdgeToEdge();route.value=intent.getStringExtra("route")?:"";setContent{FocusTheme{FocusApp(route,epoch)}}}
 override fun onNewIntent(intent:Intent){super.onNewIntent(intent);setIntent(intent);route.value="";route.value=intent.getStringExtra("route")?:"";epoch.value++}
 override fun onResume(){super.onResume();epoch.value++;(application as AttentionApp).scope.launch{(application as AttentionApp).scheduler.reschedule()}}
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun FocusApp(route:MutableStateFlow<String>,epoch:MutableStateFlow<Int>,vm:FocusViewModel=viewModel()){
 val tasks by vm.tasks.collectAsStateWithLifecycle();val prefs by vm.preferences.collectAsStateWithLifecycle();val events by vm.events.collectAsStateWithLifecycle();val external by route.collectAsStateWithLifecycle();val resume by epoch.collectAsStateWithLifecycle();val p=prefs;val scope=rememberCoroutineScope();val tap=haptic();val snack=remember{SnackbarHostState()}
 var voice by rememberSaveable{mutableStateOf(false)};val voiceVm:VoiceViewModel=viewModel()
 var page by rememberSaveable{mutableStateOf("Задачи")};var sort by rememberSaveable{mutableStateOf("Фокус")};var query by rememberSaveable{mutableStateOf("")};var searching by remember{mutableStateOf(false)};var map by remember{mutableStateOf(false)};var editor by remember{mutableStateOf<Task?>(null)};var creating by remember{mutableStateOf(false)};var progressTask by remember{mutableStateOf<Task?>(null)};var actionTask by remember{mutableStateOf<Task?>(null)};var deleteTask by remember{mutableStateOf<Task?>(null)};var now by remember{mutableLongStateOf(System.currentTimeMillis())};var pendingRoute by remember{mutableStateOf("")};var available by rememberSaveable{mutableStateOf<Int?>(null)};var timeSheet by remember{mutableStateOf(false)}
 val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){vm.host.scope.launch{vm.host.scheduler.reschedule()}}
 LaunchedEffect(resume){now=System.currentTimeMillis()};LaunchedEffect(Unit){while(true){delay(15000);now=System.currentTimeMillis()}};LaunchedEffect(Unit){vm.messages.collect{snack.showSnackbar(it)}}
 LaunchedEffect(external,resume){if(external.isNotEmpty()){pendingRoute=external;route.value=""}}
 LaunchedEffect(pendingRoute,tasks){if(pendingRoute=="voice"){page="Задачи";voice=true;pendingRoute=""}else if(pendingRoute=="create"){page="Задачи";creating=true;pendingRoute=""}else if(pendingRoute=="calendar"){page="Календарь";pendingRoute=""}else if(pendingRoute=="home"){page="Задачи";map=false;pendingRoute=""}else if(pendingRoute=="summary"){page="Сводка";pendingRoute=""}else if(pendingRoute.startsWith("task:")){tasks.firstOrNull{it.id==pendingRoute.removePrefix("task:")}?.let{editor=it;pendingRoute=""}}}
 fun finish(t:Task){if(t.subtasks.isNotEmpty()){editor=t;return};vm.save(t.withProgress(if(t.done)0 else 100));scope.launch{snack.currentSnackbarData?.dismiss();if(snack.showSnackbar(if(t.done)"Задача возвращена" else "Задача выполнена","Отменить",duration=SnackbarDuration.Short)==SnackbarResult.ActionPerformed)vm.save(t)}}
 fun updateProgress(t:Task,value:Int){vm.save(t.withProgress(value));if(value==100&&!t.done)scope.launch{snack.currentSnackbarData?.dismiss();if(snack.showSnackbar("Задача выполнена","Отменить")==SnackbarResult.ActionPerformed)vm.save(t)}}
 BackHandler(page !in listOf("Задачи","Календарь","Аналитика","История")||searching||map){if(searching){searching=false;query=""}else if(map)map=false else page="Задачи"}
 if(p==null){Box(Modifier.fillMaxSize().background(Ink));return}
 if(!p.onboarded){Onboarding{vm.settings(p.copy(onboarded=true));if(Build.VERSION.SDK_INT>=33)permission.launch(Manifest.permission.POST_NOTIFICATIONS)};return}
 val active=tasks.filter{!it.done};val filtered=active.filter{query.isBlank()||"${it.title} ${it.note} ${it.category}".contains(query,true)}.filter{available==null||EffortLogic.fits(it,available!!)}
 val ordered=when(if(available!=null)"Фокус" else sort){"Важность"->filtered.sortedWith(compareByDescending<Task>{it.importance}.thenBy{it.due}.thenBy{it.createdAt});"Срочность"->filtered.sortedWith(compareBy<Task>{it.due}.thenByDescending{it.importance});else->filtered.sortedWith(compareByDescending<Task>{it.due<now}.thenByDescending{FocusLogic.score(it.importance,it.progress,it.due,now)}.thenBy{it.due}.thenBy{it.createdAt})}
 Scaffold(containerColor=Ink,snackbarHost={SnackbarHost(snack)},contentWindowInsets=WindowInsets.safeDrawing,
  bottomBar={Row(Modifier.fillMaxWidth().background(Ink).navigationBarsPadding().padding(horizontal=16.dp,vertical=8.dp),horizontalArrangement=Arrangement.SpaceEvenly){listOf("Задачи" to Icons.Rounded.ShortText,"Календарь" to Icons.Rounded.CalendarToday,"Аналитика" to Icons.Rounded.BarChart,"История" to Icons.Rounded.DoneAll).forEach{(name,icon)->Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable{tap();page=name}.padding(vertical=9.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(icon,name,tint=if(page==name)Accent else Muted,modifier=Modifier.size(22.dp));Spacer(Modifier.height(5.dp));Text(name,fontSize=10.sp,color=if(page==name)TextPrimary else Muted)}}}},
  floatingActionButton={if(page!="Настройки")Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){if(page=="Задачи")Box(Modifier.size(48.dp).background(Surface,CircleShape).clip(CircleShape).clickable{tap();voice=true},contentAlignment=Alignment.Center){Icon(Icons.Rounded.Mic,"Голосовой ввод",tint=Accent)};Box(Modifier.size(56.dp).shadow(14.dp,CircleShape,ambientColor=Accent.copy(alpha=.2f),spotColor=Accent.copy(alpha=.25f)).background(Accent,CircleShape).clip(CircleShape).clickable{tap();creating=true},contentAlignment=Alignment.Center){Icon(Icons.Rounded.Add,"Добавить задачу",tint=Ink,modifier=Modifier.size(28.dp))}}}){insets->
  Column(Modifier.fillMaxSize().padding(insets).padding(horizontal=20.dp)){
   Row(Modifier.fillMaxWidth().padding(top=12.dp,bottom=4.dp),verticalAlignment=Alignment.CenterVertically){if(page=="Сводка"||page=="Настройки")Glyph(Icons.Rounded.ArrowBack,"Назад",{page="Задачи"});Column(Modifier.weight(1f)){Text(if(page=="Сводка")LocalDate.now().format(DateTimeFormatter.ofPattern("d MMMM",Ru)) else "F O C U S",fontSize=10.sp,letterSpacing=2.sp,color=Muted);Spacer(Modifier.height(6.dp));Text(when(page){"Задачи"->if(map)"Карта внимания" else "Фокус";"Сводка"->"Доброе утро";else->page},fontSize=30.sp,fontWeight=FontWeight.SemiBold,letterSpacing=(-.8).sp)};if(page=="Задачи"){Glyph(Icons.Rounded.WbTwilight,"Утренняя сводка",{page="Сводка"});Glyph(Icons.Rounded.Settings,"Настройки",{page="Настройки"})}}
   if(page in listOf("Задачи","Сводка"))Text("Активных: ${active.size} · сегодня: ${active.count{it.date==LocalDate.now().toString()}}"+(if(active.any{it.due<now})" · просрочено: ${active.count{it.due<now}}" else ""),fontSize=12.sp,color=Muted,modifier=Modifier.padding(top=6.dp,bottom=12.dp))
   if(page=="Задачи"&&p.enabled&&!vm.host.scheduler.exactAllowed())TextButton(onClick={page="Настройки"},contentPadding=PaddingValues(0.dp)){Icon(Icons.Rounded.Schedule,null,Modifier.size(14.dp),tint=Muted);Spacer(Modifier.width(7.dp));Text("Настроить точное время сводки",fontSize=11.sp,color=Muted)}
   when(page){
    "Задачи"->{Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Row(Modifier.weight(1f)){if(!map&&available!=null){Pill("Фокус",true){};Pill("Все задачи"){available=null}}else if(!map)listOf("Фокус","Важность","Срочность").forEach{s->Pill(s,sort==s){tap();sort=s}}else Text("Важность × срочность",color=Muted,fontSize=13.sp)};Glyph(if(map)Icons.Rounded.ViewList else Icons.Rounded.ScatterPlot,if(map)"Список" else "Карта",{tap();map=!map},if(map)Accent else Muted)}
     Row(verticalAlignment=Alignment.CenterVertically){Glyph(Icons.Rounded.Schedule,"Есть время",{timeSheet=true},if(available==null)Muted else Accent);if(searching)TextField(value=query,onValueChange={query=it},placeholder={Text("Название, заметка, категория",fontSize=13.sp)},singleLine=true,modifier=Modifier.weight(1f),colors=TextFieldDefaults.colors(focusedContainerColor=Surface,unfocusedContainerColor=Surface,focusedIndicatorColor=Color.Transparent,unfocusedIndicatorColor=Color.Transparent),shape=RoundedCornerShape(12.dp))else Text(if(available!=null)"Есть ${effortText(available!!)} · Фокус" else if(map)"Тап по точке — открыть задачу" else when(sort){"Фокус"->"Приоритет с учётом срока";"Важность"->"От 10 к 1";else->"Ближайшие сроки первыми"},fontSize=11.sp,color=Muted,modifier=Modifier.weight(1f));Glyph(if(searching)Icons.Rounded.Close else Icons.Rounded.Search,"Поиск",{searching=!searching;if(!searching)query=""})}
     if(map)PriorityMap(filtered,now){editor=it}else LazyColumn(verticalArrangement=Arrangement.spacedBy(9.dp),contentPadding=PaddingValues(top=5.dp,bottom=100.dp)){if(ordered.isEmpty())item{QuietEmpty(if(available!=null)"Нет задач на это время" else if(query.isBlank())"Всё на своих местах" else "Ничего не найдено",if(available!=null)"Выберите больше времени или добавьте оценку объёма в задаче." else if(query.isBlank())"Добавьте задачу. Срок и важность\nпомогут выбрать, что делать первым." else "Попробуйте другой запрос")};items(ordered,key={it.id}){t->TaskCard(t,now,{editor=t},{finish(t)},{if(t.subtasks.isEmpty())progressTask=t else editor=t},{actionTask=t},Modifier.animateItem(fadeInSpec=tween(180),fadeOutSpec=tween(220),placementSpec=spring(stiffness=450f)))}}
    }
    "Календарь"->CalendarScreen(active,now,{editor=it},{finish(it)},{if(it.subtasks.isEmpty())progressTask=it else editor=it},{actionTask=it})
    "Аналитика"->AnalyticsScreen(events,active,now)
    "История"->HistoryScreen(tasks.filter{it.done},now,{editor=it},{finish(it)},{if(it.subtasks.isEmpty())progressTask=it else editor=it},{actionTask=it})
    "Сводка"->SummaryScreen(active,now,{editor=it},{finish(it)},{if(it.subtasks.isEmpty())progressTask=it else editor=it},{actionTask=it})
    "Настройки"->SettingsScreen(vm,p,resume,snack)
   }
  }
 }
 if(voice)VoiceSheet({voice=false},{editor=it},{receipt,label->scope.launch{snack.currentSnackbarData?.dismiss();if(snack.showSnackbar(label,"Отменить",duration=SnackbarDuration.Long)==SnackbarResult.ActionPerformed)runCatching{voiceVm.undo(receipt)}.onFailure{snack.showSnackbar("Задача уже изменена. Отмена недоступна")}}},voiceVm)
 if(timeSheet)TimeAvailableSheet(available,{timeSheet=false}){available=it;sort="Фокус"}
 if(creating||editor!=null)TaskEditor(editor,p,{creating=false;editor=null},{vm.save(it);tap();creating=false;editor=null},{deleteTask=it},{name->vm.settings(p.copy(categories=(p.categories.split('|')+name).distinct().joinToString("|")))},{vm.save(it)})
 progressTask?.let{original->val current=tasks.firstOrNull{it.id==original.id}?:original;ProgressSheet(current,{progressTask=null}){value->updateProgress(current,value);if(value==100)progressTask=null}}
 actionTask?.let{t->AlertDialog(onDismissRequest={actionTask=null},containerColor=Surface,title={Text(t.title,maxLines=2)},text={Column{TextButton(onClick={vm.save(t.copy(date=LocalDate.now().plusDays(1).toString()));actionTask=null}){Text("Перенести на завтра")};TextButton(onClick={editor=t;actionTask=null}){Text("Редактировать")};TextButton(onClick={deleteTask=t;actionTask=null}){Text("Удалить",color=Color(0xFFF0787E))}}},confirmButton={},dismissButton={TextButton(onClick={actionTask=null}){Text("Закрыть")}})}
 deleteTask?.let{t->AlertDialog(onDismissRequest={deleteTask=null},containerColor=Surface,title={Text("Удалить задачу?")},text={Text(t.title)},confirmButton={TextButton(onClick={vm.delete(t);deleteTask=null;editor=null;scope.launch{snack.currentSnackbarData?.dismiss();if(snack.showSnackbar("Задача удалена","Отменить")==SnackbarResult.ActionPerformed)vm.save(t)}}){Text("Удалить",color=Color(0xFFF0787E))}},dismissButton={TextButton(onClick={deleteTask=null}){Text("Оставить")}})}
}
