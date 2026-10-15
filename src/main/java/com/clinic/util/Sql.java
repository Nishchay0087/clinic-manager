package com.clinic.util;
import java.sql.*;import java.util.*;
public final class Sql {
 private Sql(){}
 public static List<Map<String,Object>> query(Connection c,String sql,Object...args)throws SQLException{
  try(var s=c.prepareStatement(sql)){bind(s,args);try(var r=s.executeQuery()){var rows=new ArrayList<Map<String,Object>>();var md=r.getMetaData();while(r.next()){var row=new LinkedHashMap<String,Object>();for(int i=1;i<=md.getColumnCount();i++){Object v=r.getObject(i);if(v instanceof java.sql.Date || v instanceof Time || v instanceof Timestamp || v instanceof java.time.temporal.TemporalAccessor)v=v.toString();row.put(md.getColumnLabel(i),v);}rows.add(row);}return rows;}}
 }
 public static Map<String,Object> one(Connection c,String sql,Object...args)throws SQLException{var a=query(c,sql,args);return a.isEmpty()?null:a.getFirst();}
 public static long insert(Connection c,String sql,Object...args)throws SQLException{try(var s=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)){bind(s,args);s.executeUpdate();try(var r=s.getGeneratedKeys()){return r.next()?r.getLong(1):0;}}}
 public static int update(Connection c,String sql,Object...args)throws SQLException{try(var s=c.prepareStatement(sql)){bind(s,args);return s.executeUpdate();}}
 public static void bind(PreparedStatement s,Object...args)throws SQLException{for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);}
 public static void audit(Connection c,long actor,String action,String type,long id)throws SQLException{update(c,"INSERT INTO audit_logs(actor_user_id,action,entity_type,entity_id,metadata) VALUES(?,?,?,?,?)",actor,action,type,id,"{}");}
}
