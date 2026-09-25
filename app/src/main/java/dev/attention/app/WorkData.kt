package dev.attention.app

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.withLock
import java.util.UUID

@Entity(tableName="subtasks",indices=[Index("parentTaskId")])
data class Subtask(@PrimaryKey val id:String=UUID.randomUUID().toString(),val parentTaskId:String,val title:String,val completed:Boolean=false,val completedAt:Long?=null,val estimatedEffortMinutes:Int?=null,val initialEstimatedEffortMinutes:Int?=null,val orderIndex:Int=0,val createdAt:Long=System.currentTimeMillis())

// Snapshots never change. Reopening voids the former completion instead of counting it twice.
@Entity(tableName="completion_events",indices=[Index("taskId"),Index("completedAt"),Index("subtaskId")])
data class CompletionEvent(@PrimaryKey val id:String=UUID.randomUUID().toString(),val taskId:String,val subtaskId:String?=null,val completedAt:Long,val estimatedEffortMinutes:Int?=null,val importance:Int,val deadline:Long,val wasOverdue:Boolean,val eventType:String="TASK",val voidedAt:Long?=null)

// Reserved for future tracking. No timer or fabricated actual time is exposed in 1.1.
@Entity(tableName="work_sessions",indices=[Index("taskId"),Index("startedAt")])
data class WorkSession(@PrimaryKey val id:String=UUID.randomUUID().toString(),val taskId:String,val subtaskId:String?=null,val startedAt:Long,val endedAt:Long?=null,val durationMillis:Long?=null)

