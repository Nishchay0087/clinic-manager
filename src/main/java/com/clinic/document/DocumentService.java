package com.clinic.document;

import com.clinic.config.Database;
import com.clinic.exception.AppException;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import java.nio.file.*;
import java.io.*;
import java.sql.*;
import java.util.*;

/** All physical file access passes through patient-level authorization. */
public class DocumentService {
    private static final Set<String> CATEGORIES = Set.of("LAB_REPORT","PRESCRIPTION","MEDICAL_HISTORY","REFERRAL","OTHER");
    private final Path storage;
    private final com.clinic.dao.IMedicalDocumentDAO dao=new com.clinic.dao.impl.JdbcMedicalDocumentDAO();
    public DocumentService() {
        String configured = System.getenv("DOCUMENT_STORAGE_DIR");
        storage = Path.of(configured == null || configured.isBlank() ? System.getProperty("user.home")+"/.clinic-manager/documents" : configured).toAbsolutePath().normalize();
    }
    DocumentService(Path storage) { this.storage=storage.toAbsolutePath().normalize(); }
    public List<Map<String,Object>> list(long userId,String role,Long patientId,String query) {
        return list(userId,role,patientId,query,Map.of());
    }
    public List<Map<String,Object>> list(long userId,String role,Long patientId,String query,Map<String,String> filters) {
        String q=query==null?"":query.trim();if(q.length()>200)throw new AppException(400,"Search is too long.");
        int page=positive(filters.get("page"),1,10000),size=positive(filters.get("pageSize"),25,100);
        String category=filters.getOrDefault("category","").trim();if(!category.isEmpty()&&!CATEGORIES.contains(category))throw new AppException(400,"Invalid document category.");
        java.time.LocalDate from=date(filters.get("from")),to=date(filters.get("to"));
        if(from!=null&&to!=null&&from.isAfter(to))throw new AppException(400,"The start date must precede the end date.");
        List<Object> args=new ArrayList<>();StringBuilder where=new StringBuilder(" WHERE m.archived=FALSE AND "+scope(role));
        if(!"ADMIN".equals(role))args.add(userId);
        if(patientId!=null){where.append(" AND m.patient_id=?");args.add(patientId);}
        if(!q.isEmpty()){where.append(" AND (m.title LIKE ? OR "+("ADMIN".equals(role)?"m.document_type":"m.extracted_text")+" LIKE ?)");args.add("%"+q+"%");args.add("%"+q+"%");}
        if(!category.isEmpty()){where.append(" AND m.document_type=?");args.add(category);}
        if(from!=null){where.append(" AND m.uploaded_at>=?");args.add(java.sql.Timestamp.valueOf(from.atStartOfDay()));}
        if(to!=null){where.append(" AND m.uploaded_at<?");args.add(java.sql.Timestamp.valueOf(to.plusDays(1).atStartOfDay()));}
        args.add(size);args.add((page-1)*size);
        String sql="SELECT m.*,u.name uploader_name FROM medical_documents m JOIN users u ON u.user_id=m.uploaded_by JOIN patients p ON p.patient_id=m.patient_id"+where+" ORDER BY m.uploaded_at DESC,m.document_id DESC LIMIT ? OFFSET ?";
        try(Connection c=Database.getConnection();PreparedStatement statement=c.prepareStatement(sql)){
            for(int i=0;i<args.size();i++)statement.setObject(i+1,args.get(i));
            List<Map<String,Object>> rows=new ArrayList<>();try(ResultSet r=statement.executeQuery()){while(r.next())rows.add(metadata(r,role));}return rows;
        }catch(SQLException e){throw new AppException(500,"Could not load documents.");}
    }
    private static int positive(String value,int fallback,int maximum){try{int n=value==null||value.isBlank()?fallback:Integer.parseInt(value);if(n<1||n>maximum)throw new NumberFormatException();return n;}catch(NumberFormatException e){throw new AppException(400,"Invalid page or page size.");}}
    private static java.time.LocalDate date(String value){try{return value==null||value.isBlank()?null:java.time.LocalDate.parse(value);}catch(java.time.DateTimeException e){throw new AppException(400,"Use dates in YYYY-MM-DD format.");}}
    public Map<String,Object> upload(long userId,String role,Long patientId,String category,String title,String description,Part file) {
        if("ADMIN".equals(role))throw new AppException(403,"Administrators can view document metadata only.");
        if(file==null || file.getSize()>PdfValidator.MAX_BYTES)throw new AppException(400,"Choose a PDF no larger than "+(PdfValidator.MAX_BYTES/1024/1024)+" MB.");
        if(category==null||!CATEGORIES.contains(category))throw new AppException(400,"Choose a valid document category.");
        if(title!=null&&title.length()>200 || description!=null&&description.length()>1000)throw new AppException(400,"Title or description is too long.");
        Path saved=null;
        try(Connection c=Database.getConnection()) {
            long patient=resolvePatient(c,userId,role,patientId);authorize(c,userId,role,patient,false);
            byte[] bytes;try(InputStream input=file.getInputStream()){bytes=input.readNBytes(PdfValidator.MAX_BYTES+1);}
            var pdf=PdfValidator.validate(file.getSubmittedFileName(),file.getContentType(),bytes);
            Files.createDirectories(storage);String name=UUID.randomUUID()+".pdf";saved=storage.resolve(name);Files.write(saved,bytes,StandardOpenOption.CREATE_NEW);
            try(PreparedStatement s=c.prepareStatement("INSERT INTO medical_documents(patient_id,uploaded_by,title,description,document_type,original_filename,storage_filename,mime_type,file_size,sha256_hash,extracted_text) VALUES(?,?,?,?,?,?,?,'application/pdf',?,?,?)",Statement.RETURN_GENERATED_KEYS)){
                s.setLong(1,patient);s.setLong(2,userId);s.setString(3,title==null||title.isBlank()?file.getSubmittedFileName().substring(0,Math.min(200,file.getSubmittedFileName().length())):title.trim());s.setString(4,description);s.setString(5,category);s.setString(6,file.getSubmittedFileName());s.setString(7,name);s.setLong(8,bytes.length);s.setString(9,pdf.sha256());s.setString(10,pdf.text());s.executeUpdate();try(ResultSet r=s.getGeneratedKeys()){r.next();return Map.of("documentId",r.getLong(1),"ocrNeeded",pdf.text().isBlank(),"message",pdf.text().isBlank()?"Uploaded. This scanned PDF needs OCR for text search; OCR is not enabled.":"Document uploaded and text indexed.");}
            }
        }catch(Exception e){if(saved!=null)try{Files.deleteIfExists(saved);}catch(IOException ignored){}if(e instanceof AppException a)throw a;throw new AppException(500,"Could not save this document.");}
    }
    public void stream(long userId,String role,long id,boolean download,HttpServletResponse response) {
        try(Connection c=Database.getConnection();PreparedStatement s=c.prepareStatement("SELECT patient_id,storage_filename,file_size FROM medical_documents WHERE document_id=? AND archived=FALSE")){
            s.setLong(1,id);try(ResultSet r=s.executeQuery()){if(!r.next())throw new AppException(404,"Document not found.");authorize(c,userId,role,r.getLong(1),false);String name=r.getString(2);if(!name.matches("[a-f0-9\\-]{36}\\.pdf"))throw new AppException(500,"Document storage is invalid.");Path path=storage.resolve(name).normalize();if(!path.startsWith(storage)||!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new AppException(404,"Document file is unavailable.");response.setContentType("application/pdf");response.setHeader("Cache-Control","private, no-store");response.setHeader("X-Content-Type-Options","nosniff");response.setHeader("Content-Disposition",(download?"attachment":"inline")+"; filename=\"document-"+id+".pdf\"");response.setContentLengthLong(Files.size(path));Files.copy(path,response.getOutputStream());}
        }catch(SQLException|IOException e){throw new AppException(500,"Could not retrieve the document.");}
    }
    public void archive(long userId,String role,long id){
        try(Connection c=Database.getConnection()) {c.setAutoCommit(false);try{
            try(PreparedStatement s=c.prepareStatement("SELECT patient_id FROM medical_documents WHERE document_id=? AND archived=FALSE FOR UPDATE")){s.setLong(1,id);try(ResultSet r=s.executeQuery()){if(!r.next())throw new AppException(404,"Document not found.");authorize(c,userId,role,r.getLong(1),false);}}
            dao.archive(c,id);
            try(PreparedStatement s=c.prepareStatement("INSERT INTO audit_logs(actor_user_id,action,entity_type,entity_id,metadata) VALUES(?,'DOCUMENT_ARCHIVED','DOCUMENT',?,'{}')")){s.setLong(1,userId);s.setLong(2,id);s.executeUpdate();}c.commit();
        }catch(Exception e){c.rollback();throw e;}}
        catch(SQLException e){throw new AppException(500,"Could not archive the document.");}
    }
    private long resolvePatient(Connection c,long user,String role,Long patient)throws SQLException{
        if(!"PATIENT".equals(role)){if(patient==null)throw new AppException(400,"Select a patient.");return patient;}
        try(PreparedStatement s=c.prepareStatement("SELECT patient_id FROM patients WHERE user_id=?")){s.setLong(1,user);try(ResultSet r=s.executeQuery()){if(!r.next())throw new AppException(404,"Patient profile not found.");long own=r.getLong(1);if(patient!=null&&patient!=own)throw new AppException(403,"You can only upload your own records.");return own;}}
    }
    static String scope(String role){return switch(role){case "PATIENT"->"p.user_id=?";case "DOCTOR"->"EXISTS(SELECT 1 FROM appointments a JOIN doctors d ON d.doctor_id=a.doctor_id WHERE a.patient_id=m.patient_id AND d.user_id=? AND a.status<>'CANCELLED')";case "ADMIN"->"1=1";default->throw new AppException(403,"Access denied.");};}
    private void authorize(Connection c,long user,String role,long patient,boolean metadata)throws SQLException{
        if("ADMIN".equals(role)){if(metadata)return;throw new AppException(403,"Administrators cannot access medical document content.");}
        String sql=switch(role){case "PATIENT"->"SELECT 1 FROM patients WHERE patient_id=? AND user_id=?";case "DOCTOR"->"SELECT 1 FROM appointments a JOIN doctors d ON d.doctor_id=a.doctor_id WHERE a.patient_id=? AND d.user_id=? AND a.status<>'CANCELLED' LIMIT 1";default->throw new AppException(403,"Access denied.");};
        try(PreparedStatement s=c.prepareStatement(sql)){s.setLong(1,patient);s.setLong(2,user);try(ResultSet r=s.executeQuery()){if(!r.next())throw new AppException(403,"You do not have access to this patient's documents.");}}
    }
    private Map<String,Object> metadata(ResultSet r,String role)throws SQLException{Map<String,Object> m=new LinkedHashMap<>();m.put("documentId",r.getLong("document_id"));m.put("patientId",r.getLong("patient_id"));m.put("title",r.getString("title"));m.put("documentType",r.getString("document_type"));m.put("uploadedAt",r.getTimestamp("uploaded_at").toString());m.put("fileSize",r.getLong("file_size"));m.put("uploadedBy",r.getString("uploader_name"));m.put("canRead",!"ADMIN".equals(role));if(!"ADMIN".equals(role)){m.put("description",r.getString("description"));m.put("extractedText",r.getString("extracted_text"));m.put("sha256",r.getString("sha256_hash"));}return m;}
}
