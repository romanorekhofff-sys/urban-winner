package dev.attention.app
import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

@Entity(tableName="tasks")
data class Task @Ignore constructor(@PrimaryKey val id:String=UUID.randomUUID().toString(),val title:String,val date:String=LocalDate.now().toString(),val minute:Int=-1,val importance:Int=5,val progress:Int=0,val note:String="",val category:String="",val createdAt:Long=System.currentTimeMillis(),val completedAt:Long?=null,val reminder:String?=null,val estimatedEffortMinutes:Int?=null,val initialEstimatedEffortMinutes:Int?=null,@ColumnInfo(defaultValue="0") val updatedAt:Long=0,@ColumnInfo(defaultValue="NULL") val deletedAt:Long?=null,@Ignore val subtasks:List<Subtask> = emptyList()) {
 constructor(id:String,title:String,date:String,minute:Int,importance:Int,progress:Int,note:String,category:String,createdAt:Long,completedAt:Long?,reminder:String?,estimatedEffortMinutes:Int?,initialEstimatedEffortMinutes:Int?,updatedAt:Long,deletedAt:Long?):this(id,title,date,minute,importance,progress,note,category,createdAt,completedAt,reminder,estimatedEffortMinutes,initialEstimatedEffortMinutes,updatedAt,deletedAt,emptyList())
 val due:Long get()=FocusLogic.deadline(date,minute)
 val done:Boolean get()=progress==100
 fun withProgress(value:Int,now:Long=System.currentTimeMillis()):Task=copy(progress=value.coerceIn(0,100),completedAt=FocusLogic.completedAt(value.coerceIn(0,100),completedAt,now))
}
@Entity(tableName="delivered") data class Delivered(@PrimaryKey val key:String)
@Dao interface TaskDao {
 @Query("SELECT * FROM tasks WHERE deletedAt IS NULL") fun observe():Flow<List<Task>>
 @Query("SELECT * FROM tasks") suspend fun all():List<Task>
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun put(task:Task)
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putAll(tasks:List<Task>)
 @Query("DELETE FROM tasks WHERE id=:id") suspend fun delete(id:String)
 @Query("SELECT `key` FROM delivered") suspend fun delivered():List<String>
 @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun delivered(item:Delivered)
}
@Database(entities=[Task::class,Delivered::class,Subtask::class,CompletionEvent::class,WorkSession::class],version=2,exportSchema=false)
abstract class FocusDatabase:RoomDatabase(){abstract fun tasks():TaskDao;abstract fun work():WorkDao}
private val Context.store by preferencesDataStore("attention_settings")
data class Preferences(val enabled:Boolean=true,val hour:Int=9,val minute:Int=0,val reminders:String="0",val onboarded:Boolean=false,val categories:String="Наука|Работа|Личное|Проекты")
class Settings(private val context:Context){
 private val enabled=booleanPreferencesKey("summary_enabled");private val hour=intPreferencesKey("summary_hour");private val minute=intPreferencesKey("summary_minute");private val reminders=stringPreferencesKey("reminders");private val onboarded=booleanPreferencesKey("onboarded");private val categories=stringPreferencesKey("categories")
 val flow=context.store.data.map {p->Preferences(p[enabled]?:true,p[hour]?:9,p[minute]?:0,p[reminders]?:"0",p[onboarded]?:false,p[categories]?:"Наука|Работа|Личное|Проекты")}
 suspend fun save(p:Preferences){context.store.edit{it[enabled]=p.enabled;it[hour]=p.hour;it[minute]=p.minute;it[reminders]=p.reminders;it[onboarded]=p.onboarded;it[categories]=p.categories}}
}
class AttentionApp:Application(){
 val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
 val db by lazy{Room.databaseBuilder(this,FocusDatabase::class.java,"attention.db").addMigrations(MIGRATION_1_2).build()}
 val settings by lazy{Settings(this)}
 val scheduler by lazy{ReminderScheduler(this)}
 val mutationLock=Mutex()
 val repository by lazy{TaskRepository(this)}
 override fun onCreate(){super.onCreate();scheduler.channels();scope.launch{scheduler.reschedule();FocusWidget.updateAll(this@AttentionApp)}}
}
class FocusViewModel(app:Application):AndroidViewModel(app){
 val host=app as AttentionApp
 val tasks=host.db.work().observeTasks().map{rows->rows.map{it.full()}}.flowOn(Dispatchers.Default).stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
 val events=host.db.work().observeEvents().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
 val preferences=host.settings.flow.stateIn(viewModelScope,SharingStarted.Eagerly,null)
 val messages=MutableSharedFlow<String>(extraBufferCapacity=4)
 fun save(task:Task){viewModelScope.launch{runCatching{host.repository.save(task)}.onFailure{messages.emit("Не удалось сохранить задачу")}}}
 fun delete(task:Task){viewModelScope.launch{runCatching{host.repository.delete(task.id)}.onFailure{messages.emit("Не удалось удалить задачу")}}}
 fun settings(p:Preferences){viewModelScope.launch{host.settings.save(p);host.scheduler.reschedule()}}
 suspend fun export():String=BackupCodec.export(host)
 suspend fun import(text:String):Int=BackupCodec.import(host,text)
}
