package com.clinic.config;

import com.clinic.util.Sql;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Explicit MySQL migrations. Never runs demo seeds, drops tables or changes existing passwords. */
public final class SchemaSetup {
    private static final List<String> MIGRATIONS=List.of("V001__initial_schema.sql","V002__desktop_and_persistent_documents.sql");
    public static void main(String[] args)throws Exception {
        boolean baseline=Arrays.asList(args).contains("--baseline-existing");
        try(var c=Database.getConnection()) {
            if(!c.getMetaData().getDatabaseProductName().equals("MySQL"))throw new IllegalStateException("Setup requires MySQL 8.0 or later.");
            try(var lock=c.prepareStatement("SELECT GET_LOCK(CONCAT(DATABASE(),':schema'),30)");var r=lock.executeQuery()){if(!r.next()||r.getInt(1)!=1)throw new IllegalStateException("Another migration is running. Try again later.");}
            try {
                boolean existing=Sql.one(c,"SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='users'")!=null;
                boolean tracking=Sql.one(c,"SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='schema_migrations'")!=null;
                if(existing&&!tracking&&!baseline)throw new IllegalStateException("Existing database detected. Back it up, verify it uses V001, then run with --baseline-existing. No data changed.");
                try(var statement=c.createStatement()){statement.execute("CREATE TABLE IF NOT EXISTS schema_migrations(version VARCHAR(100) PRIMARY KEY, checksum CHAR(64) NOT NULL, success BOOLEAN NOT NULL DEFAULT FALSE, applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");}
                for(String name:MIGRATIONS) {
                    byte[] bytes;try(var input=Objects.requireNonNull(SchemaSetup.class.getResourceAsStream("/db/migrations/"+name),"Missing migration")){bytes=input.readAllBytes();}
                    String checksum=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
                    var applied=Sql.one(c,"SELECT checksum,success FROM schema_migrations WHERE version=?",name);
                    if(applied!=null){if(!Boolean.parseBoolean(applied.get("success").toString())||!checksum.equals(applied.get("checksum")))throw new IllegalStateException("Migration incomplete or modified: "+name+". Inspect and restore from backup before retrying.");continue;}
                    if(existing&&!tracking&&baseline&&name.startsWith("V001")){Sql.update(c,"INSERT INTO schema_migrations(version,checksum,success) VALUES(?,?,TRUE)",name,checksum);continue;}
                    Sql.update(c,"INSERT INTO schema_migrations(version,checksum,success) VALUES(?,?,FALSE)",name,checksum);
                    String sql=new String(bytes,StandardCharsets.UTF_8).replaceAll("(?m)^--.*$","");
                    try(var statement=c.createStatement()){for(String part:sql.split(";"))if(!part.isBlank())statement.execute(part);}
                    Sql.update(c,"UPDATE schema_migrations SET success=TRUE WHERE version=?",name);System.out.println("Applied "+name);
                }
                System.out.println("Schema ready. No sample accounts, appointments or medical records were inserted.");
            }finally{try(var statement=c.createStatement()){statement.execute("SELECT RELEASE_LOCK(CONCAT(DATABASE(),':schema'))");}}
        }finally{Database.close();}
    }
}
