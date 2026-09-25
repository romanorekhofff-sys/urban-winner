package dev.attention.app
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
fun dayTitle(d:LocalDate):String=when(d){LocalDate.now()->"Сегодня";LocalDate.now().plusDays(1)->"Завтра";LocalDate.now().minusDays(1)->"Вчера";else->d.format(DateTimeFormatter.ofPattern(if(d.year==LocalDate.now().year)"d MMMM" else "d MMMM yyyy",Ru))}
@Composable fun CalendarScreen(tasks:List<Task>,now:Long,open:(Task)->Unit,complete:(Task)->Unit,progress:(Task)->Unit,actions:(Task)->Unit){
 var month by remember{mutableStateOf(YearMonth.now())};var day by remember{mutableStateOf(LocalDate.now())};var timeline by remember{mutableStateOf(false)};val tap=haptic()
 Column{Row(Modifier.padding(top=14.dp,bottom=10.dp)){Pill("Месяц",!timeline){tap();timeline=false};Pill("Лента",timeline){tap();timeline=true}}
 if(timeline){val groups=tasks.sortedBy{it.due}.groupBy{it.date};LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=100.dp)){if(groups.isEmpty())item{QuietEmpty("Нет сроков","Добавленные задачи появятся здесь.",Icons.Rounded.CalendarToday)};groups.forEach{(date,list)->item(key="d$date"){SectionLabel(dayTitle(LocalDate.parse(date)),"${list.size} · ${EffortLogic.total(list).label()}")};items(list,key={it.id}){t->TaskCard(t,now,{open(t)},{complete(t)},{progress(t)},{actions(t)})}}}}
 else{LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=100.dp)){
 item{Column(Modifier.clip(RoundedCornerShape(22.dp)).background(Surface).padding(14.dp)){
  Row(verticalAlignment=Alignment.CenterVertically){Glyph(Icons.Rounded.ChevronLeft,"Предыдущий месяц",{month=month.minusMonths(1);tap()});Text(month.format(DateTimeFormatter.ofPattern("LLLL yyyy",Ru)).replaceFirstChar{it.uppercase()},Modifier.weight(1f),fontSize=16.sp,textAlign=androidx.compose.ui.text.style.TextAlign.Center);Glyph(Icons.Rounded.ChevronRight,"Следующий месяц",{month=month.plusMonths(1);tap()})}
  Row(Modifier.fillMaxWidth()){listOf("П","В","С","Ч","П","С","В").forEach{Text(it,Modifier.weight(1f).padding(vertical=10.dp),color=Muted,fontSize=11.sp,textAlign=androidx.compose.ui.text.style.TextAlign.Center)}}
  val start=month.atDay(1).dayOfWeek.value-1;val rows=ceil((start+month.lengthOfMonth())/7.0).toInt()
  repeat(rows){r->Row(Modifier.fillMaxWidth()){repeat(7){c->val n=r*7+c-start+1;val date=if(n in 1..month.lengthOfMonth())month.atDay(n) else null;Column(Modifier.weight(1f).height(49.dp).padding(2.dp).clip(RoundedCornerShape(11.dp)).background(if(date==day)Raised else Color.Transparent).then(if(date!=null)Modifier.clickable{tap();day=date} else Modifier),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){if(date!=null){Text(n.toString(),fontSize=14.sp,color=if(date==LocalDate.now())Accent else TextPrimary,fontWeight=if(date==day)FontWeight.Bold else FontWeight.Normal);Row(Modifier.height(7.dp).padding(top=4.dp),horizontalArrangement=Arrangement.spacedBy(3.dp)){tasks.filter{it.date==date.toString()}.sortedByDescending{it.importance}.take(3).forEach{Box(Modifier.size(3.dp).background(importanceColor(it.importance),CircleShape))}}}}}}}
 }}
 item{Text("${tasks.count{it.date==day.toString()}} задач · "+EffortLogic.total(tasks.filter{it.date==day.toString()}).label(),color=Muted,fontSize=12.sp)}
 item{Row(verticalAlignment=Alignment.CenterVertically){Text(dayTitle(day),Modifier.weight(1f).padding(top=14.dp,bottom=6.dp),fontSize=16.sp);if(day!=LocalDate.now())TextButton(onClick={day=LocalDate.now();month=YearMonth.now()}){Text("Сегодня",fontSize=12.sp)}}}
 val selected=tasks.filter{it.date==day.toString()}.sortedBy{it.due};if(selected.isEmpty())item{QuietEmpty("Нет дедлайнов","На этот день ничего не запланировано.",Icons.Rounded.CalendarToday)}
 items(selected,key={it.id}){t->TaskCard(t,now,{open(t)},{complete(t)},{progress(t)},{actions(t)},Modifier.animateItem())}
 }} }
}
@Composable fun HistoryScreen(tasks:List<Task>,now:Long,open:(Task)->Unit,complete:(Task)->Unit,progress:(Task)->Unit,actions:(Task)->Unit){val today=LocalDate.now();val groups=tasks.sortedByDescending{it.completedAt}.groupBy{val d=Instant.ofEpochMilli(it.completedAt?:it.createdAt).atZone(ZoneId.systemDefault()).toLocalDate();when{d==today->"Сегодня";d==today.minusDays(1)->"Вчера";!d.isBefore(today.with(DayOfWeek.MONDAY))->"Эта неделя";else->"Ранее"}};LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=100.dp)){if(tasks.isEmpty())item{QuietEmpty("История пока пуста","Выполненные задачи сохраняются здесь.")};groups.forEach{(title,list)->item{SectionLabel(title,"${list.size}")};items(list,key={it.id}){t->TaskCard(t,now,{open(t)},{complete(t)},{progress(t)},{actions(t)})}}}}
@Composable fun SummaryScreen(tasks:List<Task>,now:Long,open:(Task)->Unit,complete:(Task)->Unit,progress:(Task)->Unit,actions:(Task)->Unit){val today=LocalDate.now();val groups=listOf("Сегодня" to tasks.filter{it.date==today.toString()}.sortedBy{it.due},"Просрочено" to tasks.filter{it.due<now}.sortedBy{it.due},"Самое важное" to tasks.sortedWith(compareByDescending<Task>{it.importance}.thenBy{it.due}).take(3),"Почти готово" to tasks.filter{it.progress in 75..99&&it.due<=now+3*86400000L}.sortedBy{it.due},"Скоро" to tasks.filter{LocalDate.parse(it.date)>today&&LocalDate.parse(it.date)<=today.plusDays(3)}.sortedBy{it.due});LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=100.dp)){item{Text(EffortLogic.total(tasks).label()+" осталось · сегодня "+EffortLogic.total(tasks.filter{it.date==today.toString()}).label(),fontSize=14.sp,color=Accent,modifier=Modifier.padding(vertical=12.dp))};if(tasks.isEmpty())item{QuietEmpty("Нет активных задач","Утренние сводки будут появляться здесь.",Icons.Rounded.WbTwilight)};groups.forEach{(title,list)->if(list.isNotEmpty()){item(key=title){SectionLabel(title,"${list.size}")};items(list,key={"$title:${it.id}"}){t->TaskCard(t,now,{open(t)},{complete(t)},{progress(t)},{actions(t)})}}}}}
@Composable fun PriorityMap(tasks:List<Task>,now:Long,open:(Task)->Unit){
 val tap=haptic();Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom=100.dp)){
  Text("ВАЖНОСТЬ",color=Muted,fontSize=10.sp,letterSpacing=1.sp,modifier=Modifier.padding(top=12.dp,bottom=12.dp))
  BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(.96f).clip(RoundedCornerShape(22.dp)).background(Surface)){
   val density=LocalDensity.current;val w=with(density){maxWidth.toPx()};val h=with(density){maxHeight.toPx()};val pad=with(density){26.dp.toPx()}
   val points=tasks.sortedBy{it.id}.map{t->val jitter=((t.id.hashCode() and 255)/255f-.5f)*10;Pair(t,Offset(pad+FocusLogic.urgency(t.due,now).toFloat()*(w-2*pad)+jitter,(h-pad)-(t.importance-1)/9f*(h-2*pad)+jitter))}
   Canvas(Modifier.fillMaxSize().pointerInput(points){detectTapGestures{o->points.minByOrNull{(it.second-o).getDistance()}?.takeIf{(it.second-o).getDistance()<32.dp.toPx()}?.let{tap();open(it.first)}}}){
    for(i in 0..4){val x=pad+i*(size.width-2*pad)/4;val y=pad+i*(size.height-2*pad)/4;drawLine(Color(0xFF272B35),Offset(x,pad),Offset(x,size.height-pad),1f);drawLine(Color(0xFF272B35),Offset(pad,y),Offset(size.width-pad,y),1f)}
    points.forEach{(t,o)->val color=importanceColor(t.importance);val radius=6.dp.toPx()+(100-t.progress)/100f*2.dp.toPx();drawCircle(Brush.radialGradient(listOf(color.copy(alpha=.18f),Color.Transparent),center=o,radius=radius*3.4f),radius*3.4f,o);drawCircle(color,radius,o)}
   }
   Text("10",color=Muted,fontSize=9.sp,modifier=Modifier.align(Alignment.TopStart).padding(8.dp));Text("1",color=Muted,fontSize=9.sp,modifier=Modifier.align(Alignment.BottomStart).padding(8.dp));Text("ВАЖНО + СРОЧНО",color=Muted.copy(alpha=.6f),fontSize=8.sp,letterSpacing=1.sp,modifier=Modifier.align(Alignment.TopEnd).padding(12.dp))
  }
  Row(Modifier.fillMaxWidth().padding(top=12.dp),horizontalArrangement=Arrangement.SpaceBetween){Text("Есть время",color=Muted,fontSize=10.sp);Text("СРОЧНОСТЬ →",color=Muted,fontSize=10.sp,letterSpacing=1.sp)}
  if(tasks.isEmpty())QuietEmpty("Карта свободна","Задачи появятся здесь после добавления.",Icons.Rounded.ScatterPlot)
  if(tasks.isNotEmpty()){SectionLabel("ЗАДАЧИ НА КАРТЕ","${tasks.size}");tasks.sortedByDescending{it.importance}.forEach{t->Row(Modifier.fillMaxWidth().clickable{open(t)}.padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(6.dp).background(importanceColor(t.importance),CircleShape));Spacer(Modifier.width(12.dp));Text(t.title,Modifier.weight(1f),fontSize=13.sp,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis);Text("${t.importance}",fontSize=13.sp,color=importanceColor(t.importance))}}}
 }
}
