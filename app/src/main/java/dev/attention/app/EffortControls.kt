package dev.attention.app
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable fun EffortPicker(value:Int?,onChange:(Int?)->Unit){
 var custom by remember{mutableStateOf(false)};var text by remember{mutableStateOf("")}
 Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())){
  Pill("Без оценки",value==null){onChange(null)}
  listOf(15,30,45,60,120,240).forEach{v->Pill(effortText(v),value==v){onChange(v)}}
  Pill(if(value!=null&&value !in listOf(15,30,45,60,120,240))effortText(value) else "Другое"){text=value?.toString()?:"";custom=true}
 }
 if(custom)AlertDialog(onDismissRequest={custom=false},containerColor=Surface,title={Text("Оценка времени")},text={OutlinedTextField(text,{text=it.filter(Char::isDigit).take(6)},label={Text("Минуты")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true)},confirmButton={TextButton(enabled=text.toIntOrNull() in 1..525600,onClick={onChange(text.toInt());custom=false}){Text("Готово")}},dismissButton={TextButton(onClick={custom=false}){Text("Отмена")}})
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun TimeAvailableSheet(current:Int?,dismiss:()->Unit,choose:(Int?)->Unit){
 var amount by remember{mutableStateOf(current?:30)};var custom by remember{mutableStateOf(false)};var input by remember{mutableStateOf("")}
 ModalBottomSheet(onDismissRequest=dismiss,containerColor=Ink){Column(Modifier.padding(24.dp).padding(bottom=24.dp)){
  Text("Есть время",fontSize=26.sp);Text("Задачи, которые помещаются в выбранное время. Неоценённый остаток не включается.",color=Muted,fontSize=13.sp,modifier=Modifier.padding(vertical=14.dp))
  Row(Modifier.horizontalScroll(rememberScrollState())){listOf(15,30,60,120).forEach{n->Pill(effortText(n),amount==n){amount=n}};Pill(if(amount !in listOf(15,30,60,120))effortText(amount) else "Другое"){input=amount.toString();custom=true}}
  Spacer(Modifier.height(20.dp));Button(onClick={choose(amount);dismiss()},modifier=Modifier.fillMaxWidth()){Text("Показать подходящие")}
  TextButton(onClick={choose(null);dismiss()},modifier=Modifier.fillMaxWidth()){Text("Показать все задачи",color=Muted)}
 }}
 if(custom)AlertDialog(onDismissRequest={custom=false},containerColor=Surface,title={Text("Сколько есть времени?")},text={OutlinedTextField(input,{input=it.filter(Char::isDigit).take(6)},label={Text("Минуты")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true)},confirmButton={TextButton(enabled=input.toIntOrNull() in 1..525600,onClick={amount=input.toInt();custom=false}){Text("Готово")}},dismissButton={TextButton(onClick={custom=false}){Text("Отмена")}})
}
