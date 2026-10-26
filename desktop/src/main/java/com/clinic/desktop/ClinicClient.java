package com.clinic.desktop;

import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

/** Native client transport. Database credentials never leave the server. */
public final class ClinicClient implements AutoCloseable {
    private final URI base;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final ObjectMapper json=new ObjectMapper();
    private String token;
    public ClinicClient(String url){base=normalize(url);}
    public static URI normalize(String url) {
        URI u=URI.create(url.strip().replaceAll("/+$","")+"/");
        if(!Set.of("http","https").contains(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null||u.getQuery()!=null||u.getFragment()!=null)throw new IllegalArgumentException("Enter a server URL, such as https://clinic.example.com/clinic-manager");
        return u;
    }
    public String server(){return base.toString();}
    public JsonNode login(String email,String password)throws Exception{return authenticate("login",Map.of("email",email,"password",password));}
    public JsonNode register(String name,String email,String password,String confirm)throws Exception{return authenticate("register",Map.of("name",name,"email",email,"password",password,"confirmPassword",confirm));}
    private JsonNode authenticate(String action,Object values)throws Exception{
        var response=request("desktop-auth/"+action,"POST",json.writeValueAsBytes(values),"application/json");
        var result=json.readTree(response);token=result.path("token").asText();if(token.isBlank())throw new IOException("Server returned no login token.");return result.path("user");
    }
    public void resetPassword(String linkOrToken,String password,String confirmation)throws Exception{
        String value=linkOrToken.strip();if(value.contains("?token="))value=value.substring(value.indexOf("?token=")+7).split("&",2)[0];
        request("desktop-auth/reset","POST",json.writeValueAsBytes(Map.of("token",value,"password",password,"confirmPassword",confirmation)),"application/json");
    }
    public void logout()throws Exception{try{request("desktop-auth/logout","POST","{}".getBytes(StandardCharsets.UTF_8),"application/json");}finally{token=null;}}
    public JsonNode get(String resource)throws Exception{return json.readTree(request("api/"+resource,"GET",null,null));}
    public JsonNode post(String resource,Object values)throws Exception{return json.readTree(request("api/"+resource,"POST",json.writeValueAsBytes(values),"application/json"));}
    public byte[] download(String resource)throws Exception{return request("api/"+resource,"GET",null,null);}
    public JsonNode upload(Path file,Map<String,String> fields)throws Exception{
        if(Files.size(file)>10*1024*1024)throw new IOException("Choose a PDF no larger than 10 MB.");
        String boundary="Clinic"+UUID.randomUUID().toString().replace("-","");var out=new ByteArrayOutputStream();
        for(var e:fields.entrySet())out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\""+e.getKey()+"\"\r\n\r\n"+e.getValue()+"\r\n").getBytes(StandardCharsets.UTF_8));
        // Fixed transport filename prevents header injection; the user supplies the title separately.
        out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"document.pdf\"\r\nContent-Type: application/pdf\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        Files.copy(file,out);out.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
        return json.readTree(request("api/documents","POST",out.toByteArray(),"multipart/form-data; boundary="+boundary));
    }
    private byte[] request(String path,String method,byte[] body,String contentType)throws Exception{
        if(path.startsWith("/")||path.contains(".."))throw new IllegalArgumentException("Invalid API resource.");
        var builder=HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(60)).header("Accept","application/json").header("X-Clinic-Client","javafx");
        if(token!=null)builder.header("Authorization","Bearer "+token);
        if(contentType!=null)builder.header("Content-Type",contentType);
        builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofByteArray(body));
        var response=http.send(builder.build(),HttpResponse.BodyHandlers.ofByteArray());
        if(response.statusCode()<200||response.statusCode()>=300){String message="Server request failed ("+response.statusCode()+"). Check the server URL and connection.";try{message=json.readTree(response.body()).path("error").asText(message);}catch(Exception ignored){}throw new ApiException(response.statusCode(),message);}
        return response.body();
    }
    public static String encode(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
    public static final class ApiException extends IOException{public final int status;public ApiException(int status,String message){super(message);this.status=status;}}
    @Override public void close(){token=null;http.close();}
}
