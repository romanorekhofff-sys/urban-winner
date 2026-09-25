package dev.attention.app
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import java.time.LocalDate
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=AttentionApp::class)
class IntegrationJvmTest {
 @Test fun databasePersistsAndSupportsCompleteUndoReopenDelete()=runBlocking {
  val app=ApplicationProvider.getApplicationContext<AttentionApp>()
  val name="persistence-test.db";var db=Room.databaseBuilder(app,FocusDatabase::class.java,name).build()
  val t=Task(title="Проверка хранения",importance=8,date=LocalDate.now().plusDays(1).toString())
  db.tasks().put(t);assertEquals(t,db.tasks().all().single());val edited=t.copy(title="Изменено",note="Заметка").withProgress(65);db.tasks().put(edited);db.close()
  db=Room.databaseBuilder(app,FocusDatabase::class.java,name).build();assertEquals(edited,db.tasks().all().single())
  db.tasks().put(edited.withProgress(100));assertNotNull(db.tasks().all().single().completedAt)
  db.tasks().put(edited);assertEquals(65,db.tasks().all().single().progress)
  db.tasks().put(edited.withProgress(100).withProgress(75));assertNull(db.tasks().all().single().completedAt)
  db.tasks().delete(t.id);assertTrue(db.tasks().all().isEmpty());db.close()
 }
 @Test fun backupValidatesBeforeWritingAndMergesById()=runBlocking {
  val app=ApplicationProvider.getApplicationContext<AttentionApp>();val vm=FocusViewModel(app)
  val t=Task(title="Тест копии",importance=9,progress=65,note="Заметка",category="Наука")
  app.db.tasks().put(t);val json=vm.export();app.db.tasks().delete(t.id);vm.import(json);assertEquals(t,app.db.tasks().all().first{it.id==t.id})
  val before=app.db.tasks().all().toSet();try{vm.import(json.replace("\"importance\": 9","\"importance\": 99"));fail("Invalid data accepted")}catch(_:IllegalArgumentException){}
  assertEquals(before,app.db.tasks().all().toSet())
  vm.import(json);assertEquals(1,app.db.tasks().all().count{it.id==t.id})
 }
 @Test fun settingsPersistAndSummaryOpensCorrectRoute()=runBlocking {
  val app=ApplicationProvider.getApplicationContext<AttentionApp>()
  app.settings.save(Preferences(hour=8,minute=30,onboarded=true));assertEquals(8,Settings(app).flow.first().hour)
  app.db.tasks().put(Task(title="Проверить уведомление",importance=8))
  app.scheduler.morning()
  val n=app.getSystemService(NotificationManager::class.java).activeNotifications.first{it.id==10}
  assertTrue(n.notification.extras.getString("android.title")!!.contains("1"))
  n.notification.contentIntent.send()
  assertEquals("summary",shadowOf(app).nextStartedActivity.getStringExtra("route"))
 }
}
