package com.clinic.triage;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class PatientQueueEntryTest {
 private PatientQueueEntry entry(long id,String severity,LocalDateTime time){return new PatientQueueEntry(id,id,id,"Fictional patient",severity,time,"WAITING",time,1L);}
 @Test void sortsSeverityThenArrivalThenStableId(){LocalDateTime now=LocalDateTime.of(2026,1,1,10,0);PriorityQueue<PatientQueueEntry> q=new PriorityQueue<>(PatientQueueEntry.ordering(PatientQueueEntry.DEFAULT_PRIORITY));q.add(entry(1,"ROUTINE",now.minusHours(2)));q.add(entry(4,"URGENT",now));q.add(entry(3,"URGENT",now));q.add(entry(5,"URGENT",now.minusMinutes(1)));q.add(entry(6,"EMERGENCY",now));assertEquals(List.of(6L,5L,3L,4L,1L),java.util.stream.IntStream.range(0,5).mapToObj(i->q.remove().triageId()).toList());}
 @Test void waitingTimeIsNonnegative(){LocalDateTime now=LocalDateTime.of(2026,1,1,10,0);Clock clock=Clock.fixed(now.toInstant(ZoneOffset.UTC),ZoneOffset.UTC);assertEquals(15,entry(1,"ROUTINE",now.minusMinutes(15)).waitingMinutes(clock));assertEquals(0,entry(2,"ROUTINE",now.plusMinutes(15)).waitingMinutes(clock));}
 @Test void patientCannotChangePriority(){assertThrows(com.clinic.exception.AppException.class,()->new TriageService().update(1,"PATIENT",1,"EMERGENCY","self-assessed"));}
}
