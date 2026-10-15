package com.clinic.document;

import com.clinic.exception.AppException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

/** File validation is independent of HTTP and persistence for deterministic testing. */
public final class PdfValidator {
    public static final int MAX_BYTES = configuredLimit();
    private static int configuredLimit(){String value=com.clinic.config.Database.value("DOCUMENT_MAX_MB","10");int mb=Integer.parseInt(value);if(mb<1||mb>50)throw new IllegalArgumentException("DOCUMENT_MAX_MB must be between 1 and 50");return mb*1024*1024;}
    private PdfValidator() {}
    public record ValidatedPdf(String text, String sha256) {}
    public static ValidatedPdf validate(String filename, String mime, byte[] bytes) {
        if (filename == null || filename.isBlank() || filename.length() > 240 || filename.contains("/") || filename.contains("\\") || filename.chars().anyMatch(c -> c < 32)
                || !filename.toLowerCase(Locale.ROOT).endsWith(".pdf")) throw new AppException(400,"Choose a PDF with a simple filename.");
        if (!"application/pdf".equalsIgnoreCase(mime)) throw new AppException(400,"Only PDF documents are accepted.");
        if (bytes.length == 0 || bytes.length > MAX_BYTES) throw new AppException(400,"PDF must be nonempty and no larger than "+(MAX_BYTES/1024/1024)+" MB.");
        if (bytes.length < 5 || !new String(bytes,0,5, StandardCharsets.US_ASCII).equals("%PDF-")) throw new AppException(400,"The file is not a valid PDF.");
        try (PDDocument document = Loader.loadPDF(bytes)) {
            if (document.isEncrypted() || document.getNumberOfPages() == 0) throw new AppException(400,"Encrypted or empty PDFs are not supported.");
            if (document.getNumberOfPages()>1000) throw new AppException(400,"PDFs are limited to 1,000 pages. Split this document into smaller files.");
            StringBuilder extracted=new StringBuilder();
            Writer bounded=new Writer(){
                @Override public void write(char[] chars,int offset,int length){if(extracted.length()+length>2_000_000)throw new AppException(400,"This PDF contains too much text. Split it into smaller documents.");extracted.append(chars,offset,length);}
                @Override public void flush(){} @Override public void close(){}
            };
            new PDFTextStripper().writeText(document,bounded);
            String text=extracted.toString();
            return new ValidatedPdf(text,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        } catch (AppException e) { throw e; }
        catch (Exception e) { throw new AppException(400,"The PDF is malformed or password protected."); }
    }
}
