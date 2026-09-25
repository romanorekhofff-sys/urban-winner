package dev.attention.app
import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.*
class ReminderScheduler(private val app:AttentionApp){
 private val alarms=app.getSystemService(AlarmManager::class.java);private val lock=Mutex()
 fun channels(){val nm=app.getSystemService(NotificationManager::class.java);nm.createNotificationChannel(NotificationChannel("morning","Утренняя сводка",NotificationManager.IMPORTANCE_DEFAULT));nm.createNotificationChannel(NotificationChannel("deadlines","Сроки задач",NotificationManager.IMPORTANCE_DEFAULT))}
 fun exactAllowed()=Build.VERSION.SDK_INT<31||alarms.canScheduleExactAlarms()
 private fun pending(kind:String,at:Long=0)=PendingIntent.getBroadcast(app,if(kind=="summary")1 else 2,Intent(app,AlarmReceiver::class.java).setAction(kind).putExtra("scheduledAt",at),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
 private fun schedule(kind:String,time:Long){val pi=pending(kind,time);try{if(exactAllowed())alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,time,pi) else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,time,pi)}catch(_:SecurityException){alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,time,pi)}}
 data class Event(val task:Task,val offset:Int,val at:Long){val key get()="${task.id}:${task.date}:${task.minute}:$offset"}
 private fun events(tasks:List<Task>,p:Preferences)=tasks.filter{!it.done}.flatMap{t->(t.reminder?:p.reminders).split(',').mapNotNull{it.toIntOrNull()}.distinct().map{Event(t,it,t.due-it*60000L)}}
 suspend fun reschedule()=lock.withLock{plan()}
 private suspend fun plan(){val p=app.settings.flow.first();val now=System.currentTimeMillis();if(p.enabled)schedule("summary",FocusLogic.nextSummary(ZonedDateTime.now(),p.hour,p.minute).toInstant().toEpochMilli()) else alarms.cancel(pending("summary"));val sent=app.db.tasks().delivered().toSet();val next=events(app.repository.all(),p).filter{it.at>now&&it.key !in sent}.minByOrNull{it.at};if(next!=null)schedule("deadline",next.at) else alarms.cancel(pending("deadline"))}
 private fun show(id:Int,channel:String,title:String,text:String,route:String,detail:String=text){if(Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(app,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;val pi=PendingIntent.getActivity(app,id,Intent(app,MainActivity::class.java).setAction("open:$route").putExtra("route",route).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE);try{NotificationManagerCompat.from(app).notify(id,NotificationCompat.Builder(app,channel).setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(detail)).setContentIntent(pi).setAutoCancel(true).setColor(0xFFB7A7F4.toInt()).setCategory(NotificationCompat.CATEGORY_REMINDER).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build())}catch(_:SecurityException){}}
 fun cancelTask(id:String){NotificationManagerCompat.from(app).cancel(id.hashCode())}
 suspend fun morning(){val tasks=app.repository.all().filter{!it.done};val today=LocalDate.now().toString();val now=System.currentTimeMillis();val line="${EffortLogic.total(tasks).label()} осталось · Сегодня: ${tasks.count{it.date==today}} (${EffortLogic.total(tasks.filter{it.date==today}).label()}) · важных: ${tasks.count{it.importance>=8}} · просрочено: ${tasks.count{it.due<now}}";val details=tasks.sortedWith(compareByDescending<Task>{it.due<now}.thenByDescending{FocusLogic.score(it.importance,it.progress,it.due,now)}).take(4).joinToString("\n"){"${it.title} · ${it.importance}/10 · ${it.progress}%"+it.workLabel().let{w->if(w.isBlank())"" else " · $w"}};show(10,"morning","Доброе утро. Активных задач: ${tasks.size}",line,"summary",line+if(details.isEmpty())"" else "\n\n$details")}
 suspend fun fire(kind:String?,scheduledAt:Long)=lock.withLock{try{val p=app.settings.flow.first();if(kind=="summary"&&p.enabled)morning();if(kind=="deadline"){val sent=app.db.tasks().delivered().toSet();val now=System.currentTimeMillis();events(app.repository.all(),p).filter{it.at in scheduledAt..now&&it.key !in sent}.groupBy{it.task.id}.values.forEach{group->val e=group.minBy{it.offset};show(e.task.id.hashCode(),"deadlines",if(e.task.due<=now)"Наступил срок задачи" else "Дедлайн: ${dateLabel(e.task)}",e.task.title,"task:${e.task.id}","${e.task.title}\nВажность ${e.task.importance}/10 · выполнено ${e.task.progress}%");group.forEach{app.db.tasks().delivered(Delivered(it.key))}}}}finally{plan()}}
}
class AlarmReceiver:BroadcastReceiver(){override fun onReceive(context:Context,intent:Intent){val pending=goAsync();val app=context.applicationContext as AttentionApp;app.scope.launch{try{app.scheduler.fire(intent.action,intent.getLongExtra("scheduledAt",System.currentTimeMillis()))}finally{pending.finish()}}}}
class RescheduleReceiver:BroadcastReceiver(){override fun onReceive(context:Context,intent:Intent){if(intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED,Intent.ACTION_MY_PACKAGE_REPLACED,Intent.ACTION_TIMEZONE_CHANGED,Intent.ACTION_TIME_CHANGED,"android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"))return;val pending=goAsync();val app=context.applicationContext as AttentionApp;app.scope.launch{try{app.scheduler.reschedule();FocusWidget.updateAll(app)}finally{pending.finish()}}}}
