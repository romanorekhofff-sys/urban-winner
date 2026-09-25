package dev.attention.app

import org.json.*
import java.time.*

enum class VoiceAction { CREATE_TASK, CREATE_TASK_WITH_SUBTASKS, UPDATE_TASK, SET_PROGRESS, COMPLETE_TASK, CHANGE_DEADLINE, ADD_SUBTASK, COMPLETE_SUBTASK, QUERY_TASKS }
data class VoiceChild(val title:String,val minutes:Int?)
data class VoiceQuery(val from:String?,val to:String?,val minImportance:Int?,val maxMinutes:Int?,val sort:String)
data class VoiceCommand(val action:VoiceAction,val taskId:String?,val title:String?,val date:String?,val minute:Int?,val importance:Int?,val progress:Int?,val minutes:Int?,val note:String?,val children:List<VoiceChild>,val subtaskIds:List<String>,val query:VoiceQuery?)
data class VoiceResult(val status:String,val confidence:Double,val message:String,val candidates:List<String>,val command:VoiceCommand?)
private fun JSONObject.s(k:String)=if(isNull(k))null else getString(k)
private fun JSONObject.n(k:String)=if(isNull(k))null else getInt(k)
fun JSONArray.strings()=(0 until length()).map{getString(it)}
object VoiceContract {
 // Validate the SAME closed schema used by the Worker, before any typed decoding.
 fun valid(s:JSONObject,v:Any?):Boolean {
  if(s.has("anyOf"))return (0 until s.getJSONArray("anyOf").length()).any{valid(s.getJSONArray("anyOf").getJSONObject(it),v)}
  if(s.has("enum")&&!s.getJSONArray("enum").strings().contains(v))return false
  return when(s.getString("type")){
   "null"->v==null||v==JSONObject.NULL
   "object"->v is JSONObject&&v.keys().asSequence().all{s.getJSONObject("properties").has(it)}&&s.getJSONArray("required").strings().all{v.has(it)&&valid(s.getJSONObject("properties").getJSONObject(it),v.get(it))}
   "array"->v is JSONArray&&v.length()<=s.getInt("maxItems")&&(0 until v.length()).all{valid(s.getJSONObject("items"),v.get(it))}
   "string"->v is String&&v.length<=s.optInt("maxLength",Int.MAX_VALUE)
   "boolean"->v is Boolean
   "integer","number"->v is Number&&v.toDouble().isFinite()&&v.toDouble()>=s.getDouble("minimum")&&v.toDouble()<=s.getDouble("maximum")&&(s.getString("type")!="integer"||v.toDouble()%1.0==0.0)
   else->false
  }
 }
 fun parse(json:JSONObject,schema:JSONObject,requestId:String):VoiceResult {
  require(json.getInt("version")==1&&json.getString("requestId")==requestId)
  val body=JSONObject(json.toString()).apply{remove("version");remove("requestId")};require(valid(schema,body))
  val c=if(body.isNull("command"))null else body.getJSONObject("command").let{c->
   val children=c.getJSONArray("subtasks");val q=if(c.isNull("query"))null else c.getJSONObject("query").let{VoiceQuery(it.s("dateFrom"),it.s("dateTo"),it.n("minImportance"),it.n("maxMinutes"),it.getString("sort"))}
   VoiceCommand(VoiceAction.valueOf(c.getString("action")),c.s("taskId"),c.s("title"),c.s("deadlineDate"),c.n("deadlineMinute"),c.n("importance"),c.n("progress"),c.n("estimatedEffortMinutes"),c.s("note"),(0 until children.length()).map{children.getJSONObject(it).let{s->VoiceChild(s.getString("title"),s.n("estimatedEffortMinutes"))}},c.getJSONArray("subtaskIds").strings(),q)
  }
  require((body.getString("status")=="READY")== (c!=null))
  return VoiceResult(body.getString("status"),body.getDouble("confidence"),body.getString("message"),body.getJSONArray("candidateIds").strings(),c)
 }
 fun validate(c:VoiceCommand,allowed:List<Task>){
  c.date?.let{require(LocalDate.parse(it).toString()==it)};c.importance?.let{require(it in 1..10)};c.progress?.let{require(it in 0..100)};c.minute?.let{require(it in -1..1439)};c.minutes?.let{require(it in 1..525600)}
  c.title?.let{require(it.isNotBlank())};require(c.children.all{it.title.isNotBlank()&&(it.minutes==null||it.minutes in 1..525600)})
  when(c.action){
   VoiceAction.CREATE_TASK,VoiceAction.CREATE_TASK_WITH_SUBTASKS->{require(c.taskId==null&&!c.title.isNullOrBlank()&&c.date!=null&&c.minute!=null);require((c.action==VoiceAction.CREATE_TASK)==c.children.isEmpty());require(c.children.sumOf{it.minutes?:0}<=525600)}
   VoiceAction.QUERY_TASKS->{val q=requireNotNull(c.query);q.from?.let{LocalDate.parse(it)};q.to?.let{LocalDate.parse(it)};require(q.from==null||q.to==null||q.from<=q.to)}
   else->{val t=allowed.singleOrNull{it.id==c.taskId}?:error("Задача не найдена. Выберите её снова")
    when(c.action){
     VoiceAction.SET_PROGRESS->require(c.progress!=null&&t.subtasks.isEmpty()){"Прогресс рассчитывается по подзадачам"}
     VoiceAction.CHANGE_DEADLINE->require(c.date!=null&&c.minute!=null)
     VoiceAction.ADD_SUBTASK->require(c.children.isNotEmpty()&&t.subtasks.size+c.children.size<=500)
     VoiceAction.COMPLETE_SUBTASK->require(c.subtaskIds.isNotEmpty()&&c.subtaskIds.distinct().size==c.subtaskIds.size&&c.subtaskIds.all{id->t.subtasks.any{it.id==id}})
     VoiceAction.UPDATE_TASK->require(c.title!=null||c.importance!=null||c.note!=null||c.minutes!=null)
     else->Unit
    }
   }
  }
 }
 fun auto(result:VoiceResult)=result.status=="READY"&&result.confidence>=.95&&result.command?.let{c->c.action==VoiceAction.CREATE_TASK&&c.importance!=null||c.action==VoiceAction.SET_PROGRESS&&(c.progress?:100)<100}==true
 fun query(q:VoiceQuery,tasks:List<Task>,now:Long)=tasks.filter{!it.done&&(q.from==null||it.date>=q.from)&&(q.to==null||it.date<=q.to)&&(q.minImportance==null||it.importance>=q.minImportance)&&(q.maxMinutes==null||EffortLogic.fits(it,q.maxMinutes))}.let{items->when(q.sort){"IMPORTANCE"->items.sortedByDescending{it.importance};"URGENCY"->items.sortedBy{it.due};else->WidgetLayout.ordered(items,now)}}
 fun candidates(text:String,tasks:List<Task>):List<Task>{
  if(Regex("^(что|какие|покажи|what|show)\\b",RegexOption.IGNORE_CASE).containsMatchIn(text.trim()))return emptyList()
  if(Regex("^(добавь|создай|создать|add|create)\\b",RegexOption.IGNORE_CASE).containsMatchIn(text.trim())&&!Regex("подзадач|subtask",RegexOption.IGNORE_CASE).containsMatchIn(text))return emptyList()
  val words=Regex("[\\p{L}]{4,}").findAll(text.lowercase()).map{it.value.take(4)}.toSet()
  return tasks.filter{!it.done}.map{t->t to words.count{t.title.lowercase().contains(it)||t.subtasks.any{s->s.title.lowercase().contains(it)}}}.filter{it.second>0}.sortedByDescending{it.second}.take(12).map{it.first}
 }
}
