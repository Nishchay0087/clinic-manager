package com.clinic.scheduler;

import com.clinic.config.Database;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/** Persistent unique appointment/type jobs make in-app notification delivery atomic and deduplicated. */
public class ReminderService implements Runnable {
    private static final Logger LOG=Logger.getLogger(ReminderService.class.getName());
    private final AtomicBoolean running=new AtomicBoolean();
    private final EmailSender email;
    public interface EmailSender { boolean configured(); void send(String recipient,String subject,String message)throws Exception; }
    public ReminderService(){this(new SmtpSender());}
    public ReminderService(EmailSender email){this.email=Objects.requireNonNull(email);}
    public static boolean eligible(LocalDateTime appointment,LocalDateTime now,int leadHours){return appointment.isAfter(now)&&!appointment.isAfter(now.plusHours(leadHours));}
    public static boolean shouldRetry(int attempts,LocalDateTime lastAttempt,LocalDateTime now){return attempts<3&&(lastAttempt==null||!lastAttempt.plusMinutes(5L*(1L<<Math.min(attempts,3))).isAfter(now));}
    @Override public void run(){if(!running.compareAndSet(false,true))return;try{enqueue();deliver();}catch(SQLException e){LOG.warning("Reminder job failed; pending reminders will be retried.");}finally{running.set(false);}}
    private void enqueue()throws SQLException{
        try(Connection c=Database.getConnection()){
            int hours=24;try(PreparedStatement s=c.prepareStatement("SELECT setting_value FROM clinic_settings WHERE setting_key='reminder_hours'");ResultSet r=s.executeQuery()){if(r.next())try{hours=Math.max(1,Math.min(168,Integer.parseInt(r.getString(1))));}catch(NumberFormatException ignored){}}
            for(String type:email.configured()?List.of("IN_APP","EMAIL"):List.of("IN_APP"))try(PreparedStatement s=c.prepareStatement("INSERT IGNORE INTO reminder_jobs(appointment_id,reminder_type,scheduled_at,status,attempt_count) SELECT appointment_id,?,DATE_SUB(TIMESTAMP(appointment_date,start_time),INTERVAL ? HOUR),'PENDING',0 FROM appointments WHERE status IN ('BOOKED','CONFIRMED') AND TIMESTAMP(appointment_date,start_time)>CURRENT_TIMESTAMP AND TIMESTAMP(appointment_date,start_time)<=DATE_ADD(CURRENT_TIMESTAMP,INTERVAL ? HOUR)")){s.setString(1,type);s.setInt(2,hours);s.setInt(3,hours);s.executeUpdate();}
            try(PreparedStatement s=c.prepareStatement("UPDATE reminder_jobs j JOIN appointments a ON a.appointment_id=j.appointment_id SET j.status='CANCELLED' WHERE j.status IN ('PENDING','RETRY') AND (a.status NOT IN ('BOOKED','CONFIRMED') OR TIMESTAMP(a.appointment_date,a.start_time)<=CURRENT_TIMESTAMP)")){s.executeUpdate();}
        }
    }
    private void deliver()throws SQLException{
        for(int n=0;n<100;n++)try(Connection c=Database.getConnection()){c.setAutoCommit(false);try{
            long id;long user;String address;String type;String when;int attempts;
            try(PreparedStatement s=c.prepareStatement("SELECT j.reminder_id,j.reminder_type,j.attempt_count,u.user_id,u.email,a.appointment_date,a.start_time FROM reminder_jobs j JOIN appointments a ON a.appointment_id=j.appointment_id JOIN patients p ON p.patient_id=a.patient_id JOIN users u ON u.user_id=p.user_id WHERE j.status IN ('PENDING','RETRY') AND j.scheduled_at<=CURRENT_TIMESTAMP AND j.attempt_count<3 AND (j.last_attempt_at IS NULL OR j.last_attempt_at<=DATE_SUB(CURRENT_TIMESTAMP,INTERVAL (5 * POW(2,j.attempt_count)) MINUTE)) AND a.status IN ('BOOKED','CONFIRMED') AND TIMESTAMP(a.appointment_date,a.start_time)>CURRENT_TIMESTAMP ORDER BY j.reminder_id LIMIT 1 FOR UPDATE SKIP LOCKED");ResultSet r=s.executeQuery()){
                if(!r.next()){c.rollback();return;}id=r.getLong(1);type=r.getString(2);attempts=r.getInt(3);user=r.getLong(4);address=r.getString(5);when=r.getDate(6)+" at "+r.getTime(7);
            }
            String message="Your clinic appointment is scheduled for "+when+". Sign in to review or manage your booking.";String status="SENT";
            if(type.equals("IN_APP"))try(PreparedStatement s=c.prepareStatement("INSERT INTO notifications(user_id,title,message,notification_type,read_status) VALUES(?,'Appointment reminder',?,'REMINDER',FALSE)")){s.setLong(1,user);s.setString(2,message);s.executeUpdate();}
            else if(!email.configured())status="SKIPPED";
            else try{email.send(address,"Clinic appointment reminder",message);}catch(Exception e){status=attempts+1>=3?"FAILED":"RETRY";LOG.warning("Email reminder delivery failed; in-app reminder remains available.");}
            try(PreparedStatement s=c.prepareStatement("UPDATE reminder_jobs SET status=?,attempt_count=attempt_count+1,last_attempt_at=CURRENT_TIMESTAMP WHERE reminder_id=?")){s.setString(1,status);s.setLong(2,id);s.executeUpdate();}c.commit();
        }catch(SQLException e){c.rollback();throw e;}}
    }
    private static final class SmtpSender implements EmailSender{
        private String env(String key){return System.getenv(key);}
        public boolean configured(){return java.util.stream.Stream.of("SMTP_HOST","SMTP_USERNAME","SMTP_PASSWORD","SMTP_FROM").allMatch(k->env(k)!=null&&!env(k).isBlank());}
        public void send(String recipient,String subject,String body)throws Exception{
            Properties p=new Properties();p.put("mail.smtp.host",env("SMTP_HOST"));p.put("mail.smtp.port",Optional.ofNullable(env("SMTP_PORT")).orElse("587"));p.put("mail.smtp.auth","true");p.put("mail.smtp.starttls.enable","true");p.put("mail.smtp.starttls.required","true");p.put("mail.smtp.connectiontimeout","5000");p.put("mail.smtp.timeout","5000");p.put("mail.smtp.writetimeout","5000");
            Session session=Session.getInstance(p,new Authenticator(){protected PasswordAuthentication getPasswordAuthentication(){return new PasswordAuthentication(env("SMTP_USERNAME"),env("SMTP_PASSWORD"));}});
            MimeMessage mail=new MimeMessage(session);mail.setFrom(new InternetAddress(env("SMTP_FROM")));mail.setRecipients(Message.RecipientType.TO,InternetAddress.parse(recipient,false));mail.setSubject(subject,"UTF-8");mail.setText(body,"UTF-8");Transport.send(mail);
        }
    }
}
