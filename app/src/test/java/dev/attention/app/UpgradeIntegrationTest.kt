package dev.attention.app
import android.database.sqlite.SQLiteDatabase
import android.content.Intent
import android.appwidget.AppWidgetManager
import android.widget.FrameLayout
import android.view.View
import android.widget.TextView
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=AttentionApp::class)
class UpgradeIntegrationTest {
 @Test fun migrateRealVersionOneSchemaWithoutDataLoss()=runBlocking{
  val app=ApplicationProvider.getApplicationContext<AttentionApp>();val file=app.getDatabasePath("upgrade.db");file.parentFile!!.mkdirs()
  SQLiteDatabase.openOrCreateDatabase(file,null).use{db->
   db.execSQL("CREATE TABLE tasks (id TEXT NOT NULL PRIMARY KEY,title TEXT NOT NULL,date TEXT NOT NULL,minute INTEGER NOT NULL,importance INTEGER NOT NULL,progress INTEGER NOT NULL,note TEXT NOT NULL,category TEXT NOT NULL,createdAt INTEGER NOT NULL,completedAt INTEGER,reminder TEXT)")
   db.execSQL("CREATE TABLE delivered (`key` TEXT NOT NULL PRIMARY KEY)")
   db.execSQL("INSERT INTO tasks VALUES('old','Старая задача','2026-09-26',600,9,65,'Заметка','Наука',100,NULL,'60')")
   db.execSQL("INSERT INTO tasks VALUES('done','Завершено','2026-09-24',600,8,100,'','',100,200,NULL)")
   db.execSQL("INSERT INTO delivered VALUES('sent')");db.version=1
  }
  val db=Room.databaseBuilder(app,FocusDatabase::class.java,"upgrade.db").addMigrations(MIGRATION_1_2).build()
  val t=db.work().row("old")!!.full();assertEquals("Старая задача",t.title);assertEquals(65,t.progress);assertEquals(9,t.importance);assertEquals("Заметка",t.note);assertEquals("Наука",t.category);assertEquals("60",t.reminder);assertEquals(600,t.minute);assertEquals(100L,t.createdAt);assertNull(t.estimatedEffortMinutes);assertTrue(t.subtasks.isEmpty());assertEquals(listOf("sent"),db.tasks().delivered());assertEquals(200L,db.work().events().single().completedAt);db.close()
  val reopened=Room.databaseBuilder(app,FocusDatabase::class.java,"upgrade.db").addMigrations(MIGRATION_1_2).build();assertEquals(2,reopened.tasks().all().size);reopened.close()
 }
 @Test fun childCrudReorderEventsReopenAndSnapshot()=runBlocking{
  val app=ApplicationProvider.getApplicationContext<AttentionApp>();val repo=app.repository
  var t=repo.save(Task(id="p",title="Лекция",importance=9,estimatedEffortMinutes=60))
  val a=Subtask(id="a",parentTaskId=t.id,title="Исследования",estimatedEffortMinutes=15)
  val b=Subtask(id="b",parentTaskId=t.id,title="Слайды",estimatedEffortMinutes=45)
  t=repo.save(t.copy(subtasks=listOf(a,b)));assertEquals(60,t.estimatedEffortMinutes)
  t=repo.save(t.copy(subtasks=listOf(b.copy(title="Новые слайды"),a)));assertEquals("b",app.db.work().row(t.id)!!.full().subtasks.first().id)
  t=repo.save(t.copy(subtasks=t.subtasks.map{if(it.id=="a")it.copy(completed=true) else it}));assertEquals(25,t.progress)
  t=repo.save(t.copy(subtasks=t.subtasks.map{it.copy(completed=true)}));assertTrue(t.done)
  var events=app.db.work().events().filter{it.voidedAt==null};assertEquals(3,events.size);assertEquals(60,events.sumOf{it.estimatedEffortMinutes?:0})
  t=repo.save(t.copy(importance=5));assertTrue(app.db.work().events().all{it.importance==9});assertEquals(60,t.initialEstimatedEffortMinutes)
  t=repo.save(t.copy(subtasks=t.subtasks.map{if(it.id=="b")it.copy(completed=false) else it}));assertFalse(t.done);events=app.db.work().events().filter{it.voidedAt==null};assertEquals(1,events.size)
  t=repo.save(t.copy(subtasks=t.subtasks.map{it.copy(completed=true)}));repo.save(t);events=app.db.work().events().filter{it.voidedAt==null};assertEquals(3,events.size);assertEquals(60,events.sumOf{it.estimatedEffortMinutes?:0})
  t=repo.save(t.copy(subtasks=t.subtasks.filter{it.id!="a"}));assertEquals(1,app.db.work().row(t.id)!!.children.size)
  repo.delete(t.id);assertTrue(repo.all().isEmpty());repo.save(t);assertEquals(1,repo.all().size)
 }
 @Test fun simpleCompletionUndoAndEstimateSnapshots()=runBlocking{
  val app=ApplicationProvider.getApplicationContext<AttentionApp>();val repo=app.repository
  var t=repo.save(Task(title="Статья",importance=8,estimatedEffortMinutes=120));t=repo.save(t.copy(estimatedEffortMinutes=60,progress=75));assertEquals(120,t.initialEstimatedEffortMinutes);assertEquals(15,EffortLogic.remaining(t).minutes)
  val before=t;t=repo.save(t.withProgress(100));assertEquals(60,app.db.work().events().single().estimatedEffortMinutes)
  repo.save(t.copy(estimatedEffortMinutes=30,importance=2));assertEquals(60,app.db.work().events().single().estimatedEffortMinutes);assertEquals(8,app.db.work().events().single().importance)
  t=repo.save(before);assertEquals(0,app.db.work().events().count{it.voidedAt==null});repo.save(t.withProgress(100));assertEquals(1,app.db.work().events().count{it.voidedAt==null})
 }
 @Test fun backupV2PreservesChildrenEventsAndIsIdempotent()=runBlocking{
  val app=ApplicationProvider.getApplicationContext<AttentionApp>();var t=Task(id="backup",title="Копия");t=app.repository.save(t.copy(subtasks=listOf(Subtask(parentTaskId=t.id,title="Шаг",estimatedEffortMinutes=45,completed=true))))
  val original=app.db.work().events();val json=BackupCodec.export(app);app.repository.delete(t.id);BackupCodec.import(app,json);BackupCodec.import(app,json)
  assertEquals(1,app.repository.all().size);assertEquals(45,app.repository.all().single().estimatedEffortMinutes);assertEquals(original,app.db.work().events());assertEquals(1,app.repository.all().single().subtasks.size)
 }
 @Test fun widgetLayoutsInflateAndRoutesOpen()=runBlocking{
  val app=ApplicationProvider.getApplicationContext<AttentionApp>();val t=Task(title="Виджет",estimatedEffortMinutes=30)
  for((w,h) in listOf(180 to 140,270 to 320,300 to 500,350 to 680)){
   val rv=FocusWidget.render(app,List(10){i->t.copy(id="$i",title="Задача $i")},w,h,System.currentTimeMillis());val view=rv.apply(app,FrameLayout(app));view.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));view.layout(0,0,w,h);assertNotNull(view.findViewById<TextView>(R.id.widget_title));view.findViewById<View>(R.id.widget_add).performClick();assertEquals("create",shadowOf(app).nextStartedActivity.getStringExtra("route"));view.findViewById<View>(R.id.widget_open).performClick();assertEquals("task:0",shadowOf(app).nextStartedActivity.getStringExtra("route"))
  }
 }
 @Test fun recompletionUsesNewCommitTimeEvenFromStaleEditor()=runBlocking{
  val app=ApplicationProvider.getApplicationContext<AttentionApp>();val repo=app.repository
  var t=repo.save(Task(title="Повтор"),1)
  t=repo.save(t.withProgress(100),100);assertEquals(100L,t.completedAt)
  val stale=t;repo.save(t.withProgress(50),200);t=repo.save(stale,300);assertEquals(300L,t.completedAt)
  assertEquals(listOf(100L,300L),app.db.work().events().filter{it.taskId==t.id}.map{it.completedAt})
  var parent=repo.save(Task(title="Шаги",subtasks=listOf(Subtask(parentTaskId="",title="Шаг",completed=true,completedAt=1))),400)
  val old=parent;parent=repo.save(parent.copy(subtasks=parent.subtasks.map{it.copy(completed=false)}),500)
  parent=repo.save(old,600);assertEquals(600L,parent.completedAt);assertEquals(600L,parent.subtasks.single().completedAt)
 }

}
