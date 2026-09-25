package dev.attention.app
import android.app.*
import android.appwidget.*
import android.content.*
import android.os.Build
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.launch
import java.time.*

object WidgetLayout {
 fun rows(width:Int,height:Int)=when{width<250||height<280->1;height<460->3;height<650->6;else->9}
 fun ordered(tasks:List<Task>,now:Long)=tasks.filter{!it.done}.sortedWith(compareByDescending<Task>{it.due<now}.thenByDescending{FocusLogic.score(it.importance,it.progress,it.due,now)}.thenBy{it.due})
 fun deadline(t:Task,now:Long):String {val hours=(kotlin.math.abs(t.due-now)/3600000);if(t.due<now)return "просрочено "+if(hours<24)"${hours.coerceAtLeast(1)} ч" else "${hours/24} д";if(hours<4)return "${hours.coerceAtLeast(1)} ч";val today=Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate();return when(LocalDate.parse(t.date)){today->"сегодня";today.plusDays(1)->"завтра";else->"${java.time.temporal.ChronoUnit.DAYS.between(today,LocalDate.parse(t.date))} д"}}
}
class FocusWidget:AppWidgetProvider(){
 override fun onReceive(context:Context,intent:Intent){
  super.onReceive(context,intent)
  if(intent.action !in setOf(AppWidgetManager.ACTION_APPWIDGET_UPDATE,AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED,AppWidgetManager.ACTION_APPWIDGET_ENABLED,AppWidgetManager.ACTION_APPWIDGET_DISABLED,AppWidgetManager.ACTION_APPWIDGET_RESTORED,"dev.attention.WIDGET_COMPLETE","dev.attention.WIDGET_REFRESH"))return
  val pending=goAsync();val app=context.applicationContext as AttentionApp
  app.scope.launch{try{if(intent.action=="dev.attention.WIDGET_COMPLETE"){
   val t=intent.getStringExtra("taskId")?.let{app.db.work().row(it)?.full()}
   if(t!=null&&!t.done&&t.subtasks.isEmpty()&&t.deletedAt==null)app.repository.save(t.withProgress(100))
  };updateAll(app)}finally{pending.finish()}}
 }
 companion object {
  fun route(context:Context,route:String)=PendingIntent.getActivity(context,route.hashCode(),Intent(context,MainActivity::class.java).setAction("widget:$route").putExtra("route",route).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  suspend fun updateAll(app:AttentionApp){
   val manager=AppWidgetManager.getInstance(app);val ids=manager.getAppWidgetIds(ComponentName(app,FocusWidget::class.java));val alarm=app.getSystemService(AlarmManager::class.java)
   val boundary=PendingIntent.getBroadcast(app,91,Intent(app,FocusWidget::class.java).setAction("dev.attention.WIDGET_REFRESH"),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
   if(ids.isEmpty()){alarm.cancel(boundary);return}
   val now=System.currentTimeMillis();val tasks=WidgetLayout.ordered(app.repository.all(),now)
   ids.forEach{id->val options=manager.getAppWidgetOptions(id);val landscape=app.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE;val width=options.getInt(if(landscape)AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,270);val height=options.getInt(if(landscape)AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,460)
    val views=if(Build.VERSION.SDK_INT>=31)RemoteViews(mapOf(SizeF(170f,140f) to render(app,tasks,170,140,now),SizeF(250f,280f) to render(app,tasks,250,280,now),SizeF(250f,460f) to render(app,tasks,250,460,now),SizeF(250f,650f) to render(app,tasks,250,650,now))) else render(app,tasks,width,height,now)
    manager.updateAppWidget(id,views)
   }
   val midnight=LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
   val next=(tasks.map{it.due}.filter{it>now}+midnight).minOrNull()?:midnight
   // No wake-up loop: data mutations, system periodic updates and a single next boundary.
   alarm.set(AlarmManager.RTC,next,boundary)
  }
  fun render(context:Context,tasks:List<Task>,width:Int,height:Int,now:Long):RemoteViews {
   val count=WidgetLayout.rows(width,height);val max=count==9;val small=count==1
   val rv=RemoteViews(context.packageName,R.layout.focus_widget)
   rv.setOnClickPendingIntent(R.id.widget_title,route(context,"home"));rv.setOnClickPendingIntent(R.id.widget_add,route(context,"create"));rv.setOnClickPendingIntent(R.id.widget_calendar,route(context,"calendar"));rv.setOnClickPendingIntent(R.id.widget_footer,route(context,"home"))
   val today=LocalDate.now().toString()
   rv.setTextViewText(R.id.widget_summary,if(small)"Активных: ${tasks.size}" else "${tasks.size} активных · ${tasks.count{it.date==today}} сегодня · ${tasks.count{it.due<now}} просрочено")
   rv.setViewVisibility(R.id.widget_load,if(max)View.VISIBLE else View.GONE);rv.setTextViewText(R.id.widget_load,EffortLogic.total(tasks).label()+" осталось")
   rv.setViewVisibility(R.id.widget_calendar,if(small)View.GONE else View.VISIBLE);rv.setViewVisibility(R.id.widget_footer,if(small)View.GONE else View.VISIBLE)
   rv.removeAllViews(R.id.widget_rows)
   tasks.take(count).forEach{t->val row=RemoteViews(context.packageName,R.layout.focus_widget_task)
    row.setTextViewText(R.id.widget_task_title,t.title);row.setTextViewText(R.id.widget_task_meta,"${WidgetLayout.deadline(t,now)} · ${t.importance} · ${t.progress}%"+t.workLabel().let{if(it.isBlank())"" else " · $it"})
    row.setInt(R.id.widget_importance,"setBackgroundColor",importanceColor(t.importance).toArgb())
    row.setOnClickPendingIntent(R.id.widget_open,route(context,"task:${t.id}"))
    row.setContentDescription(R.id.widget_check,if(t.subtasks.isEmpty())"Завершить ${t.title}" else "Открыть подзадачи ${t.title}")
    val action=if(t.subtasks.isNotEmpty())route(context,"task:${t.id}") else PendingIntent.getBroadcast(context,t.id.hashCode(),Intent(context,FocusWidget::class.java).setAction("dev.attention.WIDGET_COMPLETE").putExtra("taskId",t.id),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    row.setOnClickPendingIntent(R.id.widget_check,action);rv.addView(R.id.widget_rows,row)
   }
   if(tasks.isEmpty()){rv.setTextViewText(R.id.widget_summary,"Нет активных задач · добавьте через +")}
   return rv
  }
 }
}
