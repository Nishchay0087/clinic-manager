package com.clinic.dao;
import java.sql.*;
import java.util.*;
/** Clinical-record read persistence; the service authorizes the patient before calling. */
public interface IConsultationDAO {
    Map<String,Object> patient(Connection connection,long patientId)throws SQLException;
    List<Map<String,Object>> listByPatient(Connection connection,long patientId)throws SQLException;
}
