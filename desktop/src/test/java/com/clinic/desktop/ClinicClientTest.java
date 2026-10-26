package com.clinic.desktop;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ClinicClientTest {
    @Test void preservesDeploymentContextAndRejectsCredentialsAndAmbiguousUrls(){
        assertEquals("https://example.com/clinic-manager/",ClinicClient.normalize("https://example.com/clinic-manager/").toString());
        assertEquals("http://localhost:8081/clinic-manager/",ClinicClient.normalize("http://localhost:8081/clinic-manager").toString());
        for(String bad:new String[]{"file:///tmp/local","https://user:password@example.com","https://example.com?x=1","https://example.com/#token"})assertThrows(IllegalArgumentException.class,()->ClinicClient.normalize(bad));
    }
}
