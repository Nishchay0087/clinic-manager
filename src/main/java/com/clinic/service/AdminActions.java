package com.clinic.service;
import com.clinic.config.Database;import com.clinic.util.Sql;import java.sql.*;
public final class AdminActions {public void auditUserCreation(long actor,long created)throws SQLException{try(var c=Database.getConnection()){Sql.audit(c,actor,"ACCOUNT_CREATED","USER",created);}}}
