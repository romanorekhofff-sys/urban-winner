package dev.attention.app
import org.junit.Assert.*
import org.junit.Test
import java.time.*
class FocusLogicTest{
 @Test fun importantWorkOutranksTrivialUrgentWork(){val n=1000000L;assertTrue(FocusLogic.score(10,0,n+7*86400000L,n)>FocusLogic.score(2,0,n+3600000L,n))}
 @Test fun approachingDeadlineRaisesScore(){val n=1000000L;assertTrue(FocusLogic.score(6,0,n+3600000L,n)>FocusLogic.score(6,0,n+7*86400000L,n))}
 @Test fun boundedProgressBonus(){val n=1000000L;assertEquals(.6,FocusLogic.score(7,90,n+3600000L,n)-FocusLogic.score(7,0,n+3600000L,n),.0001)}
 @Test fun noDistantProgressBonus(){val n=1000000L;assertEquals(FocusLogic.score(7,90,n+7*86400000L,n),FocusLogic.score(7,0,n+7*86400000L,n),.0001)}
 @Test fun endOfDay(){val zone=ZoneId.of("Europe/Moscow");assertEquals(LocalTime.of(23,59,59),Instant.ofEpochMilli(FocusLogic.deadline("2026-09-24",-1,zone)).atZone(zone).toLocalTime())}
 @Test fun followsTimezone(){assertEquals(3600000L,FocusLogic.deadline("2026-09-24",540,ZoneId.of("Europe/Berlin"))-FocusLogic.deadline("2026-09-24",540,ZoneId.of("Europe/Moscow")))}
 @Test fun beforeAndAfterNine(){val now=ZonedDateTime.parse("2026-09-24T08:59:00+03:00[Europe/Moscow]");assertEquals(now.toLocalDate(),FocusLogic.nextSummary(now,9,0).toLocalDate());assertEquals(now.toLocalDate().plusDays(1),FocusLogic.nextSummary(now.plusMinutes(1),9,0).toLocalDate())}
 @Test fun dstKeepsWallClock(){val next=FocusLogic.nextSummary(ZonedDateTime.parse("2026-10-24T10:00:00+02:00[Europe/Berlin]"),9,0);assertEquals(9,next.hour);assertEquals(ZoneOffset.ofHours(1),next.offset)}
 @Test fun completionReopen(){assertEquals(200L,FocusLogic.completedAt(100,null,200));assertEquals(100L,FocusLogic.completedAt(100,100,200));assertNull(FocusLogic.completedAt(99,100,200))}
 @Test fun urgencyBounded(){assertEquals(1.0,FocusLogic.urgency(1,100),.0001);assertTrue(FocusLogic.urgency(9999999999,100) in 0.0..1.0)}
}
