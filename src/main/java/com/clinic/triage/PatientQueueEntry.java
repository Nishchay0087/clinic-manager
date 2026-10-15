package com.clinic.triage;

import java.time.*;
import java.util.Comparator;
import java.util.Map;

/** Encapsulated immutable queue snapshot; Comparator provides polymorphic ordering. */
public record PatientQueueEntry(long triageId,long patientId,long appointmentId,String patientName,
        String severityLevel,LocalDateTime arrivalTime,String queueStatus,LocalDateTime priorityTimestamp,Long assignedClinician) {
    public static final Map<String,Integer> DEFAULT_PRIORITY=Map.of("EMERGENCY",0,"URGENT",1,"ROUTINE",2);
    public static Comparator<PatientQueueEntry> ordering(Map<String,Integer> priority) {
        return Comparator.comparingInt((PatientQueueEntry e)->priority.getOrDefault(e.severityLevel(),Integer.MAX_VALUE))
                .thenComparing(PatientQueueEntry::arrivalTime).thenComparingLong(PatientQueueEntry::triageId);
    }
    public long waitingMinutes(Clock clock){return Math.max(0,Duration.between(arrivalTime,LocalDateTime.now(clock)).toMinutes());}
}
