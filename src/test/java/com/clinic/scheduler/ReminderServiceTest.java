package com.clinic.scheduler;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
class ReminderServiceTest {
 @Test void leadWindowExcludesPastIncludesBoundary(){var now=LocalDateTime.of(2026,1,1,10,0);assertTrue(ReminderService.eligible(now.plusHours(24),now,24));assertFalse(ReminderService.eligible(now.plusHours(25),now,24));assertFalse(ReminderService.eligible(now,now,24));assertFalse(ReminderService.eligible(now.minusSeconds(1),now,24));}
 @Test void retriesAreBoundedAndBackedOff(){var now=LocalDateTime.of(2026,1,1,10,0);assertTrue(ReminderService.shouldRetry(0,null,now));assertFalse(ReminderService.shouldRetry(1,now.minusMinutes(9),now));assertTrue(ReminderService.shouldRetry(1,now.minusMinutes(10),now));assertFalse(ReminderService.shouldRetry(3,now.minusDays(1),now));}
}
