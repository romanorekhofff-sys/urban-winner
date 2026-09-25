package dev.attention.app
import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
@Composable fun SettingsScreen(vm:FocusViewModel,p:Preferences,resume:Int,snack:SnackbarHostState){
 val context=LocalContext.current;val scope=rememberCoroutineScope();var busy by remember{mutableStateOf(false)};var importText by remember{mutableStateOf<String?>(null)};var permissionRefresh by remember{mutableIntStateOf(0)}
 val permitted=remember(resume,permissionRefresh){NotificationManagerCompat.from(context).areNotificationsEnabled()};val exact=remember(resume){vm.host.scheduler.exactAllowed()};val request=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){permissionRefresh++}
 fun message(s:String){scope.launch{snack.showSnackbar(s)}}
 val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->if(uri!=null)scope.launch{busy=true;runCatching{val data=vm.export();withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri,"wt")?.bufferedWriter()?.use{it.write(data)}?:error("Нет доступа")}}.onSuccess{message("Резервная копия сохранена")}.onFailure{message("Не удалось сохранить копию")};busy=false}}
 val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)scope.launch{busy=true;runCatching{withContext(Dispatchers.IO){context.contentResolver.openInputStream(uri)?.use{input->val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);var total=0;while(true){val n=input.read(buffer);if(n<0)break;total+=n;require(total<=10000000);output.write(buffer,0,n)};output.toString("UTF-8")}?:error("Нет доступа")}}.onSuccess{importText=it}.onFailure{message("Не удалось прочитать копию (максимум 10 МБ)")};busy=false}}
 Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom=30.dp)){
  SectionLabel("УТРЕННЯЯ СВОДКА");Column(Modifier.fillMaxWidth().background(Surface,RoundedCornerShape(20.dp)).padding(16.dp)){Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Каждое утро",fontSize=16.sp);Text("Краткая фактическая картина дня",color=Muted,fontSize=11.sp,modifier=Modifier.padding(top=4.dp))};Switch(p.enabled,{vm.settings(p.copy(enabled=it))})};Row(Modifier.fillMaxWidth().clickable{TimePickerDialog(context,{_,h,m->vm.settings(p.copy(hour=h,minute=m))},p.hour,p.minute,true).show()}.padding(top=18.dp,bottom=6.dp),verticalAlignment=Alignment.CenterVertically){Text("Время",Modifier.weight(1f),color=Muted,fontSize=14.sp);Text("%02d:%02d".format(p.hour,p.minute),fontSize=26.sp,fontWeight=FontWeight.Light,color=Accent)}}
  if(!permitted||!exact){Spacer(Modifier.height(12.dp));Column(Modifier.fillMaxWidth().background(Surface,RoundedCornerShape(16.dp)).padding(16.dp)){
   if(!permitted){Text("Уведомления выключены",fontSize=14.sp);Text("Разрешите их для сводки и сроков задач.",color=Muted,fontSize=12.sp);TextButton(onClick={if(Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)request.launch(Manifest.permission.POST_NOTIFICATIONS)else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))}){Text("Разрешить уведомления")}}
   if(!exact){Text("Точное время не разрешено",fontSize=14.sp);Text("Без разрешения Android может задержать сводку.",color=Muted,fontSize=12.sp);TextButton(onClick={if(Build.VERSION.SDK_INT>=31)runCatching{context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:${context.packageName}")))}}){Text("Настроить точное время")}}
  }}
  TextButton(onClick={scope.launch{vm.host.scheduler.morning();snack.showSnackbar(if(permitted)"Тестовая сводка отправлена" else "Сначала разрешите уведомления")}}){Text("Проверить уведомление",fontSize=12.sp)}
  SectionLabel("НАПОМИНАНИЯ О ЗАДАЧАХ");Text("По умолчанию для всех задач. Можно изменить в самой задаче.",color=Muted,fontSize=12.sp,lineHeight=18.sp);ReminderChoices(p.reminders){vm.settings(p.copy(reminders=it))}
  SectionLabel("РЕЗЕРВНАЯ КОПИЯ");Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(enabled=!busy,onClick={export.launch("FOCUS-backup-${java.time.LocalDate.now()}.json")},shape=RoundedCornerShape(12.dp),modifier=Modifier.weight(1f)){Icon(Icons.Rounded.FileUpload,null,Modifier.size(17.dp));Spacer(Modifier.width(7.dp));Text("Экспорт")};OutlinedButton(enabled=!busy,onClick={import.launch(arrayOf("application/json","text/plain","application/octet-stream"))},shape=RoundedCornerShape(12.dp),modifier=Modifier.weight(1f)){Icon(Icons.Rounded.FileDownload,null,Modifier.size(17.dp));Spacer(Modifier.width(7.dp));Text("Импорт")}}
  Text("Задачи и настройки хранятся только на устройстве. Копия включает подзадачи, оценки и историю завершений.",color=Muted,fontSize=12.sp,lineHeight=18.sp,modifier=Modifier.padding(top=10.dp));if(busy)LinearProgressIndicator(Modifier.fillMaxWidth().padding(top=12.dp),color=Accent)
  SectionLabel("ГОЛОСОВОЙ ВВОД");VoiceConnectionSettings()
  SectionLabel("ВИДЖЕТ");Text("На домашнем экране откройте список виджетов и выберите FOCUS. Растяните панель на свободный экран: количество задач адаптируется к размеру.",color=Muted,fontSize=12.sp,lineHeight=20.sp)
  SectionLabel("КАК РАБОТАЕТ ФОКУС");Text("Просроченные — первыми. Остальные: важность × (1 + 1,4 × срочность). Срочность = 1 / (1 + часы до срока / 24). Прогресс 80–99% добавляет 0,6 балла, если до срока меньше суток.",color=Muted,fontSize=12.sp,lineHeight=20.sp);Text("FOCUS 1.2 · задачи локально",color=Muted.copy(alpha=.7f),fontSize=11.sp,modifier=Modifier.padding(top=28.dp))
 }
 if(importText!=null)AlertDialog(onDismissRequest={importText=null},containerColor=Surface,title={Text("Восстановить копию?")},text={Text("Задачи с тем же идентификатором будут обновлены. Остальные сохранятся. Настройки будут взяты из копии.")},confirmButton={TextButton(onClick={val text=importText!!;importText=null;scope.launch{busy=true;runCatching{vm.import(text)}.onSuccess{message("Восстановлено задач: $it")}.onFailure{message("Копия повреждена или имеет другой формат")};busy=false}}){Text("Импортировать")}},dismissButton={TextButton(onClick={importText=null}){Text("Отмена")}})
}
