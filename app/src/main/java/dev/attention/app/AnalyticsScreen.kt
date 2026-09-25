package dev.attention.app
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import kotlinx.coroutines.*
import java.time.*

@Composable fun AnalyticsScreen(events:List<CompletionEvent>,active:List<Task>,now:Long){
 var period by rememberSaveable{mutableStateOf(AnalyticsPeriod.WEEK)};var counts by rememberSaveable{mutableStateOf(false)}
 val report by produceState<AnalyticsReport?>(null,events,active,period,now){value=withContext(Dispatchers.Default){AnalyticsLogic.build(events,active,period,now)}}
 Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom=100.dp)){
  Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical=12.dp)){AnalyticsPeriod.entries.forEach{p->Pill(p.title,period==p){period=p}}}
  val r=report
  if(r==null){Text("Расчёт…",color=Muted);return@Column}
  Text(volumeText(r.minutes),fontSize=42.sp,fontWeight=FontWeight.Light,letterSpacing=(-1).sp,modifier=Modifier.padding(top=16.dp))
  Text("закрытый оценённый объём",fontSize=12.sp,color=Muted,modifier=Modifier.padding(top=4.dp,bottom=22.dp))
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(30.dp)){
   Metric("Задач",r.completedTasks.toString());Metric("Подзадач",r.completedSubtasks.toString());Metric("Ср. важность",r.averageImportance?.let{"%.1f".format(Ru,it)}?:"—")
  }
  val total=r.onTime+r.late
  Text("Вовремя: ${if(total==0)"—" else "${r.onTime*100/total}%"} · с опозданием: ${r.late}"+(if(total>0)" (${r.late*100/total}%)" else ""),color=Muted,fontSize=12.sp,modifier=Modifier.padding(top=18.dp))
  if(r.unestimated>0)Text("Без оценки времени: ${r.unestimated} завершений",color=Muted,fontSize=11.sp,modifier=Modifier.padding(top=6.dp))
  SectionLabel(if(period==AnalyticsPeriod.DAY)"ЗАВЕРШЕНИЯ ПО ВРЕМЕНИ СУТОК" else "ВЫПОЛНЕННЫЙ ОБЪЁМ")
  Row{Pill("Время",!counts){counts=false};Pill("Задачи",counts){counts=true}}
  if(period==AnalyticsPeriod.MONTH)MonthHeatmap(r.days,counts) else WorkBars(r.buckets,counts)
  Text("Оценённый объём учитывается в момент завершения. Это не фактические часы работы.",fontSize=11.sp,color=Muted,lineHeight=17.sp,modifier=Modifier.padding(top=12.dp,bottom=4.dp))
  SectionLabel("КОГДА ЗАВЕРШАЕТСЯ БОЛЬШЕ")
  CompletionHeatmap(r.heatmap)
  SectionLabel("АКТИВНЫЙ ОСТАТОК")
  Text("${r.activeCount} задач · ${r.activeEffort.label()}",fontSize=22.sp,fontWeight=FontWeight.Light)
  if(!r.activeEffort.complete)Text("Часть работы пока без оценки",fontSize=11.sp,color=Muted,modifier=Modifier.padding(top=5.dp))
  if(r.overdueActive>0)Text("Просрочено активных: ${r.overdueActive}",fontSize=12.sp,color=Muted,modifier=Modifier.padding(top=8.dp))
  SectionLabel("ПРЕДСТОЯЩАЯ НАГРУЗКА · 7 ДНЕЙ")
  r.workload.forEach{d->val items=active.filter{it.date==d.date.toString()};val a=EffortLogic.total(items)
   Row(Modifier.fillMaxWidth().padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically){Text(dayTitle(d.date),Modifier.weight(1f),fontSize=13.sp);Text("${d.tasks} · ${if(d.tasks==0)"—" else a.label()}",fontSize=13.sp,color=Muted)}
  }
  Text("Нагрузка привязана к дедлайнам, а не к расписанию работы.",fontSize=11.sp,color=Muted,lineHeight=17.sp,modifier=Modifier.padding(top=10.dp))
 }
}
@Composable private fun Metric(label:String,value:String){Column{Text(value,fontSize=27.sp,fontWeight=FontWeight.Light);Text(label,fontSize=11.sp,color=Muted,modifier=Modifier.padding(top=4.dp))}}
@Composable private fun WorkBars(buckets:List<WorkBucket>,counts:Boolean){
 var selected by remember(buckets,counts){mutableStateOf<Int?>(null)}
 val peak=(buckets.maxOfOrNull{if(counts)it.tasks.toLong() else it.minutes}?:0).coerceAtLeast(1)
 Row(Modifier.fillMaxWidth().height(174.dp).padding(top=20.dp),horizontalArrangement=Arrangement.spacedBy(7.dp),verticalAlignment=Alignment.Bottom){
  buckets.forEachIndexed{i,b->val v=if(counts)b.tasks.toLong() else b.minutes;val fraction by animateFloatAsState(v.toFloat()/peak,tween(220),label="bar")
   Column(Modifier.weight(1f).fillMaxHeight().clickable{selected=i}.semantics{contentDescription="${b.label}: ${if(counts)"${b.tasks} задач" else volumeText(b.minutes)}"},verticalArrangement=Arrangement.Bottom,horizontalAlignment=Alignment.CenterHorizontally){
    Box(Modifier.fillMaxWidth().height((fraction*125+2).dp).clip(RoundedCornerShape(topStart=5.dp,topEnd=5.dp)).background(Brush.verticalGradient(listOf(if(v==0L)Raised else Accent,Color(0xFF51456B)))))
    Text(b.label,color=Muted,fontSize=10.sp,maxLines=1,modifier=Modifier.padding(top=10.dp))
   }
  }
 }
 selected?.let{buckets.getOrNull(it)?.let{b->Text("${b.label} · "+if(counts)"${b.tasks} задач" else volumeText(b.minutes),fontSize=12.sp,color=Accent,modifier=Modifier.padding(top=10.dp))}}
 if(buckets.isEmpty()||buckets.all{if(counts)it.tasks==0 else it.minutes==0L})Text(if(counts)"Нет завершённых задач за период" else "Нет оценённого объёма за период",fontSize=12.sp,color=Muted,modifier=Modifier.padding(top=12.dp))
}
@Composable private fun MonthHeatmap(days:List<DayWork>,counts:Boolean){
 var selected by remember{mutableStateOf<DayWork?>(null)};val max=(days.maxOfOrNull{if(counts)it.tasks.toLong() else it.minutes}?:1).coerceAtLeast(1)
 val pad=(days.firstOrNull()?.date?.dayOfWeek?.value?:1)-1;val cells=List<DayWork?>(pad){null}+days
 Column(Modifier.padding(top=16.dp)){
  Row{listOf("Пн","Вт","Ср","Чт","Пт","Сб","Вс").forEach{Text(it,Modifier.weight(1f),color=Muted,fontSize=10.sp)}}
  cells.chunked(7).forEach{row->Row(Modifier.fillMaxWidth().padding(top=5.dp),horizontalArrangement=Arrangement.spacedBy(5.dp)){(row+List(7-row.size){null}).forEach{d->val v=if(counts)(d?.tasks?:0).toLong() else d?.minutes?:0
   Box(Modifier.weight(1f).height(32.dp).clip(RoundedCornerShape(6.dp)).background(if(d==null)Color.Transparent else lerp(Raised,Accent,(v.toFloat()/max)*.85f)).then(if(d==null)Modifier else Modifier.clickable{selected=d}.semantics{contentDescription="${d.date}: ${if(counts)"${d.tasks} задач" else volumeText(d.minutes)}"}),contentAlignment=Alignment.Center){if(d!=null)Text(d.date.dayOfMonth.toString(),fontSize=10.sp,color=if(v.toFloat()/max>.55)Ink else Muted)}
  }}}
  selected?.let{Text("${dayTitle(it.date)} · "+if(counts)"${it.tasks} задач" else volumeText(it.minutes),fontSize=12.sp,color=Accent,modifier=Modifier.padding(top=12.dp))}
 }
}
@Composable private fun CompletionHeatmap(matrix:List<List<Long>>){
 val labels=listOf("06–09","09–12","12–15","15–18","18–21","21–00","00–06");val days=listOf("Пн","Вт","Ср","Чт","Пт","Сб","Вс");val max=matrix.flatten().maxOrNull()?.coerceAtLeast(1)?:1
 var selected by remember{mutableStateOf<String?>(null)}
 Row{Spacer(Modifier.width(50.dp));days.forEach{Text(it,Modifier.weight(1f),fontSize=10.sp,color=Muted)}}
 matrix.forEachIndexed{r,row->Row(Modifier.fillMaxWidth().padding(top=5.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)){
  Text(labels[r],Modifier.width(46.dp),fontSize=10.sp,color=Muted)
  row.forEachIndexed{c,v->Box(Modifier.weight(1f).height(25.dp).clip(RoundedCornerShape(5.dp)).background(lerp(Raised,Accent,v.toFloat()/max*.85f)).clickable{selected="${days[c]}, ${labels[r]} · ${volumeText(v)}"}.semantics{contentDescription="${days[c]} ${labels[r]}: ${volumeText(v)}"})}
 }}
 Text(selected?:"Нажмите ячейку, чтобы увидеть объём завершений.",color=Muted,fontSize=11.sp,modifier=Modifier.padding(top=12.dp))
}
