package dev.attention.app

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock

data class VoiceUndo(val before:Task?,val after:Task,val events:List<CompletionEvent>)
class VoiceExecutor(private val app:AttentionApp){
 suspend fun apply(c:VoiceCommand,context:List<Task>):VoiceUndo=withContext(Dispatchers.IO){
  VoiceContract.validate(c,context)
  val before=c.taskId?.let{id->context.single{it.id==id}}
  val events=app.db.work().events().filter{it.taskId==before?.id}
  val base=before?:Task(title=requireNotNull(c.title),date=requireNotNull(c.date),minute=c.minute?:-1,importance=c.importance?:5,estimatedEffortMinutes=c.minutes,note=c.note?:"")
  fun children()=c.children.map{Subtask(parentTaskId=base.id,title=it.title,estimatedEffortMinutes=it.minutes)}
  val changed=when(c.action){
   VoiceAction.CREATE_TASK->base
   VoiceAction.CREATE_TASK_WITH_SUBTASKS->base.copy(subtasks=children())
   VoiceAction.SET_PROGRESS->base.withProgress(requireNotNull(c.progress))
   VoiceAction.COMPLETE_TASK->base.withProgress(100).copy(subtasks=base.subtasks.map{it.copy(completed=true)})
   VoiceAction.CHANGE_DEADLINE->base.copy(date=requireNotNull(c.date),minute=requireNotNull(c.minute))
   VoiceAction.UPDATE_TASK->base.copy(title=c.title?:base.title,importance=c.importance?:base.importance,note=c.note?:base.note,estimatedEffortMinutes=c.minutes?:base.estimatedEffortMinutes)
   VoiceAction.ADD_SUBTASK->base.copy(subtasks=base.subtasks+children())
   VoiceAction.COMPLETE_SUBTASK->base.copy(subtasks=base.subtasks.map{if(it.id in c.subtaskIds)it.copy(completed=true)else it})
   VoiceAction.QUERY_TASKS->error("Запрос не изменяет задачи")
  }
  val saved=app.repository.save(changed,expected=before,checkExpected=true)
  VoiceUndo(before,saved,events)
 }
 suspend fun undo(receipt:VoiceUndo)=withContext(Dispatchers.IO){
  app.mutationLock.withLock{app.db.withTransaction{
   val dao=app.db.work();check(dao.row(receipt.after.id)?.full()==receipt.after){"Задача уже изменена. Отмена недоступна"}
   dao.clearSubtasks(receipt.after.id);dao.clearEvents(listOf(receipt.after.id))
   if(receipt.before==null)app.db.tasks().delete(receipt.after.id)else{app.db.tasks().put(receipt.before);dao.putSubtasks(receipt.before.subtasks);dao.putEvents(receipt.events)}
  }};app.scheduler.cancelTask(receipt.after.id);app.scheduler.reschedule();FocusWidget.updateAll(app)
 }
}
fun VoiceCommand.summary(tasks:List<Task>):String {
 val t=tasks.firstOrNull{it.id==taskId};val name=title?:t?.title?:"Задача"
 return when(action){
  VoiceAction.CREATE_TASK,VoiceAction.CREATE_TASK_WITH_SUBTASKS->"$name\n$date${if(minute!=null&&minute>=0)" · %02d:%02d".format(minute/60,minute%60) else " · конец дня"} · важность ${importance?:5}"+(if(importance==null)" (по умолчанию)" else "")+(minutes?.let{"\n≈${effortText(it)}"}?:"")+(if(children.isNotEmpty())"\n"+children.joinToString("\n"){"• ${it.title}"+(it.minutes?.let{m->" · ${effortText(m)}"}?:"")}else "")
  VoiceAction.SET_PROGRESS->"$name → $progress%"
  VoiceAction.COMPLETE_TASK->"Завершить «$name»"+(if(t?.subtasks?.any{!it.completed}==true)"\nИ все невыполненные подзадачи" else "")
  VoiceAction.CHANGE_DEADLINE->"$name\nНовый срок: $date"+(if(minute!=null&&minute>=0)" · %02d:%02d".format(minute/60,minute%60)else " · конец дня")
  VoiceAction.ADD_SUBTASK->"$name\n"+children.joinToString("\n"){"+ ${it.title}"+(it.minutes?.let{m->" · ${effortText(m)}"}?:"")}
  VoiceAction.COMPLETE_SUBTASK->"$name\nЗавершить:\n"+t?.subtasks.orEmpty().filter{it.id in subtaskIds}.joinToString("\n"){"• ${it.title}"}
  VoiceAction.UPDATE_TASK->"$name"+(importance?.let{"\nВажность → $it"}?:"")+(minutes?.let{"\nОценка → ${effortText(it)}"}?:"")+(note?.let{"\nЗаметка: $it"}?:"")
  VoiceAction.QUERY_TASKS->"Найденные задачи"
 }
}
