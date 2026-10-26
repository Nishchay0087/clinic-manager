package com.clinic.listener;
import com.clinic.config.Database;import com.clinic.scheduler.*;import jakarta.servlet.*;import jakarta.servlet.annotation.WebListener;import java.util.concurrent.*;import java.util.*;
@WebListener public final class AppListener implements ServletContextListener {
 private ScheduledExecutorService scheduler;
 @Override public void contextInitialized(ServletContextEvent e){TimeZone.setDefault(TimeZone.getTimeZone(Database.value("CLINIC_TIMEZONE","Asia/Kolkata")));Database.initialize();var cookies=e.getServletContext().getSessionCookieConfig();cookies.setHttpOnly(true);cookies.setSecure(Boolean.parseBoolean(Database.value("COOKIE_SECURE","false")));if(!Boolean.parseBoolean(Database.value("SCHEDULER_ENABLED","true")))return;scheduler=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"clinic-background");t.setDaemon(true);return t;});scheduler.scheduleWithFixedDelay(new ReminderService(),20,60,TimeUnit.SECONDS);scheduler.scheduleWithFixedDelay(new BookingCleanupService(),30,60,TimeUnit.SECONDS);}
 @Override public void contextDestroyed(ServletContextEvent e){if(scheduler!=null){scheduler.shutdownNow();try{scheduler.awaitTermination(5,TimeUnit.SECONDS);}catch(InterruptedException x){Thread.currentThread().interrupt();}}try{Database.close();}catch(Exception ignored){}}
}
