package com.clinic.dao;
import java.sql.*;
import java.util.*;
/** Read persistence operations; caller retains transaction and authorization ownership. */
public interface IAppointmentDAO {
    Map<String,Object> find(Connection connection,long appointmentId)throws SQLException;
    List<Map<String,Object>> history(Connection connection,long appointmentId)throws SQLException;
}
