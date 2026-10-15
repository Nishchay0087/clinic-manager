// Run only against a fictional development DB. See docs/TESTING.md.
import com.clinic.config.Database;import com.clinic.scheduler.*;import java.sql.*;import java.util.*;
class JobSmoke {
 static long scalar(Connection c,String sql)throws Exception{try(var s=c.prepareStatement(sql);var r=s.executeQuery()){r.next();return r.getLong(1);}}
 public static void main(String[]args)throws Exception{try(var c=Database.getConnection()){
   try(var s=c.prepareStatement("INSERT INTO appointments(patient_id,doctor_id,appointment_date,start_time,end_time,reason_for_visit,status) VALUES(1,2,CURRENT_DATE,'17:00','17:30','FICTIONAL job test','BOOKED')",Statement.RETURN_GENERATED_KEYS)){s.executeUpdate();try(var r=s.getGeneratedKeys()){r.next();long appointment=r.getLong(1);
    ReminderService service=new ReminderService(new ReminderService.EmailSender(){public boolean configured(){return false;}public void send(String a,String b,String d){throw new AssertionError("Email must not be sent");}});service.run();long before=scalar(c,"SELECT COUNT(*) FROM notifications WHERE notification_type='REMINDER'");service.run();if(before!=scalar(c,"SELECT COUNT(*) FROM notifications WHERE notification_type='REMINDER'"))throw new AssertionError("Reminder duplicate");if(scalar(c,"SELECT COUNT(*) FROM reminder_jobs WHERE appointment_id="+appointment+" AND reminder_type='IN_APP' AND status='SENT'")!=1)throw new AssertionError("Reminder not delivered");
    try(var cancel=c.prepareStatement("UPDATE appointments SET status='CANCELLED' WHERE appointment_id=?")){cancel.setLong(1,appointment);cancel.executeUpdate();}
   }}
   long completed=scalar(c,"SELECT COUNT(*) FROM appointments WHERE status='COMPLETED'");long reservation;
   try(var s=c.prepareStatement("INSERT INTO reservations(doctor_id,patient_id,start_datetime,end_datetime,expires_at,status) VALUES(1,1,NOW(),DATE_ADD(NOW(),INTERVAL 30 MINUTE),DATE_SUB(NOW(),INTERVAL 1 MINUTE),'ACTIVE')",Statement.RETURN_GENERATED_KEYS)){s.executeUpdate();try(var r=s.getGeneratedKeys()){r.next();reservation=r.getLong(1);}}
   new BookingCleanupService().run();new BookingCleanupService().run();if(scalar(c,"SELECT COUNT(*) FROM reservations WHERE reservation_id="+reservation+" AND status='EXPIRED'")!=1)throw new AssertionError("Reservation not expired");if(completed!=scalar(c,"SELECT COUNT(*) FROM appointments WHERE status='COMPLETED'"))throw new AssertionError("Clinical records modified");
   System.out.println("PASS MySQL reminder delivery/deduplication, absent-SMTP fallback, reservation cleanup and clinical-record retention");
  }finally{Database.close();}}
}
