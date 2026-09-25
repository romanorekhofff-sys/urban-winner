package dev.attention.app
import java.time.*
object FocusLogic {
 fun deadline(date:String,minute:Int,zone:ZoneId=ZoneId.systemDefault()):Long = LocalDate.parse(date).atTime(if(minute<0)LocalTime.of(23,59,59) else LocalTime.of(minute/60,minute%60)).atZone(zone).toInstant().toEpochMilli()
 fun urgency(due:Long,now:Long):Double=1.0/(1.0+((due-now)/3600000.0).coerceAtLeast(0.0)/24.0)
 fun score(importance:Int,progress:Int,due:Long,now:Long):Double=importance*(1.0+1.4*urgency(due,now))+(if(progress in 80..99 && due-now<=86400000L)0.6 else 0.0)
 fun nextSummary(now:ZonedDateTime,hour:Int,minute:Int):ZonedDateTime {val next=now.toLocalDate().atTime(hour,minute).atZone(now.zone);return if(next.isAfter(now))next else now.toLocalDate().plusDays(1).atTime(hour,minute).atZone(now.zone)}
 fun completedAt(progress:Int,previous:Long?,now:Long):Long?=if(progress==100)previous?:now else null
}
