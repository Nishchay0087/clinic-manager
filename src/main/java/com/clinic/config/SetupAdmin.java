package com.clinic.config;

import com.clinic.service.AuthService;
import com.clinic.util.Sql;
import org.mindrot.jbcrypt.BCrypt;
import java.util.*;

/** Run explicitly after importing schema/migrations. Never installs sample accounts. */
public final class SetupAdmin {
    public static boolean create(String name,String email,String password)throws Exception {
        AuthService.validatePassword(password);
        if(name==null||name.isBlank()||name.length()>120||email==null||email.length()>190||!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))throw new IllegalArgumentException("Valid administrator name and email required.");
        try(var c=Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                // Stable schema row serializes concurrent first-admin setup attempts.
                if(Sql.one(c,"SELECT setting_key FROM clinic_settings WHERE setting_key='clinic_name' FOR UPDATE")==null)throw new IllegalStateException("Import the schema first.");
                if(Sql.one(c,"SELECT user_id FROM users WHERE role='ADMIN' LIMIT 1")!=null){c.rollback();return false;}
                Sql.insert(c,"INSERT INTO users(name,email,password_hash,role) VALUES(?,?,?,'ADMIN')",name.strip(),email.strip().toLowerCase(Locale.ROOT),BCrypt.hashpw(password,BCrypt.gensalt(12)));
                c.commit();return true;
            }catch(Exception e){c.rollback();throw e;}
        }
    }
    public static void main(String[] args)throws Exception {
        try(var c=Database.getConnection()){
            if(Sql.one(c,"SELECT user_id FROM users WHERE role='ADMIN' LIMIT 1")!=null){System.out.println("Administrator already exists; no credentials changed.");Database.close();return;}
        }
        var console=System.console();
        String name=System.getenv("CLINIC_ADMIN_NAME"),email=System.getenv("CLINIC_ADMIN_EMAIL"),password=System.getenv("CLINIC_ADMIN_PASSWORD");
        if(console!=null){if(name==null)name=console.readLine("Administrator name: ");if(email==null)email=console.readLine("Administrator email: ");if(password==null){char[] chars=console.readPassword("Administrator password (10+ characters): ");password=new String(chars);Arrays.fill(chars,'\0');}}
        try{System.out.println(create(name,email,password)?"Administrator created. Sign in and create doctors; patients can register in JavaFX.":"Administrator already exists; no accounts or credentials changed.");}finally{Database.close();}
    }
}
