package dev.attention.app
import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.os.Looper
import android.widget.TextView
import android.view.View
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=AttentionApp::class)
class WidgetInteractionTest {
 @Test fun updatesResizeCompleteAndComplexGuard()=runBlocking {
  val app=ApplicationProvider.getApplicationContext<AttentionApp>();val manager=AppWidgetManager.getInstance(app);val host=shadowOf(manager)
  val id=host.createWidget(FocusWidget::class.java,R.layout.focus_widget)
  val t=app.repository.save(Task(title="Из виджета",estimatedEffortMinutes=30))
  assertEquals("Из виджета",host.getViewFor(id).findViewById<TextView>(R.id.widget_task_title).text.toString())
  manager.updateAppWidgetOptions(id,Bundle().apply{putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,350);putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,680)})
  FocusWidget.updateAll(app);assertEquals(View.VISIBLE,host.getViewFor(id).findViewById<View>(R.id.widget_load).visibility)
  host.reconstructWidgetViewAsIfPhoneWasRotated(id);assertNotNull(host.getViewFor(id).findViewById<View>(R.id.widget_add))
  host.getViewFor(id).findViewById<View>(R.id.widget_check).performClick();shadowOf(Looper.getMainLooper()).idle()
  withTimeout(5000){while(app.db.work().row(t.id)?.task?.done!=true){delay(10)}}
  assertEquals(1,app.db.work().events().count{it.voidedAt==null})
  val complex=app.repository.save(Task(title="Комплексная",subtasks=listOf(Subtask(parentTaskId="",title="Шаг"))))
  host.getViewFor(id).findViewById<View>(R.id.widget_check).performClick();assertEquals("task:${complex.id}",shadowOf(app).nextStartedActivity.getStringExtra("route"));assertFalse(app.db.work().row(complex.id)!!.task.done)
 }
}
