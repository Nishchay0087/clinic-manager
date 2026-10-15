package com.clinic.dao;
import java.sql.*;import java.util.*;
/** Persistence abstraction; ownership checks remain mandatory in DocumentService. */
public interface IMedicalDocumentDAO {Map<String,Object> storageMetadata(Connection c,long documentId)throws SQLException;void archive(Connection c,long documentId)throws SQLException;}
