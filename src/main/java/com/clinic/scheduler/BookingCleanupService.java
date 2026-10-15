package com.clinic.scheduler;

import com.clinic.config.Database;
import java.sql.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/** Releases temporary reservations only. Confirmed appointments are never expired. */
public class BookingCleanupService implements Runnable {
    private static final Logger LOG=Logger.getLogger(BookingCleanupService.class.getName());
    private final AtomicBoolean running=new AtomicBoolean();
    @Override public void run(){
        if(!running.compareAndSet(false,true))return;
        try(Connection c=Database.getConnection()){c.setAutoCommit(false);try{
            try(PreparedStatement s=c.prepareStatement("SELECT reservation_id FROM reservations WHERE status='ACTIVE' AND expires_at<CURRENT_TIMESTAMP ORDER BY reservation_id LIMIT 200 FOR UPDATE SKIP LOCKED");ResultSet r=s.executeQuery()){
                while(r.next()){long id=r.getLong(1);try(PreparedStatement u=c.prepareStatement("UPDATE reservations SET status='EXPIRED' WHERE reservation_id=? AND status='ACTIVE' AND expires_at<CURRENT_TIMESTAMP")){u.setLong(1,id);if(u.executeUpdate()==1)try(PreparedStatement a=c.prepareStatement("INSERT INTO audit_logs(actor_user_id,action,entity_type,entity_id,metadata) VALUES(NULL,'RESERVATION_EXPIRED','RESERVATION',?,'{}')")){a.setLong(1,id);a.executeUpdate();}}}
            }c.commit();
        }catch(SQLException e){c.rollback();throw e;}}
        catch(SQLException e){LOG.warning("Reservation cleanup failed; it will retry on the next scheduled run.");}
        finally{running.set(false);}
    }
}
