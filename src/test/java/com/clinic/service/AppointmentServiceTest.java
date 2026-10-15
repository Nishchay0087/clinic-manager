package com.clinic.service;

import com.clinic.exception.AppException;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AppointmentServiceTest {
    @Test void transitionGraphProtectsTerminalVisits(){
        assertTrue(AppointmentService.validTransition("BOOKED","CHECKED_IN"));
        assertTrue(AppointmentService.validTransition("CONFIRMED","NO_SHOW"));
        assertTrue(AppointmentService.validTransition("CHECKED_IN","IN_PROGRESS"));
        assertTrue(AppointmentService.validTransition("IN_PROGRESS","COMPLETED"));
        assertFalse(AppointmentService.validTransition("BOOKED","COMPLETED"));
        assertFalse(AppointmentService.validTransition("CHECKED_IN","CANCELLED"));
        for(String terminal:List.of("COMPLETED","CANCELLED","NO_SHOW"))for(String next:List.of("BOOKED","CONFIRMED","CHECKED_IN","IN_PROGRESS","COMPLETED","CANCELLED","NO_SHOW"))assertFalse(AppointmentService.validTransition(terminal,next));
        assertFalse(AppointmentService.validTransition(null,"BOOKED"));
    }
    @Test void schedulesPermitBreaksAndAdjacentWindowsButRejectOverlap(){
        var morning=new AppointmentService.Window(1,LocalTime.of(9,0),LocalTime.of(12,0));
        var afternoon=new AppointmentService.Window(1,LocalTime.of(13,0),LocalTime.of(17,0));
        assertDoesNotThrow(()->AppointmentService.validateWindows(List.of(morning,afternoon)));
        var adjacent=new AppointmentService.Window(1,LocalTime.of(12,0),LocalTime.of(13,0));
        assertDoesNotThrow(()->AppointmentService.validateWindows(List.of(morning,adjacent)));
        var overlapping=new AppointmentService.Window(1,LocalTime.of(11,30),LocalTime.of(13,0));
        assertThrows(AppException.class,()->AppointmentService.validateWindows(List.of(morning,overlapping)));
        assertThrows(AppException.class,()->AppointmentService.validateWindows(List.of(morning,morning)));
    }
    @Test void rejectsInvalidHoursDatesAndIdentifiers(){
        assertThrows(AppException.class,()->AppointmentService.window(Map.of("dayOfWeek",8,"startTime","09:00","endTime","17:00")));
        assertThrows(AppException.class,()->AppointmentService.window(Map.of("dayOfWeek",1,"startTime","17:00","endTime","09:00")));
        assertThrows(AppException.class,()->AppointmentService.time("09:00:01"));
        assertThrows(AppException.class,()->AppointmentService.date("2026-02-30"));
        assertThrows(AppException.class,()->AppointmentService.id("1 OR 1=1"));
        assertThrows(AppException.class,()->AppointmentService.id(-1));
        assertThrows(AppException.class,()->AppointmentService.end(LocalTime.of(23,45),30));
    }
    @Test void publicClinicalWritesRejectNonCliniciansBeforeDatabaseAccess(){
        assertThrows(AppException.class,()->new ConsultationService().save(1,"PATIENT",Map.of()));
        assertThrows(AppException.class,()->new ConsultationService().save(1,"ADMIN",Map.of()));
        assertThrows(AppException.class,()->new AppointmentService().status(1,"PATIENT",2,"COMPLETED"));
        assertThrows(AppException.class,()->new ConsultationService().records(1,"ADMIN",2L));
    }
}
