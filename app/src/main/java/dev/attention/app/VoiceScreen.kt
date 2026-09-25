package dev.attention.app

import android.Manifest
import android.app.Application
import android.content.*
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import androidx.lifecycle.compose.*
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class VoiceStage { IDLE, LISTENING, UNDERSTANDING, CONFIRMATION, CLARIFY, RESULTS, APPLYING, DONE, ERROR }
data class VoiceState(val stage:VoiceStage=VoiceStage.IDLE,val text:String="",val error:String="",val result:VoiceResult?=null,val context:List<Task> = emptyList(),val found:List<Task> = emptyList(),val undo:VoiceUndo?=null)
class VoiceViewModel(application:Application):AndroidViewModel(application){
 private val app=application as AttentionApp
 val state=MutableStateFlow(VoiceState());private var job:Job?=null
 fun listening(){job?.cancel();state.value=VoiceState(stage=VoiceStage.LISTENING)}
 fun heard(s:String){state.value=state.value.copy(text=s)}
 fun error(s:String){state.value=state.value.copy(stage=VoiceStage.ERROR,error=s)}
 fun reset(){if(state.value.stage==VoiceStage.APPLYING)return;job?.cancel();state.value=VoiceState()}
 fun stopped(){if(state.value.stage==VoiceStage.LISTENING)state.value=state.value.copy(stage=VoiceStage.IDLE)}
 fun understand(text:String,selected:Task?=null){
  if(text.isBlank()){error("Не расслышал");return};if(text.length>1800){error("Слишком длинная команда. Сократите текст");return}
  job?.cancel();state.value=VoiceState(stage=VoiceStage.UNDERSTANDING,text=text)
  job=viewModelScope.launch{
   try{
    val connection=withContext(Dispatchers.IO){VoiceCredentials(app).load()};if(connection.url.isBlank())throw VoiceFailure("Сначала настройте подключение")
    val all=app.repository.all();val candidates=selected?.let{s->listOfNotNull(all.firstOrNull{it.id==s.id})}?:VoiceContract.candidates(text,all)
    val input=if(selected==null)text else "$text\nУточнение пользователя: применить к задаче с id ${selected.id}."
    val result=VoiceApi.understand(app,connection,input,candidates);ensureActive()
    if(result.status!="READY"){
     state.value=state.value.copy(stage=if(result.status=="NEEDS_CLARIFICATION")VoiceStage.CLARIFY else VoiceStage.ERROR,error=result.message,result=result,context=candidates,found=if(result.candidates.isNotEmpty())all.filter{it.id in result.candidates} else if(candidates.isNotEmpty())candidates else all.filter{!it.done});return@launch
    }
    val c=requireNotNull(result.command);VoiceContract.validate(c,candidates)
    state.value=state.value.copy(stage=VoiceStage.CONFIRMATION,result=result,context=candidates)
    if(c.action==VoiceAction.QUERY_TASKS){state.value=state.value.copy(stage=VoiceStage.RESULTS,found=withContext(Dispatchers.Default){VoiceContract.query(requireNotNull(c.query),all,System.currentTimeMillis())})}
    else if(VoiceContract.auto(result))applyCommand()
   }catch(e:CancellationException){throw e}catch(e:Exception){error(if(e is VoiceFailure)e.label else "Не удалось обработать команду")}
  }
 }
 fun confirm(){if(state.value.stage!=VoiceStage.CONFIRMATION)return;job=viewModelScope.launch{applyCommand()}}
 private suspend fun applyCommand(){
  val current=state.value;val c=current.result?.command?:return
  state.value=current.copy(stage=VoiceStage.APPLYING)
  // Once the local transaction starts, retain its receipt even if the sheet closes.
  withContext(NonCancellable){try{val receipt=VoiceExecutor(app).apply(c,current.context);state.value=current.copy(stage=VoiceStage.DONE,undo=receipt)}catch(e:Exception){error("Задача изменилась или не удалось сохранить. Повторите команду")}}
 }
 suspend fun undo(receipt:VoiceUndo){VoiceExecutor(app).undo(receipt);state.value=VoiceState()}
}
class SpeechCapture(private val context:Context,private val text:(String)->Unit,private val result:(String)->Unit,private val error:(String)->Unit){
 private var recognizer:SpeechRecognizer?=null;private var active=false;private var generation=0
 fun start(language:String){
  cancel();if(!SpeechRecognizer.isRecognitionAvailable(context)){error("Распознавание речи недоступно. Введите текст");return}
  try{active=true;val current=++generation;val r=SpeechRecognizer.createSpeechRecognizer(context);recognizer=r
   r.setRecognitionListener(object:RecognitionListener{
    override fun onReadyForSpeech(params:Bundle?){}
    override fun onBeginningOfSpeech(){}
    override fun onRmsChanged(rmsdB:Float){}
    override fun onBufferReceived(buffer:ByteArray?){}
    override fun onEndOfSpeech(){}
    override fun onEvent(eventType:Int,params:Bundle?){}
    override fun onPartialResults(partialResults:Bundle?){if(active&&current==generation)partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(text)}
    override fun onResults(results:Bundle?){if(!active||current!=generation)return;val value=results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty();cancel();if(value.isBlank())error("Не расслышал")else result(value)}
    override fun onError(code:Int){if(!active||current!=generation)return;cancel();error(when(code){SpeechRecognizer.ERROR_NETWORK,SpeechRecognizer.ERROR_NETWORK_TIMEOUT->"Нет соединения";SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS->"Разрешите доступ к микрофону";SpeechRecognizer.ERROR_NO_MATCH,SpeechRecognizer.ERROR_SPEECH_TIMEOUT->"Не расслышал";else->"Ошибка распознавания. Повторите или введите текст"})}
   });r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_LANGUAGE,language).putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true).putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1))
  }catch(e:Exception){cancel();error("Распознавание недоступно. Введите текст")}
 }
 fun cancel(){generation++;active=false;recognizer?.cancel();recognizer?.destroy();recognizer=null}
}
@Composable fun VoiceConnectionSettings(onSaved:()->Unit={}){
 val context=LocalContext.current;val scope=rememberCoroutineScope();var url by remember{mutableStateOf("")};var token by remember{mutableStateOf("")};var language by remember{mutableStateOf("ru-RU")};var error by remember{mutableStateOf("")};var ready by remember{mutableStateOf(false)};var edit by remember{mutableStateOf(false)}
 LaunchedEffect(Unit){val c=withContext(Dispatchers.IO){VoiceCredentials(context).load()};url=c.url;token=c.token;language=c.language;ready=c.url.isNotBlank()}
 Column(Modifier.fillMaxWidth()){
  if(ready&&!edit){Text("Голосовой ввод подключён",fontSize=14.sp);Text(url,color=Muted,fontSize=11.sp);TextButton(onClick={edit=true}){Text("Изменить подключение")}}
  else{
   Text("Подключение голосового ввода",fontSize=18.sp)
   Text("Речь распознаёт Android. Текст и несколько подходящих задач отправляются через ваш Worker в OpenAI. Аудио может обрабатывать системный сервис распознавания.",color=Muted,fontSize=12.sp,lineHeight=18.sp,modifier=Modifier.padding(vertical=10.dp))
   OutlinedTextField(url,{url=it},label={Text("HTTPS-адрес Worker")},singleLine=true,modifier=Modifier.fillMaxWidth())
   OutlinedTextField(token,{token=it},label={Text("APP_TOKEN")},visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth())
   Text("Здесь нужен отдельный токен Worker. Ключ OpenAI сюда не вставляйте.",color=Muted,fontSize=11.sp,modifier=Modifier.padding(vertical=6.dp))
   OutlinedTextField(language,{language=it},label={Text("Язык речи")},singleLine=true,modifier=Modifier.fillMaxWidth())
   if(error.isNotBlank())Text(error,color=Accent,fontSize=12.sp)
   Button(onClick={scope.launch{try{withContext(Dispatchers.IO){VoiceCredentials(context).save(VoiceConnection(url.trim(),token.trim(),language.trim()))};ready=true;edit=false;error="";onSaved()}catch(e:Exception){error=e.message?:"Не удалось сохранить подключение"}}},shape=RoundedCornerShape(12.dp)){Text("Сохранить подключение")}
  }
 }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun VoiceSheet(onClose:()->Unit,onOpen:(Task)->Unit,onDone:(VoiceUndo,String)->Unit,vm:VoiceViewModel=viewModel()){
 val context=LocalContext.current;val state by vm.state.collectAsStateWithLifecycle();val lifecycle=LocalLifecycleOwner.current;val tap=haptic();var configured by remember{mutableStateOf<Boolean?>(null)};var language by remember{mutableStateOf("ru-RU")};var manual by remember{mutableStateOf("")}
 val capture=remember{SpeechCapture(context,{vm.heard(it)},{vm.heard(it);vm.understand(it)},{vm.error(it)})}
 fun listen(){tap();vm.listening();capture.start(language)}
 val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){if(it)listen()else vm.error("Микрофон не разрешён. Можно ввести текст")}
 fun start(){if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)listen()else permission.launch(Manifest.permission.RECORD_AUDIO)}
 LaunchedEffect(Unit){val c=withContext(Dispatchers.IO){VoiceCredentials(context).load()};configured=c.url.isNotBlank();language=c.language;if(configured==true&&state.stage==VoiceStage.IDLE)start()}
 LaunchedEffect(state.text){manual=state.text}
 LaunchedEffect(state.stage){if(state.stage==VoiceStage.LISTENING){delay(25000);capture.cancel();vm.error("Не расслышал. Попробуйте ещё раз")};if(state.stage==VoiceStage.DONE){val receipt=state.undo;if(receipt!=null){tap();onDone(receipt,if(receipt.before==null)"Создано: ${receipt.after.title}" else "Обновлено: ${receipt.after.title}");vm.reset();onClose()}}}
 DisposableEffect(lifecycle){val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_STOP){capture.cancel();vm.stopped()}};lifecycle.lifecycle.addObserver(observer);onDispose{capture.cancel();vm.stopped();lifecycle.lifecycle.removeObserver(observer)}}
 val busy=state.stage in listOf(VoiceStage.UNDERSTANDING,VoiceStage.APPLYING)
 ModalBottomSheet(onDismissRequest={if(state.stage!=VoiceStage.APPLYING){capture.cancel();vm.reset();onClose()}},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=Surface){
  Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal=24.dp).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
   if(configured==false)VoiceConnectionSettings{configured=true;start()}
   else if(configured==true){
    Row(verticalAlignment=Alignment.CenterVertically){val scale by animateFloatAsState(if(state.stage==VoiceStage.LISTENING)1.08f else 1f,label="mic");Icon(Icons.Rounded.Mic,null,Modifier.size(26.dp).scale(scale),tint=Accent);Spacer(Modifier.width(12.dp));Text(when(state.stage){VoiceStage.LISTENING->"Слушаю";VoiceStage.UNDERSTANDING->"Обрабатываю";VoiceStage.APPLYING->"Сохраняю";VoiceStage.CONFIRMATION->"Проверьте команду";VoiceStage.CLARIFY->"Нужно уточнение";VoiceStage.RESULTS->"Найдено: ${state.found.size}";else->"Голосовой ввод"},fontSize=22.sp)}
    if(busy)LinearProgressIndicator(Modifier.fillMaxWidth(),color=Accent)
    if(state.stage==VoiceStage.LISTENING){Text(state.text.ifBlank{"Скажите, что нужно сделать"},color=Muted);TextButton(onClick={capture.cancel();vm.stopped()}){Text("Отменить запись")}}
    else if(!busy&&state.stage!=VoiceStage.RESULTS){OutlinedTextField(manual,{manual=it},modifier=Modifier.fillMaxWidth(),label={Text("Текст команды")},minLines=2,maxLines=5)}
    if(state.stage in listOf(VoiceStage.ERROR,VoiceStage.CLARIFY))Text(state.error.ifBlank{"Уточните команду"},color=Accent,fontSize=13.sp)
    if(state.stage==VoiceStage.CONFIRMATION){Text(state.result?.command?.summary(state.context).orEmpty(),lineHeight=24.sp);Button(enabled=manual==state.text,onClick={tap();vm.confirm()},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp)){Text("Подтвердить")}}
    if(state.stage==VoiceStage.CLARIFY&&state.result?.candidates?.isNotEmpty()==true){state.found.forEach{t->TextButton(onClick={vm.understand(manual,t)}){Text(t.title)}}}
    if(state.stage==VoiceStage.CLARIFY&&state.result?.candidates?.isEmpty()==true){var choose by remember{mutableStateOf(false)};TextButton(onClick={choose=!choose}){Text("Выбрать существующую задачу")};if(choose)state.found.forEach{t->TextButton(onClick={vm.understand(manual,t)}){Text(t.title)}}}
    if(state.stage==VoiceStage.RESULTS){if(state.found.isEmpty())Text("Подходящих активных задач нет",color=Muted);state.found.take(50).forEach{t->Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable{vm.reset();onClose();onOpen(t)}.background(Ink).padding(14.dp)){Text(t.title);Text("${t.date} · ${t.importance} · ${t.progress}%"+(t.workLabel().let{if(it.isBlank())""else " · $it"}),color=Muted,fontSize=12.sp)}};if(state.found.size>50)Text("Показаны первые 50. Уточните запрос",color=Muted)}
    if(!busy&&state.stage!=VoiceStage.LISTENING){Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){TextButton(onClick={start()}){Text("Повторить голосом")};TextButton(onClick={vm.understand(manual)},enabled=manual.isNotBlank()){Text(if(state.stage==VoiceStage.CONFIRMATION)"Разобрать заново" else "Отправить текст")}}}
    if(state.stage==VoiceStage.UNDERSTANDING)TextButton(onClick={vm.reset()}){Text("Отмена")}
   }
  }
 }
}
