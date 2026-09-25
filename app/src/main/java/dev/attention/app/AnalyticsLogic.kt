package dev.attention.app
import java.time.*
import java.time.temporal.ChronoUnit

enum class AnalyticsPeriod(val title:String,val days:Long?){DAY("Сегодня",1),WEEK("7 дней",7),MONTH("30 дней",30),ALL("Всё время",null)}
data class WorkBucket(val label:String,val minutes:Long,val tasks:Int)
data class DayWork(val date:LocalDate,val minutes:Long,val tasks:Int)
data class AnalyticsReport(val completedTasks:Int=0,val completedSubtasks:Int=0,val minutes:Long=0,val unestimated:Int=0,val averageImportance:Double?=null,val onTime:Int=0,val late:Int=0,val buckets:List<WorkBucket> = emptyList(),val days:List<DayWork> = emptyList(),val heatmap:List<List<Long>> = List(7){List(7){0L}},val workload:List<DayWork> = emptyList(),val activeCount:Int=0,val activeEffort:EffortAmount=EffortAmount(0),val overdueActive:Int=0)
object AnalyticsLogic {
 fun build(events:List<CompletionEvent>,tasks:List<Task>,period:AnalyticsPeriod,now:Long=System.currentTimeMillis(),zone:ZoneId=ZoneId.systemDefault()):AnalyticsReport {
  val today=Instant.ofEpochMilli(now).atZone(zone).toLocalDate();val start=period.days?.let{today.minusDays(it-1).atStartOfDay(zone).toInstant().toEpochMilli()}?:Long.MIN_VALUE
  val valid=events.filter{it.voidedAt==null&&it.completedAt in start..now};val parents=valid.filter{it.subtaskId==null};val work=valid.sumOf{(it.estimatedEffortMinutes?:0).toLong()}
  fun local(e:CompletionEvent)=Instant.ofEpochMilli(e.completedAt).atZone(zone)
  fun bucket(label:String,es:List<CompletionEvent>)=WorkBucket(label,es.sumOf{(it.estimatedEffortMinutes?:0).toLong()},es.count{it.subtaskId==null})
  val dates=valid.groupBy{local(it).toLocalDate()}
  val days=(0L..29L).map{n->val d=today.minusDays(29-n);val list=dates[d].orEmpty();DayWork(d,list.sumOf{(it.estimatedEffortMinutes?:0).toLong()},list.count{it.subtaskId==null})}
  val buckets=when(period){
   AnalyticsPeriod.DAY->(0..7).map{i->bucket("%02d".format(i*3),valid.filter{local(it).hour/3==i})}
   AnalyticsPeriod.WEEK->(0L..6L).map{i->val d=today.minusDays(6-i);bucket(listOf("Пн","Вт","Ср","Чт","Пт","Сб","Вс")[d.dayOfWeek.value-1],dates[d].orEmpty())}
   AnalyticsPeriod.MONTH->days.map{WorkBucket(it.date.dayOfMonth.toString(),it.minutes,it.tasks)}
   AnalyticsPeriod.ALL->{val first=valid.minOfOrNull{local(it).year}?:today.year;val years=today.year-first;if(years>1) (first..today.year).map{y->bucket(y.toString(),valid.filter{local(it).year==y})} else {
    val months=valid.groupBy{YearMonth.from(local(it))};months.toSortedMap().map{(month,list)->bucket("${month.monthValue}/${month.year%100}",list)}
   }}
  }
  val heat=List(7){MutableList(7){0L}};valid.forEach{e->val t=local(e);val row=if(t.hour<6)6 else (t.hour-6)/3;heat[row][t.dayOfWeek.value-1]+=(e.estimatedEffortMinutes?:0).toLong()}
  val active=tasks.filter{!it.done&&it.deletedAt==null};val upcoming=(0L..6L).map{n->val d=today.plusDays(n);val items=active.filter{it.date==d.toString()};DayWork(d,items.sumOf{(EffortLogic.remaining(it).minutes?:0).toLong()},items.size)}
  return AnalyticsReport(parents.size,valid.count{it.subtaskId!=null},work,valid.count{it.estimatedEffortMinutes==null},parents.map{it.importance}.takeIf{it.isNotEmpty()}?.average(),parents.count{!it.wasOverdue},parents.count{it.wasOverdue},buckets,days,heat,upcoming,active.size,EffortLogic.total(active),active.count{it.due<now})
 }
}
fun volumeText(minutes:Long):String=if(minutes<60)"$minutes мин" else "${minutes/60} ч"+if(minutes%60==0L)"" else " ${minutes%60} мин"
