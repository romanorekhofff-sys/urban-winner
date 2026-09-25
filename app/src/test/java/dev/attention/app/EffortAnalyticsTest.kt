package dev.attention.app
import org.junit.Test
import org.junit.Assert.*
import java.time.*

class EffortAnalyticsTest {
 private fun sub(n:Int?,done:Boolean=false)=Subtask(parentTaskId="p",title="Шаг",estimatedEffortMinutes=n,completed=done,completedAt=if(done)1 else null)
 @Test fun emptyWorkloadIsKnownZero(){assertEquals(EffortAmount(0),EffortLogic.total(emptyList()))}
 @Test fun weightedProgress(){assertEquals(25,EffortLogic.progress(listOf(sub(15,true),sub(45))))}
 @Test fun equalWhenAllUnknown(){assertEquals(50,EffortLogic.progress(listOf(sub(null,true),sub(null))))}
 @Test fun mixedEstimatesUseCounts(){assertEquals(50,EffortLogic.progress(listOf(sub(15,true),sub(null))))}
 @Test fun roundingNeverCompletesUnfinishedWork(){assertEquals(99,EffortLogic.progress(listOf(sub(10000,true),sub(1))))}
 @Test fun simpleRemainingAndTimeFilter(){val t=Task(title="Тест",estimatedEffortMinutes=120,progress=75);assertEquals(30,EffortLogic.remaining(t).minutes);assertTrue(EffortLogic.fits(t,30));assertFalse(EffortLogic.fits(t,15));assertFalse(EffortLogic.fits(Task(title="Без оценки"),120))}
 @Test fun incompleteEstimateIsNotZero(){val t=Task(title="Тест",subtasks=listOf(sub(30),sub(null)));assertEquals(30,EffortLogic.remaining(t).minutes);assertFalse(EffortLogic.remaining(t).complete);assertFalse(EffortLogic.fits(t,120))}
 @Test fun completedUnknownDoesNotHideKnownRemaining(){val t=Task(title="Тест",subtasks=listOf(sub(null,true),sub(30)));assertTrue(EffortLogic.fits(t,30))}
 @Test fun parentSumAndAutomaticReopening(){val t=Task(title="Тест",subtasks=listOf(sub(15,true),sub(45,true)));val done=EffortLogic.normalize(t,100);assertEquals(60,done.estimatedEffortMinutes);assertEquals(100,done.progress);assertEquals(100L,done.completedAt);val reopened=EffortLogic.normalize(done.copy(subtasks=t.subtasks.mapIndexed{i,s->if(i==1)s.copy(completed=false,completedAt=null) else s}),200);assertEquals(25,reopened.progress);assertNull(reopened.completedAt);assertEquals(45,EffortLogic.remaining(reopened).minutes)}
 @Test fun timePeriodsSnapshotsAndNoDoubleVolume(){
  val zone=ZoneId.of("Europe/Moscow");val now=ZonedDateTime.of(2026,9,25,15,0,0,0,zone).toInstant().toEpochMilli()
  fun e(id:String,at:Long,volume:Int?,sub:String?=null,void:Long?=null)=CompletionEvent(id,"p",sub,at,volume,9,now-100000,at>now-100000,if(sub==null)"TASK" else "SUBTASK",void)
  val events=listOf(e("a",now-1000,15,"a"),e("b",now-1000,45,"b"),e("p",now-1000,0),e("void",now-1000,120,void=now),e("old",now-8*86400000,30))
  val r=AnalyticsLogic.build(events,emptyList(),AnalyticsPeriod.DAY,now,zone)
  assertEquals(60,r.minutes);assertEquals(1,r.completedTasks);assertEquals(2,r.completedSubtasks);assertEquals(9.0,r.averageImportance!!,.001);assertEquals(1,r.late);assertEquals(60,r.heatmap[2][4]);assertEquals(1,AnalyticsLogic.build(events,emptyList(),AnalyticsPeriod.WEEK,now,zone).completedTasks);assertEquals(2,AnalyticsLogic.build(events,emptyList(),AnalyticsPeriod.MONTH,now,zone).completedTasks)
 }
 @Test fun workloadUsesRemainingByDate(){val zone=ZoneId.of("UTC");val now=Instant.parse("2026-09-25T12:00:00Z").toEpochMilli();val t=Task(title="Тест",date="2026-09-26",estimatedEffortMinutes=120,progress=75);val r=AnalyticsLogic.build(emptyList(),listOf(t),AnalyticsPeriod.ALL,now,zone);assertEquals(30,r.workload[1].minutes);assertEquals(30,r.activeEffort.minutes)}
 @Test fun widgetSizesAndPriority(){assertEquals(1,WidgetLayout.rows(180,140));assertEquals(3,WidgetLayout.rows(270,320));assertEquals(6,WidgetLayout.rows(300,500));assertEquals(9,WidgetLayout.rows(350,680));val now=System.currentTimeMillis();val old=Task(title="Просрочено",date=LocalDate.now().minusDays(1).toString(),importance=1);val next=Task(title="Важно",date=LocalDate.now().plusDays(1).toString(),importance=10);assertEquals(old.id,WidgetLayout.ordered(listOf(next,old),now).first().id)}
}
