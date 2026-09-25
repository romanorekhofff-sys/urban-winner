package dev.attention.app
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.semantics.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.alpha
import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun TaskEditor(task:Task?,p:Preferences,onDismiss:()->Unit,onSave:(Task)->Unit,onDelete:(Task)->Unit,onAddCategory:(String)->Unit,onSubtasksChange:(Task)->Unit={}){
 val context=LocalContext.current;val tap=haptic();val focus=remember{FocusRequester()};val keyboard=LocalSoftwareKeyboardController.current
 var title by remember(task?.id){mutableStateOf(task?.title?:"")};var date by remember{mutableStateOf(task?.date?:LocalDate.now().toString())};var minute by remember{mutableIntStateOf(task?.minute?:-1)};var importance by remember{mutableIntStateOf(task?.importance?:5)};var progress by remember{mutableIntStateOf(task?.progress?:0)};var note by remember{mutableStateOf(task?.note?:"")};var category by remember{mutableStateOf(task?.category?:"")};var reminder by remember{mutableStateOf(task?.reminder)};var more by remember{mutableStateOf(task!=null)};var newCategory by remember{mutableStateOf(false)};var categoryName by remember{mutableStateOf("")}
 val parentId=remember{task?.id?:UUID.randomUUID().toString()};val scope=rememberCoroutineScope()
 var subs by remember{mutableStateOf(task?.subtasks?:emptyList())};var effort by remember{mutableStateOf(task?.estimatedEffortMinutes)}
 var subEditor by remember{mutableStateOf<Subtask?>(null)};var subTitle by remember{mutableStateOf("")};var subEffort by remember{mutableStateOf<Int?>(null)};var removeSub by remember{mutableStateOf<Subtask?>(null)}
 fun draft(children:List<Subtask> = subs)=EffortLogic.normalize((task?:Task(id=parentId,title=title.trim())).copy(title=title.trim(),date=date,minute=minute,importance=importance,progress=progress,note=note.trim(),category=category,reminder=reminder,estimatedEffortMinutes=effort,subtasks=children))
 fun updateSubs(children:List<Subtask>){subs=children;progress=if(children.isNotEmpty())EffortLogic.progress(children) else progress;if(task!=null&&title.isNotBlank())onSubtasksChange(draft(children))}
 ModalBottomSheet(onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=Ink,dragHandle={Box(Modifier.padding(top=10.dp,bottom=6.dp).size(32.dp,3.dp).background(Muted.copy(alpha=.4f),CircleShape))}){
  Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal=24.dp).padding(bottom=24.dp)){
   Row(verticalAlignment=Alignment.CenterVertically){Text(if(task==null)"Новая задача" else "Задача",Modifier.weight(1f),color=Muted,fontSize=12.sp);if(task!=null)Glyph(Icons.Rounded.DeleteOutline,"Удалить",{onDelete(task)});Glyph(Icons.Rounded.Close,"Закрыть",onDismiss)}
   TextField(value=title,onValueChange={title=it.take(300)},placeholder={Text("Что нужно сделать?",fontSize=24.sp,color=Muted)},textStyle=LocalTextStyle.current.copy(fontSize=24.sp,fontWeight=FontWeight.Medium),maxLines=3,modifier=Modifier.fillMaxWidth().focusRequester(focus),colors=TextFieldDefaults.colors(focusedContainerColor=Ink,unfocusedContainerColor=Ink,focusedIndicatorColor=Color.Transparent,unfocusedIndicatorColor=Color.Transparent))
   SectionLabel("СРОК",LocalDate.parse(date).format(DateTimeFormatter.ofPattern("d MMMM",Ru)))
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){listOf("Сегодня" to 0L,"Завтра" to 1L,"Неделя" to 7L).forEach{(name,days)->Pill(name,date==LocalDate.now().plusDays(days).toString()){tap();date=LocalDate.now().plusDays(days).toString()}};Pill("Дата",date !in listOf(0L,1L,7L).map{LocalDate.now().plusDays(it).toString()}){keyboard?.hide();val d=LocalDate.parse(date);DatePickerDialog(context,{_,y,m,day->date=LocalDate.of(y,m+1,day).toString();tap()},d.year,d.monthValue-1,d.dayOfMonth).show()}}
   Row(verticalAlignment=Alignment.CenterVertically){TextButton(onClick={keyboard?.hide();TimePickerDialog(context,{_,h,m->minute=h*60+m;tap()},if(minute<0)17 else minute/60,if(minute<0)0 else minute%60,true).show()}){Icon(Icons.Rounded.Schedule,null,Modifier.size(15.dp),tint=Muted);Spacer(Modifier.width(8.dp));Text(if(minute<0)"До конца дня · добавить время" else "%02d:%02d".format(minute/60,minute%60),fontSize=12.sp,color=Muted)};if(minute>=0)Glyph(Icons.Rounded.Close,"Убрать точное время",{minute=-1})}
   SectionLabel("ВАЖНОСТЬ")
   Row(verticalAlignment=Alignment.CenterVertically){Text(importance.toString(),Modifier.width(62.dp),fontSize=40.sp,fontWeight=FontWeight.Light,color=importanceColor(importance));Column(Modifier.weight(1f)){Slider(value=importance.toFloat(),onValueChange={val next=it.roundToInt();if(next!=importance){importance=next;tap()}},valueRange=1f..10f,steps=8,colors=SliderDefaults.colors(thumbColor=importanceColor(importance),activeTrackColor=importanceColor(importance),inactiveTrackColor=Raised,activeTickColor=Color.Transparent,inactiveTickColor=Color.Transparent));Row(Modifier.fillMaxWidth().padding(horizontal=7.dp),horizontalArrangement=Arrangement.SpaceBetween){Text("1",fontSize=10.sp,color=Muted);Text("10",fontSize=10.sp,color=Muted)}}}
   if(task!=null){SectionLabel("ВЫПОЛНЕНО","${if(subs.isEmpty())progress else EffortLogic.progress(subs)}%");if(subs.isEmpty())ProgressControl(progress){progress=it}else Text(if(EffortLogic.weighted(subs))"По объёму подзадач" else "По числу подзадач · для взвешивания оцените каждую",color=Muted,fontSize=12.sp);Text(remaining(task.copy(date=date,minute=minute),System.currentTimeMillis()),color=urgencyColor(task.copy(date=date,minute=minute),System.currentTimeMillis()),fontSize=12.sp,modifier=Modifier.padding(top=10.dp))}
   if(task!=null||subs.isNotEmpty()){
    SectionLabel("ПОДЗАДАЧИ",if(subs.isEmpty())"" else "${subs.count{it.completed}}/${subs.size}")
    subs.forEachIndexed{index,sub->key(sub.id){
     val alpha by animateFloatAsState(if(sub.completed).55f else 1f,label="subtask")
     Row(Modifier.fillMaxWidth().animateContentSize().alpha(alpha),verticalAlignment=Alignment.CenterVertically){
      Checkbox(sub.completed,{checked->tap();val next=subs.map{if(it.id==sub.id)it.copy(completed=checked,completedAt=if(checked)System.currentTimeMillis() else null) else it};updateSubs(next);if(next.all{it.completed})scope.launch{delay(220);onSave(draft(next))}},modifier=Modifier.semantics{contentDescription="Подзадача ${sub.title}"})
      Column(Modifier.weight(1f).clickable{subEditor=sub;subTitle=sub.title;subEffort=sub.estimatedEffortMinutes}.padding(vertical=10.dp)){Text(sub.title,fontSize=14.sp);sub.estimatedEffortMinutes?.let{Text(effortText(it),color=Muted,fontSize=11.sp)}}
      if(index>0)Glyph(Icons.Rounded.KeyboardArrowUp,"Выше",{val next=subs.toMutableList();val previous=next[index-1];next[index-1]=sub;next[index]=previous;updateSubs(next);tap()})
      Glyph(Icons.Rounded.Close,"Удалить подзадачу",{removeSub=sub})
     }
    }}
   }
   TextButton(onClick={subEditor=Subtask(parentTaskId=parentId,title="");subTitle="";subEffort=null}){Text("+ Подзадача",color=Accent)}
   if(subs.isNotEmpty())Text("Осталось: ${EffortLogic.remaining(draft()).label()}",fontSize=12.sp,color=Muted)
   if(task!=null){SectionLabel("ПРЕДПОЛАГАЕМЫЙ ОБЪЁМ");if(subs.isEmpty())EffortPicker(effort){effort=it}else Text(EffortLogic.estimate(draft()).label()+" · сумма подзадач",color=Muted,fontSize=13.sp)}
   TextButton(onClick={more=!more}){Text(if(more)"Скрыть дополнительные поля" else "Заметка, категория, напоминания",color=Muted,fontSize=12.sp);Icon(if(more)Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,null,Modifier.size(18.dp),tint=Muted)}
   AnimatedVisibility(more){Column{
    if(task==null&&subs.isEmpty()){SectionLabel("ПРЕДПОЛАГАЕМЫЙ ОБЪЁМ");EffortPicker(effort){effort=it}}
    TextField(value=note,onValueChange={note=it.take(10000)},placeholder={Text("Короткая заметка",fontSize=14.sp)},modifier=Modifier.fillMaxWidth(),maxLines=5,shape=RoundedCornerShape(12.dp),colors=TextFieldDefaults.colors(focusedContainerColor=Surface,unfocusedContainerColor=Surface,focusedIndicatorColor=Color.Transparent,unfocusedIndicatorColor=Color.Transparent))
    SectionLabel("КАТЕГОРИЯ");Row(Modifier.horizontalScroll(rememberScrollState())){Pill("Без категории",category.isEmpty()){category=""};(p.categories.split('|')+category).filter{it.isNotBlank()}.distinct().forEach{c->Pill(c,category==c){tap();category=c}};Pill("+"){newCategory=true}}
    SectionLabel("НАПОМИНАНИЯ");Row{Pill("По умолчанию",reminder==null){reminder=null};Pill("Свои",reminder!=null){reminder=reminder?:p.reminders}};if(reminder!=null)ReminderChoices(reminder?:""){reminder=it}
   }}
   Spacer(Modifier.height(20.dp));Button(onClick={keyboard?.hide();onSave(draft())},enabled=title.isNotBlank(),modifier=Modifier.fillMaxWidth().height(54.dp),shape=RoundedCornerShape(16.dp)){Text(if(task==null)"Добавить" else "Сохранить",fontSize=15.sp,fontWeight=FontWeight.SemiBold)}
  }
 }
 subEditor?.let{sub->AlertDialog(onDismissRequest={subEditor=null},containerColor=Surface,title={Text(if(sub.title.isEmpty())"Подзадача" else "Изменить подзадачу")},text={Column{OutlinedTextField(subTitle,{subTitle=it.take(300)},label={Text("Название")},maxLines=3);SectionLabel("ОЦЕНКА ВРЕМЕНИ");EffortPicker(subEffort){subEffort=it}}},confirmButton={TextButton(enabled=subTitle.isNotBlank(),onClick={val next=sub.copy(title=subTitle.trim(),estimatedEffortMinutes=subEffort);updateSubs(if(subs.any{it.id==sub.id})subs.map{if(it.id==sub.id)next else it} else subs+next);subEditor=null;tap()}){Text("Сохранить")}},dismissButton={TextButton(onClick={subEditor=null}){Text("Отмена")}})}
 removeSub?.let{sub->AlertDialog(onDismissRequest={removeSub=null},containerColor=Surface,title={Text("Удалить подзадачу?")},text={Text(sub.title)},confirmButton={TextButton(onClick={updateSubs(subs.filter{it.id!=sub.id});removeSub=null}){Text("Удалить")}},dismissButton={TextButton(onClick={removeSub=null}){Text("Оставить")}})}
 LaunchedEffect(Unit){if(task==null){delay(250);focus.requestFocus();keyboard?.show()}}
 if(newCategory)AlertDialog(onDismissRequest={newCategory=false},containerColor=Surface,title={Text("Новая категория")},text={TextField(categoryName,{categoryName=it.take(40).replace("|","")},singleLine=true)},confirmButton={TextButton(enabled=categoryName.isNotBlank(),onClick={category=categoryName.trim();onAddCategory(category);newCategory=false}){Text("Добавить")}},dismissButton={TextButton(onClick={newCategory=false}){Text("Отмена")}})
}
@Composable fun ProgressControl(value:Int,onChange:(Int)->Unit){val tap=haptic();Slider(value.toFloat(),{val next=it.roundToInt();if(next!=value){onChange(next);if(next%5==0)tap()}},valueRange=0f..100f,steps=99,colors=SliderDefaults.colors(thumbColor=TextPrimary,activeTrackColor=Muted,inactiveTrackColor=Raised,activeTickColor=Color.Transparent,inactiveTickColor=Color.Transparent));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){listOf(0,25,50,75,100).forEach{v->Pill("$v",value==v){tap();onChange(v)}}}}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ProgressSheet(task:Task,onDismiss:()->Unit,save:(Int)->Unit){var value by remember(task.id){mutableIntStateOf(task.progress)};ModalBottomSheet(onDismissRequest=onDismiss,containerColor=Ink){Column(Modifier.padding(horizontal=24.dp).padding(bottom=30.dp)){Text(task.title,fontSize=20.sp,fontWeight=FontWeight.Medium);SectionLabel("ВЫПОЛНЕНО");Text("$value%",fontSize=48.sp,fontWeight=FontWeight.Light);ProgressControl(value){value=it};Spacer(Modifier.height(20.dp));Button(onClick={save(value);onDismiss()},modifier=Modifier.fillMaxWidth().height(52.dp),shape=RoundedCornerShape(16.dp)){Text(if(value==100)"Завершить задачу" else "Сохранить")}}}}
@Composable fun ReminderChoices(value:String,onChange:(String)->Unit){val selected=value.split(',').mapNotNull{it.toIntOrNull()}.toSet();Column{listOf(0 to "В момент дедлайна",60 to "За 1 час",180 to "За 3 часа",1440 to "За день").forEach{(n,label)->Row(Modifier.fillMaxWidth().clickable{onChange((if(n in selected)selected-n else selected+n).sorted().joinToString(","))}.padding(vertical=2.dp),verticalAlignment=Alignment.CenterVertically){Checkbox(n in selected,{checked->onChange((if(checked)selected+n else selected-n).sorted().joinToString(","))});Text(label,fontSize=14.sp)}}}}
@Composable fun Onboarding(done:()->Unit){var step by remember{mutableIntStateOf(0)};Column(Modifier.fillMaxSize().background(Ink).safeDrawingPadding().padding(28.dp),verticalArrangement=Arrangement.SpaceBetween){Text("F O C U S",color=Muted,fontSize=12.sp,letterSpacing=3.sp,modifier=Modifier.padding(top=20.dp));Column{Text(if(step==0)"Важное\n≠ срочное" else "Внимание —\nтому, что сейчас.",fontSize=42.sp,lineHeight=49.sp,fontWeight=FontWeight.Medium,letterSpacing=(-1).sp);Spacer(Modifier.height(36.dp));if(step==0){Text("Важность задаёте вы",fontSize=14.sp,color=Muted);Spacer(Modifier.height(12.dp));Box(Modifier.fillMaxWidth().height(4.dp).background(Brush.horizontalGradient(listOf(importanceColor(1),importanceColor(5),importanceColor(10))),CircleShape));Row(Modifier.fillMaxWidth().padding(top=8.dp),horizontalArrangement=Arrangement.SpaceBetween){Text("1",color=Muted);Text("10",color=Muted)};Spacer(Modifier.height(24.dp));Text("Срочность определяется сроком",fontSize=14.sp,color=Muted);Spacer(Modifier.height(12.dp));Box(Modifier.fillMaxWidth().height(4.dp).background(Brush.horizontalGradient(listOf(Color(0xFF83BDA2),Color(0xFFDDCF83),Color(0xFFE8B471))),CircleShape));Row(Modifier.fillMaxWidth().padding(top=8.dp),horizontalArrangement=Arrangement.SpaceBetween){Text("Есть время",fontSize=12.sp,color=Muted);Text("Срок близко",fontSize=12.sp,color=Muted)}}else{TaskCard(Task(title="Подготовить лекцию",date=LocalDate.now().plusDays(1).toString(),minute=840,importance=8,progress=70),System.currentTimeMillis(),{},{},{},{});Spacer(Modifier.height(18.dp));Text("Цвет края — важность\nЦвет срока — срочность\nТонкая линия — прогресс",lineHeight=27.sp,color=Muted,fontSize=14.sp)}};Column{Row(Modifier.padding(bottom=20.dp),horizontalArrangement=Arrangement.spacedBy(7.dp)){repeat(2){Box(Modifier.size(if(it==step)24.dp else 7.dp,4.dp).background(if(it==step)Accent else Raised,CircleShape))}};Button(onClick={if(step==0)step=1 else done()},modifier=Modifier.fillMaxWidth().height(54.dp),shape=RoundedCornerShape(16.dp)){Text(if(step==0)"Далее" else "Начать")}}}}
