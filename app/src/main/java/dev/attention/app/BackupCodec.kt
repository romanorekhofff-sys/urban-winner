package dev.attention.app
import androidx.room.withTransaction
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import org.json.*

object BackupCodec {
 private fun JSONObject.nInt(k:String)=if(isNull(k))null else getInt(k)
 private fun JSONObject.nLong(k:String)=if(isNull(k))null else getLong(k)
 private fun JSONObject.nString(k:String)=if(isNull(k))null else getString(k)
 private fun obj(vararg pairs:Pair<String,Any?>)=JSONObject().apply{pairs.forEach{put(it.first,it.second?:JSONObject.NULL)}}
 private fun array(items:List<JSONObject>)=JSONArray().apply{items.forEach{put(it)}}
 private fun objects(root:JSONObject,key:String):List<JSONObject>{val a=root.optJSONArray(key)?:JSONArray();require(a.length()<=100000);return (0 until a.length()).map{a.getJSONObject(it)}}
 suspend fun export(app:AttentionApp):String=withContext(Dispatchers.IO){
  app.mutationLock.withLock{
   val rows=app.db.work().backupRows();val p=app.settings.flow.first()
   val tasks=rows.map{it.task}.map{t->obj("id" to t.id,"title" to t.title,"date" to t.date,"minute" to t.minute,"importance" to t.importance,"progress" to t.progress,"note" to t.note,"category" to t.category,"createdAt" to t.createdAt,"completedAt" to t.completedAt,"reminder" to t.reminder,"estimatedEffortMinutes" to t.estimatedEffortMinutes,"initialEstimatedEffortMinutes" to t.initialEstimatedEffortMinutes,"updatedAt" to t.updatedAt,"deletedAt" to t.deletedAt)}
   val subs=rows.flatMap{it.children}.map{s->obj("id" to s.id,"parentTaskId" to s.parentTaskId,"title" to s.title,"completed" to s.completed,"completedAt" to s.completedAt,"estimatedEffortMinutes" to s.estimatedEffortMinutes,"initialEstimatedEffortMinutes" to s.initialEstimatedEffortMinutes,"orderIndex" to s.orderIndex,"createdAt" to s.createdAt)}
   val events=app.db.work().events().map{e->obj("id" to e.id,"taskId" to e.taskId,"subtaskId" to e.subtaskId,"completedAt" to e.completedAt,"estimatedEffortMinutes" to e.estimatedEffortMinutes,"importance" to e.importance,"deadline" to e.deadline,"wasOverdue" to e.wasOverdue,"eventType" to e.eventType,"voidedAt" to e.voidedAt)}
   val sessions=app.db.work().sessions().map{w->obj("id" to w.id,"taskId" to w.taskId,"subtaskId" to w.subtaskId,"startedAt" to w.startedAt,"endedAt" to w.endedAt,"durationMillis" to w.durationMillis)}
   obj("format" to "attention-backup","version" to 2,"tasks" to array(tasks),"subtasks" to array(subs),"events" to array(events),"workSessions" to array(sessions),"settings" to obj("enabled" to p.enabled,"hour" to p.hour,"minute" to p.minute,"reminders" to p.reminders,"categories" to p.categories)).toString(2)
  }
 }
 suspend fun import(app:AttentionApp,text:String):Int=withContext(Dispatchers.IO){
  require(text.toByteArray().size<=10000000);val root=JSONObject(text);require(root.getString("format")=="attention-backup");val version=root.getInt("version");require(version in 1..2)
  val tasks=objects(root,"tasks").map{o->Task(id=o.getString("id"),title=o.getString("title"),date=o.getString("date"),minute=o.getInt("minute"),importance=o.getInt("importance"),progress=o.getInt("progress"),note=o.optString("note"),category=o.optString("category"),createdAt=o.getLong("createdAt"),completedAt=o.nLong("completedAt"),reminder=o.nString("reminder"),estimatedEffortMinutes=o.nInt("estimatedEffortMinutes"),initialEstimatedEffortMinutes=o.nInt("initialEstimatedEffortMinutes"),updatedAt=o.optLong("updatedAt",o.getLong("createdAt")),deletedAt=o.nLong("deletedAt"))}
  require(tasks.size<=20000);val ids=tasks.map{it.id}.toSet();require(ids.size==tasks.size)
  tasks.forEach{t->require(t.id.length in 1..100&&t.title.isNotBlank()&&t.title.length<=1000&&t.importance in 1..10&&t.progress in 0..100&&t.minute in -1..1439&&t.note.length<=100000);require(validReminders(t.reminder?:""));require(t.estimatedEffortMinutes==null||t.estimatedEffortMinutes>0);t.due}
  val subs=if(version==1)emptyList() else objects(root,"subtasks").map{o->Subtask(o.getString("id"),o.getString("parentTaskId"),o.getString("title"),o.getBoolean("completed"),o.nLong("completedAt"),o.nInt("estimatedEffortMinutes"),o.nInt("initialEstimatedEffortMinutes"),o.getInt("orderIndex"),o.getLong("createdAt"))}
  require(subs.map{it.id}.distinct().size==subs.size)
  subs.forEach{s->require(s.parentTaskId in ids&&s.id.length in 1..100&&s.title.isNotBlank()&&s.title.length<=1000&&s.orderIndex>=0);EffortLogic.validate(s.estimatedEffortMinutes);EffortLogic.validate(s.initialEstimatedEffortMinutes);require(s.completed==(s.completedAt!=null))}
  val groups=subs.groupBy{it.parentTaskId};require(groups.values.all{it.size<=500})
  val hydrated=tasks.map{t->EffortLogic.normalize(t.copy(subtasks=groups[t.id].orEmpty().sortedBy{it.orderIndex}))}
  val events=if(version==1)hydrated.filter{it.done}.map{t->CompletionEvent(id="legacy:${t.id}",taskId=t.id,completedAt=t.completedAt!!,importance=t.importance,deadline=t.due,wasOverdue=t.completedAt>t.due,eventType="LEGACY")} else objects(root,"events").map{o->CompletionEvent(o.getString("id"),o.getString("taskId"),o.nString("subtaskId"),o.getLong("completedAt"),o.nInt("estimatedEffortMinutes"),o.getInt("importance"),o.getLong("deadline"),o.getBoolean("wasOverdue"),o.getString("eventType"),o.nLong("voidedAt"))}
  require(events.map{it.id}.distinct().size==events.size)
  events.forEach{e->require(e.id.length in 1..150&&e.taskId in ids&&e.importance in 1..10&&e.eventType in listOf("TASK","SUBTASK","LEGACY")&&(e.estimatedEffortMinutes==null||e.estimatedEffortMinutes>=0));require((e.eventType=="SUBTASK")== (e.subtaskId!=null));require(e.wasOverdue==(e.completedAt>e.deadline))}
  require(events.filter{it.voidedAt==null}.groupBy{Pair(it.taskId,it.subtaskId)}.all{it.value.size==1})
  val sessions=if(version==1)emptyList() else objects(root,"workSessions").map{o->WorkSession(o.getString("id"),o.getString("taskId"),o.nString("subtaskId"),o.getLong("startedAt"),o.nLong("endedAt"),o.nLong("durationMillis"))}
  sessions.forEach{require(it.taskId in ids&&(it.endedAt==null||it.endedAt>=it.startedAt)&&(it.durationMillis==null||it.durationMillis>=0))}
  val o=root.getJSONObject("settings");val p=Preferences(o.getBoolean("enabled"),o.getInt("hour"),o.getInt("minute"),o.getString("reminders"),true,o.optString("categories","Наука|Работа|Личное|Проекты"));require(p.hour in 0..23&&p.minute in 0..59&&validReminders(p.reminders))
  app.mutationLock.withLock{app.db.withTransaction{
   // A malicious/incorrect backup cannot move a child or event belonging to an unrelated local task.
   val existing=app.db.work().backupRows().filter{it.task.id !in ids}.flatMap{it.children}.map{it.id}.toSet();require(subs.none{it.id in existing})
   val unrelatedEvents=app.db.work().events().filter{it.taskId !in ids}.map{it.id}.toSet();require(events.none{it.id in unrelatedEvents})
   app.db.tasks().putAll(hydrated);ids.forEach{app.db.work().clearSubtasks(it)};app.db.work().putSubtasks(subs);app.db.work().clearEvents(ids.toList());app.db.work().putEvents(events);app.db.work().putSessions(sessions)
  };app.settings.save(p)}
  ids.forEach{app.scheduler.cancelTask(it)};app.scheduler.reschedule();FocusWidget.updateAll(app);tasks.count{it.deletedAt==null}
 }
 private fun validReminders(s:String)=s.split(',').filter{it.isNotEmpty()}.all{it.toIntOrNull() in listOf(0,60,180,1440)}
}