data class TaskWithSubtasks(@Embedded val task:Task,@Relation(parentColumn="id",entityColumn="parentTaskId") val children:List<Subtask>){fun full()=task.copy(subtasks=children.sortedWith(compareBy<Subtask>{it.orderIndex}.thenBy{it.createdAt}))}
@Dao interface WorkDao {
 @Transaction @Query("SELECT * FROM tasks WHERE deletedAt IS NULL") fun observeTasks():Flow<List<TaskWithSubtasks>>
 @Transaction @Query("SELECT * FROM tasks WHERE deletedAt IS NULL") suspend fun activeRows():List<TaskWithSubtasks>
 @Transaction @Query("SELECT * FROM tasks") suspend fun backupRows():List<TaskWithSubtasks>
 @Transaction @Query("SELECT * FROM tasks WHERE id=:id") suspend fun row(id:String):TaskWithSubtasks?
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putSubtasks(items:List<Subtask>)
 @Query("DELETE FROM subtasks WHERE parentTaskId=:id") suspend fun clearSubtasks(id:String)
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putEvents(items:List<CompletionEvent>)
 @Query("SELECT * FROM completion_events ORDER BY completedAt") fun observeEvents():Flow<List<CompletionEvent>>
 @Query("SELECT * FROM completion_events ORDER BY completedAt") suspend fun events():List<CompletionEvent>
 @Query("SELECT * FROM completion_events WHERE taskId=:id AND voidedAt IS NULL") suspend fun currentEvents(id:String):List<CompletionEvent>
 @Query("UPDATE completion_events SET voidedAt=:now WHERE taskId=:id AND subtaskId IS NULL AND voidedAt IS NULL") suspend fun voidTask(id:String,now:Long)
 @Query("UPDATE completion_events SET voidedAt=:now WHERE subtaskId=:id AND voidedAt IS NULL") suspend fun voidSubtask(id:String,now:Long)
 @Query("DELETE FROM completion_events WHERE taskId IN (:ids)") suspend fun clearEvents(ids:List<String>)
 @Query("UPDATE tasks SET deletedAt=:now WHERE id=:id") suspend fun softDelete(id:String,now:Long)
 @Query("SELECT * FROM work_sessions") suspend fun sessions():List<WorkSession>
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putSessions(items:List<WorkSession>)
}
val MIGRATION_1_2=object:Migration(1,2){override fun migrate(db:SupportSQLiteDatabase){
 db.execSQL("ALTER TABLE tasks ADD COLUMN estimatedEffortMinutes INTEGER")
 db.execSQL("ALTER TABLE tasks ADD COLUMN initialEstimatedEffortMinutes INTEGER")
 db.execSQL("ALTER TABLE tasks ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
 db.execSQL("ALTER TABLE tasks ADD COLUMN deletedAt INTEGER DEFAULT NULL")
 db.execSQL("UPDATE tasks SET updatedAt=createdAt")
 db.execSQL("CREATE TABLE IF NOT EXISTS subtasks (id TEXT NOT NULL PRIMARY KEY,parentTaskId TEXT NOT NULL,title TEXT NOT NULL,completed INTEGER NOT NULL,completedAt INTEGER,estimatedEffortMinutes INTEGER,initialEstimatedEffortMinutes INTEGER,orderIndex INTEGER NOT NULL,createdAt INTEGER NOT NULL)")
 db.execSQL("CREATE INDEX IF NOT EXISTS index_subtasks_parentTaskId ON subtasks(parentTaskId)")
 db.execSQL("CREATE TABLE IF NOT EXISTS completion_events (id TEXT NOT NULL PRIMARY KEY,taskId TEXT NOT NULL,subtaskId TEXT,completedAt INTEGER NOT NULL,estimatedEffortMinutes INTEGER,importance INTEGER NOT NULL,deadline INTEGER NOT NULL,wasOverdue INTEGER NOT NULL,eventType TEXT NOT NULL,voidedAt INTEGER)")
 db.execSQL("CREATE INDEX IF NOT EXISTS index_completion_events_taskId ON completion_events(taskId)")
 db.execSQL("CREATE INDEX IF NOT EXISTS index_completion_events_completedAt ON completion_events(completedAt)")
 db.execSQL("CREATE INDEX IF NOT EXISTS index_completion_events_subtaskId ON completion_events(subtaskId)")
 db.execSQL("CREATE TABLE IF NOT EXISTS work_sessions (id TEXT NOT NULL PRIMARY KEY,taskId TEXT NOT NULL,subtaskId TEXT,startedAt INTEGER NOT NULL,endedAt INTEGER,durationMillis INTEGER)")
 db.execSQL("CREATE INDEX IF NOT EXISTS index_work_sessions_taskId ON work_sessions(taskId)")
 db.execSQL("CREATE INDEX IF NOT EXISTS index_work_sessions_startedAt ON work_sessions(startedAt)")
 // Legacy rows have no estimate. Preserve their known completion date and importance.
 db.query("SELECT id,date,minute,importance,completedAt FROM tasks WHERE progress=100 AND completedAt IS NOT NULL").use{c->while(c.moveToNext()){
  val due=FocusLogic.deadline(c.getString(1),c.getInt(2));val at=c.getLong(4)
  db.execSQL("INSERT INTO completion_events VALUES(?,?,NULL,?,NULL,?,?,?,'LEGACY',NULL)",arrayOf("legacy:"+c.getString(0),c.getString(0),at,c.getInt(3),due,if(at>due)1 else 0))
 }}
}}
class TaskRepository(private val app:AttentionApp){
 suspend fun all()=app.db.work().activeRows().map{it.full()}
 suspend fun save(input:Task,now:Long=System.currentTimeMillis(),expected:Task?=null,checkExpected:Boolean=false):Task=withContext(Dispatchers.IO){
  require(input.title.isNotBlank()&&input.importance in 1..10);input.due
  val result=app.mutationLock.withLock{app.db.withTransaction{
   val dao=app.db.work();val previous=dao.row(input.id)?.full();val oldSubs=previous?.subtasks?.associateBy{it.id}.orEmpty()
   if(checkExpected)check(previous==expected){"Задача уже изменена. Повторите команду"}
   require(input.subtasks.size<=500&&input.subtasks.map{it.id}.distinct().size==input.subtasks.size)
   val subs=input.subtasks.mapIndexed{i,s->require(s.title.isNotBlank());EffortLogic.validate(s.estimatedEffortMinutes);val old=oldSubs[s.id]
    s.copy(parentTaskId=input.id,orderIndex=i,initialEstimatedEffortMinutes=old?.initialEstimatedEffortMinutes?:s.initialEstimatedEffortMinutes?:s.estimatedEffortMinutes,completedAt=if(s.completed){if(old?.completed==true)old.completedAt?:now else now}else null)
   }
   if(subs.isEmpty())EffortLogic.validate(input.estimatedEffortMinutes)
   val normalized=EffortLogic.normalize(input.copy(subtasks=subs,deletedAt=null),now)
   val saved=normalized.copy(completedAt=if(normalized.done){if(previous?.done==true)previous.completedAt?:now else now}else null,updatedAt=now,initialEstimatedEffortMinutes=previous?.initialEstimatedEffortMinutes?:input.initialEstimatedEffortMinutes?:normalized.estimatedEffortMinutes)
   val current=dao.currentEvents(input.id)
   for(s in subs){
    if(!s.completed)dao.voidSubtask(s.id,now)
    else if(current.none{it.subtaskId==s.id})dao.putEvents(listOf(CompletionEvent(taskId=saved.id,subtaskId=s.id,completedAt=s.completedAt?:now,estimatedEffortMinutes=s.estimatedEffortMinutes,importance=saved.importance,deadline=saved.due,wasOverdue=(s.completedAt?:now)>saved.due,eventType="SUBTASK")))
   }
   if(!saved.done)dao.voidTask(saved.id,now)
   else if(current.none{it.subtaskId==null})dao.putEvents(listOf(CompletionEvent(taskId=saved.id,completedAt=saved.completedAt?:now,estimatedEffortMinutes=if(subs.isEmpty())saved.estimatedEffortMinutes else 0,importance=saved.importance,deadline=saved.due,wasOverdue=(saved.completedAt?:now)>saved.due)))
   app.db.tasks().put(saved);dao.clearSubtasks(saved.id);dao.putSubtasks(subs);saved
  }}
  changed(result.id);result
 }
 suspend fun delete(id:String)=withContext(Dispatchers.IO){app.mutationLock.withLock{app.db.work().softDelete(id,System.currentTimeMillis())};changed(id)}
 private suspend fun changed(id:String){app.scheduler.cancelTask(id);app.scheduler.reschedule();FocusWidget.updateAll(app)}
}
