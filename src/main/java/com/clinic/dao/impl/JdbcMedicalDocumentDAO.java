package com.clinic.dao.impl;
import com.clinic.dao.IMedicalDocumentDAO;import com.clinic.util.Sql;import java.sql.*;import java.util.*;
public final class JdbcMedicalDocumentDAO implements IMedicalDocumentDAO {
 public Map<String,Object> storageMetadata(Connection c,long id)throws SQLException{return Sql.one(c,"SELECT patient_id AS patientId,storage_filename AS storageFilename,file_size AS fileSize FROM medical_documents WHERE document_id=? AND archived=FALSE",id);}
 public void archive(Connection c,long id)throws SQLException{Sql.update(c,"UPDATE medical_documents SET archived=TRUE WHERE document_id=?",id);}
}
