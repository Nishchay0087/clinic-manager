package com.clinic.service;

import com.clinic.config.Database;
import com.clinic.filter.SecurityFilter;
import com.clinic.util.Sql;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** Opaque desktop tokens survive instance restarts; only their hashes are stored. */
public final class DesktopSessions {
    public static String issue(Map<String,Object> user) throws SQLException {
        String token=SecurityFilter.token();
        try(var c=Database.getConnection()) {
            Sql.update(c,"DELETE FROM desktop_sessions WHERE expires_at<CURRENT_TIMESTAMP");
            Sql.update(c,"INSERT INTO desktop_sessions(token_hash,user_id,auth_version,expires_at) VALUES(?,?,?,?)",
                hash(token),user.get("id"),user.get("authVersion"),java.sql.Timestamp.from(Instant.now().plusSeconds(86400)));
        }
        return token;
    }
    public static Map<String,Object> resolve(String token) throws SQLException {
        if(token==null||!token.matches("[A-Za-z0-9_-]{43}"))return null;
        try(var c=Database.getConnection()) {
            return Sql.one(c,"SELECT u.user_id AS id,u.name,u.email,u.role,u.account_status AS accountStatus,u.auth_version AS authVersion FROM desktop_sessions s JOIN users u ON u.user_id=s.user_id WHERE s.token_hash=? AND s.expires_at>CURRENT_TIMESTAMP AND s.auth_version=u.auth_version AND u.account_status='ACTIVE'",hash(token));
        }
    }
    public static void revoke(String token) throws SQLException {
        try(var c=Database.getConnection()){Sql.update(c,"DELETE FROM desktop_sessions WHERE token_hash=?",hash(token));}
    }
    private static String hash(String value) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
