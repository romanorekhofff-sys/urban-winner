package dev.attention.app
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
val Ink=Color(0xFF0B0C0F);val Surface=Color(0xFF14161C);val Raised=Color(0xFF1D2028);val TextPrimary=Color(0xFFEAEAF1);val Muted=Color(0xFF9A9EAE);val Accent=Color(0xFFB7A7F4);val Ru=Locale("ru")
fun importanceColor(n:Int):Color{val f=(n-1).coerceIn(0,9)/9f;return if(f<.5f)lerp(Color(0xFF9980DF),Color(0xFFDB71B0),f*2) else lerp(Color(0xFFDB71B0),Color(0xFFF0787E),(f-.5f)*2)}
fun urgencyColor(t:Task,now:Long):Color{if(t.due<now)return Color(0xFFEAA076);val u=FocusLogic.urgency(t.due,now).toFloat();return when{u<.4f->lerp(Color(0xFF83BDA2),Color(0xFFBDD082),u/.4f);u<.75f->lerp(Color(0xFFBDD082),Color(0xFFDDCF83),(u-.4f)/.35f);else->lerp(Color(0xFFDDCF83),Color(0xFFE8B471),(u-.75f)/.25f)}}
fun dateLabel(t:Task):String{val d=LocalDate.parse(t.date);val today=LocalDate.now();val label=when(d){today->"Сегодня";today.plusDays(1)->"Завтра";today.minusDays(1)->"Вчера";else->d.format(DateTimeFormatter.ofPattern(if(d.year==today.year)"d MMM" else "d MMM yyyy",Ru))};return label+if(t.minute<0)" · до конца дня" else " · %02d:%02d".format(t.minute/60,t.minute%60)}
fun remaining(t:Task,now:Long):String{val delta=t.due-now;val m=abs(delta)/60000;val a=when{m<1->"< 1 мин";m<60->"$m мин";m<1440->"${m/60} ч"+(if(m%60>0)" ${m%60} мин" else "");else->"${m/1440} д ${m%1440/60} ч"};return if(delta<0)"Просрочено $a" else "Осталось $a"}
@Composable fun FocusTheme(content:@Composable ()->Unit){MaterialTheme(colorScheme=darkColorScheme(primary=Accent,onPrimary=Ink,background=Ink,surface=Surface,onSurface=TextPrimary,onBackground=TextPrimary,secondary=Accent,surfaceVariant=Raised,onSurfaceVariant=Muted,outline=Color(0xFF444958)),content=content)}
@Composable fun haptic():()->Unit{val view=LocalView.current;return{view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)}}
@Composable fun Glyph(icon:ImageVector,label:String,onClick:()->Unit,color:Color=Muted){IconButton(onClick=onClick){Icon(icon,label,tint=color,modifier=Modifier.size(21.dp))}}
@Composable fun SectionLabel(text:String,trailing:String=""){Row(Modifier.fillMaxWidth().padding(top=18.dp,bottom=10.dp),verticalAlignment=Alignment.CenterVertically){Text(text,Modifier.weight(1f),color=Muted,fontSize=12.sp,fontWeight=FontWeight.Medium,letterSpacing=1.sp);if(trailing.isNotEmpty())Text(trailing,color=Muted,fontSize=12.sp)}}
@Composable fun Pill(text:String,selected:Boolean=false,onClick:()->Unit){Box(Modifier.clip(RoundedCornerShape(12.dp)).background(if(selected)Raised else Color.Transparent).clickable(onClick=onClick).padding(horizontal=12.dp,vertical=11.dp),contentAlignment=Alignment.Center){Text(text,color=if(selected)TextPrimary else Muted,fontSize=13.sp,fontWeight=if(selected)FontWeight.Medium else FontWeight.Normal,maxLines=1)}}
@Composable fun QuietEmpty(title:String,subtitle:String,icon:ImageVector=Icons.Rounded.CheckCircleOutline){Column(Modifier.fillMaxWidth().padding(vertical=50.dp,horizontal=20.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(icon,null,tint=Accent.copy(alpha=.65f),modifier=Modifier.size(36.dp));Spacer(Modifier.height(20.dp));Text(title,color=TextPrimary,fontSize=20.sp);Spacer(Modifier.height(8.dp));Text(subtitle,color=Muted,fontSize=14.sp,textAlign=androidx.compose.ui.text.style.TextAlign.Center)}}
@Composable fun TaskCard(t:Task,now:Long,open:()->Unit,complete:()->Unit,progress:()->Unit,actions:()->Unit,modifier:Modifier=Modifier){
 val tap=haptic();val currentComplete by rememberUpdatedState(complete);val currentActions by rememberUpdatedState(actions);val scope=rememberCoroutineScope();var finishing by remember(t.id,t.done){mutableStateOf(false)};val scale by animateFloatAsState(if(finishing).97f else 1f,tween(180),label="finish")
 fun toggle(){if(finishing)return;if(t.subtasks.isNotEmpty()){tap();open();return};tap();if(t.done)currentComplete() else{finishing=true;scope.launch{delay(200);currentComplete()}}}
 var drag by remember(t.id){mutableFloatStateOf(0f)};val offset by animateFloatAsState(drag,label="swipe")
 Box(modifier.scale(scale).clip(RoundedCornerShape(20.dp)).background(Raised)){
  Row(Modifier.matchParentSize().padding(horizontal=20.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Icon(Icons.Rounded.Done,"Выполнить",tint=Accent);Icon(Icons.Rounded.MoreHoriz,"Действия",tint=Muted)}
  Column(Modifier.offset{IntOffset(offset.toInt(),0)}.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface).pointerInput(t.id,t.done){detectHorizontalDragGestures(onDragEnd={if(drag>110)toggle() else if(drag< -110){tap();currentActions()};drag=0f},onDragCancel={drag=0f}){change,amount->change.consume();drag=(drag+amount).coerceIn(-220f,220f)}}.clickable(onClick=open).drawBehind{drawRoundRect(importanceColor(t.importance).copy(alpha=if(t.done).3f else .8f),topLeft=Offset(0f,22.dp.toPx()),size=androidx.compose.ui.geometry.Size(2.dp.toPx(),size.height-44.dp.toPx()),cornerRadius=androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))}.padding(start=9.dp,end=16.dp,top=10.dp,bottom=13.dp).alpha(if(t.done||finishing).65f else 1f)){
   Row(verticalAlignment=Alignment.CenterVertically){IconButton(onClick={toggle()},modifier=Modifier.size(44.dp)){Icon(if(t.done||finishing)Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,if(t.done)"Вернуть в активные" else "Завершить ${t.title}",tint=if(t.done||finishing)Accent else Color(0xFF626879),modifier=Modifier.size(23.dp))};Text(t.title,Modifier.weight(1f).padding(end=8.dp),color=TextPrimary,fontSize=16.sp,fontWeight=FontWeight.Medium,maxLines=2,overflow=TextOverflow.Ellipsis);Text(t.importance.toString(),color=importanceColor(t.importance),fontSize=18.sp,fontWeight=FontWeight.Medium,modifier=Modifier.semantics{contentDescription="Важность ${t.importance} из 10"})}
   Column(Modifier.padding(start=44.dp)){
    Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(4.dp).background(if(t.done)Muted else urgencyColor(t,now),CircleShape));Spacer(Modifier.width(6.dp));Text(if(t.done)"Выполнено · "+Instant.ofEpochMilli(t.completedAt?:t.createdAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM, HH:mm",Ru)) else dateLabel(t),Modifier.weight(1f),color=if(t.done)Muted else urgencyColor(t,now),fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis)}
    Row(verticalAlignment=Alignment.CenterVertically){Text(if(t.done)t.category else remaining(t,now),Modifier.weight(1f),color=Muted,fontSize=11.sp,maxLines=1);Box(Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick=progress).padding(start=12.dp,top=9.dp,bottom=8.dp).semantics{contentDescription="Прогресс ${t.progress} процентов, изменить"}){Text("${t.progress}%",fontSize=12.sp,color=TextPrimary)}}
    if(t.workLabel().isNotEmpty())Text(t.workLabel(),fontSize=10.sp,color=Muted,modifier=Modifier.padding(bottom=8.dp))
    Box(Modifier.fillMaxWidth().height(2.dp).background(Color(0xFF282C35),CircleShape)){val amount by animateFloatAsState(t.progress/100f,tween(220),label="progress");Box(Modifier.fillMaxWidth(amount).fillMaxHeight().background(Color(0xFFB7BDCC),CircleShape))}
   }
  }
 }
}
