package dev.attention.app
import kotlin.math.roundToInt
import kotlin.math.ceil

data class EffortAmount(val minutes:Int?,val complete:Boolean=true)
object EffortLogic {
 fun validate(value:Int?){require(value==null||value in 1..525600)}
 fun weighted(subs:List<Subtask>)=subs.isNotEmpty()&&subs.all{it.estimatedEffortMinutes!=null}
 fun progress(subs:List<Subtask>):Int {
  if(subs.isEmpty())return 0
  if(subs.all{it.completed})return 100
  val total=if(weighted(subs))subs.sumOf{it.estimatedEffortMinutes!!.toLong()} else subs.size.toLong()
  val closed=if(weighted(subs))subs.filter{it.completed}.sumOf{it.estimatedEffortMinutes!!.toLong()} else subs.count{it.completed}.toLong()
  return (closed*100.0/total).roundToInt().coerceIn(0,99)
 }
 fun estimate(t:Task):EffortAmount=if(t.subtasks.isEmpty())EffortAmount(t.estimatedEffortMinutes,t.estimatedEffortMinutes!=null) else sumSubs(t.subtasks)
 private fun sumSubs(subs:List<Subtask>):EffortAmount {if(subs.isEmpty())return EffortAmount(0);val known=subs.mapNotNull{it.estimatedEffortMinutes};return EffortAmount(if(known.isEmpty())null else known.sum(),known.size==subs.size)}
 fun remaining(t:Task):EffortAmount=when{
  t.done->EffortAmount(0)
  t.subtasks.isNotEmpty()->sumSubs(t.subtasks.filter{!it.completed})
  t.estimatedEffortMinutes!=null->EffortAmount(ceil(t.estimatedEffortMinutes*(100-t.progress)/100.0).toInt())
  else->EffortAmount(null,false)
 }
 fun total(tasks:List<Task>):EffortAmount {if(tasks.isEmpty())return EffortAmount(0);val a=tasks.map(::remaining);return EffortAmount(if(a.all{it.minutes==null})null else a.sumOf{it.minutes?:0},a.all{it.complete})}
 fun fits(t:Task,minutes:Int)=!t.done&&remaining(t).let{it.complete&&it.minutes!=null&&it.minutes<=minutes}
 fun normalize(t:Task,now:Long=System.currentTimeMillis()):Task {if(t.subtasks.isEmpty())return t.withProgress(t.progress,now);val p=progress(t.subtasks);return t.copy(estimatedEffortMinutes=estimate(t).minutes).withProgress(p,now)}
}
fun effortText(minutes:Int):String=when{minutes<60->"$minutes мин";minutes%60==0->"${minutes/60} ч";else->"${minutes/60} ч ${minutes%60} мин"}
fun EffortAmount.label():String=if(minutes==null)"Без оценки" else (if(complete)"≈" else "≥")+effortText(minutes)
fun Task.workLabel():String {val parts=mutableListOf<String>();if(subtasks.isNotEmpty())parts+="${subtasks.count{it.completed}}/${subtasks.size}";val r=EffortLogic.remaining(this);if(r.minutes!=null&&!done)parts+=r.label();return parts.joinToString(" · ")}
