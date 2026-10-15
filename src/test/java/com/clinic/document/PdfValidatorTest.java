package com.clinic.document;
import com.clinic.exception.AppException;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.encryption.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;
class PdfValidatorTest {
    private byte[] pdf(boolean encrypted,boolean page)throws Exception{
        try(PDDocument d=new PDDocument();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            if(page){PDPage p=new PDPage();d.addPage(p);try(PDPageContentStream content=new PDPageContentStream(d,p)){content.beginText();content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),12);content.newLineAtOffset(20,700);content.showText("Fictional test report");content.endText();}}
            if(encrypted)d.protect(new StandardProtectionPolicy("owner","password",new AccessPermission()));d.save(out);return out.toByteArray();
        }
    }
    @Test void extractsTextAndHashes()throws Exception{var result=PdfValidator.validate("report.pdf","application/pdf",pdf(false,true));assertTrue(result.text().contains("Fictional test report"));assertEquals(64,result.sha256().length());}
    @Test void rejectsTraversalAndWrongMime()throws Exception{byte[] data=pdf(false,true);assertThrows(AppException.class,()->PdfValidator.validate("../report.pdf","application/pdf",data));assertThrows(AppException.class,()->PdfValidator.validate("report.pdf","text/html",data));}
    @Test void rejectsMalformedEncryptedAndEmpty()throws Exception{assertThrows(AppException.class,()->PdfValidator.validate("report.pdf","application/pdf","%PDF- broken".getBytes()));assertThrows(AppException.class,()->PdfValidator.validate("report.pdf","application/pdf",pdf(true,true)));assertThrows(AppException.class,()->PdfValidator.validate("report.pdf","application/pdf",pdf(false,false)));assertThrows(AppException.class,()->PdfValidator.validate("report.pdf","application/pdf",new byte[0]));}
    @Test void rejectsOversized(){assertThrows(AppException.class,()->PdfValidator.validate("report.pdf","application/pdf",new byte[PdfValidator.MAX_BYTES+1]));}
    @Test void deniesUnrecognizedRole(){assertThrows(AppException.class,()->DocumentService.scope("UNKNOWN"));}
}
